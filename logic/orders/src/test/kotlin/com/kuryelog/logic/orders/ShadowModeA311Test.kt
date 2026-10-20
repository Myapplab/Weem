package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import com.kuryelog.logic.capture.SahaFrames.frames
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/**
 * A3.11 (S16 (a)): gölge mod anahtarı. Açıkken WEEM soru sormaz (nakit bahşiş K32, cep nakdi M15) ve bildirim 12'yi çizmez; ekran okuma,
 * kayıtlar, [OrderFlow.reminder] durumu ve CASH PENDING satırları aynen sürer. Kapalıyken (varsayılan) hiçbir şey değişmez.
 */
class ShadowModeA311Test {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val wolt = "com.wolt.courierapp"

    private class FakeClock(var now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private inner class Env(root: File, shadow: Boolean) : OrderFlow.Env {
        val clockImpl = FakeClock(0, baku)
        override val clock: Clock get() = clockImpl
        override val prefs = KuryelogPrefs.Memory().also {
            it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, OrderFlow.MIGRATION_07 to true,
                OrderSettings.SHADOW to shadow))
        }
        override val db: CourierRecords = CourierRecords.Memory()
        override val log = LogStore(root, now = { clockImpl.now }, zone = { baku })
        val asked = ArrayList<String>()
        override fun askCashTip(trip: TripRecord) { asked += trip.id }
        val pocketAsked = ArrayList<Long>()
        override fun askPocketCash(shiftStartMs: Long) { pocketAsked += shiftStartMs }
        val notified = ArrayList<Pair<OrderFlow.Reminder, Boolean>>()
        override fun notifyReminder(reminder: OrderFlow.Reminder, update: Boolean) { notified += reminder to update }
        override fun geocode(address: String, done: (DoubleArray?) -> Unit) { done(doubleArrayOf(40.4000, 49.8500)) }
    }

    // ---- anahtar ----

    @Test fun theSwitchIsOffByDefaultAndReadFromThePrefs() {
        assertFalse(OrderSettings.shadowMode(KuryelogPrefs.Memory()))
        assertTrue(OrderSettings.shadowMode(KuryelogPrefs.Memory().also { it.put(mapOf(OrderSettings.SHADOW to true)) }))
        assertEquals("shadowMode", OrderSettings.SHADOW)
        assertFalse(OrderFlow(Env(tmp.newFolder(), false)).shadowMode()); assertTrue(OrderFlow(Env(tmp.newFolder(), true)).shadowMode())
    }

    // ---- cep nakdi (M15) ----

    @Test fun pocketCashIsAskedOnlyOutsideShadowModeAndCashPendingRowsAreWrittenEitherWay() {
        for (shadow in listOf(false, true)) {
            val env = Env(tmp.newFolder("p$shadow"), shadow)
            val flow = OrderFlow(env).also { it.start() }
            val start = 1_000_000L; env.clockImpl.now = start
            flow.cashPendingHooks.onNewShift(start, "shift_start")
            assertEquals("shadow=$shadow", if (shadow) emptyList() else listOf(start), env.pocketAsked)
            assertEquals(CashPending.SHIFT_START, flow.cashPendingRows(env.log.day(start)).single().getString("kind"))
        }
    }

    // ---- nakit bahşiş (K32) ----

    private val cashLines = listOf("Cash order", "No change needed", "Steps to complete the delivery", "Collect cash payment", "I have collected the cash (AZN 20.40)")
    private fun waiting(flow: OrderFlow, env: Env, at: Long) { env.clockImpl.now = at; flow.onScreen(wolt, listOf("Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00"), emptyList(), at, "accessibility") }
    private fun offer(flow: OrderFlow, env: Env, at: Long) { env.clockImpl.now = at; flow.onScreen(wolt, listOf("Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran",
        "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept"), listOf("Decline", "Accept"), at, "accessibility") }
    private fun pickup(flow: OrderFlow, env: Env, at: Long) { env.clockImpl.now = at; flow.onScreen(wolt, listOf("Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders"), listOf("Collect", "Cancel this delivery"), at, "accessibility") }
    private fun card(flow: OrderFlow, env: Env, at: Long, extra: List<String>) { env.clockImpl.now = at; flow.onScreen(wolt, listOf("Dropoff to", "Customer A", "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!") + extra,
        listOf("Send a message", "Order delivered!"), at, "accessibility") }

    private fun deliverACashOrder(shadow: Boolean): Pair<Env, OrderFlow> {
        val env = Env(tmp.newFolder("c$shadow"), shadow)
        val flow = OrderFlow(env).also { it.start() }
        waiting(flow, env, 0); waiting(flow, env, 1000); offer(flow, env, 10_000); offer(flow, env, 11_000)
        env.clockImpl.now = 14_000; flow.click(wolt, "Accept", 14_000)
        pickup(flow, env, 15_000); card(flow, env, 600_000, emptyList()); card(flow, env, 601_000, emptyList()); card(flow, env, 601_500, cashLines)
        waiting(flow, env, 602_000); waiting(flow, env, 603_100)
        return env to flow
    }

    @Test fun theCashTipIsAskedAfterACashDeliveryOutsideShadowMode() {
        val (env, flow) = deliverACashOrder(false)
        val trip = flow.core.trips.values.single()
        assertTrue("delivered: ${trip.phase}", trip.countsAsDelivered)
        assertEquals(listOf(trip.id), env.asked)
    }

    @Test fun inShadowModeNoCashTipQuestionIsAskedAndTheRecordStaysTheSame() {
        val (envOff, flowOff) = deliverACashOrder(false)
        val (envOn, flowOn) = deliverACashOrder(true)
        assertTrue(envOn.asked.isEmpty())
        val off = RecordJson.trip(flowOff.core.trips.values.single()).toString()
        val on = RecordJson.trip(flowOn.core.trips.values.single()).toString()
        // Same trip, same fields; only the generated ids carry nothing of the switch.
        assertEquals(off.replace(Regex("\"id\":\"[^\"]*\""), ""), on.replace(Regex("\"id\":\"[^\"]*\""), ""))
        assertEquals(1, envOff.asked.size)
    }

    // ---- bildirim 12 (restoran balonu, müşteri kartı) ----

    private fun atRestaurant(shadow: Boolean): Triple<OrderFlow, Env, TripRecord> {
        val env = Env(tmp.newFolder("r$shadow"), shadow)
        val flow = OrderFlow(env).also { it.start() }
        for (f in frames.filter { it.day == "2026-10-12" }) {
            env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src)
            val t = flow.core.trips.values.firstOrNull { it.active && it.pickups.any { p -> p.pickedAt == null && p.address != null } && (it.phase == TripPhase.TO_RESTAURANT || it.phase == TripPhase.WAITING) }
            if (t != null) return Triple(flow, env, t)
        }
        fail("no order reached its restaurant stage"); throw IllegalStateException()
    }

    @Test fun inShadowModeTheBubbleStateAndRecordsRunButNoNotificationIsPosted() {
        for (shadow in listOf(false, true)) {
            val (flow, env, t) = atRestaurant(shadow)
            env.log.lastLocation = LogStore.Loc(40.4000 + 40.0 / 111_195.08, 49.8500, 10.0, env.clockImpl.now)
            flow.tick(env.clockImpl.now)
            assertEquals("shadow=$shadow", "restaurant", flow.reminder!!.kind)
            assertNotNull(t.pickups.first { it.pickedAt == null }.reminderAt)
            assertEquals("shadow=$shadow", if (shadow) 0 else 1, env.notified.size)
            // The countdown follows silently only when notifications are on.
            env.clockImpl.now += 61_000; flow.tick(env.clockImpl.now)
            assertTrue("shadow=$shadow", !shadow || env.notified.isEmpty())
        }
    }

    // ---- bağlantılar (kaynak denetimi; pencereler telefonda denenmedi) ----

    @Test fun androidHostDrawsNoWindowsInShadowMode() {
        val host = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/OrdersHost.kt")
        assertTrue(host.contains("flow.shadowMode()"))
        val shadowBranch = host.substringAfter("if (flow.shadowMode())").substringBefore("else if")
        assertTrue(shadowBranch.contains("removeBadge(service)") && shadowBranch.contains("reminders?.update(null)"))
        val orders = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/Orders.kt")
        assertTrue(orders.contains("override fun shadowMode(): Boolean") && orders.contains("FLAG_DEBUGGABLE"))
    }
}
