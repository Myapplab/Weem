package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * SPEC 17.1 feed (WP7), WEEM: the feed v2 offers are `OrderEvents.offers` (`logic/core` `OfferSummary`, produced by
 * `OfferSummaries`; ORDERS.md 13.2, 15.2). Kept: K21 (no offer unseen for > 90 s or from before a restart), at most 8 offers
 * and only known platforms, the v2 field mapping. Not translated: v1 byte identity and the E8 change key (no feed, no
 * change announcement; S7). Not carried by `OfferSummary`: `feeBasis`, `producer`, `courierState`, `duringTripId` (the last
 * one is `:economics`' to rebuild, A4.1).
 */
class OfferFeedV2Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private val t0 = 1791295200000L
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String>) = CourierParser.parse(pkg, text.toList(), actions, at)
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun woltOffer(at: Long) = obs(wolt, at, "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1",
        "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
    private fun pending(): List<OfferState> {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(t0)); c.observe(woltOffer(t0 + 1_000)); c.observe(boltOffer(t0 + 3_000))
        return c.pending.values.toList()
    }

    @Test fun ghostOffersAreNotExported() {
        val offers = pending() // last seen t0 + 3 s (Bolt), t0 + 1 s (Wolt)
        assertEquals(2, OfferSummaries.of(true, offers, t0 + 91_000).size)
        assertEquals(1, OfferSummaries.of(true, offers, t0 + 92_000).size)
        // G23: read 5 min later
        assertEquals(0, OfferSummaries.of(true, offers, t0 + 300_000).size)
    }

    @Test fun offersFromBeforeARestartWaitUntilSeenAgain() {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(t0))
        val restartedAt = t0 + 1
        assertEquals(0, OfferSummaries.of(true, c.pending.values, t0 + 2_000, restartedAt).size)
        c.observe(boltOffer(t0 + 2_500))
        assertEquals(1, OfferSummaries.of(true, c.pending.values, t0 + 3_000, restartedAt).size)
    }

    @Test fun summaryCarriesTheV1AndV2Fields() {
        val offers = pending()
        val rows = OfferSummaries.of(true, offers, t0 + 4_000)
        assertEquals(offers.size, rows.size)
        for (o in offers) {
            val r = rows.single { it.offerKey == o.id }
            assertEquals(o.firstAt, r.firstSeenAtMs); assertEquals(maxOf(o.lastSeenAt, o.firstAt), r.lastSeenAtMs)
            assertEquals(o.data.gross, r.displayedFeeAzn); assertEquals(o.data.km, r.platformDistanceKm); assertEquals(o.data.restaurant, r.restaurant)
            assertEquals(o.data.customerAddress, r.dropoffAddress)
            assertTrue(r.visibleNow)   // last seen 1 s (Bolt) and 3 s (Wolt) ago: within 5 s
        }
        val byPlatform = rows.associateBy { it.platform }
        val bolt = byPlatform.getValue(CourierPlatform.BOLT); val wolt = byPlatform.getValue(CourierPlatform.WOLT)
        // G02: Bolt shows no drop-off; the 8/14 split is carried. G26: Wolt "2 stops".
        assertFalse(bolt.dropoffKnown); assertEquals(8.0, bolt.shown.approachMin!!, 1e-9)
        assertEquals(2, wolt.shown.stops); assertTrue(wolt.dropoffKnown)
        // Scoring and the zone come from `:economics` (OfferAnnotator); without it they are unknown, never invented.
        for (r in rows) { assertNull(r.score); assertNull(r.netAzn); assertEquals(OfferSummary.Zone.UNKNOWN, r.pickupZone) }
        assertTrue(rows.sumOf { it.toJson().toString().length } <= 100_000)
        // A later frame makes the offer no longer visible now.
        assertFalse(OfferSummaries.of(true, offers, t0 + 20_000).any { it.visibleNow })
    }

    @Test fun atMostEightOffersAndOnlyKnownPlatforms() {
        val many = (0 until 10).map { i -> OfferState("o$i", "com.wolt.courierapp$i", t0, woltOffer(t0).copy(pkg = "com.wolt.courierapp$i")) } +
            OfferState("x", "com.example.courier", t0, boltOffer(t0).copy(pkg = "com.example.courier"))
        assertEquals(OfferSummaries.MAX_OFFERS, OfferSummaries.of(true, many, t0 + 1_000).size)
        assertEquals(0, OfferSummaries.of(true, listOf(many.last()), t0 + 1_000).size)
        assertEquals(0, OfferSummaries.of(false, many, t0 + 1_000).size)
    }
}
