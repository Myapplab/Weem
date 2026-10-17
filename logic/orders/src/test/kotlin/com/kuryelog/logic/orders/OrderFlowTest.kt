package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.SahaFrame
import com.kuryelog.logic.capture.SahaFrames.frames
import com.kuryelog.logic.capture.SahaFrames.parse
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.core.WeatherPort
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.util.TimeZone

/**
 * WEEM (A3.5; ORDERS.md 4, 15.3 "eski testi olmayanlar"): saf `OrderFlow` = Rawstats `OrderTracker`'ın karar ve sırası. Karakterizasyon:
 * saha günleri `OrderFlow`'dan geçince çekirdeğin sonucu `SahaTekrarTest.replay` ile aynı (liste karesi erken biter, nakit satırı,
 * kare sırası); kalıcılık (`courier.db` değişen satır, telefon yalnız orada, `orders.jsonl`), yeniden başlatma (4.1), kayıtların
 * yeniden kurulması (4.12), teklif anı eklentileri yalnız `offer` olayında.
 */
class OrderFlowTest {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")

    private class FakeClock(var now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private inner class Env(root: File, override val prefs: KuryelogPrefs.Values = shiftOn(), override val db: CourierRecords = CourierRecords.Memory(),
                            val clockImpl: FakeClock = FakeClock(0, baku)) : OrderFlow.Env {
        override val clock: Clock get() = clockImpl
        override val log = LogStore(root, now = { clockImpl.now }, zone = { baku })
        var weatherNow: WeatherNow? = null
        override val weather = object : WeatherPort { override fun current() = weatherNow }
        override fun phoneBattery() = 77
        val asked = ArrayList<String>()
        override fun askCashTip(trip: TripRecord) { asked += trip.id }
        val pocketAsked = ArrayList<Long>()
        override fun askPocketCash(shiftStartMs: Long) { pocketAsked += shiftStartMs }
        /** Bildirim 12 istekleri: (hatırlatıcı, sessiz güncelleme mi). */
        val notified = ArrayList<Pair<OrderFlow.Reminder, Boolean>>()
        override fun notifyReminder(reminder: OrderFlow.Reminder, update: Boolean) { notified += reminder to update }
        /** Her adres bu koordinata çözülür (null: çözülmez). */
        var geocoded: DoubleArray? = null
        override fun geocode(address: String, done: (DoubleArray?) -> Unit) { geocoded?.let { done(it) } }
    }

    private fun shiftOn() = KuryelogPrefs.Memory().also {
        it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, OrderFlow.MIGRATION_07 to true))
    }

    private fun day(d: String, until: Long = Long.MAX_VALUE) = frames.filter { it.day == d && it.at <= until }

    private fun feed(flow: OrderFlow, env: Env, fs: List<SahaFrame>) {
        for (f in fs) { env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src) }
    }

    /** `SahaTekrarTest.replay`'in çekirdek yolu (liste karesi çekirdeğe girmez, Wolt nakit satırı teslim saatini verir). */
    private fun coreReplay(fs: List<SahaFrame>): DeliveryCore {
        val tz = TimeZone.getTimeZone(baku)
        val core = DeliveryCore().also { it.start() }
        for (f in fs) {
            if (PlatformHistory.parse(f.pkg, f.texts, f.at, tz) != null) continue
            for (r in PlatformHistory.cashRows(f.pkg, f.texts)) {
                val at = PlatformHistory.toMs(r.day, r.clock, tz)
                if (at <= f.at + 60_000) PlatformHistory.cashMatch(r, at, core.trips.values)?.let { core.cashCollected(it.id, at, r.amount, f.at) }
            }
            core.observe(parse(f))
        }
        return core
    }

    private fun summary(t: TripRecord) = listOf(t.id, t.pkg, t.phase, t.completedAt, t.deliveredInferredAt, t.deliveredInferredReason,
        t.pickups.map { it.arrivedAt to it.pickedAt }, t.customers.values.map { it.deliveredAt }, t.cashCollectedAt, t.verifiedBy == null).toString()

    @Test fun sahaDaysThroughTheFlowEndLikeTheCoreReplay() {
        for (d in listOf("2026-10-11", "2026-10-12")) {
            val fs = day(d)
            val env = Env(tmp.newFolder(d))
            val flow = OrderFlow(env).also { it.start() }
            feed(flow, env, fs)
            val direct = coreReplay(fs)
            assertEquals(d, direct.trips.size, flow.core.trips.size)
            // The flow also verifies orders from the earnings list (D3 c); everything else is the core's own result.
            assertEquals(d, direct.trips.values.map(::summary).map { it.substringBeforeLast(",") },
                flow.core.trips.values.map(::summary).map { it.substringBeforeLast(",") })
            // Every trip is in courier.db and its events in orders.jsonl.
            assertEquals(flow.core.trips.keys, env.db.trips().map { it.getString("id") }.toSet())
            val rows = env.log.dayDir(d).resolve(OrderFlow.ORDERS_FILE).readLines().map(::JSONObject)
            assertEquals(flow.core.trips.size, rows.count { it.optString("type") == "accepted" })
            assertTrue(rows.any { it.optString("type") == "record_snapshot" })
            assertFalse(flow.hasOpenOrder())
        }
    }

    @Test fun offerTimeExtrasOnlyOnTheOfferEvent() {
        val fs = day("2026-10-11")
        val env = Env(tmp.newFolder())
        env.weatherNow = WeatherNow(fs.first().at - 10 * 60_000L, tempC = 14.0, code = 3)
        val flow = OrderFlow(env).also { it.start() }
        feed(flow, env, fs)
        val offers = flow.core.trips.values.mapNotNull { it.offer }
        assertTrue(offers.isNotEmpty())
        for (o in offers) assertEquals(77, o.phoneBattery)
        // Weather older than 90 min at offer time is not written ("not observed", never an old value).
        val withWeather = offers.filter { it.weather != null }
        assertTrue(withWeather.isNotEmpty())
        for (o in withWeather) { assertEquals(OfferWeather.SOURCE_YOLGO, o.weather!!.source); assertTrue(o.firstAt - o.weather!!.fetchedAt <= OfferWeather.MAX_AGE_MS) }
        assertTrue(offers.any { it.weather == null })
    }

    @Test fun restartRestoresTheOpenOrderAndClosesItAtShiftEnd() {
        val fs = day("2026-10-12")
        val root = tmp.newFolder()
        val env = Env(root)
        val flow = OrderFlow(env).also { it.start() }
        // Up to the first accepted order's restaurant stage.
        var cut = fs.size
        for ((i, f) in fs.withIndex()) { env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src); if (flow.hasOpenOrder()) { cut = i; break } }
        assertTrue(cut < fs.size)
        val open = flow.core.trips.values.single { it.active }
        // Process death: a new flow over the same courier.db, prefs and logs.
        val env2 = Env(root, env.prefs, env.db, FakeClock(env.clockImpl.now + 5_000, baku))
        val again = OrderFlow(env2).also { it.start() }
        assertEquals(open.id, again.core.trips.values.single { it.active }.id)
        assertEquals(open.phase, again.core.trips.getValue(open.id).phase)
        // Shift end closes it as unconfirmed and writes the event.
        again.stop()
        assertEquals(TripPhase.END_UNCONFIRMED, again.core.trips.getValue(open.id).phase)
        val last = env2.log.dayDir("2026-10-12").resolve(OrderFlow.ORDERS_FILE).readLines().map(::JSONObject).last { it.optString("type") == "end_unconfirmed" }
        assertEquals("manual_shift_stop", last.optString("reason"))
    }

    @Test fun pendingOfferOfAnEndedShiftIsDroppedOnRestart() {
        val fs = day("2026-10-11")
        val root = tmp.newFolder()
        val env = Env(root)
        val flow = OrderFlow(env).also { it.start() }
        for (f in fs) { env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src); if (flow.core.pending.isNotEmpty()) break }
        val offer = flow.core.pending.values.single()
        assertEquals(offer.id, env.db.session()!!.getJSONArray("pending").getJSONObject(0).getString("id"))
        val off = KuryelogPrefs.Memory(HashMap((env.prefs as KuryelogPrefs.Memory).map)).also { it.put(mapOf(KuryelogPrefs.SHIFT to false)) }
        val again = OrderFlow(Env(root, off, env.db, FakeClock(env.clockImpl.now + 1_000, baku))).also { it.init() }
        assertTrue(again.core.pending.isEmpty())
        assertEquals(0, env.db.session()!!.getJSONArray("pending").length())
        // K21: an offer from before the restart is not exported until seen again.
        assertTrue(again.restartedAtMs > offer.lastSeenAt)
    }

    @Test fun phoneStaysInCourierDbOnly() {
        val fs = day("2026-10-12")
        val env = Env(tmp.newFolder())
        val flow = OrderFlow(env).also { it.start() }
        for (f in fs) { env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src); if (flow.core.trips.values.any { it.active && it.atCustomer }) break }
        val t = flow.core.trips.values.first { it.active && it.atCustomer }
        t.customers.getValue(t.currentCustomer!!).details.phone = "+994501112233"
        flow.setTip(t.id, 0.5) // a forced persist
        assertTrue(env.db.trips().single { it.getString("id") == t.id }.toString().contains("+994501112233"))
        val log = env.log.dayDir("2026-10-12").resolve(OrderFlow.ORDERS_FILE).readText()
        assertFalse(log.contains("+994501112233"))
    }

    @Test fun recordsAreRebuiltFromTheOrderLogs() {
        val fs = day("2026-10-11")
        val root = tmp.newFolder()
        val env = Env(root)
        val flow = OrderFlow(env).also { it.start() }
        feed(flow, env, fs)
        val want = env.db.trips().associate { it.getString("id") to it.getString("phase") }
        // A fresh courier.db (migration flag not set): every trip comes back from orders.jsonl, newest record per trip.
        val prefs = KuryelogPrefs.Memory(HashMap((env.prefs as KuryelogPrefs.Memory).map)).also { it.put(mapOf(OrderFlow.MIGRATION_07 to null)) }
        val db = CourierRecords.Memory()
        OrderFlow(Env(root, prefs, db, FakeClock(fs.last().at + 1_000, baku))).init()
        assertTrue(prefs.getBoolean(OrderFlow.MIGRATION_07, false))
        assertEquals(want, db.trips().associate { it.getString("id") to it.getString("phase") })
        assertTrue(db.offerCount("2026-10-11", "accepted") > 0)
    }

    @Test fun nothingIsRecordedWithoutAShift() {
        val env = Env(tmp.newFolder(), KuryelogPrefs.Memory().also { it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, OrderFlow.MIGRATION_07 to true)) })
        val flow = OrderFlow(env)
        feed(flow, env, day("2026-10-11").take(300))
        assertTrue(flow.core.trips.isEmpty() && flow.core.pending.isEmpty())
        assertFalse(env.log.logsRoot.walk().any { it.isFile })
    }

    // ------------------------------------------------------------------ A3.6: para ve platform (ORDERS.md 7–12)
    @Test fun officialTotalsAndCashPendingComeFromTheFieldScreens() {
        val env = Env(tmp.newFolder())
        val flow = OrderFlow(env).also { it.start() }
        feed(flow, env, frames)
        // F8.3: Wolt Stats of the day (newest per key wins), Bolt's day header, Wolt's cash collected.
        val wolt = flow.platformTotals("2026-10-12").filter { it.source == PlatformTotals.WOLT_STATS }
        assertEquals(1, wolt.size)
        val totals = env.log.dayDir("2026-10-12").resolve(PlatformTotals.FILE).readLines().map(::JSONObject)
        val newest = totals.last { it.optString("source_screen") == PlatformTotals.WOLT_STATS }
        assertEquals(newest.getInt("deliveries"), wolt.single().deliveries); assertEquals(newest.optDouble("earnings_azn", Double.NaN), wolt.single().earnings ?: Double.NaN, 1e-9)
        assertTrue(totals.first { it.optString("source_screen") == PlatformTotals.WOLT_STATS }.getInt("deliveries") < wolt.single().deliveries!!)
        // The day's cash is the sum of every row read so far (a scroll adds the rest): 60.65 at first, more later.
        val cash = env.log.dayDir("2026-10-11").resolve(PlatformTotals.FILE).readLines().map(::JSONObject).filter { it.optString("source_screen") == PlatformTotals.WOLT_CASH }
        assertEquals(60.65, cash.first().getDouble("cash_azn"), 1e-9)
        assertEquals(cash.last().getDouble("cash_azn"), flow.platformTotals("2026-10-11").single { it.source == PlatformTotals.WOLT_CASH }.cash!!, 1e-9)
        assertTrue(cash.last().getDouble("cash_azn") > 60.65)
        assertEquals(7, flow.platformTotals("2026-10-08").single { it.source == PlatformTotals.BOLT_DAYS }.deliveries)
        // An unchanged total is not written again: no two consecutive rows of one key carry the same figures.
        for ((a, b) in totals.zipWithNext()) assertNotEquals(a.without("observedAtMs").toString(), b.without("observedAtMs").toString())
        // M17: the first CASH PENDING of 11 Oct, the cash offload row in its own day, the last value kept in the prefs across restarts.
        val pending = flow.cashPendingRows("2026-10-11")
        assertEquals(21.54, pending.first { it.optString("kind") == CashPending.KIND_PENDING }.getDouble("pending_azn"), 1e-9)
        assertEquals(70.0, flow.cashPendingRows("2026-10-08").single { it.optString("kind") == CashPending.KIND_OFFLOAD }.getDouble("amount_azn"), 1e-9)
        val last = flow.lastPending()!!
        assertEquals(8.24, last.first, 1e-9)
        assertEquals(last, OrderFlow(Env(tmp.newFolder(), env.prefs)).lastPending())
        // A frame read twice writes nothing twice: the offload row is deduped by its day, clock and amount.
        val before = flow.cashPendingRows("2026-10-08").size
        feed(flow, env, frames.filter { it.day == "2026-10-08" && CashPending.pending(it.pkg, it.texts) != null || PlatformHistory.offloadRows(it.pkg, it.texts).isNotEmpty() }.take(5))
        assertEquals(before, flow.cashPendingRows("2026-10-08").size)
    }

    private fun JSONObject.without(key: String) = JSONObject(toString()).also { it.remove(key) }

    @Test fun shiftHooksAskForThePocketCashAndWriteTheCashPendingRows() {
        val fs = day("2026-10-12")
        val env = Env(tmp.newFolder())
        val flow = OrderFlow(env).also { it.start() }
        val start = fs.first().at - 60_000
        env.clockImpl.now = start
        flow.cashPendingHooks.onNewShift(start, "shift_start")
        assertEquals(listOf(start), env.pocketAsked)
        val dayOfShift = env.log.day(start)
        val begin = flow.cashPendingRows(dayOfShift).single()
        assertEquals(CashPending.SHIFT_START, begin.getString("kind"))
        // M15: the answer lands in the shift start's day folder; a correction appends and the newest row wins.
        env.clockImpl.now = start + 5_000
        assertNotNull(flow.recordPocketCash(start, 30.0, PocketCash.VIA_REPLY))
        env.clockImpl.now = start + 9_000
        assertNotNull(flow.recordPocketCash(start, 12.5, PocketCash.VIA_WINDOW))
        assertEquals(12.5, PocketCash.latest(flow.pocketCashRows(dayOfShift), start)!!, 1e-9)
        assertEquals(listOf(start to 12.5), PocketCash.forDay(flow.pocketCashRows(dayOfShift), dayOfShift))
        // The shift reads an order and a CASH PENDING screen, then ends: end row with that value, one snapshot per order.
        for (f in fs) { env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src) }
        val end = fs.last().at + 1_000
        val seen = flow.lastPending()!!.first
        env.clockImpl.now = end
        flow.cashPendingHooks.onEnd(start, end, "manual")
        val rows = flow.cashPendingRows(dayOfShift)
        val closing = rows.single { it.optString("kind") == CashPending.SHIFT_END }
        assertEquals(seen, closing.getDouble("pending_azn"), 1e-9)
        assertEquals(seen, rows.last { it.optString("kind") == CashPending.KIND_PENDING }.getDouble("pending_azn"), 1e-9)
        assertEquals(flow.core.trips.values.count { it.acceptedAt >= start }, rows.count { it.optString("kind") == CashPending.KIND_ORDER })
        assertTrue(CashPending.shifts(rows).any { it.first == start && it.second != null })
    }

    /** Restoran aşamasındaki ilk açık sipariş: ([flow], [env]) ve alışın adresi çözülmüş. */
    private fun atRestaurant(): Triple<OrderFlow, Env, TripRecord> {
        val env = Env(tmp.newFolder()).also { it.geocoded = doubleArrayOf(40.4000, 49.8500) }
        val flow = OrderFlow(env).also { it.start() }
        for (f in day("2026-10-12")) {
            env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src)
            val t = flow.core.trips.values.firstOrNull { it.active && it.pickups.any { p -> p.pickedAt == null && p.address != null } && (it.phase == TripPhase.TO_RESTAURANT || it.phase == TripPhase.WAITING) }
            if (t != null) return Triple(flow, env, t)
        }
        fail("no order reached its restaurant stage"); throw IllegalStateException()
    }

    private fun fix(env: Env, northM: Double, accuracy: Double = 10.0) {
        env.log.lastLocation = LogStore.Loc(40.4000 + northM / 111_195.08, 49.8500, accuracy, env.clockImpl.now)
    }

    @Test fun restaurantBubbleOpensAt50mClosesPast150mAndNotifiesOnce() {
        val (flow, env, t) = atRestaurant()
        val i = t.pickups.indexOfLast { it.pickedAt == null }
        val notified = env.notified.size
        // 80 m: not yet; no fix: nothing opens.
        fix(env, 80.0); flow.tick(env.clockImpl.now)
        assertNull(flow.reminder)
        // 40 m: the bubble opens and the first opening posts bildirim 12 once.
        fix(env, 40.0); flow.tick(env.clockImpl.now)
        assertEquals("restaurant", flow.reminder!!.kind); assertEquals("pickup-$i", flow.reminder!!.customer)
        assertEquals(notified + 1, env.notified.size); assertFalse(env.notified.last().second)
        flow.tick(env.clockImpl.now)
        assertEquals(notified + 1, env.notified.size)
        // 100 m: stays open (hysteresis); 200 m: closes; 40 m again: reopens but does not notify a second time.
        fix(env, 100.0); flow.tick(env.clockImpl.now); assertNotNull(flow.reminder)
        fix(env, 200.0); flow.tick(env.clockImpl.now); assertNull(flow.reminder)
        fix(env, 40.0); flow.tick(env.clockImpl.now); assertNotNull(flow.reminder)
        assertEquals(notified + 1, env.notified.size)
        // The courier closes it: it does not open again for this pickup.
        flow.dismissReminder(); flow.tick(env.clockImpl.now)
        assertNull(flow.reminder)
        // An inaccurate or old fix measures nothing.
        val (flow2, env2, _) = atRestaurant()
        fix(env2, 10.0, accuracy = 80.0); flow2.tick(env2.clockImpl.now); assertNull(flow2.reminder)
        env2.log.lastLocation = LogStore.Loc(40.4000, 49.8500, 10.0, env2.clockImpl.now - 60_000); flow2.tick(env2.clockImpl.now); assertNull(flow2.reminder)
    }

    @Test fun customerCardOpensOnceWithin300mOfTheAddress() {
        val env = Env(tmp.newFolder()).also { it.geocoded = doubleArrayOf(40.4000, 49.8500) }
        val flow = OrderFlow(env).also { it.start() }
        var trip: TripRecord? = null
        for (f in day("2026-10-12")) {
            env.clockImpl.now = f.at; flow.onScreen(f.pkg, f.texts, f.actions, f.at, f.src)
            trip = flow.core.trips.values.firstOrNull { it.active && it.phase == TripPhase.TO_CUSTOMER && it.customers[it.currentCustomer]?.lat != null }
            if (trip != null) break
        }
        val t = trip ?: run { fail("no order reached its customer stage with a geocoded address"); throw IllegalStateException() }
        val c = t.customers.getValue(t.currentCustomer!!)
        assertNull(c.reminderAt)
        val notified = env.notified.size
        fix(env, 500.0); flow.tick(env.clockImpl.now)
        assertNull(flow.reminder)
        fix(env, 250.0); flow.tick(env.clockImpl.now)
        val r = flow.reminder!!
        assertEquals("customer", r.kind); assertEquals(c.id, r.customer)
        assertEquals("Adrese yaklaşık 250 m (kuş uçuşu)", r.estimate)
        assertEquals(env.clockImpl.now, c.reminderAt)
        assertEquals(notified + 1, env.notified.size)
        // Closed by the courier: it stays closed (reminderAt remains), no second notification.
        flow.dismissReminder(); flow.tick(env.clockImpl.now)
        assertNull(flow.reminder); assertEquals(notified + 1, env.notified.size)
    }
}
