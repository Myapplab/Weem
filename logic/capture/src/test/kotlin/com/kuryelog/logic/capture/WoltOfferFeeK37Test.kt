package com.kuryelog.logic.capture

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
    @Test fun theSyntheticFramesAreOfferCardsWithoutAFee() {
        val o = CourierParser.parse(wolt, e5NoFee, listOf("Decline", "Accept"), t0, "accessibility")
        assertEquals(ScreenStage.OFFER, o.stage); assertNull(o.gross)
        assertTrue(OfferCapture.reread(o, 0)); assertFalse(OfferCapture.reread(o, OfferCapture.MAX_REREADS))
        assertFalse(OfferCapture.reread(CourierParser.parse(wolt, e5, emptyList(), t0, "accessibility"), 0))
    }

    @Test fun ocrFallsBackToTheDisplayAfterAnInvalidWindow() {
        assertTrue(OfferCapture.useDisplay(33, null, 0))
        assertFalse(OfferCapture.useDisplay(34, null, 0))
        assertTrue(OfferCapture.useDisplay(34, 1000, 1000 + OfferCapture.DISPLAY_FALLBACK_MS - 1))
        assertFalse(OfferCapture.useDisplay(34, 1000, 1000 + OfferCapture.DISPLAY_FALLBACK_MS))
        assertFalse(OfferCapture.useDisplay(35, 5000, 4000))
    }
}
