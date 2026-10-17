package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * K34: an order closed because the next one reached the customer stage is an unverified delivery (`delivered_inferred`).
 * It keeps its own flag in the trip record (orders.jsonl / courier.db), its own orders.jsonl event type, and an
 * economics-legs row (that part is A4.1's `legsRowIsNeverADelivery`; here the order record only: ORDERS.md 15.1).
 * Labels as in [CustomerStageK34Test]; names and addresses synthetic.
 */
class CustomerStageK34RecordTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)
    private fun woltOffer(at: Long) = obs(wolt, at, "Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran",
        "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept", actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Test tarif", "Translate",
        "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders",
        actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long) = obs(wolt, at, "Dropoff to", "Customer A", "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details", "Order details",
        "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!", actions = listOf("Send a message", "Order delivered!"))
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltCard(at: Long) = obs(bolt, at, "Drop off in -4 min", "Test C. #UWG2Q", "Items (3)", "Address", "Test 5, Bakı", "Instructions",
        "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm")

    private class Day(val core: DeliveryCore, val wolt: TripRecord, val bolt: TripRecord, val inferredEvent: CoreEvent)
    /** A Wolt order at its customer, then a Bolt order at its customer; every core event goes through the economics recorder. */
    private fun day(): Day {
        val c = DeliveryCore(); c.start(); val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) {
            events += c.observe(o)
        }
        fun tap(pkg: String, text: String, at: Long) {
            events += c.click(pkg, text, at)
        }
        see(woltOffer(10_000)); tap(wolt, "Accept", 14_000); see(woltPickup(15_000)); see(woltCard(600_000)); see(woltCard(601_000))
        val w = c.active(wolt)!!
        see(boltOffer(610_000)); tap(bolt, "22 min, 4,49 ₼", 612_000); see(obs(bolt, 613_000, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99"))
        see(boltCard(1_200_000))
        return Day(c, w, c.active(bolt)!!, events.single { it.type == "delivered_inferred" })
    }

    @Test fun theTripKeepsItsOwnUnverifiedDeliveryFlag() {
        val d = day(); val w = d.wolt
        assertTrue(w.deliveredInferred); assertFalse(w.active); assertFalse(w.atCustomer)
        assertEquals(1_200_000L, w.deliveredInferredAt); assertEquals(d.bolt.id, w.deliveredInferredBy)
        assertEquals("next_order_at_customer_after_slider", w.deliveredInferredReason)
        assertNull(w.completedAt); assertNull(w.actualMinutes)
        assertEquals(TripPhase.TO_CUSTOMER, d.bolt.phase); assertNull(d.bolt.deliveredInferredAt); assertFalse(d.bolt.deliveredInferred)
    }
    @Test fun recordJsonKeepsTheFlagAndOtherRecordsKeepTheirShape() {
        val d = day()
        val j = RecordJson.trip(d.wolt)
        assertEquals("END_UNCONFIRMED", j.getString("phase")); assertTrue(j.isNull("completed_at"))
        assertEquals(1_200_000L, j.getLong("delivered_inferred_at")); assertEquals(d.bolt.id, j.getString("delivered_inferred_by"))
        assertEquals("next_order_at_customer_after_slider", j.getString("delivered_inferred_reason"))
        val back = RecordJson.trip(JSONObject(j.toString()))
        assertTrue(back.deliveredInferred); assertEquals(d.wolt.deliveredInferredAt, back.deliveredInferredAt)
        assertEquals(d.wolt.deliveredInferredBy, back.deliveredInferredBy); assertEquals(d.wolt.deliveredInferredReason, back.deliveredInferredReason)
        val open = RecordJson.trip(d.bolt)
        assertFalse(open.has("delivered_inferred_at")); assertFalse(open.has("delivered_inferred_by")); assertFalse(open.has("delivered_inferred_reason"))
        assertNull(RecordJson.trip(open).deliveredInferredAt)
    }
    @Test fun orderCardShowsAnUnverifiedDelivery() {
        val d = day(); val tr = { a: String, _: String -> a }; val az = { _: String, b: String -> b }
        assertEquals("Teslim edildi (doğrulanmadı)", OrderHistory.status(d.wolt, tr)); assertEquals("Çatdırıldı (təsdiqlənmədi)", OrderHistory.status(d.wolt, az))
        assertEquals("Müşteriye gidiliyor", OrderHistory.status(d.bolt, tr))
        // A plain unconfirmed end keeps its own text.
        val stopped = TripRecord("trip-x", wolt, null, 0, TripPhase.END_UNCONFIRMED)
        assertEquals("Sonuç doğrulanamadı", OrderHistory.status(stopped, tr))
        val day = OrderHistory.Day("2026-10-08", listOf(d.wolt, d.bolt, stopped))
        // D3(c) (13 Oct): an unverified delivery counts in the deliveries and the money; it keeps its own label.
        assertEquals(1, day.inferred); assertEquals(1, day.unconfirmed); assertEquals(1, day.orders); assertEquals(3.94, day.gross, 1e-9)
        // A platform list row verifies it.
        d.wolt.verifiedBy = "wolt_earnings_list"
        assertEquals("Teslim edildi (listede doğrulandı)", OrderHistory.status(d.wolt, tr)); assertEquals("Çatdırıldı (siyahıda təsdiqləndi)", OrderHistory.status(d.wolt, az))
        assertEquals(0, OrderHistory.Day("2026-10-08", listOf(d.wolt, d.bolt, stopped)).inferred)
        d.wolt.verifiedBy = null
        // Confirmed by hand: a delivery, no longer an unverified one.
        d.core.confirmDelivered(d.wolt.id, 1_300_000)
        assertEquals("Teslim edildi", OrderHistory.status(d.wolt, tr))
        val after = OrderHistory.Day("2026-10-08", listOf(d.wolt, d.bolt, stopped))
        assertEquals(0, after.inferred); assertEquals(1, after.orders); assertEquals(3.94, after.gross, 1e-9)
    }
    @Test fun waitBeforeTheNextOfferIsUnknownAfterAnInferredDelivery() {
        fun trip(id: String, at: Long, done: Long? = null, inferredAt: Long? = null) =
            TripRecord(id, wolt, null, at, if (done != null) TripPhase.FINISHED else TripPhase.END_UNCONFIRMED, completedAt = done).also { it.deliveredInferredAt = inferredAt }
        val a = trip("a", 0, done = 100_000); val u = trip("u", 200_000, inferredAt = 900_000)
        val n = TripRecord("n", wolt, null, 500_000, TripPhase.TO_CUSTOMER); val m = trip("m", 1_000_000, done = 1_100_000); val p = trip("p", 1_200_000, done = 1_300_000)
        val day = listOf(a, u, n, m, p)
        // Without K34 handling the wait before "n" was measured from "a" (6.7 min) although "u" was still being handed over.
        assertNull(OrderHistory.waitBeforeOffer(n, day))
        // "m" follows the inferred close: its real end is not known either.
        assertNull(OrderHistory.waitBeforeOffer(m, listOf(a, u, m)))
        // After a real delivery that came later, the wait is measured again.
        assertEquals(100_000 / 60000.0, OrderHistory.waitBeforeOffer(p, listOf(a, u, m, p))!!, 1e-9)
        assertEquals(1.0, OrderHistory.waitBeforeOffer(trip("x", 160_000, done = 170_000), listOf(a))!!, 1e-9)
    }
    @Test fun theCourierCanStillConfirmTheDeliveryByHand() {
        val d = day()
        val e = d.core.confirmDelivered(d.wolt.id, 1_300_000).single()
        assertEquals("delivered", e.type); assertEquals("courier_manual_correction", e.reason)
        assertEquals(TripPhase.FINISHED, d.wolt.phase); assertEquals(1_300_000L, d.wolt.completedAt)
        // History stays: it was inferred first, then confirmed.
        assertFalse(d.wolt.deliveredInferred); assertEquals(1_200_000L, d.wolt.deliveredInferredAt)
    }
    @Test fun theOrderEventIsItsOwnType() {
        val d = day()
        assertEquals(wolt, d.inferredEvent.pkg); assertEquals(d.wolt.id, d.inferredEvent.tripId); assertNull(d.inferredEvent.offerId)
        // D3(c): OnlineSessions counts the unverified delivery too, and its later real signal adds nothing.
        val s = OnlineSessions(); s.observe(obs(wolt, 0, "Baku Central", "Delivery demand", "Low", "Go offline")); s.event(d.inferredEvent)
        s.event(CoreEvent("delivered", wolt, 1_300_000, tripId = d.wolt.id, reason = "courier_manual_correction"))
        assertEquals(1, s.stop().single().delivered)
        // Faz 2 hata taraması (14 Ekim): a process restart in between (open session restored, memory gone) adds nothing either.
        val before = OnlineSessions(); before.observe(obs(wolt, 0, "Baku Central", "Delivery demand", "Low", "Go offline")); before.event(d.inferredEvent)
        val after = OnlineSessions(); after.restore(before.encode())
        after.event(CoreEvent("delivered", wolt, 1_300_000, tripId = d.wolt.id, reason = "delivery_slider_then_waiting"), inferredBefore = true)
        assertEquals(1, after.stop().single().delivered)
    }
}
