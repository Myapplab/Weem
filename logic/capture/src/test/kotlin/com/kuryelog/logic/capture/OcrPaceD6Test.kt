package com.kuryelog.logic.capture

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * D6 (13 Ekim planı): okuma hızı. Sahada (11–12 Ekim) Wolt kartının bütün kareleri OCR'dı ve 1,5 sn arayla geldi; Wolt kartı
 * önce tutarsız gösterip tutarı 1–12 sn sonra yüklüyor. [OcrPace]: bildirimden / yeni pencere durumundan sonra ve tutarsız kart
 * ekrandayken pencere Android'in izin verdiği en kısa aralıkla (350 ms) okunur, aynı görüntü yeniden OCR'lanmaz.
 * Benzetim: tutar ekrana geldikten kayda girene kadar geçen süre, eski ve yeni temposuyla (OCR süresi varsayım, telefonda
 * ölçülecek: teklif kaydında fee_at / fee_gap_ms / fee_ocr_ms / notified_at).
 */
class OcrPaceD6Test {
    private val wolt = "com.wolt.courierapp"
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun loading(at: Long) = obs(at, "Expected earnings for the full delivery", "Delivery from", "Borani Sahil", "Accept", "Uzeyir Hajibayli str. 17, AZ1000 Baku")
    private fun card(at: Long) = obs(at, "AZN 2.61", "Expected earnings for the full delivery", "Delivery from", "Borani Sahil", "Route distance", "Estimated",
        "9 - 17 min", "2 km", "Accept", "Uzeyir Hajibayli str. 17, AZ1000 Baku")
    private fun waiting(at: Long) = obs(at, "Quieter than usual", "Delivery demand", "l Low", "EXPECTED DEMAND", "Go offline")

    @Test fun framesTheRulesUse() {
        assertEquals(ScreenStage.OFFER, loading(0).stage); assertNull(loading(0).gross)
        assertEquals(ScreenStage.OFFER, card(0).stage); assertEquals(2.61, card(0).gross!!, 1e-9)
        assertEquals(ScreenStage.IDLE, waiting(0).stage)
    }

    @Test fun aQuietWindowKeepsItsGap() {
        val p = OcrPace()
        assertTrue(p.due(1, wolt, 0)); p.started(1, wolt, 0)
        assertFalse(p.due(1, wolt, 1_499)); assertTrue(p.due(1, wolt, 1_500))
        p.read(1, waiting(0)); assertFalse(p.hot(1, wolt, 100)); assertNull(p.hotDelay(100))
    }

    @Test fun aNotificationMakesItsAppHotForTenSeconds() {
        val p = OcrPace()
        p.started(1, wolt, 0); p.started(2, "com.bolt.food.courier", 400)
        p.trigger(wolt, 500)
        assertTrue(p.hot(1, wolt, 500)); assertFalse(p.hot(2, "com.bolt.food.courier", 500))
        // Another screenshot only 350 ms after the last one (Android refuses one within 333 ms), then the hot window first.
        assertFalse(p.due(1, wolt, 749)); assertTrue(p.due(1, wolt, 750))
        assertEquals(1, p.next(listOf(2, 1), { it }, { if (it == 1) wolt else "com.bolt.food.courier" }, 2_000))
        assertEquals(350L - 100L, p.hotDelay(500))
        assertFalse(p.hot(1, wolt, 500 + OfferCapture.HOT_AFTER_TRIGGER_MS + 1))
    }

    @Test fun aCardWithoutItsFeeStaysHotUntilTheFeeOrTwentySeconds() {
        val p = OcrPace()
        p.started(1, wolt, 0); p.read(1, loading(0))
        assertTrue(p.hot(1, wolt, 400)); assertTrue(p.due(1, wolt, 400)); assertEquals(350L, p.hotDelay(0))
        p.started(1, wolt, 400); p.read(1, loading(400))
        assertTrue(p.hot(1, wolt, OfferCapture.HOT_OFFER_MS)); assertFalse(p.hot(1, wolt, OfferCapture.HOT_OFFER_MS + 1))
        p.read(1, card(800)); assertFalse(p.hot(1, wolt, 900)); assertFalse(p.due(1, wolt, 900))
    }

    @Test fun aFailedScreenshotWaitsTheNormalGap() {
        val p = OcrPace()
        p.trigger(wolt, 0); p.started(1, wolt, 0); p.failed(1, 0)
        assertFalse(p.due(1, wolt, 400)); assertTrue(p.due(1, wolt, 1_500))
        p.started(1, wolt, 1_500); assertTrue(p.due(1, wolt, 1_850))
    }

    @Test fun anIdenticalScreenshotReusesItsLinesAndEveryPixelCounts() {
        val p = OcrPace()
        val a = IntArray(960 * 4) { it * 31 }; val b = a.copyOf(); b[b.lastIndex - 7] += 1
        val ha = OcrPace.fnv(OcrPace.FNV_OFFSET, a, a.size); val hb = OcrPace.fnv(OcrPace.FNV_OFFSET, b, b.size)
        assertNotEquals(ha, hb); assertEquals(ha, OcrPace.fnv(OcrPace.FNV_OFFSET, a.copyOf(), a.size))
        assertNull(p.same(1, ha)); p.remember(1, ha, listOf("AZN 2.61"))
        assertEquals(listOf("AZN 2.61"), p.same(1, ha)); assertNull(p.same(1, hb)); assertNull(p.same(2, ha))
        p.retain(setOf(2)); assertNull(p.same(1, ha))
    }

