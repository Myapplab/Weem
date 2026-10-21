package com.kuryelog.logic.map.surface
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SurfaceSignalTest {
    private fun sample(linear: Boolean, tilt: Double = 0.0, hz: Int = 100, horizontal: Double = 0.0): List<SurfaceSignal.Window> {
        val processor = SurfaceSignal(); val windows = ArrayList<SurfaceSignal.Window>()
        val up = doubleArrayOf(sin(tilt), 0.0, cos(tilt))
        for (i in 1..(hz * 9)) {
            val seconds = i.toDouble() / hz; val stopped = seconds <= 3
            val vertical = if (stopped) 0.0 else .5 * sin(2 * PI * 8 * seconds)
            val withGravity = vertical + if (linear) 0.0 else 9.80665
            val input = DoubleArray(3) { withGravity * up[it] }
            input[1] = if (stopped) 0.0 else horizontal
            processor.feed((i * 1e9 / hz).toLong(), input, up, linear, stopped, if (stopped) 0.0 else 5.0)?.let { windows.add(it) }
        }; return windows.filter { it.valid }
    }
    @Test fun rawAndLinearRemoveGravityExactlyOnce() {
        val raw = sample(false); val linear = sample(true)
        assertTrue(raw.size >= 2); assertEquals(raw.last().rms, linear.last().rms, .001)
    }
    @Test fun worldVerticalIsStableWhenMountIsTilted() {
        assertEquals(sample(true).last().rms, sample(true, .9).last().rms, .001)
    }
    @Test fun brakingDoesNotBecomeVerticalRoughness() {
        assertEquals(sample(true).last().rms, sample(true, horizontal = 8.0).last().rms, .001)
        assertTrue(sample(true, horizontal = 8.0).last().horizontalRms > 7)
    }
    @Test fun sampleRateUsesSensorTimestampRatherThanRequestedRate() {
        assertEquals(80.0, sample(true, hz = 80).last().hz, .1)
    }
    @Test fun unavailableGyroIsNullAndFrequencyBandsRemainAuxiliary() {
        val w = sample(true).last(); assertNull(w.gyro); assertEquals(3, w.bands.size)
        assertTrue(w.bands[1] > w.bands[0]); assertTrue(w.jerk > 0)
    }
    @Test fun noOrientationOrNoStationaryOffsetCannotCreateAValidMeasurement() {
        val processor = SurfaceSignal(); val windows = ArrayList<SurfaceSignal.Window>()
        for (i in 1..500) processor.feed(i * 10_000_000L, doubleArrayOf(0.0, 0.0, 1.0), null, true, false, 5.0)?.let { windows.add(it) }
        assertTrue(windows.isNotEmpty()); assertTrue(windows.none { it.valid })
    }
    @Test fun windowsDoNotOverlapOrAcceptDuplicateTimestamps() {
        val p = SurfaceSignal(); val windows = ArrayList<SurfaceSignal.Window>()
        for (i in 1..700) {
            val at = i * 10_000_000L
            p.feed(at, doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), true, i < 300, 5.0)?.let { windows.add(it) }
            assertNull(p.feed(at, doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), true, false, 5.0))
        }
        for (i in 1 until windows.size) assertEquals(windows[i - 1].endNs, windows[i].startNs)
    }
    @Test fun aGapResetsFiltersAndDoesNotInventJerkAcrossMissingData() {
        val p = SurfaceSignal(); p.feed(10_000_000L, doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), true, true, 0.0)
        assertNull(p.feed(2_000_000_000L, doubleArrayOf(0.0, 0.0, 4.0), doubleArrayOf(0.0, 0.0, 1.0), true, false, 5.0))
        assertFalse(p.offsetReady)
    }
    @Test fun sameMountRestartPreservesOffsetButNewSetupRequiresStationaryZero() {
        val p = SurfaceSignal(); val up = doubleArrayOf(0.0, 0.0, 1.0)
        for (i in 1..300) p.feed(i * 10_000_000L, doubleArrayOf(0.0, 0.0, .3), up, true, true, 0.0)
        assertTrue(p.offsetReady)
        p.restartWindowPreservingOffset()
        val windows = ArrayList<SurfaceSignal.Window>()
        for (i in 301..700) p.feed(i * 10_000_000L, doubleArrayOf(0.0, 0.0, .3), up, true, false, 5.0)?.let { windows.add(it) }
        assertTrue(windows.any { it.valid }); assertEquals(0.0, windows.last().rms, .01)
        p.reset(); assertFalse(p.offsetReady)
    }
    private fun shocks(secondAt: Int?): List<SurfaceSignal.Impact> {
        val p = SurfaceSignal(); val hits = linkedMapOf<Long, SurfaceSignal.Impact>()
        for (i in 1..800) {
            val peak = if (i in 450..452 || (secondAt != null && i in secondAt..secondAt + 2)) 14.0 else 0.0
            p.feed(i * 10_000_000L, doubleArrayOf(0.0, 0.0, peak), doubleArrayOf(0.0, 0.0, 1.0), true, i < 300, 5.0)
                ?.impacts?.forEach { hits[it.atNs] = it }
        }
        return hits.values.toList()
    }
    @Test fun anIsolatedLargeShockIsNotAveragedAway() {
        val events = shocks(null); assertEquals(1, events.size); assertTrue(events.single().peak > 6)
    }
    @Test fun wheelRepeatMergesButAnotherNearbyShockRemainsSeparate() {
        assertEquals(1, shocks(469).size) // .95 m wheelbase at 5 m/s: 190 ms.
        assertEquals(2, shocks(490).size) // 400 ms is a distinct event.
    }
}
