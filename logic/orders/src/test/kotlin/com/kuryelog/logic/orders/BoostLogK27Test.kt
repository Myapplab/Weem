package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** K27: boost changes, Bolt's 30-minute slots ("gözlem yok" never guessed), the offer's boost and the day statistics. */
class BoostLogK27Test {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.food.courier"
    private val t0 = 1_760_000_400_000L - 1_760_000_400_000L % BoostLog.SLOT_MS // a slot start
    private val loc = JSONObject().put("lat", 40.41).put("lon", 49.86).put("acc", 8.0).put("age_s", 2)
    private fun p(pkg: String, at: Long, vararg t: String) = CourierParser.parse(pkg, t.toList(), emptyList(), at)
    private fun boltIdle(at: Long, x: String? = "1.6x") = p(bolt, at, *listOfNotNull(x, "Looking for orders", "Go offline").toTypedArray())
    private fun woltIdle(at: Long, line: String? = "Hər sifariş üçün əlavə 20% from 18:00") =
        p(wolt, at, *listOfNotNull("Expect a task soon", "Delivery demand", "Medium", line).toTypedArray())

    @Test fun boltWritesOneSlotPerHalfHourWithLocationAndChangesOnce() {
        val log = BoostLog()
        val first = log.observe(boltIdle(t0 + 1000), "2026-10-09", loc)
        assertEquals(listOf("change", "slot"), first.map { it.getString("kind") })
        assertEquals(1.6, first[1].getDouble("value"), 1e-9); assertEquals("x", first[1].getString("unit"))
        assertEquals(40.41, first[1].getDouble("lat"), 1e-9); assertTrue(first[0].getBoolean("first_seen"))
        // Same half hour, same multiplier: nothing new.
        assertTrue(log.observe(boltIdle(t0 + 600_000), "2026-10-09", loc).isEmpty())
        // Multiplier changes within the slot: one change row with the previous value, no second slot row.
        val change = log.observe(boltIdle(t0 + 900_000, "1.4x"), "2026-10-09", null)
        assertEquals(listOf("change"), change.map { it.getString("kind") })
        assertEquals(1.6, change[0].getDouble("prev_value"), 1e-9); assertTrue(change[0].getBoolean("prev_known"))
        assertTrue(change[0].isNull("lat"))
    }

    @Test fun unseenHalfHoursAreRecordedAsNoObservation() {
        val log = BoostLog()
        log.observe(boltIdle(t0), "2026-10-09", loc)
        val rows = log.observe(boltIdle(t0 + 3 * BoostLog.SLOT_MS + 5000), "2026-10-09", loc)
        val slots = rows.filter { it.getString("kind") == "slot" }
        assertEquals(listOf(false, false, true), slots.map { it.getBoolean("observed") })
        assertTrue(slots[0].isNull("at")); assertFalse(slots[0].has("value"))
        // A gap longer than 4 hours is a break: no invented rows.
        val later = log.observe(boltIdle(t0 + 20 * BoostLog.SLOT_MS), "2026-10-09", loc).filter { it.getString("kind") == "slot" }
        assertEquals(listOf(true), later.map { it.getBoolean("observed") })
    }

    @Test fun woltPlanThenChangeAndNoBoostIsAValueButNotificationsAreNot() {
        val log = BoostLog()
        // D24 (13 Oct): Wolt plan slots also write "plan" rows; this test is about the K27 change rows.
        fun k27(rows: List<org.json.JSONObject>) = rows.filter { it.getString("kind") != "plan" }
        val plan = k27(log.observe(woltIdle(t0), "2026-10-09", loc))
        assertEquals(1, plan.size); assertEquals(20.0, plan[0].getDouble("value"), 1e-9); assertEquals("pct", plan[0].getString("unit"))
        assertTrue(plan[0].getBoolean("first_seen")); assertTrue(plan.none { it.getString("kind") == "slot" })
        val up = k27(log.observe(woltIdle(t0 + 60_000, "Hər sifariş üçün əlavə 40% from 18:00"), "2026-10-09", loc))
        assertEquals(20.0, up.single().getDouble("prev_value"), 1e-9)
        val none = k27(log.observe(woltIdle(t0 + 120_000, null), "2026-10-09", loc))
        assertTrue(none.single().isNull("value"))
        assertNull(log.seen(woltIdle(t0, null).copy(source = "notification")))
        assertNull(log.seen(woltIdle(t0, null).copy(complete = false)))
        // Next day: the first row is again a plan, not a change.
        assertTrue(log.observe(woltIdle(t0 + 86_400_000L, null), "2026-10-10", loc).single().getBoolean("first_seen"))
    }

