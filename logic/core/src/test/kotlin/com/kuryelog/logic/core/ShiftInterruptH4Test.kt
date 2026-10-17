package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * H4 (Ekim planı F1.3): the process died during a shift and Android restarted the sticky service, but refused the
 * foreground start (Android 12+ from the background). Before, the service stopped silently with `shift=true` left on:
 * no GPS, no `shift_end`, YolGo saw an open shift. Now the shift stays on, a `shift_interrupted` event is written,
 * the interruption is visible to the shift state, and a notification asks for one tap to resume.
 */
/* WEEM: `serviceKeepsTheShiftWritesTheEventAndAsksForATap` (kaynak taraması) A2.4'te `ShiftMachine` saf testi olur: olay, tercih, 5103, devamda `shift_resumed` + silme (CORE.md 6.1). */
class ShiftInterruptH4Test {
    @Test fun onlyAStickyRestartOfARunningShiftIsInterrupted() {
        assertEquals(ManualSessionPolicy.StartFailure.INTERRUPTED, ManualSessionPolicy.onStartFailure(stickyRestart = true, shiftOn = true))
        // An explicit start the courier pressed, or a restart with no shift: the old path (error message, stop).
        assertEquals(ManualSessionPolicy.StartFailure.ABORT, ManualSessionPolicy.onStartFailure(stickyRestart = false, shiftOn = true))
        assertEquals(ManualSessionPolicy.StartFailure.ABORT, ManualSessionPolicy.onStartFailure(stickyRestart = true, shiftOn = false))
        assertEquals(ManualSessionPolicy.StartFailure.ABORT, ManualSessionPolicy.onStartFailure(stickyRestart = false, shiftOn = false))
    }

    @Test fun interruptionBelongsToTheCurrentShiftOnly() {
        val start = 1_760_000_000_000L
        assertTrue(ManualSessionPolicy.interrupted(shiftOn = true, interruptedAt = start + 60_000, shiftStart = start))
        assertFalse(ManualSessionPolicy.interrupted(shiftOn = true, interruptedAt = 0L, shiftStart = start)) // never interrupted
        assertFalse(ManualSessionPolicy.interrupted(shiftOn = true, interruptedAt = start - 1, shiftStart = start)) // an earlier shift's
        assertFalse(ManualSessionPolicy.interrupted(shiftOn = false, interruptedAt = start + 60_000, shiftStart = start)) // ended
    }
}
