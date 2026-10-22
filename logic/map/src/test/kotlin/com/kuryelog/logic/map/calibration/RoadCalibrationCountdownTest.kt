package com.kuryelog.logic.map.calibration

import org.junit.Assert.*
import org.junit.Test

class RoadCalibrationCountdownTest {
    private val pressed = 50_000L

    @Test fun waitsForOffsetWithoutStartingThreeTwoOne() {
        assertNull(calibrationCountdownDeadline(pressed, pressed, offsetReady = false))
        assertNull(calibrationCountdownDeadline(pressed + 14_999, pressed, offsetReady = false))
    }
    @Test fun offsetReadyStartsANormalThreeSecondCountdownFromThatMoment() {
        assertEquals(pressed + 2_300 + 3_000, calibrationCountdownDeadline(pressed + 2_300, pressed, offsetReady = true))
        // GPS/duruş baştan hazırsa beklemeden 3-2-1.
        assertEquals(pressed + 3_000, calibrationCountdownDeadline(pressed, pressed, offsetReady = true))
    }
    @Test fun fifteenSecondsWithoutOffsetStillStartsThreeTwoOne() {
        assertEquals(pressed + 15_000 + 3_000, calibrationCountdownDeadline(pressed + 15_000, pressed, offsetReady = false))
    }
    @Test fun waitingNeverShowsAFinishedOrRunningCounter() {
        assertEquals(3, calibrationCountdownRemaining(pressed + 9_000, null))
        val deadline = pressed + 3_000
        assertEquals(listOf(3, 3, 2, 1, 0, 0), listOf(0L, 900L, 1_000L, 2_000L, 3_000L, 4_000L)
            .map { calibrationCountdownRemaining(pressed + it, deadline) })
    }
    /** Servisin 100 ms tik döngüsü: ofset 4,2 sn'de hazır olur → 3-2-1 ondan sonra. */
    @Test fun serviceTickLoopWaitsThenCountsDownOnce() {
        var deadline: Long? = null; var startedAt = -1L; var recordingAt = -1L
        val shown = arrayListOf<Int>()
        var t = pressed
        while (recordingAt < 0 && t < pressed + 30_000) {
            val ready = t - pressed >= 4_200
            if (deadline == null) calibrationCountdownDeadline(t, pressed, ready)?.let { deadline = it; startedAt = t }
            else if (t >= deadline!!) recordingAt = t
            shown.add(calibrationCountdownRemaining(t, deadline))
            t += 100
        }
        assertEquals(pressed + 4_200, startedAt)
        assertEquals(pressed + 7_200, recordingAt)
        // Beklerken 0 ya da eksi sayı görünmez; geri sayım 3 → 2 → 1 → 0 iner.
        assertEquals(listOf(3, 2, 1, 0), shown.distinct())
        assertTrue(shown.zipWithNext().all { (a, b) -> b <= a })
    }
}
