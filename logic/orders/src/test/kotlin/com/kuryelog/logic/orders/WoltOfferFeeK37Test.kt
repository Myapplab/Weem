package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K37 (8 Oct: two Wolt offers recorded as "Fiyat okunamadı"): the fee of an offer card must survive frames that lack it,
 * including an OCR result that comes back after the next accessibility reads. Card strings are field corpus E5/E8
 * (sonuc-2026-10-06/saha, as in ParserEconomicsTest); the frames "without the fee line" are synthetic (E5 minus "₼3.94").
 */
class WoltOfferFeeK37Test {
    private val wolt = "com.wolt.courierapp"; private val t0 = 1791295200000L
    private val e5 = listOf("Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran", "Test küç. 1, Baku, AZ1025",
        "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept")
    private val e5NoFee = e5.filterNot { it == "₼3.94" }
    private val badge = listOf("● 30.1 km", "Wolt · 2.21₼ · Teklif", "Bugün: 7 sipariş")
    private val e8 = listOf("Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Test tarif", "Translate", "Collect order", "#014",
        "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders")
    private val core = DeliveryCore().also { it.start() }
    private fun frame(at: Long, source: String, texts: List<String>, actions: List<String> = emptyList()) =
        core.observe(CourierParser.parse(wolt, texts, actions, t0 + at, source))

    @Test fun aFeeSeenOnlyOnTheFirstFrameReachesTheTrip() {
        frame(0, "accessibility", e5, listOf("Decline", "Accept"))
        for (at in listOf(180L, 500L, 1000L, 1500L)) frame(at, "accessibility", e5NoFee, listOf("Decline", "Accept"))
        assertEquals(listOf(3.94), core.pending.values.map { it.data.gross })
        core.click(wolt, "Accept", t0 + 1700)
        frame(2000, "accessibility", e8, listOf("Collect", "Cancel this delivery"))
        assertEquals(listOf(3.94), core.trips.values.map { it.gross })
    }

    @Test fun aLateOcrResultFillsTheFeeOfThePendingOffer() {
        // OCR of the first frame (t=0) returns after the accessibility reads at 180 ms and 500 ms, which lack the fee.
        frame(180, "accessibility", e5NoFee, listOf("Decline", "Accept"))
        frame(500, "accessibility", e5NoFee, listOf("Decline", "Accept"))
        assertTrue(frame(0, "ocr", badge + e5).isEmpty())
        val p = core.pending.getValue(wolt)
        assertEquals(3.94, p.data.gross!!, 1e-9); assertEquals("ocr", p.fieldSource["gross"])
        assertEquals(t0 + 180, p.firstAt)
        core.click(wolt, "Accept", t0 + 900)
        frame(1200, "accessibility", e8, listOf("Collect", "Cancel this delivery"))
        assertEquals(listOf(3.94), core.trips.values.map { it.gross })
    }

    @Test fun aLateOcrResultFillsTheFeeOfTheTripAcceptedAfterIt() {
        frame(0, "accessibility", e5NoFee, listOf("Decline", "Accept"))
        core.click(wolt, "Accept", t0 + 300)
        frame(400, "accessibility", e8, listOf("Collect", "Cancel this delivery"))
        assertEquals(listOf<Double?>(null), core.trips.values.map { it.gross })
        assertTrue(frame(100, "ocr", badge + e5).isEmpty())
        assertEquals(listOf(3.94), core.trips.values.map { it.gross })
        assertTrue(core.pending.isEmpty())
    }

    @Test fun aLateFrameNeverOverwritesNorResurrects() {
        // F07: an accessibility value stays; a late OCR jitter (4.3 km, within the same-card tolerance) fills nothing.
        frame(500, "accessibility", e5, listOf("Decline", "Accept"))
        frame(0, "ocr", e5.map { if (it.startsWith("2 stops")) "2 stops (4.3 km) • 11–19 min" else it })
        assertEquals(4.2, core.pending.getValue(wolt).data.km!!, 1e-9); assertEquals("accessibility", core.pending.getValue(wolt).fieldSource["km"] ?: "accessibility")
        // Another card's late frame does not touch this offer.
        val other = core.pending.getValue(wolt).id
        frame(400, "ocr", e5NoFee.map { if (it == "Test Restoran") "Başqa Kafe" else it } + "₼5.10")
        assertEquals(3.94, core.pending.getValue(wolt).data.gross!!, 1e-9); assertEquals(other, core.pending.getValue(wolt).id)
        // After acceptance, a late offer frame taken after the acceptance opens nothing.
        core.click(wolt, "Accept", t0 + 900)
        frame(1200, "accessibility", e8, listOf("Collect", "Cancel this delivery"))
        assertTrue(frame(1000, "ocr", e5).isEmpty()); assertTrue(core.pending.isEmpty()); assertEquals(1, core.trips.size)
    }

}
