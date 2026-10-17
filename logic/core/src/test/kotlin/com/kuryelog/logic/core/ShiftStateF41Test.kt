package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * F4.1 (Ekim planı madde 1, SORU-1a → A): `content://com.kuryelog.app.economics/shift/v1/state` tells YolGo whether the
 * Rawstats shift runs, when it started and when/why the last one ended. The offers grants are revoked at shift stop, so
 * YolGo could not tell "shift ended" from "Rawstats missing"; this path is granted like v3 (share switch + YolGo's
 * signature) and stays readable after the shift. No coordinates. Announced at shift start and stop.
 */
/* WEEM: `pathAndUri` ve `providerGrantAndAnnouncement` çevrilmez (içerik sağlayıcısı kalkar, S7); `ShiftStateFeed.snapshot` → `ShiftStateSnapshot.json`. */
class ShiftStateF41Test {
    @Test fun runningShift() {
        val j = ShiftStateSnapshot.json(active = true, startedAtMs = 1_000L, endedAtMs = 500L, endReason = "manual",
            interruptedAtMs = 0L, now = 2_000L)
        assertEquals(1, j.getInt("schemaVersion"))
        assertTrue(j.getBoolean("active"))
        assertEquals(1_000L, j.getLong("startedAtMs"))
        // The last shift's end stays visible; YolGo acts on it only when active is false.
        assertEquals(500L, j.getLong("endedAtMs"))
        assertEquals("manual", j.getString("endReason"))
        assertFalse(j.getBoolean("interrupted"))
        assertEquals(2_000L, j.getLong("generatedAtMs"))
        assertEquals(setOf("schemaVersion", "active", "startedAtMs", "endedAtMs", "endReason", "interrupted", "generatedAtMs"), j.keys().asSequence().toSet())
    }

    @Test fun endedShiftAndUnknowns() {
        val j = ShiftStateSnapshot.json(active = false, startedAtMs = 1_000L, endedAtMs = 9_000L, endReason = "home_arrival",
            interruptedAtMs = 0L, now = 10_000L)
        assertFalse(j.getBoolean("active"))
        assertEquals(9_000L, j.getLong("endedAtMs"))
        assertEquals("home_arrival", j.getString("endReason"))
        val none = ShiftStateSnapshot.json(active = false, startedAtMs = 0L, endedAtMs = 0L, endReason = null, interruptedAtMs = 0L, now = 1L)
        assertTrue(none.isNull("startedAtMs")); assertTrue(none.isNull("endedAtMs")); assertTrue(none.isNull("endReason"))
    }

    @Test fun interruptedShiftIsVisible() {
        val j = ShiftStateSnapshot.json(active = true, startedAtMs = 1_000L, endedAtMs = 0L, endReason = null, interruptedAtMs = 5_000L, now = 6_000L)
        assertTrue(j.getBoolean("interrupted"))
        assertFalse(ShiftStateSnapshot.json(active = true, startedAtMs = 6_000L, endedAtMs = 0L, endReason = null, interruptedAtMs = 5_000L, now = 7_000L)
            .getBoolean("interrupted")) // an earlier shift's interruption
    }
}
