package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K1–K5 (docs/KURYELOG_ISTEKLER.md): labels transcribed from the courier's Wolt / Bolt Food
 * screenshots of 6 Oct 2026. Names, addresses, codes and items are synthetic.
 */
class WoltFlowK1K5Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, at, source)

    private val chart = arrayOf("NOW", "09:00", "15:00", "21:00", "03:00", "EXPECTED DEMAND", "EARN EXTRA")
    private val promo = arrayOf("Starts in 4 hours", "Boost", "Hər sifariş üçün əlavə 20%", "18:00-20:00")
    private val links = arrayOf("Quick links", "Previous task", "Scheduled offline", "Support")
    private fun online(at: Long, vararg extra: String) = obs(wolt, at, "Baku Central", "Delivery demand", "Low", *chart, *promo, *links, *extra, "Go offline")
    private fun offline(at: Long, label: String = "Go online", source: String = "accessibility") = obs(wolt, at, "Delivery demand", "Low", *chart, *promo, *links, label, source = source)
    /** E6: collapsed waiting sheet, no slider visible. */
    private fun collapsed(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    private fun feedback(at: Long, banner: Boolean = true) = obs(wolt, at, *(if (banner) arrayOf("1 deliveries declined", "Quick reminder, declining too many can mean being set offline!") else emptyArray()),
        "Feedback", "App functionality", "How were the time estimates?", "Venues", "How was your collaboration with the venue?",
        "Wolt support", "Was everything OK with Wolt support?", "Add a comment", "Submit feedback")
    private fun offer(at: Long, price: String = "₼3.94", restaurant: String = "Test Aptek") = obs(wolt, at, "Decline", "Ready in 1 min", price, "2 stops (4.2 km) • 11–19 min",
        restaurant, "Test street, Baku, AZ1025", "Customer drop-off", "40°23'50\"N 49°49'34\"E, Baku", "Accept", actions = listOf("Decline", "Accept"))
    private fun declineSheet(at: Long) = obs(wolt, at, "Ready in 7 min", "Decline offer?",
        "You'll lose potential earnings and it might take some time to find another offer in your delivery area.", "Decline", "Return to offer details",
        actions = listOf("Decline", "Return to offer details"))
    private fun pickup(at: Long, restaurant: String = "TEST HOUSE") = obs(wolt, at, "Pickup from", restaurant, "Test Street 1, AZ1010 Baku", "Notes",
        "Test prospekti kəsişməsi, mağazanın yanı", "Translate", "Collect order", "#014", "Test R.", "ORDER DETAILS", "1 x Test Künəfə 1 nəfərlik",
        "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))

    @Test fun midSlideWithHiddenLabelDoesNotChangeState() {
        val s = OnlineSessions()
        s.observe(offline(1000)); s.observe(offline(2000, label = "ne", source = "ocr")); s.observe(offline(2100, label = "", source = "ocr"))
        assertTrue(s.openSessions().isEmpty()); assertEquals("offline", s.state(wolt))
        s.observe(online(3000))
        assertEquals(3000L, s.openSessions().single().startAt); assertTrue(s.openSessions().single().startObserved)
    }
    @Test fun singleOcrFrameDoesNotFlipButTwoDo() {
        val s = OnlineSessions(); s.observe(online(1000))
        assertNull(s.observe(offline(2000, label = "> Go online", source = "ocr")))
        assertEquals("online", s.state(wolt))
        val c = s.observe(offline(3500, label = "> Go online", source = "ocr"))!!
        assertEquals(2000L, c.endAt)
    }
    @Test fun feedbackPageEndsTheSessionRightAfterGoOffline() {
        val s = OnlineSessions(); s.observe(online(1000)); s.observe(online(60_000))
        val c = s.observe(feedback(61_000))!!
        assertEquals(61_000L, c.endAt); assertTrue(c.endObserved); assertEquals("wolt_feedback_after_go_offline", c.endReason)
        assertEquals(ScreenStage.UNKNOWN, feedback(61_000).stage)
    }
    @Test fun lateFrameCannotReopenASession() {
        val s = OnlineSessions(); s.observe(online(1000)); s.observe(feedback(5000))
        assertNull(s.observe(online(4000)))
        assertTrue(s.openSessions().isEmpty())
    }
    @Test fun ordersCarryTheirOnlineSession() {
        val s = OnlineSessions(); val c = DeliveryCore(); c.start()
        fun see(o: CourierObservation) { s.observe(o); c.observe(o).forEach { e -> s.event(e) } }
        see(offline(0)); see(online(1000)); see(offer(2000)); see(pickup(3000))
        val id = s.currentId(wolt)!!
        val closed = s.observe(feedback(10_000))!!
        assertEquals(id, closed.id); assertEquals(1, closed.offerIds.size); assertEquals(c.trips.keys.toList(), closed.tripIds)
        val again = OnlineSessions.closed(OnlineSessions.json(closed))
        assertEquals(closed.id, again.id); assertEquals(closed.tripIds, again.tripIds); assertEquals(3000L, again.lastOnlineAt)
    }
    @Test fun schemaVersion1SessionRowsStillLoad() {
        val j = org.json.JSONObject("""{"schemaVersion":1,"pkg":"$wolt","startAtMs":1000,"endAtMs":61000,"durationMinutes":1.0,"startObserved":true,"endObserved":true,"endReason":"offline_screen","offersSeen":2,"accepted":1,"delivered":1}""")
        val c = OnlineSessions.closed(j)
        assertEquals("wolt-1000", c.id); assertEquals(2, c.offers); assertTrue(c.tripIds.isEmpty())
    }
    // ---- K2: Wolt reddi ----

    @Test fun woltDeclineThroughConfirmationSheetIsRecorded() {
        val c = DeliveryCore(); c.start()
        val id = c.observe(offer(1000)).single().offerId
        c.click(wolt, "Decline", 2000)
        assertTrue(c.observe(declineSheet(2200)).isEmpty())
        assertEquals(ScreenStage.UNKNOWN, declineSheet(2200).stage)
        val e = c.click(wolt, "Decline", 3000).single()
        assertEquals("rejected", e.type); assertEquals(id, e.offerId); assertEquals("courier_declined_confirmed", e.reason)
        assertTrue(c.pending.isEmpty()); assertTrue(c.trips.isEmpty())
    }
    @Test fun returnToOfferDetailsKeepsTheOffer() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000))
        c.click(wolt, "Decline", 2000); c.observe(declineSheet(2200)); c.click(wolt, "Return to offer details", 2500)
        assertTrue(c.click(wolt, "Accept", 3000).isEmpty())
        assertEquals("accepted", c.observe(pickup(4000)).single().type)
        assertEquals(3.94, c.active(wolt)!!.gross!!, 0.001)
    }
    @Test fun declineThenWaitingMapIsACourierDecline() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.click(wolt, "Decline", 2000)
        c.observe(collapsed(3000))
        assertEquals("courier_declined", c.observe(collapsed(3400)).single().reason)
    }
    @Test fun declinedBannerConfirmsADeclineOnce() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.click(wolt, "Decline", 2000)
        val first = c.observe(feedback(2500)).single { it.type == "rejected" }
        assertEquals("courier_declined_banner", first.reason)
        c.observe(offer(10_000, price = "₼2.10", restaurant = "Other Doner"))
        assertTrue(c.observe(feedback(11_000)).none { it.type == "rejected" })
    }
    @Test fun offerShownDuringADeliveryAndDeclinedIsNotANewOrder() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.observe(pickup(2000))
        c.observe(offer(60_000, price = "₼3.83", restaurant = "Other Doner"))
        c.click(wolt, "Decline", 61_000); c.observe(declineSheet(61_200))
        assertEquals("courier_declined_confirmed", c.click(wolt, "Decline", 62_000).single().reason)
        c.observe(pickup(63_000))
        assertEquals(1, c.trips.size)
    }
    @Test fun offerOnTopOfTheCurrentTaskIsNotConsumedByThatTask() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.observe(pickup(2000))
        c.observe(offer(60_000, price = "₼3.83", restaurant = "Other Doner"))
        assertTrue(c.observe(pickup(61_000)).none { it.type == "accepted" })
        assertEquals(1, c.trips.size); assertEquals(1, c.pending.size)
        assertEquals("accepted", c.observe(pickup(70_000, restaurant = "Other Doner")).single { it.type == "accepted" }.type)
        assertEquals(2, c.trips.size)
    }
    @Test fun offerNotSeenForMinutesIsDropped() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000))
        assertTrue(c.observe(obs(wolt, 60_000, "Your stats", "TODAY")).isEmpty())
        assertEquals("offer_not_seen", c.observe(obs(wolt, 1000 + DeliveryCore.OFFER_STALE_MS, "Your stats", "TODAY")).single().reason)
    }

    // ---- K3: hayalet sipariş ----

    @Test fun frameWithoutRestaurantCustomerOrCodeOpensNoOrder() {
        val c = DeliveryCore(); c.start()
        assertTrue(c.observe(obs(bolt, 1000, "Order is being prepared", "Orders to pick up:")).isEmpty())
        assertTrue(c.trips.isEmpty())
    }
    @Test fun woltWaitingMapClosesAStaleOrder() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.observe(pickup(2000))
        c.observe(collapsed(10_000)); c.observe(collapsed(11_000))
        assertNotNull(c.active(wolt))
        val e = c.observe(collapsed(13_100)).single { it.type == "end_unconfirmed" }
        assertEquals("waiting_map_before_pickup", e.reason); assertNull(c.active(wolt))
    }
    @Test fun boltWaitingScreenDoesNotCloseBoltOrders() {
        val c = DeliveryCore(); c.start()
        c.observe(obs(bolt, 1000, "Arrive in 5 min", "Test Pizza", "Test 1, Baku"))
        c.observe(obs(bolt, 2000, "Looking for orders", "Go offline")); c.observe(obs(bolt, 9000, "Looking for orders", "Go offline"))
        assertNotNull(c.active(bolt))
    }

    // ---- K5: restoran kartı ----

    @Test fun woltPickupKeepsRestaurantNoteCodeAndItems() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.observe(pickup(2000))
        val p = c.active(wolt)!!.pickups.single()
        assertEquals("Test prospekti kəsişməsi, mağazanın yanı", p.note); assertEquals("#014", p.code)
        assertEquals(listOf("1 x Test Künəfə 1 nəfərlik"), p.items)
        assertTrue(c.active(wolt)!!.customers.values.none { it.details.note != null })
    }
    @Test fun woltReadyInSetsReadyTimeButBoltArrivalMinutesDoNot() {
        val c = DeliveryCore(); c.start()
        c.observe(obs(wolt, 1000, "Ready in 7 min", "Pickup from", "TEST HOUSE", "Test Street 1"))
        assertEquals(1000L + 7 * 60_000, c.active(wolt)!!.pickups.single().readyAt)
        c.observe(obs(bolt, 1000, "Order is being prepared", "Arrive in 11 min", "Test Pizza", "Test 1, Baku"))
        val b = c.active(bolt)!!.pickups.single()
        assertNull(b.readyAt); assertEquals(1000L, b.preparingAt)
    }
    @Test fun readyNotificationMarksTheRestaurantReady() {
        val c = DeliveryCore(); c.start(); c.observe(obs(bolt, 1000, "Arrive in 5 min", "Test Pizza", "Test 1, Baku"))
        c.readinessNotice(bolt, "Your order is ready for pickup", 5000)
        assertEquals(5000L, c.active(bolt)!!.pickups.single().readyObservedAt)
        c.readinessNotice(wolt, "Order is ready", 6000)
        assertEquals(5000L, c.active(bolt)!!.pickups.single().readyObservedAt)
    }
    @Test fun restaurantCardFieldsSurviveJson() {
        val c = DeliveryCore(); c.start(); c.observe(offer(1000)); c.observe(pickup(2000))
        val t = c.active(wolt)!!; t.sessionId = "wolt-500"
        val back = RecordJson.trip(RecordJson.trip(t))
        assertEquals("wolt-500", back.sessionId); assertEquals("#014", back.pickups.single().code)
        assertEquals("Test prospekti kəsişməsi, mağazanın yanı", back.pickups.single().note)
    }
}
