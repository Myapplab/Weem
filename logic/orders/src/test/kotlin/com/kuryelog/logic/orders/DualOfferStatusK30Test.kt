package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K30: two offers at once, one accepted and the other declined. The accepted platform shows its trip phase; the declined
 * one shows "Sipariş bekleniyor" while online there and "Çevrimdışı" once the courier went offline after the decline.
 */
class DualOfferStatusK30Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private val tr = { t: String, _: String -> t }
    private fun platform(p: String) = if (p.contains("wolt")) "Wolt" else "Bolt Food"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "Looking for orders", "Go offline")
    private fun boltOffline(at: Long) = obs(bolt, at, "You're offline", "Go online")
    private fun woltOffer(at: Long) = obs(wolt, at, "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1",
        "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
    private fun woltWaiting(at: Long) = obs(wolt, at, "Expect a task soon", "Delivery demand", "Medium")
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup 3 min", "Pickup from", "Donerist-Kebabist Sahil", "Sabail district, Uzeyir Hajibeyov str., 27, AZ1000 Baku",
        actions = listOf("Collect"))
    private fun woltCard(at: Long) = obs(wolt, at, "Dropoff to", "Test A.", "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details",
        "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))

    private class Run { val core = DeliveryCore().apply { start() }; val sessions = OnlineSessions()
        fun feed(o: CourierObservation) { sessions.observe(o); core.observe(o) } }
    private fun badge(r: Run) = OverlayText.badge(r.core, r.sessions, 0, false, { null }, ::platform, tr).trim().lines().dropLast(1)

    /** Both online, both offers on screen; Wolt accepted, Bolt declined. */
    private fun twoOffers(): Run {
        val r = Run()
        r.feed(woltWaiting(0)); r.feed(boltWaiting(1_000))
        r.feed(woltOffer(10_000)); r.feed(boltOffer(11_000))
        r.core.click(wolt, "Accept", 15_000); r.feed(woltPickup(17_000))
        r.core.click(bolt, "Decline", 18_000); r.feed(boltWaiting(20_000)); r.feed(boltWaiting(21_000))
        return r
    }

    @Test fun declinedAndStillOnlineIsWaitingWhileTheOtherGoesToTheRestaurant() {
        val r = twoOffers()
        assertEquals(listOf("Wolt · Restorana gidiliyor", "Bolt Food · Sipariş bekleniyor"), badge(r))
    }

    @Test fun declinedThenOfflineIsOfflineWhileTheOtherIsAtTheCustomerStage() {
        val r = twoOffers()
        r.feed(boltOffline(30_000)); r.feed(boltOffline(31_000))
        r.feed(woltCard(60_000))
        assertEquals(listOf("Wolt · Müşteriye gidiliyor", "Bolt Food · Çevrimdışı"), badge(r))
    }

    @Test fun onePlatformLineEvenWithTwoPackages() {
        val r = twoOffers()
        r.feed(CourierParser.parse("ee.mtakso.courier", listOf("You're offline", "Go online"), emptyList(), 40_000))
        assertEquals(1, badge(r).count { it.startsWith("Bolt Food") })
        assertTrue(badge(r).contains("Bolt Food · Sipariş bekleniyor"))
    }
}
