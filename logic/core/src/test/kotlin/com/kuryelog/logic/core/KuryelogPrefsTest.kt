package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** CORE.md 1.1, 5.4: `kuryelog` anahtarları Rawstats'la aynı; `ensureManualMode` göçü bir kez (A2.3). */
class KuryelogPrefsTest {
    private class Fake(val map: MutableMap<String, Any> = mutableMapOf(), private val inner: KuryelogPrefs.Memory = KuryelogPrefs.Memory(map)) :
        KuryelogPrefs.Values by inner {
        var writes = 0
        override fun put(values: Map<String, Any?>) { writes++; inner.put(values) }
    }

    @Test fun oldAutomaticSessionsAreClosedOnceAfterUpgrade() {
        val p = Fake(mutableMapOf("shift" to true, "manualSession" to true, "autoShift" to true, "autoStarted" to true))
        assertTrue(KuryelogPrefs.shiftOn(p))
        assertTrue(KuryelogPrefs.ensureManualMode(p))
        assertEquals(mapOf("shift" to false, "manualSession" to false, "autoShift" to false, "autoStarted" to false, "manualPolicy" to 1), p.map)
        assertFalse(KuryelogPrefs.shiftOn(p))
        assertFalse(KuryelogPrefs.ensureManualMode(p))
        assertEquals(1, p.writes)
    }

    @Test fun installedOverRawstatsTheRunningShiftStays() {
        val p = Fake(mutableMapOf("manualPolicy" to 1, "shift" to true, "manualSession" to true))
        assertFalse(KuryelogPrefs.ensureManualMode(p))
        assertEquals(0, p.writes)
        assertTrue(KuryelogPrefs.shiftOn(p))
        assertFalse(KuryelogPrefs.shiftOn(Fake(mutableMapOf("shift" to true))))
    }

    @Test fun keysAreRawstatsNames() {
        assertEquals("kuryelog", KuryelogPrefs.FILE)
        assertEquals(listOf("shift", "manualSession", "autoStarted", "autoShift", "manualPolicy", "shiftStart", "shiftStartReason", "shiftEndedAt",
            "shiftEndReason", "shiftInterruptedAt", "dist", "home.lat", "home.lon", "home.updatedAt", "home.startBlocked", "home.endArmed", "crashSeenAt", "weem.deviceId", "weem.setupDone"),
            with(KuryelogPrefs) { listOf(SHIFT, MANUAL_SESSION, AUTO_STARTED, AUTO_SHIFT, MANUAL_POLICY, SHIFT_START, SHIFT_START_REASON, SHIFT_ENDED_AT,
                SHIFT_END_REASON, SHIFT_INTERRUPTED_AT, DIST, HOME_LAT, HOME_LON, HOME_UPDATED_AT, HOME_START_BLOCKED, HOME_END_ARMED, CRASH_SEEN_AT, DEVICE_ID, SETUP_DONE) })
    }
}
