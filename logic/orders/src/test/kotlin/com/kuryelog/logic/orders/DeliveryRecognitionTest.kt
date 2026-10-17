package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Screen labels transcribed from the courier's Wolt / Bolt Food screenshots (October 2026).
 * Customer names, addresses and numbers are synthetic.
 */
class DeliveryRecognitionTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)

    private fun woltCard(at: Long, name: String = "Test Customer", id: String = "#408") = obs(wolt, at,
        "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details", "Address details",
        "Block B ( TEST evleri) (Domofon kodu: 68🔔)", "Notes", "BLOCK B\n(Domofon kodu: 68🔔)\nMertebe 6...", "Translate",
        "Order details", "From", "Punkap", id, "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))
    private fun woltWaiting(at: Long) = obs(wolt, at, "Expect a task soon", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 50% from 08:00")
    private fun woltHot(at: Long) = obs(wolt, at, "Orders coming in hot!", "8 live hotspots", "Hər sifariş üçün əlavə 50% from 08:00")
    private fun woltOffline(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 50% from 08:00", "Go online")
    private fun woltOffer(at: Long) = obs(wolt, at, "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Test Doner", "Test 1, Baku", "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup 3 min", "Pickup from", "Punkap", "Test 3, Baku", actions = listOf("Collect orders"))

    private fun boltCard(at: Long, id: String = "#UWG2Q", slider: String = "Delivered") = obs(bolt, at,
        "Drop off in -4 min", "Test C. $id", "Items (3)", "Address", "Test 5, Bakı", "Instructions", "Meet at the door",
        "Call", "Chat", "Delivery issues?", slider, "Slide to confirm")
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")
    private fun boltOffline(at: Long) = obs(bolt, at, "1.5x", "1.45x", "BADAMDAR", "Go online")

    @Test fun woltDeliveryIsRecordedAndCourierIsWaitingAgain() {
        val c = DeliveryCore(); c.start(); c.observe(woltCard(1000)); c.observe(woltCard(5000))
        assertTrue(c.observe(woltWaiting(6000)).isEmpty())
        assertEquals("delivered", c.observe(woltWaiting(7100)).single().type)
        assertNull(c.active(wolt)); assertEquals(TripPhase.FINISHED, c.trips.values.single().phase)
        // The next offer is a new job, not the old one.
        c.observe(woltOffer(9000)); assertEquals(1, c.pending.size); assertEquals(1, c.trips.size)
    }
    @Test fun ordersComingInHotIsTheWoltWaitingMap() {
        assertEquals(ScreenStage.IDLE, woltHot(1000).stage)
        val c = DeliveryCore(); c.start(); c.observe(woltCard(1000)); c.observe(woltHot(2000)); c.observe(woltHot(3100))
        assertEquals(TripPhase.FINISHED, c.trips.values.single().phase)
    }
    @Test fun boltDeliveredSliderThenLookingForOrdersIsADelivery() {
        val o = boltCard(1000)
        assertEquals(ScreenStage.DROPOFF, o.stage); assertTrue(o.deliverControl)
        assertEquals(ScreenStage.IDLE, boltWaiting(2000).stage)
        val c = DeliveryCore(); c.start(); c.observe(o); c.observe(boltWaiting(2000))
        assertEquals("delivered", c.observe(boltWaiting(3200)).single().type)
    }
    @Test fun boltArrivedSliderAloneIsNotADelivery() {
        assertFalse(boltCard(1000, slider = "Arrived").deliverControl)
        val c = DeliveryCore(); c.start(); c.observe(boltCard(1000, slider = "Arrived")); c.observe(boltWaiting(2000)); c.observe(boltWaiting(4000))
        assertNull(c.active(bolt)!!.completedAt)
    }
    @Test fun nextCustomerCardMarksThePreviousOneDelivered() {
        val c = DeliveryCore(); c.start()
        c.observe(woltCard(1000, "Customer A")); val events = c.observe(woltCard(2000, "Customer B"))
        assertEquals("next_customer_after_delivery_slider", events.single { it.type == "customer_delivered" }.reason)
        val t = c.active(wolt)!!
        assertNotNull(t.customers["name:customer a"]!!.deliveredAt); assertNull(t.customers["name:customer b"]!!.deliveredAt)
        c.observe(woltWaiting(3000)); c.observe(woltWaiting(4100))
        assertEquals(TripPhase.FINISHED, t.phase); assertEquals(2, t.customers.values.count { it.deliveredAt != null }); assertEquals(1, c.trips.size)
    }
    @Test fun boltBundleSecondOrderFollowsTheFirst() {
        val c = DeliveryCore(); c.start()
        c.observe(obs(bolt, 500, "Order is being prepared", "Arrive in 5 min", "Test Kebab", "Test 9", "Orders to pick up:", "2", "#AAAA1", "#BBBB2"))
        c.observe(boltCard(1000, "#AAAA1")); c.observe(boltCard(2000, "#BBBB2"))
        val t = c.active(bolt)!!; assertNotNull(t.customers["#AAAA1"]!!.deliveredAt); assertNull(t.customers["#BBBB2"]!!.deliveredAt)
        c.observe(boltWaiting(3000)); c.observe(boltWaiting(4200)); assertEquals(TripPhase.FINISHED, t.phase)
    }

    // ---- Menü, destek, açılır pencere: sipariş durumu değişmez ----

    @Test fun woltSideMenuDuringDeliveryChangesNothing() {
        val menu = obs(wolt, 2000, "Test Courier", "Online", "Delivery settings", "Insights", "Finances", "Stats", "Earn extra", "Benefits and insurance", "Referrals", "Settings", "Support", "Help center", "Version 2.201.5 (22010500)")
        assertEquals(ScreenStage.UNKNOWN, menu.stage)
        val c = DeliveryCore(); c.start(); c.observe(woltCard(1000)); c.observe(menu); c.observe(menu.copy(at = 4000))
        assertNull(c.active(wolt)!!.completedAt)
    }
    @Test fun popupOverTheCustomerCardIsNotADelivery() {
        val popup = obs(wolt, 2000, "Keyfiyyəti qoruyaq!", "Termoçantadan istifadə qaydaları.", "Anladım!")
        assertEquals(ScreenStage.UNKNOWN, popup.stage)
        val c = DeliveryCore(); c.start(); c.observe(woltCard(1000)); c.observe(popup); c.observe(popup.copy(at = 5000)); c.observe(woltCard(6000))
        assertNull(c.active(wolt)!!.completedAt)
    }

    // ---- Çevrimiçi oturumlar ----

    @Test fun onlineSessionCountsOffersAcceptedAndDelivered() {
        val s = OnlineSessions(); val c = DeliveryCore(); c.start()
        fun see(o: CourierObservation): OnlineSessions.Closed? { val closed = s.observe(o); c.observe(o).forEach { e -> s.event(e, e.tripId?.let { c.trips[it] }?.customers?.size ?: 1) }; return closed }
        see(woltOffline(0))
        see(woltWaiting(60_000)); see(woltOffer(120_000)); see(woltPickup(130_000)); see(woltCard(600_000)); see(woltWaiting(900_000)); see(woltWaiting(901_500))
        see(woltOffer(1_000_000)); see(woltWaiting(1_010_000)); see(woltWaiting(1_011_000))
        val closed = see(woltOffline(3_600_000))!!
        assertTrue(closed.startObserved); assertTrue(closed.endObserved)
        assertEquals(60_000L, closed.startAt); assertEquals(3_600_000L, closed.endAt)
        assertEquals(2, closed.offers); assertEquals(1, closed.accepted); assertEquals(1, closed.delivered)
        assertTrue(s.openSessions().isEmpty())
    }
    @Test fun bundleDeliveriesAreCountedOncePerCustomer() {
        val s = OnlineSessions(); val c = DeliveryCore(); c.start()
        fun see(o: CourierObservation) { s.observe(o); c.observe(o).forEach { e -> s.event(e, e.tripId?.let { c.trips[it] }?.customers?.size ?: 1) } }
        see(woltWaiting(0)); see(woltCard(1000, "Customer A")); see(woltCard(2000, "Customer B")); see(woltWaiting(3000)); see(woltWaiting(4100))
        assertEquals(2, s.stop().single().delivered)
    }
    @Test fun shiftStopClosesAtTheLastOnlineScreenAndMenusDoNotExtendIt() {
        val s = OnlineSessions()
        s.observe(woltWaiting(1000)); s.observe(woltWaiting(5000)); s.observe(obs(wolt, 9000, "Your stats", "TODAY"))
        val c = s.stop().single()
        assertFalse(c.startObserved); assertFalse(c.endObserved); assertEquals(5000L, c.endAt); assertEquals("shift_stop", c.endReason)
    }
    @Test fun openSessionSurvivesProcessDeath() {
        val a = OnlineSessions(); a.observe(boltWaiting(1000)); a.observe(boltWaiting(2000))
        val b = OnlineSessions(); b.restore(a.encode())
        assertEquals(1000L, b.openSessions().single().startAt)
        val closed = b.observe(boltOffline(9000))!!
        assertEquals(1000L, closed.startAt); assertEquals(9000L, closed.endAt); assertFalse(closed.startObserved)
    }

    @Test fun phoneIsShownButNeverStored() {
        val o = obs(bolt, 1, "Drop off in 2 min", "Test C. #ABCD1", "Address", "Test 5", "Additional note", "Zəng edin 055 123 45 67, mertebe 4")
        assertEquals("+994 55 123 45 67", o.customer.phone)
        assertTrue(o.customer.display().contains("+994 55 123 45 67"))
        val stored = RecordJson.details(o.customer).toString() + RecordJson.observation(o).toString()
        assertFalse(stored.contains("123 45 67")); assertFalse(stored.contains("phone")); assertTrue(stored.contains("[tel]"))
        assertNull(RecordJson.details(RecordJson.details(o.customer)).phone)
    }
}
