package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
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

    @Test fun offerRecordsWhenItsFeeWasRead() {
        val c = DeliveryCore().also { it.start() }
        c.observe(waiting(0)); c.observe(waiting(1_000))
        c.observe(loading(10_000)); c.observe(loading(11_500)); c.observe(card(13_000)); c.observe(card(14_500))
        val o = c.pending[wolt]!!
        assertEquals(10_000L, o.firstAt); assertEquals(13_000L, o.feeAt); assertEquals(1_500L, o.feeGapMs)
        o.feeOcrMs = 420; o.notifiedAt = 9_600
        val back = RecordJson.offer(JSONObject(RecordJson.offer(o).toString()))
        assertEquals(13_000L, back.feeAt); assertEquals(1_500L, back.feeGapMs); assertEquals(420L, back.feeOcrMs); assertEquals(9_600L, back.notifiedAt)
        // A card read with its fee at once; an older record without the fields reads back as not observed.
        val d = DeliveryCore().also { it.start() }
        d.observe(card(20_000))
        assertEquals(20_000L, d.pending[wolt]!!.feeAt); assertNull(d.pending[wolt]!!.feeGapMs)
        val old = JSONObject(RecordJson.offer(o).toString()).apply { remove("fee_at"); remove("fee_gap_ms"); remove("fee_ocr_ms"); remove("notified_at") }
        assertNull(RecordJson.offer(old).feeAt); assertNull(RecordJson.offer(old).notifiedAt)
        // The order card's line, and none for an older record.
        assertEquals("Okuma: bildirim → kart 0.4 sn · kart → tutar 3.0 sn · OCR 0.4 sn", OrderHistory.readLine(o) { tr, _ -> tr })
        assertNull(OrderHistory.readLine(RecordJson.offer(old)) { tr, _ -> tr })
    }

    @Test fun lateFrameWithTheFeeFillsItsTime() {
        val c = DeliveryCore().also { it.start() }
        c.observe(loading(10_000)); c.observe(loading(11_500))
        // An OCR result of 10.8 s that came back after the 11.5 s frame: the fee was on screen then.
        c.observe(card(10_800))
        assertEquals(10_800L, c.pending[wolt]!!.feeAt); assertNull(c.pending[wolt]!!.feeGapMs)
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

}
