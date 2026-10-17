package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/** NT `TimingTest.java` (S1, S6; 9 denetim): fix yaşı, yeniden bağlanma gecikmeleri, hex. */
class TimingTest : NinebotSuite() {
    @Test fun coreRegressionChecks() {
        check(Timing.ageMs(20_000_000_000L, null) == null, "missing GPS stays missing")
        check(Timing.ageMs(20_000_000_000L, 21_000_000_000L) == null, "future GPS rejected")
        check(Timing.ageMs(20_000_000_000L, 10_000_000_000L) == 10000L, "stale fix age preserved")
        check(Timing.ageMs(20_000_000_000L, 20_000_000_000L) == 0L, "same time")
        check(Timing.hex(byteArrayOf(0, 15, 16, 255.toByte(), 128.toByte())) == "000f10ff80", "raw bytes lossless")
        check(Timing.retryMs(0) == 1000L && Timing.retryMs(3) == 8000L && Timing.retryMs(4) == 15000L && Timing.retryMs(1000) == 60000L, "retry capped")
        check(Timing.retryMs(Timing.FAST_RETRIES - 1) == 15000L && Timing.retryMs(Timing.FAST_RETRIES) == 60000L, "quick reconnects for a short stop, slow after")
        var waited = 0L
        for (a in 0 until Timing.FAST_RETRIES) waited += Timing.retryMs(a) + 15000
        check(waited >= 10 * 60_000L && waited <= 13 * 60_000L, "quick phase lasts about ten minutes: $waited")
        check(Timing.retryMs(-1) == 1000L, "negative retry safe")
        assertEquals("9 core regression checks passed", 9, count)
    }
}