    @Test fun offerTakesItsOwnBoostElseAFreshHomeOne() {
        val log = BoostLog()
        val offer = p(wolt, t0 + 60_000, "Decline", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran", "Accept")
        assertEquals(BoostLog.NOT_OBSERVED, log.atOffer(offer, t0 + 60_000).source)
        log.observe(woltIdle(t0, "Hər sifariş üçün əlavə 20% from 08:00"), "2026-10-09", loc)
        val a = log.atOffer(offer, t0 + 60_000)
        assertEquals(BoostLog.SOURCE_HOME, a.source); assertEquals(t0, a.seenAt)
        // Older than 30 minutes: unknown again.
        assertEquals(BoostLog.NOT_OBSERVED, log.atOffer(offer, t0 + 31 * 60_000L).source)
        val boltOffer = boltIdle(t0, "1.7x").copy(stage = ScreenStage.OFFER)
        val b = log.atOffer(boltOffer, t0)
        assertEquals(BoostLog.SOURCE_OFFER, b.source); assertEquals(1.7, b.value!!, 1e-9); assertEquals(BoostLog.UNIT_X, b.unit)
    }

    @Test fun announcedWoltBoostDoesNotApplyToTheOffer() {
        val log = BoostLog()
        val s = log.seen(woltIdle(t0).copy(bonusPct = null, boostPct = 30, boostStartsInMin = 240))!!
        assertFalse(s.active); assertEquals(30.0, s.value!!, 1e-9)
        log.observe(woltIdle(t0).copy(bonusPct = null, boostPct = 30, boostStartsInMin = 240), "2026-10-09", loc)
        val a = log.atOffer(p(wolt, t0 + 1000, "Decline", "₼3.94", "Accept"), t0 + 1000)
        assertNull(a.value); assertEquals(BoostLog.SOURCE_HOME, a.source)
    }

    @Test fun dayStatistics() {
        val log = BoostLog(); val rows = ArrayList<JSONObject>()
        rows += log.observe(boltIdle(t0), "2026-10-09", loc)
        rows += log.observe(boltIdle(t0 + BoostLog.SLOT_MS, null), "2026-10-09", loc)
        rows += log.observe(boltIdle(t0 + 3 * BoostLog.SLOT_MS, "1.8x"), "2026-10-09", loc)
        val s = BoostLog.stats(rows, "bolt")
        assertEquals(2, s.changes); assertEquals(3, s.slotsObserved); assertEquals(1, s.slotsMissing); assertEquals(2, s.slotsBoosted)
        assertEquals(1.8, s.max!!, 1e-9); assertEquals("x", s.maxUnit)
        assertEquals(0, BoostLog.stats(rows, "wolt").slotsObserved)
    }

    @Test fun offerBoostSurvivesTheRecordRoundTrip() {
        val o = OfferState("o1", bolt, t0, boltIdle(t0)).apply { boostValue = 1.6; boostUnit = "x"; boostSource = "offer_screen"; boostSeenAt = t0 }
        val back = RecordJson.offer(JSONObject(RecordJson.offer(o).toString()))
        assertEquals(1.6, back.boostValue!!, 1e-9); assertEquals("offer_screen", back.boostSource); assertEquals(t0, back.boostSeenAt)
        val old = RecordJson.offer(JSONObject(RecordJson.offer(OfferState("o2", bolt, t0, boltIdle(t0))).toString()))
        assertNull(old.boostSource); assertFalse(RecordJson.offer(OfferState("o2", bolt, t0, boltIdle(t0))).has("boost_source"))
    }
}
