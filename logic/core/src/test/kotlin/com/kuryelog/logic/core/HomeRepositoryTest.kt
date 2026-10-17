package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

class HomeRepositoryTest {
    private class Memory(var value: HomeRepository.Saved = HomeRepository.Saved()) : HomeRepository.Storage {
        override fun read() = value
        override fun write(value: HomeRepository.Saved) { this.value = value }
    }
    private fun fix(t: Long, northM: Double, speedKmh: Double = 10.0) =
        HomeShift.Fix(t, 40.0 + northM / 111_195.08, 49.0, 8.0, speedKmh / 3.6)

    @Test fun noHomeMeansNoAutomaticStartOrEndAndNoWatcher() {
        for (s in listOf(HomeRepository.Saved(), HomeRepository.Saved("NaN", "49"), HomeRepository.Saved("40", null))) {
            val r = HomeRepository(Memory(s))
            assertNull(r.point()); assertNull(r.engine()); assertFalse(r.mayWatch(false, false, true, true))
            for (active in listOf(false, true)) for (i in 1L..5)
                assertEquals(HomeShift.Decision.NONE, r.observe(fix(i * 2_000, 100.0), active))
        }
    }
    @Test fun oldCoordinatesAreRetainedAndDateRemainsUnknown() {
        val r = HomeRepository(Memory(HomeRepository.Saved("40", "49", startBlocked = true)))
        assertEquals(40.0, r.point()!!.lat, 0.0); assertNull(r.point()!!.updatedAt)
        assertTrue(r.engine()!!.startBlocked)
    }
    @Test fun savingImmediatelyReplacesTheEngineAndResetsOldGuards() {
        val m = Memory(); val r = HomeRepository(m)
        r.set(41.0, 49.0, 10); val old = r.engine()
        r.changeGuard { it.onManualStop() }
        r.set(40.0, 49.0, 20)
        assertNotSame(old, r.engine()); assertEquals(20L, r.point()!!.updatedAt)
        assertFalse(r.engine()!!.startBlocked)
        assertEquals(HomeShift.Decision.NONE, r.observe(fix(1_000, 60.0), false))
        assertEquals(HomeShift.Decision.START, r.observe(fix(3_000, 66.0), false))
        r.clear(); assertNull(r.engine()); assertNull(m.value.lat)
        assertEquals(HomeShift.Decision.NONE, r.observe(fix(5_000, 72.0), false))
    }
    @Test fun straightLineDistanceAndBothThresholdsRemain() {
        for ((distance, speed, starts) in listOf(Triple(49.9, 10.0, false), Triple(50.1, 9.9, false), Triple(50.1, 10.0, true))) {
            val r = HomeRepository(Memory()); r.set(40.0, 49.0, 10)
            assertEquals(distance, r.engine()!!.distanceToHomeM(fix(0, distance)), 0.01)
            r.observe(fix(1_000, distance, speed), false)
            assertEquals(if (starts) HomeShift.Decision.START else HomeShift.Decision.NONE, r.observe(fix(3_000, distance, speed), false))
        }
    }
    @Test fun watcherNeedsAllPermissionsAndNoShiftOrImport() {
        val r = HomeRepository(Memory()); r.set(40.0, 49.0, 10)
        assertTrue(r.mayWatch(false, false, true, true))
        assertFalse(r.mayWatch(true, false, true, true)); assertFalse(r.mayWatch(false, true, true, true))
        assertFalse(r.mayWatch(false, false, false, true)); assertFalse(r.mayWatch(false, false, true, false))
    }
    @Test fun queuedStartForAnOldHomeIsRejectedEvenWhenSavedInTheSameMillisecond() {
        val r = HomeRepository(Memory()); r.set(40.0, 49.0, 10)
        val old = r.point()!!.updatedAt
        assertTrue(r.acceptsRevision(old)); assertFalse(r.acceptsRevision(null))
        r.set(40.1, 49.0, 10)
        assertFalse(r.acceptsRevision(old)); assertTrue(r.acceptsRevision(r.point()!!.updatedAt))
        r.clear(); assertFalse(r.acceptsRevision(r.point()?.updatedAt))
    }
    @Test fun invalidSaveDoesNotEraseAValidHome() {
        val r = HomeRepository(Memory()); r.set(40.0, 49.0, 10)
        try { r.set(Double.NaN, 49.0, 20); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(10L, r.point()!!.updatedAt)
    }
}
