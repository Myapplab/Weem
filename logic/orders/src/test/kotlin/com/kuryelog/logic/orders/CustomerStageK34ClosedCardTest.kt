package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K34 follow-up (independent verifier, 8 Oct 2026): after an order is closed as an inferred delivery by an order of the
 * OTHER app, its customer card can still reach the core: split screen, or the courier going back to Wolt/Bolt to swipe an
 * order they forgot. Before this fix only a frame naming that order exactly (order id, or [NameMatch.sameName]) was
 * recognised as its card. An OCR variant of the name ("AIi Q." for "Ali Q."; short words must match exactly), a frame
 * without the name line, or a Bolt card without its #id opened a missed-offer phantom order at the customer stage; that
 * phantom closed the other app's real order as `delivered_inferred`, whose own cards were then ignored for good.
 *
 * Now, with no open order of that platform, a customer card that carries no order id of another order is the closed
 * order's card while that card is the platform's last task screen (within [DeliveryCore.SLIDER_TO_WAITING_MS]), also after
 * a restart; with an offer on hold, only if nothing on it names another order (the open-order rule). An order first seen at
 * a customer card (pickup not seen) in that window closes nothing; outside it, it closes the others as the courier's rule
 * says.
 *
 * Labels: Wolt offer E5, pickup E8, waiting sheet E6 (6 Oct field corpus), Wolt customer card (DeliveryRecognitionTest),
 * Bolt offer, pickup, card and waiting screen (CourierFlowTest, CustomerStageK34RoutingTest). The nameless Wolt card is the
 * same card without its "Dropoff to" header and name line, the id-less Bolt card the same card without "#UWG2Q" (the
 * crops the verifier described). Names, ids and addresses are synthetic.
 */
