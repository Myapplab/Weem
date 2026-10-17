package com.kuryelog.logic.capture

import com.kuryelog.logic.core.LogStore
import java.io.File
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** CAPTURE.md 6.1, 6.3, 8 (`ScreenRowTest`): fields as Rawstats `WatchService.accept` wrote them; phones redacted, null fields absent. */
class CaptureRowsTest {
    private val log = LogStore(File("build/tmp/capture-rows"), zone = { ZoneId.of("Asia/Baku") })
    private val wolt = "com.wolt.courierapp"

    @Test fun accessibilityScreenRow() {
        val node = CaptureRows.Node("Call +994 50 123 45 67", "", "id/call", "android.widget.Button", "0 0 10 10", true)
        val j = CaptureRows.screen(log, 1_000, wolt, 7, "0 0 1280 2856", "accessibility", ScreenStage.DROPOFF,
            listOf("Dropoff to", "Leyla 0501234567"), listOf("Call +994 50 123 45 67"), listOf(node), 81, spinner = true)
        assertEquals(log.ts(1_000), j.getString("t"))
        assertEquals("DROPOFF", j.getString("stage")); assertFalse(j.getBoolean("offerLike"))
        assertEquals("Leyla [tel]", j.getJSONArray("texts").getString(1))
        assertEquals("Call [tel]", j.getJSONArray("actions").getString(0))
        val n = j.getJSONArray("nodes").getJSONArray(0)
        assertEquals("Call [tel]", n.getString(0)); assertEquals("id/call", n.getString(2)); assertTrue(n.getBoolean(5))
        assertEquals(81, j.getInt("phone_battery_pct")); assertTrue(j.getBoolean("deliver_spinner")); assertEquals(7, j.getInt("window"))
    }

    @Test fun ocrRowHasNoNodesNoSpinnerAndNoUnknownBattery() {
        val j = CaptureRows.screen(log, 1_000, wolt, 7, "0 0 1280 2856", "ocr", ScreenStage.OFFER, listOf("Expected earnings"), emptyList(),
            listOf(CaptureRows.Node("x", "", "", "", "", false)), null, spinner = true)
        assertTrue(j.getBoolean("offerLike"))
        assertFalse(j.has("nodes")); assertFalse(j.has("deliver_spinner")); assertFalse(j.has("phone_battery_pct")); assertFalse(j.has("loc"))
    }

    @Test fun clickTextIsRedactedAndEmptyClicksAreNotWritten() {
        assertEquals("Call [tel] Call", CaptureRows.click(wolt, listOf("Call +994 50 123 45 67", "Call", "Call"))!!.getString("text"))
        assertNull(CaptureRows.click(wolt, listOf("  ")))
        val s = CaptureRows.screenshotUnavailable(wolt, 5, false)
        assertEquals(5, s.getInt("code")); assertFalse(s.getBoolean("display"))
    }

    @Test fun timingConstantsLiveInOnePlace() {
        assertEquals(180L, CaptureTiming.SECOND_READ_MS)
        assertEquals(500L, CaptureTiming.TICK_SHIFT_MS); assertEquals(2_000L, CaptureTiming.TICK_IDLE_MS)
        assertEquals(5_000L, CaptureTiming.HOME_TICK_MS)
    }
}
