package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** K29: minutes left to deliver at pickup and at delivery, from Bolt's drop-off clock, else the offer's upper estimate. */
class DeliveryDeadlineK29Test {
    private val bolt = "com.bolt.food.courier"; private val wolt = "com.wolt.courierapp"
    private fun obs(pkg: String, at: Long, vararg t: String) = CourierParser.parse(pkg, t.toList(), emptyList(), at)

    @Test fun boltClockBecomesAStableDeadline() {
        val at = 1_000_000L
        val d1 = DeliveryDeadline.fromClock(at, 12)
        assertEquals(at + 12 * 60_000L, d1)
        // One minute later the countdown says 11: same deadline (rounding), kept.
        assertEquals(d1, DeliveryDeadline.merge(d1, DeliveryDeadline.fromClock(at + 61_000, 11)))
        // The platform moved it by 5 min: taken.
        assertEquals(at + 17 * 60_000L, DeliveryDeadline.merge(d1, DeliveryDeadline.fromClock(at, 17)))
        assertEquals(-4, obs(bolt, at, "Drop off in -4 min", "Test C. #UWG2Q", "Items (3)").dropoffClock)
    }

    @Test fun marginsFromThePlatformDeadline() {
        val t = TripRecord("t1", bolt, null, 0L)
        t.customers["#A"] = CustomerRecord("#A").apply { deadlineAt = 30 * 60_000L; deliveredAt = 33 * 60_000L }
        t.pickups += PickupRecord("R", null, pickedAt = 12 * 60_000L)
        val m = DeliveryDeadline.margins(t)!!
        assertEquals(DeliveryDeadline.PLATFORM, m.deadline.source)
        assertEquals(18.0, m.atPickupMin!!, 1e-9); assertEquals(-3.0, m.atDeliveryMin!!, 1e-9)
    }

    @Test fun woltFallsBackToTheOfferUpperEstimateAndUnknownStaysNull() {
        val offer = OfferState("o", wolt, 0L, obs(wolt, 0L, "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Accept"))
        val t = TripRecord("t2", wolt, offer, 30_000L).apply { completedAt = 14 * 60_000L }
        val m = DeliveryDeadline.margins(t)!!
        assertEquals(DeliveryDeadline.OFFER_ESTIMATE, m.deadline.source); assertNull(m.atPickupMin); assertEquals(2.0, m.atDeliveryMin!!, 1e-9)
        assertNull(DeliveryDeadline.margins(TripRecord("t3", wolt, null, 0L)))
    }

    @Test fun deadlineSurvivesTheRecordAndCoreSetsIt() {
        val t = TripRecord("t4", bolt, null, 0L)
        t.customers["#A"] = CustomerRecord("#A").apply { deadlineAt = 123_456L }
        val back = RecordJson.trip(JSONObject(RecordJson.trip(t).toString()))
        assertEquals(123_456L, back.customers["#A"]!!.deadlineAt)
        val old = JSONObject(RecordJson.trip(TripRecord("t5", bolt, null, 0L).also { it.customers["#B"] = CustomerRecord("#B") }).toString())
        assertNull(RecordJson.trip(old).customers["#B"]!!.deadlineAt)
    }
}
