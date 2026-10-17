package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K34 root cause, second half: with two open orders of one platform every tap and the waiting map went to the NEWEST
 * order (`active(pkg)`), whatever screen was shown. "Arrived" on the older order's customer card marked the new order as
 * arrived at its restaurant, and the slider + waiting map of the older order could not complete it. Taps and the waiting
 * map now belong to the order whose screen was seen last ([DeliveryCore.shown]).
 * Also: a customer card naming the customer of an inferred delivery is that closed order's (ignored), unless the platform
 * gave a new order after the close — then it is the new order's card (Wolt shows names only, names repeat).
 *
 * Labels: Bolt drop card with "Arrived" + "Slide to confirm" (CourierFlowTest), Bolt pickup and offer (CourierFlowTest,
 * CustomerStageK34Test), Wolt offer E5, pickup E8, waiting sheet E6 (6 Oct field corpus), Wolt customer card
 * (DeliveryRecognitionTest). Names, ids, addresses and the second restaurant are synthetic.
 */
class CustomerStageK34RoutingTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)

    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltPickup(at: Long, restaurant: String = "Xengeland", address: String = "Mehdi Hüseyn 99") =
        obs(bolt, at, "Order is being prepared", "Arrive in 11 min", restaurant, address)
    /** Bolt drop card before arrival: "Arrived" is the slider's label. */
    private fun boltDrop(at: Long, id: String) = obs(bolt, at, "Drop off in -1 min", "Test C. $id", "Items (1)", "Address", "Test 5, Bakı", "Instructions",
        "Meet at my door", "Floor", "3", "Arrived", "Slide to confirm", actions = listOf("Arrived"))
    private fun boltCard(at: Long, id: String = "#UWG2Q") = obs(bolt, at, "Drop off in -4 min", "Test C. $id", "Items (3)", "Address", "Test 5, Bakı",
        "Instructions", "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm")
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")

    private fun woltWaiting(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran") = obs(wolt, at, "Decline", "Ready in 1 min", fee,
        "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept",
        actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(wolt, at, "Pickup from", restaurant, "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long, name: String) = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details",
        "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))

    private fun core() = DeliveryCore().also { it.start() }
    private fun atCustomer(c: DeliveryCore) = c.trips.values.filter { it.atCustomer }

    /** Wolt order at "Customer A" with its slider on screen. */
    private fun woltAtCustomer(c: DeliveryCore, events: MutableList<CoreEvent>): TripRecord {
        events += c.observe(woltWaiting(0)); events += c.observe(woltWaiting(1000))
        events += c.observe(woltOffer(10_000)); events += c.click(wolt, "Accept", 14_000); events += c.observe(woltPickup(15_000))
        events += c.observe(woltCard(600_000, "Customer A")); events += c.observe(woltCard(601_000, "Customer A"))
        return c.active(wolt)!!
    }

    @Test fun shownIsTheOrderWhoseScreenWasSeenLast() {
        val c = core()
        c.observe(boltOffer(1000)); c.click(bolt, "22 min, 4,49 ₼", 1500); c.observe(boltPickup(2000)); c.observe(boltDrop(300_000, "#AAAA1"))
        val first = c.active(bolt)!!
        assertSame(first, c.shown(bolt))
        c.observe(boltOffer(310_000)); c.click(bolt, "22 min, 4,49 ₼", 311_000); c.observe(boltPickup(312_000, "Test Kebab", "Test 9"))
        val second = c.active(bolt)!!
        assertNotSame(first, second); assertSame(second, c.shown(bolt))
        // The older order's card again (named by its order id): that order is shown, the newest one stays where it was.
        c.observe(boltDrop(320_000, "#AAAA1"))
        assertSame(first, c.shown(bolt)); assertSame(second, c.active(bolt))
        assertEquals(TripPhase.TO_RESTAURANT, second.phase); assertTrue(second.customers.isEmpty())
        assertNull(c.shown(wolt))
    }
    @Test fun arrivedOnTheOlderOrdersCardIsThatOrdersArrival() {
        val c = core()
        c.observe(boltOffer(1000)); c.click(bolt, "22 min, 4,49 ₼", 1500); c.observe(boltPickup(2000)); c.observe(boltDrop(300_000, "#AAAA1"))
        val first = c.active(bolt)!!
        c.observe(boltOffer(310_000)); c.click(bolt, "22 min, 4,49 ₼", 311_000); c.observe(boltPickup(312_000, "Test Kebab", "Test 9"))
        val second = c.active(bolt)!!
        c.observe(boltDrop(320_000, "#AAAA1")); c.click(bolt, "Arrived", 321_000)
        // Before K34 the tap went to the newest order: "arrived at the restaurant" (WAITING) for an order not yet reached.
        assertEquals(TripPhase.AT_CUSTOMER, first.phase); assertEquals(321_000L, first.customers.getValue("#AAAA1").arrivedAt)
        assertEquals(TripPhase.TO_RESTAURANT, second.phase); assertNull(second.pickups.single().arrivedAt)
        assertEquals(listOf(first), atCustomer(c))
    }
    @Test fun sliderThenWaitingMapCompletesTheOrderWhoseCardWasShown() {
        val c = core(); val events = ArrayList<CoreEvent>()
        val first = woltAtCustomer(c, events)
        // Next offer accepted while still at the customer; its pickup screen, then the first order's card with the slider again.
        events += c.observe(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); events += c.click(wolt, "Accept", 605_000)
        events += c.observe(woltPickup(606_000, restaurant = "OTHER DONER")); val second = c.active(wolt)!!
        events += c.observe(woltCard(607_000, "Customer A"))
        events += c.observe(woltWaiting(608_000)); events += c.observe(woltWaiting(609_100))
        // Before K34 the slider of the shown order was checked on the newest order, and the map later closed both unconfirmed.
        val done = events.single { it.type == "delivered" }
        assertEquals(first.id, done.tripId); assertEquals("delivery_slider_then_waiting", done.reason); assertEquals(TripPhase.FINISHED, first.phase)
        assertTrue(second.active)
        events += c.observe(woltWaiting(611_200))
        assertEquals("waiting_map_before_pickup", events.single { it.type == "end_unconfirmed" && it.tripId == second.id }.reason)
        // D3(b): the second order's restaurant screen after the slider first counted the first order as an unverified
        // delivery (606 s); its own slider then waiting map made it a real one, the history kept.
        val inferred = events.single { it.type == "delivered_inferred" }
        assertEquals(first.id, inferred.tripId); assertEquals(606_000L, inferred.at); assertEquals(606_000L, first.deliveredInferredAt)
    }
    @Test fun aNewOrderAcceptedAfterTheCloseOwnsACardWithTheSameName() {
        val c = core(); val events = ArrayList<CoreEvent>()
        val first = woltAtCustomer(c, events)
        events += c.observe(boltOffer(610_000)); events += c.click(bolt, "22 min, 4,49 ₼", 612_000); events += c.observe(boltPickup(613_000))
        events += c.observe(boltCard(1_200_000))
        assertTrue(first.deliveredInferred)
        events += c.observe(boltWaiting(1_300_000)); events += c.observe(boltWaiting(1_301_100))
        assertEquals("delivery_slider_then_waiting", events.single { it.type == "delivered" && it.pkg == bolt }.reason)
        // A later Wolt order for a customer with the same shown name (Wolt cards carry names only).
        events += c.observe(woltWaiting(1_350_000))
        events += c.observe(woltOffer(1_400_000, fee = "₼2.91", restaurant = "Other Doner")); events += c.click(wolt, "Accept", 1_403_000)
        events += c.observe(woltPickup(1_404_000, restaurant = "OTHER DONER"))
        val next = c.active(wolt)!!
        events += c.observe(woltCard(2_000_000, "Customer A"))
        assertEquals(TripPhase.TO_CUSTOMER, next.phase); assertTrue(next.customers.containsKey("name:customer a"))
        assertEquals(2_000_000L, next.pickups.single().pickedAt)
        assertEquals(listOf(next), atCustomer(c))
        // The closed order is left as it was.
        assertTrue(first.deliveredInferred); assertEquals(1_200_000L, first.deliveredInferredAt)
        assertEquals(1, events.count { it.type == "delivered_inferred" })
    }
    @Test fun anOrderAcceptedBeforeTheCloseDoesNotTakeTheClosedOrdersCard() {
        val c = core(); val events = ArrayList<CoreEvent>()
        val first = woltAtCustomer(c, events)
        // Next Wolt order accepted while handing over the first; then a Bolt order reaches its customer and closes the first.
        events += c.observe(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); events += c.click(wolt, "Accept", 605_000)
        events += c.observe(woltPickup(606_000, restaurant = "OTHER DONER")); val second = c.active(wolt)!!
        events += c.observe(boltOffer(610_000)); events += c.click(bolt, "22 min, 4,49 ₼", 612_000); events += c.observe(boltPickup(613_000))
        events += c.observe(boltCard(1_200_000)); val b = c.active(bolt)!!
        assertEquals(first.id, events.single { it.type == "delivered_inferred" }.tripId)
        // The first order's card drawn again (split screen): not the second order's customer, and it closes nothing.
        events += c.observe(woltCard(1_200_500, "Customer A")); events += c.observe(boltCard(1_201_000))
        assertEquals(TripPhase.TO_RESTAURANT, second.phase); assertNull(second.pickups.single().pickedAt); assertTrue(second.customers.isEmpty())
        assertEquals(listOf(b), atCustomer(c)); assertEquals(1, events.count { it.type == "delivered_inferred" })
    }
}