class CustomerStageK34ClosedCardTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, at, source)

    private fun woltWaiting(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    private fun woltOffer(at: Long) = obs(wolt, at, "Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran",
        "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept", actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Test tarif", "Translate",
        "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery",
        "Collect orders", actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long, name: String, source: String = "accessibility") = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO",
        "ITEMS", "Address details", "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"), source = source)
    /** The same card without its "Dropoff to" header and name line (an OCR crop). */
    private fun woltCardWithoutName(at: Long) = obs(wolt, at, "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details", "Order details", "From",
        "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!", actions = listOf("Send a message", "Order delivered!"),
        source = "ocr")
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min",
        "22 min, 4,49 ₼", actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltPickup(at: Long) = obs(bolt, at, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99")
    private fun boltCard(at: Long, id: String = "#UWG2Q") = obs(bolt, at, "Drop off in -4 min", "Test C. $id", "Items (3)", "Address", "Test 5, Bakı",
        "Instructions", "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm")
    /** The same card without the order id (an OCR crop). */
    private fun boltCardWithoutId(at: Long) = obs(bolt, at, "Drop off in -4 min", "Test C.", "Items (3)", "Address", "Test 5, Bakı", "Instructions",
        "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm", source = "ocr")
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")

    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(pkg: String, text: String, at: Long) { events += core.click(pkg, text, at) }
    }
    private fun run() = Run(DeliveryCore().also { it.start() })
    private fun atCustomer(c: DeliveryCore) = c.trips.values.filter { it.atCustomer }

    /** Wolt order at [name] with its slider on screen, then a Bolt order reaches its customer and closes it (K34). */
    private fun woltClosedByBolt(r: Run, name: String): Pair<TripRecord, TripRecord> {
        r.see(woltWaiting(0)); r.see(woltWaiting(1000))
        r.see(woltOffer(10_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(woltCard(600_000, name)); r.see(woltCard(601_000, name))
        val w = r.core.active(wolt)!!
        r.see(boltOffer(610_000)); r.tap(bolt, "22 min, 4,49 ₼", 612_000); r.see(boltPickup(613_000)); r.see(boltCard(1_200_000))
        val b = r.core.active(bolt)!!
        assertTrue(w.deliveredInferred); assertEquals(b.id, w.deliveredInferredBy)
        return w to b
    }
    /** Bolt order at its customer, then a Wolt order reaches its customer and closes it (K34). */
    private fun boltClosedByWolt(r: Run): Pair<TripRecord, TripRecord> {
        r.see(boltOffer(10_000)); r.tap(bolt, "22 min, 4,49 ₼", 12_000); r.see(boltPickup(13_000)); r.see(boltCard(600_000)); r.see(boltCard(601_000))
        val b = r.core.active(bolt)!!
        r.see(woltWaiting(602_000)); r.see(woltWaiting(603_000))
        r.see(woltOffer(610_000)); r.tap(wolt, "Accept", 612_000); r.see(woltPickup(613_000)); r.see(woltCard(1_200_000, "Customer A"))
        val w = r.core.active(wolt)!!
        assertTrue(b.deliveredInferred); assertEquals(w.id, b.deliveredInferredBy)
        return b to w
    }
    /** Nothing opened or closed since the cross-app close; [real] is still the one order at the customer stage. */
    private fun nothingChanged(r: Run, trips: Int, real: TripRecord, closedPkg: String) {
        assertEquals(trips, r.core.trips.size); assertNull(r.core.active(closedPkg))
        assertEquals(listOf(real), atCustomer(r.core)); assertNull(real.deliveredInferredAt)
        assertEquals(1, r.events.count { it.type == "delivered_inferred" })
        assertTrue(r.events.none { it.type == "accepted" && it.at > 1_200_000 })
    }

    @Test fun ocrNameVariantOfTheClosedWoltOrderOpensNothingAndClosesNothing() {
        val r = run(); val (_, b) = woltClosedByBolt(r, "Ali Q."); val trips = r.core.trips.size
        // Short words must match exactly in sameName, so this read does not name the closed order.
        assertFalse(NameMatch.sameName("Ali Q.", "AIi Q."))
        r.see(woltCard(1_200_500, "AIi Q.", source = "ocr"))
        nothingChanged(r, trips, b, wolt)
        // The Bolt order still completes with its own slider and waiting screen.
        r.see(boltCard(1_201_000)); r.see(boltWaiting(1_300_000)); r.see(boltWaiting(1_301_100))
        val done = r.events.single { it.type == "delivered" }
        assertEquals(b.id, done.tripId); assertEquals("delivery_slider_then_waiting", done.reason); assertEquals(TripPhase.FINISHED, b.phase)
        assertTrue(atCustomer(r.core).isEmpty())
    }
    @Test fun namelessWoltCardOfTheClosedOrderOpensNothingAndClosesNothing() {
        val r = run(); val (_, b) = woltClosedByBolt(r, "Customer A"); val trips = r.core.trips.size
        val o = woltCardWithoutName(1_200_500)
        assertEquals(ScreenStage.DROPOFF, o.stage); assertNull(o.customer.name); assertTrue(DeliveryCore.keyIds(o).isEmpty())
        r.see(o); r.see(woltCardWithoutName(1_230_000))
        nothingChanged(r, trips, b, wolt)
        r.see(boltCard(1_231_000)); r.see(boltWaiting(1_300_000)); r.see(boltWaiting(1_301_100))
        assertEquals(b.id, r.events.single { it.type == "delivered" }.tripId); assertEquals(TripPhase.FINISHED, b.phase)
    }
    @Test fun boltCardWithoutItsIdOpensNothingAndClosesNothing() {
        val r = run(); val (_, w) = boltClosedByWolt(r); val trips = r.core.trips.size
        val o = boltCardWithoutId(1_200_500)
        assertEquals(ScreenStage.DROPOFF, o.stage); assertTrue(DeliveryCore.keyIds(o).isEmpty())
        r.see(o)
        nothingChanged(r, trips, w, bolt)
        // The Wolt order still completes with its own slider and waiting map.
        r.see(woltCard(1_201_000, "Customer A")); r.see(woltWaiting(1_300_000)); r.see(woltWaiting(1_301_100))
        val done = r.events.single { it.type == "delivered" }
        assertEquals(w.id, done.tripId); assertEquals("delivery_slider_then_waiting", done.reason); assertEquals(TripPhase.FINISHED, w.phase)
    }
    @Test fun aCardWithAnotherOrderIdStillOpensThatOrder() {
        val r = run(); val (b, w) = boltClosedByWolt(r); val trips = r.core.trips.size
        r.see(boltCard(1_210_000, "#ZZZZ9"))
        assertEquals(trips + 1, r.core.trips.size)
        val x = r.core.active(bolt)!!
        assertNotSame(b, x); assertTrue(x.customers.containsKey("#ZZZZ9"))
        assertEquals("active_screen_offer_missed", r.events.single { it.type == "accepted" && it.tripId == x.id }.reason)
        // Offer and pickup missed, first seen at its customer card: such an order closes nothing (it may be a stale card).
        assertTrue(w.atCustomer); assertEquals(1, r.events.count { it.type == "delivered_inferred" })
    }
    @Test fun anOrderFirstSeenAtItsCustomerCardStillClosesTheOtherWhenNoClosedCardCanBeOnScreen() {
        // No order closed yet (e.g. recording resumed mid-delivery): the Bolt order's offer and pickup were missed, its first
        // frame is its customer card. The courier's rule holds: one order at the customer stage.
        val r = run()
        r.see(woltOffer(10_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000)); r.see(woltCard(600_000, "Customer A"))
        val w = r.core.active(wolt)!!
        r.see(boltCard(700_000))
        val x = r.core.active(bolt)!!
        assertTrue(x.missedOffer); assertTrue(x.pickups.isEmpty())
        assertEquals(listOf(x), atCustomer(r.core)); assertEquals(w.id, r.events.single { it.type == "delivered_inferred" }.tripId)
    }
    @Test fun aCardWithAnotherOrderIdLongAfterTheCloseClosesTheOther() {
        val r = run(); val (_, w) = boltClosedByWolt(r)
        val later = 1_200_000 + DeliveryCore.SLIDER_TO_WAITING_MS + 1
        r.see(boltCard(later, "#ZZZZ9"))
        val x = r.core.active(bolt)!!
        assertEquals(listOf(x), atCustomer(r.core)); assertEquals(x.id, w.deliveredInferredBy)
    }
    @Test fun afterARestartTheClosedOrdersNamelessCardStillOpensNothing() {
        val r = run(); val (_, b) = woltClosedByBolt(r, "Customer A")
        // Process death: trips come back from courier.db; what was seen in memory is gone.
        val c = DeliveryCore()
        for (t in r.core.trips.values) RecordJson.trip(RecordJson.trip(t)).let { c.trips[it.id] = it }
        c.start()
        val r2 = Run(c)
        r2.see(woltCardWithoutName(1_230_000))
        assertEquals(2, c.trips.size); assertNull(c.active(wolt)); assertEquals(listOf(c.trips.getValue(b.id)), atCustomer(c))
        assertTrue(r2.events.isEmpty())
    }
    @Test fun anOfferLeftToTimeOutIsNotAcceptedByTheClosedOrdersNamelessCard() {
        // A Wolt offer pops up over the closed order's card and goes without a tap; the card is read again without its name.
        val r = run(); val (_, b) = woltClosedByBolt(r, "Customer A"); val trips = r.core.trips.size
        r.see(woltOffer(1_210_000)); r.see(woltCardWithoutName(1_240_000))
        nothingChanged(r, trips, b, wolt); assertNotNull(r.core.pending[wolt])
        // Like over an open order's card, an offer not seen for minutes is gone.
        r.see(woltCardWithoutName(1_210_000 + DeliveryCore.OFFER_STALE_MS))
        assertEquals("offer_not_seen", r.events.single { it.type == "offer_unconfirmed" }.reason)
        nothingChanged(r, trips, b, wolt)
    }
    @Test fun aCardNamingTheClosedOrderLeavesAPendingOfferPending() {
        val r = run(); val (w, _) = woltClosedByBolt(r, "Customer A")
        r.see(woltOffer(1_210_000)); r.see(woltCard(1_215_000, "Customer A"))
        assertNull(r.core.active(wolt)); assertNotNull(r.core.pending[wolt]); assertTrue(w.deliveredInferred)
        assertTrue(r.events.none { it.type == "accepted" && it.at > 1_200_000 })
    }
    @Test fun theClosedOrdersCardIsOnlyItsOwnForAWhile() {
        val r = run(); val (_, b) = woltClosedByBolt(r, "Customer A")
        // Bolt order delivered; much later a nameless Wolt card (offer and pickup missed) is a new order again.
        r.see(boltWaiting(1_300_000)); r.see(boltWaiting(1_301_100))
        assertEquals(TripPhase.FINISHED, b.phase)
        val later = 1_200_000 + DeliveryCore.SLIDER_TO_WAITING_MS + 1
        r.see(woltCardWithoutName(later))
        assertEquals("active_screen_offer_missed", r.events.single { it.type == "accepted" && it.at == later }.reason)
    }
}
