package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Offer economics v2, WP6: offer lifecycle, decline window and wait spells (SPEC 2.1.0 §13, §17.2; findings K5, K7, K9,
 * K10, K17, K18, VM5, N11). Screen texts are the research golden corpus labels (synthetic customer data).
 */
class CoreLifecycleV2Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, at, source)
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")
    private fun boltPickup(at: Long) = obs(bolt, at, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99", "Orders to pick up:", "2", "#AAAA1", "Items (0)", "#BBBB2")
    private fun woltOffer(at: Long, km: String = "2.8", restaurant: String = "Izmir Doner Tantuni No1") = obs(wolt, at, "Ready in 3 min", "Decline", "₼2.31",
        "2 stops ($km km) • 8–16 min", restaurant, "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
    private fun woltOfferOcr(at: Long, km: String = "2.8") = obs(wolt, at, "Decline", "Ready in 3 min", "₼2.31", "2 stops ($km km) • 8–16 min", "Izmir Doner Tantuni Nol",
        "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2,", "Bakı", "Accept", actions = listOf("Decline", "Accept"), source = "ocr")
    private fun woltWaiting(at: Long, demand: String = "Medium") = obs(wolt, at, "Expect a task soon", "Delivery demand", demand, "Hər sifariş üçün əlavə 50% from 08:00")
    private fun woltHot(at: Long) = obs(wolt, at, "Orders coming in hot!", "8 live hotspots", "Hər sifariş üçün əlavə 50% from 08:00")
    private fun woltPopup(at: Long) = obs(wolt, at, "Keyfiyyəti qoruyaq!", "Termoçantadan istifadə qaydaları.", "Anladım!")
    private fun woltOffline(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 50% from 08:00", "Go online")
    private fun woltBan(at: Long) = obs(wolt, at, "Unable to go online", "You've been set offline until 21:46 for declining too many deliveries.", "Go online",
        "Delivery demand Medium", "Hər sifariş üçün əlavə 40% from 12:00")
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup 3 min", "Pickup from", "Donerist-Kebabist Sahil", "Sabail district, Uzeyir Hajibeyov str., 27, AZ1000 Baku",
        actions = listOf("Collect"))
    private fun woltCard(at: Long, name: String) = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details",
        "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))

    // ---------------------------------------------------------------- K17: timeout vs rejection (G04)
    @Test fun offerLeftAtTheDocumentedWindowIsATimeoutNotARejection() {
        val c = DeliveryCore(); c.start(); c.observe(boltWaiting(0))
        val id = c.observe(boltOffer(20_000)).single().offerId
        c.observe(boltOffer(30_000)); c.observe(boltOffer(40_000))
        assertTrue(c.observe(boltWaiting(81_000)).isEmpty())
        val e = c.observe(boltWaiting(82_000)).single()
        assertEquals("offer_unconfirmed", e.type); assertEquals("expired_inferred", e.reason); assertEquals(id, e.offerId)
        val outcome = c.offerOutcomes.single()
        assertTrue(outcome.countsAsDecline); assertFalse(outcome.explicitDecline)
    }
    @Test fun windowBoundaryIsFirstSeenPlusWindowMinusTwoSeconds() {
        val window = DeliveryCore.offerWindowMs(bolt)!!
        assertEquals(60_000L, window); assertEquals(60_000L, DeliveryCore.offerWindowMs(wolt)); assertNull(DeliveryCore.offerWindowMs("com.example.app"))
        fun outcome(firstGone: Long): CoreEvent {
            val c = DeliveryCore(); c.start(); c.observe(boltOffer(1_000))
            c.observe(boltWaiting(firstGone)); return c.observe(boltWaiting(firstGone + 500)).single()
        }
        assertEquals("expired_inferred", outcome(1_000 + window - DeliveryCore.EXPIRY_MARGIN_MS).reason)
        assertEquals("offer_to_idle_map", outcome(1_000 + window - DeliveryCore.EXPIRY_MARGIN_MS - 1).reason)
    }
    @Test fun aDeclineTapStaysADeclineAfterTheWindow() {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(1_000)); c.click(bolt, "Decline", 5_000)
        c.observe(boltWaiting(70_000)); val e = c.observe(boltWaiting(71_000)).single()
        assertEquals("rejected", e.type); assertEquals("courier_declined", e.reason)
    }
    @Test fun lastSeenAtIsKept() {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(1_000)); c.observe(boltOffer(9_000))
        assertEquals(9_000L, c.pending[bolt]!!.lastSeenAt); assertEquals(1_000L, c.pending[bolt]!!.firstAt)
    }

    // ---------------------------------------------------------------- K10: short Wolt ids (P04)
    @Test fun shortWoltIdIsKeptButNeverKeysACustomer() {
        val o = woltCard(1_000, "Customer A")
        assertEquals(ScreenStage.DROPOFF, o.stage); assertTrue("#408" in o.orderIds); assertTrue(DeliveryCore.keyIds(o).isEmpty())
        val c = DeliveryCore(); c.start(); c.observe(o); c.observe(woltCard(2_000, "Customer B"))
        assertEquals(setOf("name:customer a", "name:customer b"), c.active(wolt)!!.customers.keys)
        // Bolt keeps its 5-character ids only.
        assertFalse(obs(bolt, 1_000, "Drop off in -1 min", "Test C. #AAAA1", "#408", "Address", "Test 5, Bakı").orderIds.contains("#408"))
        assertEquals(listOf("#AAAA1", "#BBBB2"), DeliveryCore.keyIds(boltPickup(1_000)))
    }

    // ---------------------------------------------------------------- K7 + N11: one offer through OCR jitter (G15, F07)
    @Test fun ocrJitterKeepsOneOfferIdentity() {
        val c = DeliveryCore(); c.start(); c.observe(woltWaiting(0))
        val events = listOf(woltOffer(20_000), woltOfferOcr(20_000), woltOffer(22_000), woltOfferOcr(23_000)).flatMap { c.observe(it) }
        assertEquals(1, events.count { it.type == "offer" }); assertTrue(events.none { it.reason == "replaced_without_idle" })
        val d = c.pending[wolt]!!.data
        assertEquals("Izmir Doner Tantuni No1", d.restaurant); assertEquals("Test 2, Bakı", d.customerAddress); assertEquals(23_000L, c.pending[wolt]!!.lastSeenAt)
        c.click(wolt, "Accept", 25_000)
        assertEquals("accepted", c.observe(woltPickup(26_000)).single().type); assertEquals(2.31, c.active(wolt)!!.gross!!, 1e-9)
    }
    @Test fun aDifferentCardIsStillANewOffer() {
        val c = DeliveryCore(); c.start(); c.observe(woltOffer(1_000))
        assertEquals("replaced_without_idle", c.observe(woltOffer(2_000, restaurant = "Other Doner")).first().reason)
        assertEquals("replaced_without_idle", c.observe(woltOffer(3_000, km = "3.2", restaurant = "Other Doner")).first().reason)
        assertTrue(c.observe(woltOffer(4_000, km = "3.3", restaurant = "Other Doner")).isEmpty())
    }
    @Test fun similarTextRules() {
        assertTrue(OfferMatch.similar("Izmir Doner Tantuni No1", "Izmir Doner Tantuni Nol"))
        assertTrue(OfferMatch.similar("Test 2, Bakı", "Test 2,"))
        assertTrue(OfferMatch.similar(null, "anything"))
        assertFalse(OfferMatch.similar("Test 2, Bakı", "Test 5, Bakı"))
        assertFalse(OfferMatch.similar("Cook Box by Merkez Lahmacun Nizami", "Cook Box 2 by Merkez"))
        assertFalse(OfferMatch.similar("Izmir Doner", "Punkap"))
    }
    @Test fun ocrNeverOverwritesAnAccessibilityValue() {
        val c = DeliveryCore(); c.start(); c.observe(woltOffer(1_000, km = "4.2")); c.observe(woltOfferOcr(2_000, km = "4.3"))
        val p = c.pending[wolt]!!
        assertEquals(4.2, p.data.km!!, 1e-9); assertEquals("Izmir Doner Tantuni No1", p.data.restaurant); assertEquals(2_000L, p.lastSeenAt)
        // OCR first, accessibility later: the better read wins.
        val d = DeliveryCore(); d.start(); d.observe(woltOfferOcr(1_000, km = "4.3")); d.observe(woltOffer(2_000, km = "4.2"))
        assertEquals(4.2, d.pending[wolt]!!.data.km!!, 1e-9); assertEquals("Izmir Doner Tantuni No1", d.pending[wolt]!!.data.restaurant)
        assertEquals("accessibility", d.pending[wolt]!!.fieldSource["km"])
    }

    // ---------------------------------------------------------------- K18: decline window at the offline-for-declines screen (G19)
    private fun declineOnce(c: DeliveryCore, at: Long) {
        c.observe(woltOffer(at)); c.click(wolt, "Decline", at + 5_000); c.observe(woltWaiting(at + 6_000)); c.observe(woltWaiting(at + 7_000))
    }
    @Test fun offlineForDeclinesReportsTheDeclinesInTheWindow() {
        val c = DeliveryCore(); c.start(); c.observe(woltWaiting(0))
        declineOnce(c, 10_000); declineOnce(c, 40_000); declineOnce(c, 70_000)
        val e = c.observe(woltBan(100_000)).single { it.type == "penalty_offline" }
        assertEquals("declines_in_window=3", e.reason); assertEquals(ScreenStage.OFFLINE, woltBan(0).stage)
        assertEquals(DeclineWindow(100_000, 3, 3, 60 * 60_000L), c.penaltyCounts[wolt])
        assertTrue(c.observe(woltBan(101_000)).none { it.type == "penalty_offline" }) // once per offline period
        c.observe(woltWaiting(200_000))
        assertEquals(1, c.observe(woltBan(300_000)).count { it.type == "penalty_offline" })
    }
    @Test fun timeoutsAndDisappearancesCountButAreNotExplicit() {
        val c = DeliveryCore(); c.start()
        c.observe(boltOffer(0)); c.observe(boltWaiting(70_000)); c.observe(boltWaiting(71_000)) // timeout
        c.observe(boltOffer(100_000)); c.observe(boltWaiting(110_000)); c.observe(boltWaiting(111_000)) // gone early
        c.observe(boltOffer(200_000)); c.click(bolt, "Decline", 201_000); c.observe(boltWaiting(202_000)); c.observe(boltWaiting(203_000)) // tap
        c.observe(boltOffer(300_000)); c.stop(301_000) // recording stopped
        assertEquals(DeclineWindow(400_000, 3, 1, DeliveryCore.DECLINE_WINDOW_MS), c.recentDeclines(bolt, 400_000))
        assertEquals(0, c.recentDeclines(wolt, 400_000).countsAsDecline)
        // An hour later the old outcomes have left the window.
        assertEquals(0, c.recentDeclines(bolt, 300_000 + DeliveryCore.DECLINE_WINDOW_MS + 1).countsAsDecline)
    }
    @Test fun countsAsDeclineFollowsSpec() {
        for (r in listOf("courier_declined", "courier_declined_confirmed", "courier_declined_banner", "offer_to_idle_map"))
            assertTrue(r, DeliveryCore.countsAsDecline("rejected", r))
        assertTrue(DeliveryCore.countsAsDecline("offer_unconfirmed", "expired_inferred"))
        assertTrue(DeliveryCore.countsAsDecline("offer_unconfirmed", "offer_not_seen"))
        assertFalse(DeliveryCore.countsAsDecline("offer_unconfirmed", "replaced_without_idle"))
        assertFalse(DeliveryCore.countsAsDecline("offer_unconfirmed", "manual_shift_stop"))
        assertFalse(DeliveryCore.countsAsDecline("accepted", "active_screen"))
        assertFalse(DeliveryCore.countsAsDecline("rejected", "courier_declined", optional = true))
        assertTrue(DeliveryCore.explicitDecline("rejected", "courier_declined_banner")); assertFalse(DeliveryCore.explicitDecline("rejected", "offer_to_idle_map"))
    }
    @Test fun onlyTheBanMessageIsAPenalty() {
        assertTrue(DeliveryCore.offlineForDeclines(listOf("You've been set offline until 21:46 for declining too many deliveries.")))
        assertTrue(DeliveryCore.offlineForDeclines(listOf("You've been set offline until 21:46 for", "declining too many deliveries.")))
        assertFalse(DeliveryCore.offlineForDeclines(listOf("1 deliveries declined", "Quick reminder, declining too many can mean being set offline!", "Baku Central")))
        val c = DeliveryCore(); c.start()
        assertTrue(c.observe(woltOffline(1_000)).none { it.type == "penalty_offline" })
    }
    @Test fun restoredOutcomesCountOnce() {
        val c = DeliveryCore(); val o = OfferOutcome("offer-1", wolt, 1_000, "rejected", "courier_declined")
        c.restoreOutcome(o); c.restoreOutcome(o)
        assertEquals(1, c.recentDeclines(wolt, 2_000).explicit)
    }

    // ---------------------------------------------------------------- K9: two apps (G08)
    @Test fun distanceGoesToTheTripWhoseTaskScreenWasSeenLast() {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(0)); c.observe(boltPickup(3_000)); c.observe(woltOffer(30_000)); c.observe(woltPickup(35_000))
        c.addDistance(3_000.0)
        assertEquals(0.0, c.active(bolt)!!.distanceM, 1e-9); assertEquals(3_000.0, c.active(wolt)!!.distanceM, 1e-9)
        c.observe(boltPickup(40_000)); c.addDistance(500.0)
        assertEquals(500.0, c.active(bolt)!!.distanceM, 1e-9)
    }
}
