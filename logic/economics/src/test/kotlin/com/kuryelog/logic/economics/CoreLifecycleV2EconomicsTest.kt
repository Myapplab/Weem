package com.kuryelog.logic.economics

import com.kuryelog.logic.capture.*
import org.junit.Assert.*
import org.junit.Test

// Rawstats CoreLifecycleV2Test.kt @ 6388c04: ekonomi payı (8 test; bekleme gözlemi `WaitObservation`), aynı girdi → aynı çıktı.
// Öbür 15 testi `logic/orders` `CoreLifecycleV2Test`'te (ORDERS.md 15.1). Kare kurucular aynen.

/**
 * Offer economics v2, WP6: offer lifecycle, decline window and wait spells (SPEC 2.1.0 §13, §17.2; findings K5, K7, K9,
 * K10, K17, K18, VM5, N11). Screen texts are the research golden corpus labels (synthetic customer data).
 */
class CoreLifecycleV2EconomicsTest {
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


    @Test fun waitWhileTheOtherAppIsBusyIsFlagged() {
        val w = WaitObservation()
        w.observe(boltPickup(3_000)); w.observe(woltWaiting(4_000)); w.observe(woltWaiting(14_000))
        val e = w.observe(woltOffer(30_000)).single()
        assertTrue(e.eventObserved); assertEquals("online_busy", e.otherAppState)
        w.observe(boltWaiting(40_000)); w.observe(woltWaiting(41_000))
        assertEquals("online_free", w.stop().single { it.pkg == wolt }.otherAppState)
        assertEquals("unknown", WaitObservation().apply { observe(woltWaiting(1_000)) }.stop().single().otherAppState)
    }

    @Test fun bannerPopupHourAndDemandStayInsideOneSpell() {
        val w = WaitObservation(); val out = ArrayList<WaitObservation.Episode>()
        out += w.observe(woltWaiting(0), "electric_scooter", "unknown", 18)
        out += w.observe(woltHot(10_000), "electric_scooter", "unknown", 18)
        out += w.observe(woltPopup(15_000), "electric_scooter", "unknown", 18)
        out += w.observe(woltWaiting(20_000), "electric_scooter", "unknown", 19)
        out += w.observe(woltWaiting(30_000, "High"), "electric_scooter", "unknown", 19)
        out += w.observe(woltOffer(50_000), "electric_scooter", "unknown", 19)
        val e = out.single()
        assertTrue(e.eventObserved); assertEquals("visible_offer", e.endReason); assertEquals(0L, e.startAt); assertEquals(50_000L, e.endAt)
        assertEquals("Medium", e.demand); assertEquals(18, e.bakuHour)
        assertEquals(listOf(WaitObservation.Segment(0, 20_000, "Medium", 18, "unknown"), WaitObservation.Segment(20_000, 30_000, "Medium", 19, "unknown"),
            WaitObservation.Segment(30_000, 50_000, "High", 19, "unknown")), e.segments)
        assertEquals(e.spellId, WaitObservation.spellIdFor(wolt, 0))
    }

    @Test fun aLongUnreadableStretchStillEndsTheSpell() {
        val w = WaitObservation(); w.observe(woltWaiting(0)); w.observe(woltWaiting(5_000))
        assertTrue(w.observe(woltPopup(6_000)).isEmpty()); assertTrue(w.observe(woltPopup(20_000)).isEmpty())
        val e = w.observe(woltPopup(21_001)).single()
        assertEquals("window_not_idle", e.endReason); assertEquals(5_000L, e.endAt); assertFalse(e.eventObserved)
        assertTrue(w.observe(woltOffer(25_000)).isEmpty())
    }

    @Test fun ocrOfferAtTheSameInstantAsAnUnreadableFrameIsTheEvent() {
        val w = WaitObservation(); w.observe(boltWaiting(0)); w.observe(boltWaiting(10_000)); w.observe(boltWaiting(20_000))
        assertTrue(w.observe(obs(bolt, 30_000, "Baku", "Mapbox", "Show map")).isEmpty())
        val e = w.observe(boltOffer(30_000).copy(source = "ocr")).single()
        assertTrue(e.eventObserved); assertEquals(30_000L, e.endAt - e.startAt)
    }

    @Test fun offlineScreenEndsTheWaitAsWentOffline() {
        val w = WaitObservation(); w.observe(woltWaiting(0)); w.observe(woltWaiting(10_000))
        val e = w.observe(woltOffline(20_000)).single()
        assertEquals("went_offline", e.endReason); assertFalse(e.eventObserved); assertEquals(10_000L, e.endAt)
    }

    @Test fun vehicleChangeStillSplits() {
        val w = WaitObservation(); w.observe(woltWaiting(0), "car", "center", 9)
        assertEquals("context_changed", w.observe(woltWaiting(1_000), "electric_scooter", "center", 9).single().endReason)
    }

    @Test fun midnightSpellIsFiledUnderItsStartDay() {
        val t0 = 1_791_316_740_000L // 2026-10-06 23:59:00 Baku
        val w = WaitObservation(); val out = ArrayList<WaitObservation.Episode>()
        for (k in 0..6) out += w.observe(woltWaiting(t0 + k * 10_000), "electric_scooter", "unknown", if (k < 6) 23 else 0)
        out += w.observe(woltOffer(t0 + 65_000), "electric_scooter", "unknown", 0)
        val e = out.single()
        assertEquals("2026-10-06", e.fileDay); assertEquals(65_000L, e.endAt - e.startAt); assertEquals(listOf(23, 0), e.segments.map { it.bakuHour })
        assertEquals("2026-10-07", WaitObservation.bakuDay(t0 + 65_000))
    }

    @Test fun segmentsSurviveProcessDeath() {
        val w = WaitObservation(); w.observe(woltWaiting(1_000), "electric_scooter", "z", 18); w.observe(woltWaiting(11_000, "High"), "electric_scooter", "z", 18)
        val back = WaitObservation.decode(WaitObservation.encode(w.snapshot())).single()
        val e = WaitObservation().restoreCensored(listOf(back)).single()
        assertEquals("process_restart", e.endReason); assertEquals(11_000L, e.endAt); assertEquals("Medium", e.demand)
        assertEquals(listOf(WaitObservation.Segment(1_000, 11_000, "Medium", 18, "unknown"), WaitObservation.Segment(11_000, 11_000, "High", 18, "unknown")), e.segments)
        assertEquals(WaitObservation.spellIdFor(wolt, 1_000), e.spellId)
        // Rows written before segments existed still restore as one segment.
        val old = WaitObservation.decode("""[{"pkg":"wolt","start":1000,"last":9000,"cause":"idle_observed","demand":"LOW","vehicle":"car","zone":"z","hour":8}]""").single()
        assertEquals(listOf(WaitObservation.Segment(1_000, 9_000, "LOW", 8, "unknown")), WaitObservation().restoreCensored(listOf(old)).single().segments)
    }
}
