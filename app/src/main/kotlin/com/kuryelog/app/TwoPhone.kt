package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.core.Store
import com.kuryelog.app.economics.Economics
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.remote.RemoteHub
import com.kuryelog.app.map.BatteryRuntime
import com.kuryelog.app.map.DayRuntime
import com.kuryelog.app.map.NavRuntime
import com.kuryelog.app.orders.Orders
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.app.scooter.ScooterShiftLink
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.PhoneBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 2 telefon modunun modüller arası bağı (LINK.md 4.7; plan 4.3 "2 telefon modunda `:link` uzak uygulamayı takar"). Modüller birbirini
 * tanımaz; bağ burada, role göre:
 * - Kapalı: tek telefon (yerel scooter köprüsü, yerel sipariş olayları).
 * - Provider: `:orders`/`:economics`/`:map` uzak scooter köprüsünü kullanır (S11: scooter BLE açılmaz); vardiya, teklifler, aşamalar,
 *   gün özeti Receiver'a gider; Wolt/Bolt "Navigasyon" hedefi Receiver'da açılır (ulaşılamazsa burada); geç enerji düzeltme satırı olur.
 * - Receiver: `:map` aşama izleyicisi uzak sipariş olaylarını dinler; batarya özeti ve rota Provider'a gider; vardiya Provider'ı izler;
 *   enerji istekleri yerel scooter'dan yanıtlanır.
 */
object TwoPhone {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var jobs: List<Job> = emptyList()

    /** `WeemApplication.onCreate`: `NavRuntime`'dan sonra. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        ScooterShiftLink.providerRole = { LinkHub.role.value == LinkRole.PROVIDER }
        // Önce tek telefon bağı; mod açıksa `init` köprüyü kurup yeniden bağlar.
        rewire(app, null)
        LinkHub.init(app, localFor = { role -> local(app, role) }, onBridge = { rewire(app, it) })
        // A9.1: uzaktan kullanım link'in `remote.*` iletilerini ve durumunu dinler.
        RemoteHub.init(app)
    }

    private fun local(app: Context, role: LinkRole): PhoneBridge.Local {
        val nav = NavRuntime.get(app)
        return when (role) {
            LinkRole.RECEIVER -> PhoneBridge.Local(nav = nav.port, scooter = Scooter.bridge, shift = ShiftHub, navOpened = { nav.showMap() })
            LinkRole.PROVIDER -> PhoneBridge.Local(nav = nav.port)
            LinkRole.OFF -> PhoneBridge.Local()
        }
    }

    @Synchronized
    private fun rewire(app: Context, b: PhoneBridge?) {
        jobs.forEach { it.cancel() }
        val nav = NavRuntime.get(app)
        val events = Orders.events(app)
        val day = DayRuntime.get(app)
        nav.controller.forward = null
        when (b?.role) {
            LinkRole.PROVIDER -> {
                Orders.bindScooter(app, b.scooter)
                BatteryRuntime.get(app).bind(b.scooter)
                day.bindOrders(events)
                nav.controller.forward = { stop -> b.forwardNav(stop.navTarget(UUID.randomUUID().toString(), System.currentTimeMillis())) }
                // S11: rol değişince açık bir scooter bağlantısı kapanır.
                Store.guarded(app, "link_provider") { Scooter.stop(app) }
                jobs = listOf(
                    scope.launch { ShiftHub.state.collect { b.providerShift(it) } },
                    scope.launch { events.offers.collect { b.providerOffers(it) } },
                    scope.launch { events.stages.collect { b.providerStages(it) } },
                    scope.launch { events.day.collect { b.providerDay(it) } },
                    scope.launch {
                        b.scooter.late.collect { Store.guarded(app, "late_energy") { Economics.observer(app).lateEnergy(it.fromWallMs, it.toWallMs, it.energy.json) } }
                    },
                )
            }
            LinkRole.RECEIVER -> {
                Orders.bindScooter(app, Scooter.bridge)
                BatteryRuntime.get(app).bind(Scooter.bridge)
                day.bindOrders(b.orders)
                jobs = listOf(
                    scope.launch { Scooter.bridge.live.collect { b.receiverScooter(it) } },
                    // Özet değer değişmese de 60 sn'de bir gider (`BatterySummary.due`).
                    scope.launch { while (true) { delay(10_000); b.receiverScooter(Scooter.bridge.live.value) } },
                    scope.launch { nav.port.route.collect { b.receiverRoute(it) } },
                )
            }
            else -> {
                Orders.bindScooter(app, Scooter.bridge)
                BatteryRuntime.get(app).bind(Scooter.bridge)
                day.bindOrders(events)
                jobs = emptyList()
            }
        }
    }
}
