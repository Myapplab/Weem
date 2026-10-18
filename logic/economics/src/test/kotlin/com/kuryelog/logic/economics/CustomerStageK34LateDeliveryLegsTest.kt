package com.kuryelog.logic.economics

import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats CustomerStageK34LateDeliveryTest.kt @ 6388c04 — dosyanın tamamı: ekonomi kaydedicisiyle (legs satırları, 6 ifade); kaydedicisiz kopya logic/orders'ta (TEST_ENVANTERI A3.2).

/**
 * K34 follow-up (independent verifier, 8 Oct 2026): an order closed as an inferred delivery (`delivered_inferred`) whose
 * REAL completion signal arrives later is a real delivery. Before this fix that signal was thrown away: its card was
 * ignored, the waiting map only looked at open orders, and a completed-status frame with no open order was dropped, so the
 * order stayed "Teslim edildi (doğrulanmadı)" (probe `forgottenSwipeExactFrames`: base 4656a3d recorded it as delivered).
 *
 * Now the closed order's own slider (or deliver tap) followed by the waiting screen, or a completed-status frame on its
 * card, finishes it with a `delivered` event, keeping `deliveredInferredAt/By/Reason` as history (like the manual
 * correction). It is not moved back to the customer stage, the order that closed it stays open, and the economics recorder
 * writes no second tripLegs row (the row was written at the close; old rows are never rewritten), also after a restart.
 *
 * Labels as in [CustomerStageK34ClosedCardTest]; Bolt "Delivery completed" from CourierFlowTest, the Wolt "Order delivered!"
 * tap from WoltDeliverySliderTest. Names, ids and addresses are synthetic.
 */
