package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/** CAPTURE.md 3.4, 8 (no Rawstats test; characterisation of `WatchService.ocrLines` @ 6388c04). */
class OcrAcceptanceTest {
    private val bolt = "ee.mtakso.courier"
    private val pickup = listOf("Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99", "Orders to pick up:", "2", "#Q3FYM")
    private val drop = listOf("Drop off in -1 min", "Ziba M. #8P6D2", "Items (1)", "Address", "Mehdi Huseyn 40, Baku", "Arrived", "Slide to confirm")
    private val offer = listOf("Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼")
    private val idle = listOf("Waiting for orders", "Online")

    @Test fun staleAfter1800ms() {
        assertTrue(OcrAcceptance.fresh(true, true, true, true, shotAt = 0, now = 1_799, lines = idle))
        assertFalse(OcrAcceptance.fresh(true, true, true, true, shotAt = 0, now = 1_800, lines = idle))
        assertEquals(1_800L, CaptureTiming.OCR_STALE_MS)
    }

    @Test fun droppedWhenTheServiceTheShiftOrTheWindowChanged() {
        assertFalse(OcrAcceptance.fresh(false, true, true, true, 0, 10, idle))   // service gone
        assertFalse(OcrAcceptance.fresh(true, false, true, true, 0, 10, idle))   // no recording permission
        assertFalse(OcrAcceptance.fresh(true, true, false, true, 0, 10, idle))   // another shift
        assertFalse(OcrAcceptance.fresh(true, true, true, false, 0, 10, idle))   // window no longer on screen
        assertFalse(OcrAcceptance.fresh(true, true, true, true, 0, 10, emptyList()))
    }

    @Test fun aNativeOrderScreenWinsOverAQuietOcrFrame() {
        for (quiet in listOf(offer, idle, listOf("Go online", "You are offline")))
            assertNull(quiet.toString(), OcrAcceptance.accept(bolt, pickup, emptyList(), quiet, 1_000))
        assertNull(OcrAcceptance.accept(bolt, drop, listOf("Arrived"), idle, 1_000))
        // An OCR drop-off frame over a native pickup is the next step: kept.
        assertNotNull(OcrAcceptance.accept(bolt, pickup, emptyList(), drop, 1_000))
        // Without a native order screen (Wolt's map windows have no accessibility text) the OCR offer is kept.
        assertEquals(listOf("Decline", "22 min, 4,49 ₼"), OcrAcceptance.accept(bolt, emptyList(), emptyList(), offer, 1_000))
    }

    @Test fun arrivedIsAnActionOnlyNextToTheSlider() {
        assertEquals(listOf("Arrived"), OcrAcceptance.actions(listOf("Drop off in 3 min", "Arrived", "Slide to confirm")))
        assertEquals(emptyList<String>(), OcrAcceptance.actions(listOf("Drop off in 3 min", "Arrived")))
        assertEquals(listOf("Accept", "Decline"), OcrAcceptance.actions(listOf("Delivery from", "Accept", "Decline")))
    }
}
