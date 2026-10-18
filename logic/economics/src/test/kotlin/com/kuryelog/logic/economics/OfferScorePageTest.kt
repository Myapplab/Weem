package com.kuryelog.logic.economics

import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.PickupRecord
import com.kuryelog.logic.orders.TripRecord
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

/**
 * E4 sayfası (karakterizasyon; beklenen = Rawstats `MainActivity.scoreDayBody/pickRating` @ 6388c04'ün çıktısı): sıra teklif anına göre,
 * etiket "SS:dd · platform · restoran", özet ve en büyük farklar, teklifsiz sipariş puanlanamaz. Ekran telefonda denenmedi.
 */
class OfferScorePageTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private val baku = ZoneId.of("Asia/Baku")
    /** 2026-10-06 18:00 Baku. */
    private val t0 = 1791295200000L
    private val tr = { a: String, _: String -> a }
    private val az = { _: String, b: String -> b }

    private fun obs(pkg: String, at: Long, restaurant: String?) = CourierObservation(pkg, at, ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(),
        gross = 3.0, restaurant = restaurant)
    private fun trip(id: String, pkg: String, at: Long, restaurant: String?, offer: Boolean = true) =
        TripRecord(id, pkg, if (offer) OfferState("o-$id", pkg, at, obs(pkg, at, restaurant)) else null, at + 5_000,
            pickups = if (offer) mutableListOf() else mutableListOf(PickupRecord(restaurant, null)))
    private fun score(id: String, v: Int?, reason: String? = null) = JSONObject().put("schemaVersion", 1).put("kind", "offer_score").put("offer_id", id)
        .put("score", v ?: JSONObject.NULL).put("reason", reason ?: JSONObject.NULL)
    private fun mine(id: String, v: Int) = OfferScore.ratingRow(id, null, wolt, v, t0, OfferScore.VIA_DAY)

    @Test fun dayPageListsBothScoresInOfferOrder() {
        val late = trip("b", bolt, t0 + 3_600_000, "Cook Box")
        val early = trip("a", wolt, t0, "Test Döner")
        val none = trip("c", wolt, t0 + 1_800_000, "Manual", offer = false)
        val rows = mapOf("a" to (score("o-a", 8) to mine("o-a", 5)), "b" to (score("o-b", null, "FEE_UNREADABLE") to null))
        val p = OfferScorePage.page(listOf(late, none, early), { rows[it.id] ?: (null to null) }, baku, tr)
        assertEquals(listOf("a", "c", "b"), p.rows.map { it.tripId })
        assertEquals("18:00 · Wolt · Test Döner", p.rows[0].label)
        assertEquals("Uygulama 8 · Sen 5 (fark +3)", p.rows[0].text); assertTrue(p.rows[0].both); assertTrue(p.rows[0].rateable); assertEquals(5, p.rows[0].rating)
        assertEquals("18:30 · Wolt · Manual", p.rows[1].label)
        assertEquals("Teklif görülmedi, puan yok", p.rows[1].text); assertFalse(p.rows[1].rateable)
        assertEquals("19:00 · Bolt Food · Cook Box", p.rows[2].label)
        assertEquals("Uygulama — (ücret okunamadı) · Sen —", p.rows[2].text); assertFalse(p.rows[2].both)
        assertEquals("Ortalama fark (uygulama − sen): +3.0 · 1 sipariş", p.summary); assertTrue(p.summaryAccent)
        assertEquals("En büyük farklar", p.biggestTitle)
        assertEquals(listOf("18:00 · Wolt · Test Döner: Uygulama 8 · Sen 5 (fark +3)"), p.biggest)
        assertTrue(p.intro.contains("EconomicsV2 ${EconomicsV2.SPEC_VERSION}"))
    }

    @Test fun withoutBothScoresTheSummaryIsADash() {
        val a = trip("a", wolt, t0, null)
        val p = OfferScorePage.page(listOf(a), { score("o-a", 6) to null }, baku, az)
        assertEquals("Orta fərq: — (iki balı da olan sifariş yoxdur)", p.summary); assertFalse(p.summaryAccent)
        assertNull(p.biggestTitle); assertTrue(p.biggest.isEmpty())
        assertEquals("18:00 · Wolt · Sifariş", p.rows.single().label)
        assertEquals("Tətbiq 6 · Sən —", p.rows.single().text)
    }

    @Test fun cardLineAndRatingQuestion() {
        assertEquals("Teklif puanı: Uygulama — (puan kaydı yok) · Sen 7", OfferScorePage.cardLine(null, mine("x", 7), tr))
        assertEquals(listOf("1 · çok kötü", "2", "3", "4", "5", "6", "7", "8", "9", "10 · çok iyi"), OfferScorePage.ratingItems(tr))
        assertEquals("Bu təklifə balın (1–10)", OfferScorePage.ratingTitle(az))
        assertEquals(listOf("Siparişler", "Teklif puanı"), OfferScorePage.tabs(tr))
    }
}
