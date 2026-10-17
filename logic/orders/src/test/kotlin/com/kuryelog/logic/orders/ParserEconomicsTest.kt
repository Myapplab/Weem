package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Offer-economics v2 parser fields (WP5). Screen texts are the exact strings of the research corpora:
 * golden_corpus.json parser rows P01–P18 / R01–R06 and the field corpus (sonuc-2026-10-06/saha) E1, E4, E5, E8 and
 * the OCR frame with KuryePlus's own badge. Rows marked "synthetic" are not from a real screen.
 */
class ParserEconomicsTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, 0L, source)
    private val tr = { t: String, _: String -> t }
    private val az = { _: String, a: String -> a }

    // Field corpus
    private val e1 = arrayOf("Baku Central", "Delivery demand", "Low", "NOW", "09:00", "15:00", "21:00", "03:00", "EXPECTED DEMAND", "EARN EXTRA",
        "Starts in 4 hours", "Boost", "Hər sifariş üçün əlavə 20%", "18:00-20:00", "Quick links", "Previous task", "Scheduled offline", "Support", "Go offline")
    private val e5 = arrayOf("Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran", "Test küç. 1, Baku, AZ1025",
        "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept")
    private val badge = arrayOf("● 30.1 km", "Wolt · 2.21₼ · Teklif", "Bugün: 7 sipariş")
    private val e8 = arrayOf("Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Test tarif", "Translate", "Collect order", "#014",
        "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders")

    @Test fun ocrOfferWithBadgeFeedsTheTripWithTheCardFee() {
        // Field scenario F06: E1, E1, OCR frame with the badge, accessibility E5, Accept tap, E8.
        val core = DeliveryCore(); core.start(); val t0 = 1791295200000L
        fun frame(at: Long, source: String, actions: List<String>, vararg t: String) = core.observe(CourierParser.parse(wolt, t.toList(), actions, t0 + at, source))
        frame(0, "accessibility", emptyList(), *e1); frame(1000, "accessibility", emptyList(), *e1)
        frame(10000, "ocr", emptyList(), *badge, *e5)
        assertEquals(listOf(3.94), core.pending.values.map { it.data.gross }); assertEquals(listOf(4.2), core.pending.values.map { it.data.km })
        frame(11000, "accessibility", listOf("Decline", "Accept"), *e5)
        core.click(wolt, "Accept", t0 + 14000)
        frame(15000, "accessibility", listOf("Collect", "Cancel this delivery"), *e8)
        assertEquals(listOf(3.94), core.trips.values.map { it.gross })
    }

    // ------------------------------------------------------------------ OverlayText: one formatter, one recogniser
    @Test fun panelFormatsMatchTheFieldBadge() {
        assertEquals("● 30.1 km", OverlayText.distance(30.1))
        assertEquals("Wolt · 2.21₼ · Teklif", OverlayText.offer("Wolt", 2.21, null, tl = tr))
        assertEquals("Bugün: 7 sipariş", OverlayText.today(7, tr))
        assertEquals("Bu gün: 7 sifariş", OverlayText.today(7, az))
        assertEquals("Wolt · 3.94₼ · net 3.15₼ · Test Restoran", OverlayText.offer("Wolt", 3.94, "Test Restoran", net = 3.15, tl = tr))
        assertEquals("Bolt Food · 0.00₼ · Təklif", OverlayText.offer("Bolt Food", null, null, tl = az))
        assertEquals("Wolt · Sipariş bekleniyor", OverlayText.status("Wolt", true, tr))
        assertEquals("Bolt Food · Oflayn", OverlayText.status("Bolt Food", false, az))
        assertEquals("Wolt · Test Restoran · Restorana gidiliyor", OverlayText.trip("Wolt", "Test Restoran", TripPhase.TO_RESTAURANT, tr))
        assertEquals("Wolt · Sifariş · Müştəriyə gedilir", OverlayText.trip("Wolt", null, TripPhase.TO_CUSTOMER, az))
        assertTrue(badge.all(OwnLines::isOwn))
    }

    @Test fun everyPanelLineIsRecognisedInBothLanguages() {
        val lines = ArrayList<String>()
        for (tl in listOf(tr, az)) for (platform in listOf("Wolt", "Bolt Food", "courierapp")) {
            for (p in TripPhase.values()) { lines += OverlayText.trip(platform, "Test Restoran", p, tl); lines += OverlayText.trip(platform, null, p, tl) }
            lines += OverlayText.status(platform, true, tl); lines += OverlayText.status(platform, false, tl)
            lines += OverlayText.offer(platform, 12.5, null, tl = tl); lines += OverlayText.offer(platform, 3.94, "Test Restoran", net = 3.15, tl = tl)
            lines += OverlayText.today(12, tl)
        }
        lines += OverlayText.distance(0.0); lines += OverlayText.distance(123.45)
        assertEquals(emptyList<String>(), lines.filterNot(OwnLines::isOwn))
        assertEquals(12, TripPhase.values().flatMap { listOf(OverlayText.phase(it, tr), OverlayText.phase(it, az)) }.distinct().size)
    }

}
