package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * WP9 integration: the offer badge's net line ("teklif gelince net kazanç"), the anonymous analysis export using the same
 * panel recogniser as the parser, the button under Ayarlar → Kayıtlar and the corrected accessibility description (N17).
 * Screen texts are the field corpus E5 frame (sonuc-2026-10-06/saha), as in ParserEconomicsTest.
 */
class OfferNetLineTest {
    private val wolt = "com.wolt.courierapp"
    private val tr = { t: String, _: String -> t }
    private val az = { _: String, a: String -> a }
    private val e5 = listOf("Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran", "Test küç. 1, Baku, AZ1025",
        "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept")

    @Test fun netIsGrossLessTheFleetCommission() {
        // U1: Wolt ₼3.94 with a 20 % fleet commission nets 3.152 (shown 3.15₼), as TripRecord.net computes it before a tip.
        assertEquals(3.152, OverlayText.net(3.94, 20.0)!!, 1e-9)
        assertEquals("Wolt · 3.94₼ · net 3.15₼ · Test Restoran", OverlayText.offer("Wolt", 3.94, "Test Restoran", net = OverlayText.net(3.94, 20.0), tl = tr))
        assertEquals("Bolt Food · 4.49₼ · net 3.82₼ · Təklif", OverlayText.offer("Bolt Food", 4.49, null, net = OverlayText.net(4.49, 15.0), tl = az))
        assertEquals(3.94, OverlayText.net(3.94, 0.0)!!, 0.0) // an explicit 0 % is a known commission
        val offer = OfferState("o", wolt, 0L, CourierObservation(wolt, 0L, ScreenStage.OFFER, true, "accessibility", emptyList(), emptyList(), gross = 3.94))
        assertEquals(TripRecord("t", wolt, offer, 0L, commissionPct = 20.0).net!!, OverlayText.net(3.94, 20.0)!!, 1e-12)
    }

    @Test fun noNetWithoutAFeeOrACommission() {
        assertNull(OverlayText.net(3.94, null))
        assertNull(OverlayText.net(null, 20.0))
        // Without a commission key the line stays as before WP9: no net part, no colour, no verdict.
        assertEquals("Wolt · 3.94₼ · Test Restoran", OverlayText.offer("Wolt", 3.94, "Test Restoran", net = OverlayText.net(3.94, null), tl = tr))
        assertEquals("Wolt · 0.00₼ · Teklif", OverlayText.offer("Wolt", null, null, net = OverlayText.net(null, 20.0), tl = tr))
    }

    @Test fun theNetLineNeverBecomesTheOfferFee() {
        val line = OverlayText.offer("Wolt", 3.94, "Test Restoran", net = OverlayText.net(3.94, 20.0), tl = tr)
        val ocr = "Wolt • 3,94 ₼ • net 3,15 ₼ • Test Restoran"
        for (badge in listOf(listOf("● 30.1 km", line, "Bugün: 7 sipariş"), listOf("● 30.1 km\n$ocr\nBugün: 7 sipariş"))) for (source in listOf("accessibility", "ocr")) {
            val o = CourierParser.parse(wolt, badge + e5, listOf("Decline", "Accept"), 0L, source)
            assertEquals("$source $badge", 3.94, o.gross!!, 1e-9)
            assertEquals("$source $badge", 4.2, o.km!!, 1e-9)
        }
    }

    // A4.3: Rawstats adı geri geldi (A3.3'te `theNetLineIsOneOfTheParsersOwnLines`, yalnız `OwnLines.isOwn` yarısı).
    @Test fun analysisExportDropsTheNetLineWithTheParsersRecogniser() {
        val lines = listOf(OverlayText.offer("Wolt", 3.94, "Test Restoran", net = 3.152, tl = tr), OverlayText.offer("Bolt Food", 4.49, null, net = 3.8165, tl = az),
            "Wolt • 3,94 ₼ • net 3,15 ₼ • Test Restoran", "Wolt · 3.94 AZN · net 3.15 AZN · Teklif")
        for (l in lines) { assertTrue(l, OwnLines.isOwn(l)); assertTrue(l, AnalysisExport.isPanelLine(l)) }
        for (l in e5) assertFalse(l, AnalysisExport.isPanelLine(l))
        val row = AnalysisExport(ByteArray(32) { it.toByte() }).screen(JSONObject().put("t", 1L).put("pkg", wolt).put("source", "ocr")
            .put("texts", JSONArray(lines + e5)).put("actions", JSONArray(listOf(lines[0], "Accept"))))
        val texts = row.getJSONArray("texts").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertTrue(texts.toString(), texts.none { "net" in it.lowercase() || "3.15" in it || "3,15" in it })
        assertTrue(texts.toString(), "₼3.94" in texts)
        assertFalse(row.toString(), row.toString().contains("net 3"))
    }

    // ------------------------------------------------------------------ app wiring (source checks; the UI itself is telefonda denenmedi)
    private fun main(): File = listOf("src/main", "app/src/main", "kuryelog/app/src/main").map(::File).firstOrNull { it.isDirectory }
        ?: error("KuryeLog kaynak klasörü bulunamadı: ${File(".").absolutePath}")

}