    @Test fun ocrInputIsAtMost960Wide() {
        assertEquals(0.75f, OfferCapture.ocrScale(1280), 1e-6f)
        assertEquals(1f, OfferCapture.ocrScale(960), 0f); assertEquals(1f, OfferCapture.ocrScale(720), 0f)
    }

    /** Saha: Wolt cards whose first OCR frame had no fee are the ones the hot pace reads faster. */
    @Test fun sahaCardsWithoutAFeeFirstAreHot() {
        val frames = SahaFrames.frames.filter { it.wolt }.map(SahaFrames::parse)
        val cards = ArrayList<MutableList<CourierObservation>>()
        for (o in frames) if (o.stage == ScreenStage.OFFER) {
            val last = cards.lastOrNull()?.last()
            if (last != null && o.at - last.at <= 20_000) cards.last().add(o) else cards.add(mutableListOf(o))
        }
        var hot = 0; val feeAfter = ArrayList<Long>()
        for (card in cards) {
            val p = OcrPace(); p.started(1, wolt, card[0].at); p.read(1, card[0])
            if (p.hot(1, wolt, card[0].at + 350)) hot++
            val fee = card.firstOrNull { it.gross != null } ?: continue
            p.read(1, fee); assertFalse(p.hot(1, wolt, fee.at + 1))
            feeAfter.add(fee.at - card[0].at)
        }
        feeAfter.sort()
        println("D6 saha: ${cards.size} Wolt kartı, ilk karede tutarsız ${hot}; tutar ilk kareden sonra medyan ${feeAfter[feeAfter.size / 2]} ms, en çok ${feeAfter.last()} ms")
        assertTrue(cards.size >= 25); assertTrue(hot >= 20); assertTrue(feeAfter.last() <= OfferCapture.HOT_OFFER_MS)
    }

    /**
     * One Wolt window: the card on screen from 0 ms, its fee from [feeAt]; the window last read at [lastShot] (before the card);
     * Wolt's notification at −200 ms when [notified]. Screen change events are frequent (the map), so a read starts as soon as
     * it is due. OCR takes [ocrMs]; with [identical], screenshots of an unchanged screen are pixel-identical (map at rest) and
     * the new pace reuses their lines (5 ms). Returns the delay from the fee on screen to the fee in the record.
     */
    private fun latency(newPace: Boolean, feeAt: Long, ocrMs: Long, lastShot: Long, notified: Boolean, identical: Boolean): Long {
        val p = OcrPace(); p.started(1, wolt, lastShot)
        var oldLast = lastShot
        fun state(t: Long) = if (t < 0) 0 else if (t < feeAt) 1 else 2
        var busyUntil: Long? = null; var shotAt = 0L; var shotState = -1; var lastState = 0
        var t = -1_000L
        while (t < 60_000) {
            busyUntil?.let { done -> if (t >= done) {
                busyUntil = null
                if (shotState == 2) return t - feeAt
                p.read(1, if (shotState == 0) waiting(shotAt) else loading(shotAt))
            } }
            if (newPace && notified && t == -200L) p.trigger(wolt, t)
            if (busyUntil == null && (if (newPace) p.next(listOf(1), { it }, { wolt }, t) != null else t - oldLast >= OfferCapture.OCR_GAP_MS)) {
                val s = state(t)
                busyUntil = t + if (newPace && identical && s == lastState) 5 else ocrMs
                shotAt = t; shotState = s; lastState = s; oldLast = t; p.started(1, wolt, t)
            }
            t += 5
        }
        error("fee never read")
    }

    @Test fun simulatedFeeDelayOldAndNewPace() {
        val fees = (300L..12_300L step 500L).toList(); val lasts = listOf(-1_500L, -1_100L, -700L, -300L, -50L)
        println("D6 benzetim: tutar ekrana geldikten kayda (ms) — ortalama / en çok")
        for (ocr in listOf(300L, 400L, 600L)) for (notified in listOf(true, false)) for (identical in listOf(false, true)) {
            val old = fees.flatMap { f -> lasts.map { l -> latency(false, f, ocr, l, notified, identical) } }
            val new = fees.flatMap { f -> lasts.map { l -> latency(true, f, ocr, l, notified, identical) } }
            println("  OCR $ocr ms · bildirim ${if (notified) "var" else "yok"} · ${if (identical) "harita duruyor" else "harita oynuyor"}: " +
                "eski ${old.average().toInt()} / ${old.max()} · yeni ${new.average().toInt()} / ${new.max()}")
            assertTrue(new.average() < old.average()); assertTrue(new.max() <= old.max())
            assertTrue(old.max() >= OfferCapture.OCR_GAP_MS)
            // With Wolt's notification the fee is in the record within 1 s of showing for OCR up to 400 ms (the worst case is a
            // fee that shows during a read: that read, then one more).
            if (notified && ocr <= 400) assertTrue("ocr $ocr identical $identical: ${new.max()}", new.max() <= 1_000)
        }
    }
}