class CustomerStageK34LateDeliveryLegsTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)

    private fun woltWaiting(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran") = obs(wolt, at, "Decline", "Ready in 1 min", fee,
        "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept",
        actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(wolt, at, "Pickup from", restaurant, "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long, name: String = "Customer A") = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min",
        "22 min, 4,49 ₼", actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltPickup(at: Long) = obs(bolt, at, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99")
    private fun boltCard(at: Long) = obs(bolt, at, "Drop off in -4 min", "Test C. #UWG2Q", "Items (3)", "Address", "Test 5, Bakı", "Instructions",
        "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm")
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")

    /** Core events go through the economics recorder like OrderTracker does; legs rows are collected. */
    private class Run(val core: DeliveryCore, val rec: EconomicsRecorder = EconomicsRecorder()) {
        val events = ArrayList<CoreEvent>(); val rows = ArrayList<EconomicsRecorder.Row>()
        fun see(o: CourierObservation) {
            val before = core.pending.values.associateBy { it.id }; val e = core.observe(o); events += e
            rows += rec.frame(o, e, before, core, emptyList(), OfferGeo.UNKNOWN) { "unknown" }
        }
        fun tap(pkg: String, text: String, at: Long) {
            val before = core.pending.values.associateBy { it.id }; val e = core.click(pkg, text, at); events += e
            rows += rec.events(at, e, before, core, emptyList(), { "unknown" })
        }
        fun legs(tripId: String) = rows.filter { it.file == EconomicsLogs.LEGS && it.json.optString("tripId") == tripId }
    }
    private fun run() = Run(DeliveryCore().also { it.start() })
    private fun atCustomer(c: DeliveryCore) = c.trips.values.filter { it.atCustomer }

    /** The courier hands over the Wolt order without swiping; a Bolt order reaches its customer and closes it (K34). */
    private fun woltClosedByBolt(r: Run): Pair<TripRecord, TripRecord> {
        r.see(woltWaiting(0)); r.see(woltWaiting(1000))
        r.see(woltOffer(10_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000)); r.see(woltCard(600_000)); r.see(woltCard(601_000))
        val w = r.core.active(wolt)!!
        r.see(boltOffer(610_000)); r.tap(bolt, "22 min, 4,49 ₼", 612_000); r.see(boltPickup(613_000)); r.see(boltCard(1_200_000))
        val b = r.core.active(bolt)!!
        assertTrue(w.deliveredInferred); assertEquals(1, r.legs(w.id).size)
        return w to b
    }
    /** A real delivery of [w] at [at] for [why], with the inferred close kept as history and nothing else touched. */
    private fun realDelivery(r: Run, w: TripRecord, b: TripRecord, at: Long, why: String) {
        val done = r.events.single { it.type == "delivered" }
        assertEquals(w.id, done.tripId); assertEquals(why, done.reason); assertEquals(at, done.at)
        assertEquals(TripPhase.FINISHED, w.phase); assertEquals(at, w.completedAt); assertFalse(w.deliveredInferred); assertFalse(w.active)
        assertEquals(at, w.customers.values.single().deliveredAt)
        assertEquals(1_200_000L, w.deliveredInferredAt); assertEquals(b.id, w.deliveredInferredBy); assertEquals("next_order_at_customer_after_slider", w.deliveredInferredReason)
        assertEquals(listOf(b), atCustomer(r.core)); assertNull(b.deliveredInferredAt)
        assertEquals(1, r.events.count { it.type == "delivered_inferred" }); assertEquals(2, r.core.trips.size)
        // The legs row was written at the close; the real delivery adds none.
        assertEquals(1, r.legs(w.id).size)
    }

    @Test fun forgottenSwipeSliderThenWaitingMapIsARealDelivery() {
        val r = run(); val (w, b) = woltClosedByBolt(r)
        // The courier opens Wolt again: the order's card with the slider, swiped, then the waiting map.
        r.see(woltCard(1_500_000)); r.see(woltWaiting(1_510_000)); r.see(woltWaiting(1_511_100))
        realDelivery(r, w, b, 1_511_100L, "delivery_slider_then_waiting")
        assertEquals("unknown", r.legs(w.id).single().json.getString("outcome"))
    }
    @Test fun waitingMapAfterTheSliderSeenBeforeTheCloseIsARealDelivery() {
        // No Wolt task screen after the order's own slider: the waiting map means Wolt has no task left (as in 4656a3d).
        val r = run(); val (w, b) = woltClosedByBolt(r)
        r.see(woltWaiting(1_300_000)); r.see(woltWaiting(1_301_100))
        realDelivery(r, w, b, 1_301_100L, "delivery_slider_then_waiting")
    }
    @Test fun deliverTapOnTheClosedOrdersCardThenWaitingMapIsARealDelivery() {
        val r = run(); val (w, b) = woltClosedByBolt(r)
        r.see(woltCard(1_500_000)); r.tap(wolt, "Order delivered!", 1_501_000); r.see(woltWaiting(1_502_000)); r.see(woltWaiting(1_502_300))
        realDelivery(r, w, b, 1_502_300L, "confirmed_delivery_to_idle")
    }
    @Test fun completedStatusOnTheClosedBoltOrderIsARealDelivery() {
        val r = run()
        r.see(boltOffer(10_000)); r.tap(bolt, "22 min, 4,49 ₼", 12_000); r.see(boltPickup(13_000)); r.see(boltCard(600_000))
        val b = r.core.active(bolt)!!
        r.see(woltOffer(610_000)); r.tap(wolt, "Accept", 612_000); r.see(woltPickup(613_000)); r.see(woltCard(1_200_000))
        val w = r.core.active(wolt)!!
        assertTrue(b.deliveredInferred)
        r.see(boltCard(1_300_000)); r.see(obs(bolt, 1_301_000, "Delivery completed"))
        val done = r.events.single { it.type == "delivered" }
        assertEquals(b.id, done.tripId); assertEquals("platform_completed", done.reason)
        assertEquals(TripPhase.FINISHED, b.phase); assertEquals(1_200_000L, b.deliveredInferredAt)
        assertEquals(listOf(w), atCustomer(r.core)); assertEquals(1, r.legs(b.id).size)
    }
    @Test fun theNewerOrdersWaitingMapDoesNotFinishTheClosedOne() {
        // 8 Oct sequence: the next Wolt order followed the slider; its own slider and waiting map finish only it.
        val r = run()
        r.see(woltOffer(10_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000)); r.see(woltCard(600_000)); r.see(woltCard(601_000))
        val first = r.core.active(wolt)!!
        r.see(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 605_000)
        r.see(woltPickup(606_000, restaurant = "OTHER DONER")); r.see(woltCard(1_200_000, "Customer B"))
        val second = r.core.active(wolt)!!
        assertTrue(first.deliveredInferred)
        r.see(woltWaiting(1_300_000)); r.see(woltWaiting(1_301_100)); r.see(woltWaiting(1_303_200)); r.see(woltWaiting(1_400_000))
        assertEquals(listOf(second.id), r.events.filter { it.type == "delivered" }.map { it.tripId })
        assertTrue(first.deliveredInferred)
    }
    @Test fun noSecondLegsRowAfterARestart() {
        val r = run(); val (w, b) = woltClosedByBolt(r)
        // Process death: trips come back from courier.db, a new recorder starts empty.
        val c = DeliveryCore()
        for (t in r.core.trips.values) RecordJson.trip(RecordJson.trip(t)).let { c.trips[it.id] = it }
        c.start()
        val r2 = Run(c)
        val w2 = c.trips.getValue(w.id); assertTrue(w2.deliveredInferred)
        r2.see(woltCard(1_500_000)); r2.see(woltWaiting(1_510_000)); r2.see(woltWaiting(1_511_100))
        val done = r2.events.single { it.type == "delivered" }
        assertEquals(w.id, done.tripId); assertEquals(TripPhase.FINISHED, w2.phase); assertEquals(1_200_000L, w2.deliveredInferredAt)
        assertTrue(r2.legs(w.id).isEmpty())
        assertTrue(c.trips.getValue(b.id).atCustomer)
    }
    @Test fun aManualCorrectionAfterARealLateDeliveryChangesNothing() {
        val r = run(); val (w, _) = woltClosedByBolt(r)
        r.see(woltCard(1_500_000)); r.see(woltWaiting(1_510_000)); r.see(woltWaiting(1_511_100))
        assertTrue(r.core.confirmDelivered(w.id, 1_600_000).isEmpty())
        assertEquals(1_511_100L, w.completedAt); assertEquals(1, r.legs(w.id).size)
    }
}

