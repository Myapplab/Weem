package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.core.Store
import com.kuryelog.app.economics.Economics
import com.kuryelog.app.link.LinkHub
import com.kuryelog.app.link.TheftRuntime
import com.kuryelog.app.remote.RemoteHub
import com.kuryelog.app.map.BatteryRuntime
import com.kuryelog.app.map.DayRuntime
import com.kuryelog.app.map.NavRuntime
import com.kuryelog.app.orders.Orders
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.app.scooter.ScooterShiftLink
import com.kuryelog.logic.core.NavPort
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

    /** `WeemApplication.onCreate`: `NavRuntime`'dan sonra. Her parça ayrı korunur: biri kalkmasa da ötekiler bağlanır. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        ScooterShiftLink.providerRole = { LinkHub.role.value == LinkRole.PROVIDER }
        // Önce tek telefon bağı; mod açıksa `init` köprüyü kurup yeniden bağlar.
        rewire(app, null)
        Store.guarded(app, "two_phone_link") { LinkHub.init(app, localFor = { role -> local(app, role) }, onBridge = { rewire(app, it) }) }
        // A9.1: uzaktan kullanım link'in `remote.*` iletilerini ve durumunu dinler.
        Store.guarded(app, "two_phone_remote") { RemoteHub.init(app) }
    }

    private fun local(app: Context, role: LinkRole): PhoneBridge.Local {
        val nav = Store.guarded(app, "two_phone_nav") { NavRuntime.get(app) }
        val port = nav?.port ?: NavPort.NONE
        return when (role) {
            LinkRole.RECEIVER -> PhoneBridge.Local(
                nav = port, scooter = Scooter.bridge, shift = ShiftHub, navOpened = { nav?.showMap() },
                // A8.4: Pixel'den Park/Kapat (LINK.md 5.2).
                theftCommand = { TheftRuntime.command(it) },
            )
            LinkRole.PROVIDER -> PhoneBridge.Local(nav = port)
            LinkRole.OFF -> PhoneBridge.Local()
        }
    }

    @Synchronized
    private fun rewire(app: Context, b: PhoneBridge?) {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        // Scooter dikişi (teklif anı pili, sipariş enerjisi R14, batarya tahmininin çapası) haritadan bağımsız: harita çalışma zamanı
        // kalkmasa da bağlanır. Provider'da uzak köprü (S11: scooter BLE açılmaz), öteki rollerde yerel.
        val scooter = if (b?.role == LinkRole.PROVIDER) b.scooter else Scooter.bridge
        Store.guarded(app, "two_phone_scooter") { Orders.bindScooter(app, scooter) }
        Store.guarded(app, "two_phone_battery") { BatteryRuntime.get(app).bind(scooter) }
        // A8.4: hırsızlık koruması role göre (Receiver korur, Provider alarmı gösterir; tek telefonda kapalı).
        Store.guarded(app, "two_phone_theft") {
            when (b?.role) {
                LinkRole.RECEIVER -> TheftRuntime.receiver(app, b) { Scooter.bridge.live.value?.takeIf { it.fresh }?.scooter?.speedKmh }
                LinkRole.PROVIDER -> TheftRuntime.provider(app, b)
                else -> TheftRuntime.stop()
            }
        }
        val nav = Store.guarded(app, "two_phone_nav") { NavRuntime.get(app) }
        val events = Store.guarded(app, "two_phone_events") { Orders.events(app) }
        val day = Store.guarded(app, "two_phone_day") { DayRuntime.get(app) }
        nav?.controller?.forward = null
        when (b?.role) {
            LinkRole.PROVIDER -> {
                events?.let { day?.bindOrders(it) }
                nav?.controller?.forward = { stop -> b.forwardNav(stop.navTarget(UUID.randomUUID().toString(), System.currentTimeMillis())) }
                // S11: rol değişince açık bir scooter bağlantısı kapanır.
                Store.guarded(app, "link_provider") { Scooter.stop(app) }
                jobs = listOfNotNull(
                    scope.launch { ShiftHub.state.collect { b.providerShift(it) } },
                    events?.let { e -> scope.launch { e.offers.collect { b.providerOffers(it) } } },
                    events?.let { e -> scope.launch { e.stages.collect { b.providerStages(it) } } },
                    events?.let { e -> scope.launch { e.day.collect { b.providerDay(it) } } },
                    scope.launch {
                        b.scooter.late.collect { Store.guarded(app, "late_energy") { Economics.observer(app).lateEnergy(it.fromWallMs, it.toWallMs, it.energy.json) } }
                    },
                )
            }
            LinkRole.RECEIVER -> {
                day?.bindOrders(b.orders)
                jobs = listOfNotNull(
                    scope.launch { Scooter.bridge.live.collect { b.receiverScooter(it) } },
                    // Özet değer değişmese de 60 sn'de bir gider (`BatterySummary.due`).
                    scope.launch { while (true) { delay(10_000); b.receiverScooter(Scooter.bridge.live.value) } },
                    nav?.let { n -> scope.launch { n.port.route.collect { b.receiverRoute(it) } } },
                )
            }
            else -> events?.let { day?.bindOrders(it) }
        }
    }
}
