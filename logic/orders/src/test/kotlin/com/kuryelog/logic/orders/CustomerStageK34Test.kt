package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K34 (docs/KURYELOG_ISTEKLER.md, field bug 8 Oct 2026): the first Wolt order stayed "Müşteriye gidiliyor" after it was
 * delivered, the second became "Müşteriye gidiliyor" too; a screenshot showed Wolt ×2 + Bolt Food ×1 at the customer stage.
 * Courier's rule: at most ONE order, of either app, is at the customer stage.
 *
 * Root cause (DeliveryCore before K34): a swiped Wolt slider is a delivery only when a stable waiting map follows it
 * (`delivery_slider_then_waiting`) or the next customer card of the SAME trip does. When the next task follows the slider
 * directly (offer → Accept → pickup, no waiting map in between), the trip stays open; once a newer trip of that platform
 * exists, `active(pkg)` routes every later frame and signal to the newest one, so the old trip can only end as
 * `end_unconfirmed` (waiting map ≥ 3 s, shift stop). A Bolt trip at its customer never touched the Wolt trips at all.
 *
 * Labels: Wolt offer E5 and pickup E8 from the 6 Oct field corpus (`sonuc-2026-10-06/saha/field_corpus.json`), waiting
 * sheet E6, Wolt customer card and Bolt card from DeliveryRecognitionTest, Bolt offer/pickup from CourierFlowTest.
 * Names, addresses and the second restaurant are synthetic. Only APIs older than K34 are used here, so this class also
 * runs against the code before the fix (it fails there).
 */
