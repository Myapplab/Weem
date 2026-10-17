package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * H4 (Ekim planı F1.3): the process died during a shift and Android restarted the sticky service, but refused the
 * foreground start (Android 12+ from the background). Before, the service stopped silently with `shift=true` left on:
 * no GPS, no `shift_end`, YolGo saw an open shift. Now the shift stays on, a `shift_interrupted` event is written,
 * the interruption is visible to the shift state, and a notification asks for one tap to resume.
 */
/* WEEM: `serviceKeepsTheShiftWritesTheEventAndAsksForATap` Rawstats'ta kaynak taramasıydı; burada `ShiftMachine` davranışı (CORE.md 6.1). */
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

    @get:org.junit.Rule val temp = org.junit.rules.TemporaryFolder()

    @Test fun serviceKeepsTheShiftWritesTheEventAndAsksForATap() {
        val r = ShiftRig(temp.root)
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        val startedAt = r.wall
        // The process died; Android restarts the sticky service (null intent) and refuses the foreground start.
        r.wall += 600_000
        r.calls.clear()
        r.refuseForeground = IllegalStateException("ForegroundServiceStartNotAllowedException")
        assertEquals(ShiftMachine.StartResult.NOT_STICKY, r.machine.onStartCommand(null, null))
        assertTrue("the shift stays on", r.machine.shiftOn())
        assertEquals(r.wall, r.prefs.map["shiftInterruptedAt"])
        assertEquals(listOf("shift_start", "gps_error", "shift_interrupted"), r.types())
        assertTrue(r.events().last().getString("err").contains("ForegroundServiceStartNotAllowedException"))
        assertEquals(listOf("startForeground", "notify:5103", "stopSelf"), r.calls)
        assertTrue(r.machine.state().interrupted)
        assertTrue(r.states.last().interrupted)
        // The tap on 5103 resumes the same shift: no new shift_start, no shift_end.
        r.refuseForeground = null
        r.wall += 60_000
        r.calls.clear()
        assertEquals(ShiftMachine.StartResult.STICKY, r.machine.onStartCommand(ShiftMachine.ACTION_START, null))
        assertEquals(listOf("shift_start", "gps_error", "shift_interrupted", "shift_resumed"), r.types())
        assertEquals(startedAt + 600_000, r.events().last().getLong("interruptedAt"))
        assertFalse(r.prefs.map.containsKey("shiftInterruptedAt"))
        assertTrue(r.calls.contains("cancel:5103"))
        assertEquals(startedAt, r.prefs.map["shiftStart"])
        assertFalse(r.machine.state().interrupted)
    }

    @Test fun aRefusedRestartOfAClosedShiftIsNotAnInterruption() {
        val r = ShiftRig(temp.root)
        r.refuseForeground = IllegalStateException("refused")
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        assertFalse(r.machine.shiftOn())
        assertFalse(r.prefs.map.containsKey("shiftInterruptedAt"))
        assertFalse(r.calls.contains("notify:5103"))
    }
}