class CustomerStageK34Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, at, source)

    /** E6: collapsed waiting sheet. */
    private fun woltWaiting(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    /** E5 offer card; the second offer of the day uses a synthetic restaurant and the 8 Oct fee AZN 2.91 (K36 list). */
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran") = obs(wolt, at, "Decline", "Ready in 1 min", fee,
        "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept",
        actions = listOf("Decline", "Accept"))
    /** E8 pickup screen. */
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(wolt, at, "Pickup from", restaurant, "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))
    /** Wolt customer card with the "Order delivered!" slider. */
    private fun woltCard(at: Long, name: String, source: String = "accessibility") = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"), source = source)
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltPickup(at: Long) = obs(bolt, at, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99")
    private fun boltCard(at: Long, id: String = "#UWG2Q") = obs(bolt, at, "Drop off in -4 min", "Test C. $id", "Items (3)", "Address", "Test 5, Bakı",
        "Instructions", "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm")

    private fun atCustomer(c: DeliveryCore) = c.trips.values.filter { it.active && (it.phase == TripPhase.TO_CUSTOMER || it.phase == TripPhase.AT_CUSTOMER) }
    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(pkg: String, text: String, at: Long) { events += core.click(pkg, text, at) }
    }
    private fun run(): Run = Run(DeliveryCore().also { it.start() })

    /** First Wolt order: offer, Accept, pickup, customer card with the slider (two frames). */
    private fun firstWoltOrderAtCustomer(r: Run) {
        r.see(woltWaiting(0)); r.see(woltWaiting(1000))
        r.see(woltOffer(10_000)); r.see(woltOffer(11_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(woltCard(600_000, "Customer A")); r.see(woltCard(601_000, "Customer A"))
    }
    /** The courier slides "Order delivered!" (no click event) and the next offer comes at once: Accept, pickup, customer card. */
    private fun nextWoltOrderRightAfterTheSlider(r: Run, cardAt: Long = 1_200_000L) {
        r.see(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 605_000)
        r.see(woltPickup(606_000, restaurant = "OTHER DONER"))
        r.see(woltCard(cardAt, "Customer B"))
    }

    // ---- root cause, reproduced with the 8 Oct sequence ----

    @Test fun nextTaskRightAfterTheSliderLeftTheDeliveredOrderAtTheCustomer() {
        val r = run(); firstWoltOrderAtCustomer(r)
        val first = r.core.active(wolt)!!
        nextWoltOrderRightAfterTheSlider(r)
        assertEquals(2, r.core.trips.size)
        // Before K34 both orders read "Müşteriye gidiliyor" here.
        assertEquals(1, atCustomer(r.core).size)
        val open = atCustomer(r.core).single()
        assertNotSame(first, open); assertTrue(open.customers.containsKey("name:customer b"))
        // The first order is closed as an unverified delivery, never as a real one. D3(b) (13 Oct): already when the next
        // order's restaurant screen came after its slider (606 s), not only at that order's customer card (1200 s).
        val e = r.events.single { it.type == "delivered_inferred" }
        assertEquals(first.id, e.tripId); assertEquals("next_order_at_restaurant_after_slider", e.reason); assertEquals(606_000L, e.at)
        assertEquals(TripPhase.END_UNCONFIRMED, first.phase); assertFalse(first.active)
        assertNull(first.completedAt); assertTrue(first.customers.values.all { it.deliveredAt == null })
        assertTrue(r.events.none { it.tripId == first.id && (it.type == "delivered" || it.type == "customer_delivered") })
    }
    @Test fun laterWaitingMapNoLongerTurnsTheFirstOrderIntoAnUnconfirmedEnd() {
        val r = run(); firstWoltOrderAtCustomer(r); val first = r.core.active(wolt)!!.id
        nextWoltOrderRightAfterTheSlider(r); val second = r.core.active(wolt)!!.id
        r.see(woltWaiting(1_300_000)); r.see(woltWaiting(1_301_100)); r.see(woltWaiting(1_303_200))
        assertEquals(second, r.events.single { it.type == "delivered" }.tripId)
        assertEquals("delivery_slider_then_waiting", r.events.single { it.type == "delivered" }.reason)
        // Before K34: end_unconfirmed / waiting_map_without_delivery for the first order (the 8 Oct "doğrulanmamış").
        assertTrue(r.events.none { it.type == "end_unconfirmed" })
        assertEquals(first, r.events.single { it.type == "delivered_inferred" }.tripId)
        assertTrue(atCustomer(r.core).isEmpty())
    }

    // ---- the rule ----

    @Test fun boltOrderAtItsCustomerClosesTheWoltOrderStillAtItsCustomer() {
        val r = run(); firstWoltOrderAtCustomer(r); val w = r.core.active(wolt)!!
        r.see(boltOffer(610_000)); r.tap(bolt, "22 min, 4,49 ₼", 612_000); r.see(boltPickup(613_000))
        // Accepting (or going to the restaurant) is not yet the customer stage: the Wolt order is left as it is.
        assertEquals(TripPhase.TO_CUSTOMER, w.phase); assertEquals(TripPhase.TO_RESTAURANT, r.core.active(bolt)!!.phase)
        assertTrue(r.events.none { it.type == "delivered_inferred" })
        r.see(boltCard(1_200_000))
        assertEquals(listOf(r.core.active(bolt)!!), atCustomer(r.core))
        val e = r.events.single { it.type == "delivered_inferred" }
        assertEquals(w.id, e.tripId); assertEquals(wolt, e.pkg); assertEquals(TripPhase.END_UNCONFIRMED, w.phase); assertNull(w.completedAt)
    }
    @Test fun screenshotOf8OctThreeOrdersAtTheCustomerBecomeOne() {
        val r = run(); firstWoltOrderAtCustomer(r); val w1 = r.core.active(wolt)!!
        nextWoltOrderRightAfterTheSlider(r); val w2 = r.core.active(wolt)!!
        r.see(boltOffer(1_210_000)); r.tap(bolt, "22 min, 4,49 ₼", 1_212_000); r.see(boltPickup(1_213_000)); r.see(boltCard(1_800_000))
        val b = r.core.active(bolt)!!
        assertEquals(listOf(b), atCustomer(r.core))
        assertEquals(listOf(w1.id, w2.id), r.events.filter { it.type == "delivered_inferred" }.map { it.tripId })
        assertTrue(r.events.none { it.type == "delivered" })
    }
    @Test fun ordersOpenAtTheCustomerAfterARestartAreReducedToOne() {
        // courier.db of 8 Oct: two trips restored at the customer stage (no slider seen on either).
        val c = DeliveryCore(); c.start()
        fun restored(id: String, at: Long, name: String) = TripRecord(id, wolt, null, at, TripPhase.TO_CUSTOMER).also {
            val key = "name:" + CourierParser.norm(name); it.currentCustomer = key; it.customers[key] = CustomerRecord(key, DeliveryDetails(name = name)) }
        val old = restored("trip-1-1", 1000, "Customer A"); val newer = restored("trip-2-2", 2000, "Customer B")
        c.trips[old.id] = old; c.trips[newer.id] = newer
        val e = c.observe(woltCard(5000, "Customer B")).single { it.type == "delivered_inferred" }
        assertEquals(old.id, e.tripId); assertEquals("next_order_at_customer", e.reason)
        assertEquals(listOf(newer), atCustomer(c))
    }

    // ---- what the rule must not do ----

    @Test fun cardOfTheClosedOrderStillOnScreenOpensNoOrderAndClosesNothing() {
        val r = run(); firstWoltOrderAtCustomer(r)
        r.see(boltOffer(610_000)); r.tap(bolt, "22 min, 4,49 ₼", 612_000); r.see(boltPickup(613_000)); r.see(boltCard(1_200_000))
        val b = r.core.active(bolt)!!; val trips = r.core.trips.size
        // Split screen: the Wolt card is still drawn (accessibility), and a late OCR crop of it arrives.
        r.see(woltCard(1_200_500, "Customer A")); r.see(woltCard(1_199_000, "Customer A", source = "ocr")); r.see(boltCard(1_201_000)); r.see(woltCard(1_201_500, "Customer A"))
        assertEquals(trips, r.core.trips.size); assertNull(r.core.active(wolt))
        assertEquals(listOf(b), atCustomer(r.core))
        assertEquals(1, r.events.count { it.type == "delivered_inferred" })
        assertTrue(r.events.none { it.type == "accepted" && it.at > 1_200_000 })
    }
    @Test fun aLateFrameNeverClosesAnOrderSeenAtItsCustomerAfterIt() {
        val c = DeliveryCore(); c.start()
        c.observe(boltOffer(1000)); c.click(bolt, "22 min, 4,49 ₼", 1500); c.observe(boltPickup(2000))
        c.observe(woltOffer(3000)); c.click(wolt, "Accept", 4000); c.observe(woltPickup(5000)); c.observe(woltCard(20_000, "Customer A"))
        val w = c.active(wolt)!!
        // A Bolt card captured at 15 s is processed after the Wolt card of 20 s.
        assertTrue(c.observe(boltCard(15_000)).none { it.type == "delivered_inferred" })
        assertTrue(w.active)
        assertEquals(w.id, c.observe(boltCard(25_000)).single { it.type == "delivered_inferred" }.tripId)
    }
    @Test fun customerCardOfTheOlderOrderStaysWithIt() {
        // Next Wolt offer accepted while the first order is still being handed over: the first order's card comes back.
        val r = run(); firstWoltOrderAtCustomer(r); val first = r.core.active(wolt)!!
        r.see(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 605_000); r.see(woltPickup(606_000, restaurant = "OTHER DONER"))
        val second = r.core.active(wolt)!!
        // D3(b): Wolt showed the next order's restaurant after the first order's slider, so the first one counts as an
        // unverified delivery from then on; its card coming back neither reopens it nor moves the second order on.
        assertEquals(first.id, r.events.single { it.type == "delivered_inferred" }.tripId)
        r.see(woltCard(610_000, "Customer A"))
        assertTrue(atCustomer(r.core).isEmpty()); assertTrue(first.deliveredInferred)
        assertEquals(TripPhase.TO_RESTAURANT, second.phase); assertNull(second.pickups.single().pickedAt); assertTrue(second.customers.isEmpty())
        // Its own customer card then moves the second order on; the first one is not closed a second time.
        r.see(woltCard(1_200_000, "Customer B"))
        assertEquals(listOf(second), atCustomer(r.core)); assertEquals(first.id, r.events.single { it.type == "delivered_inferred" }.tripId)
    }
    @Test fun aRealDeliveryStaysARealDelivery() {
        val r = run(); firstWoltOrderAtCustomer(r); val w = r.core.active(wolt)!!
        r.see(woltWaiting(602_000)); r.see(woltWaiting(603_100))
        assertEquals("delivered", r.events.single { it.tripId == w.id && it.type == "delivered" }.type)
        assertEquals(TripPhase.FINISHED, w.phase)
        r.see(boltOffer(610_000)); r.tap(bolt, "22 min, 4,49 ₼", 612_000); r.see(boltPickup(613_000)); r.see(boltCard(1_200_000))
        assertTrue(r.events.none { it.type == "delivered_inferred" }); assertEquals(TripPhase.FINISHED, w.phase)
    }
    @Test fun customersOfOneBundleAreNotSeparateOrders() {
        val r = run()
        r.see(obs(bolt, 500, "Order is being prepared", "Arrive in 5 min", "Test Kebab", "Test 9", "Orders to pick up:", "2", "#AAAA1", "#BBBB2"))
        r.see(boltCard(1000, "#AAAA1")); r.see(boltCard(2000, "#BBBB2"))
        assertEquals(1, r.core.trips.size); assertTrue(r.events.none { it.type == "delivered_inferred" })
        assertEquals(1, atCustomer(r.core).size)
    }
}
