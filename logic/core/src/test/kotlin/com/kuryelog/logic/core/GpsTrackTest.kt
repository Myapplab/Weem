package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** CORE.md 1.3 adım 1, 2, 6 (RS `GpsService.kt:196-237`): fix yaşı, türetilmiş hız, D8 GPS birikimi. Mesafe burada haversine. */
class GpsTrackTest {
    private val lat0 = 40.4093
    private val lon0 = 49.8671
    /** [m] metre kuzeyde, [s] saniyede (monoton). */
    private fun fix(m: Double, s: Double, acc: Float = 5f, speed: Float? = null) = GpsFix(
        elapsedNanos = (s * 1e9).toLong(), timeMs = 1_000_000L + (s * 1000).toLong(), lat = lat0 + Math.toDegrees(m / 6_371_008.8), lon = lon0,
        accuracy = acc, hasAccuracy = true, speed = speed ?: 0f, hasSpeed = speed != null,
    )

    @Test fun firstGoodFixIsTheAnchorAndRealStepsCount() {
        val t = GpsTrack()
        assertEquals(0.0, t.accumulate(fix(0.0, 0.0)), 0.0)
        assertEquals(20.0, t.accumulate(fix(20.0, 2.0)), 1e-6)
        assertEquals(30.0, t.accumulate(fix(50.0, 4.0)), 1e-6)
        assertEquals(50.0, t.distanceM, 1e-6)
    }

    @Test fun jitterJumpsAndLooseFixesDoNotCount() {
        val t = GpsTrack()
        t.accumulate(fix(0.0, 0.0, acc = 10f))
        assertEquals("under max(4 m, mean accuracy)", 0.0, t.accumulate(fix(9.0, 2.0, acc = 10f)), 0.0)
        assertEquals("over 25 m/s: the anchor stays", 0.0, t.accumulate(fix(200.0, 4.0, acc = 10f)), 0.0)
        assertEquals("accuracy over 30 m is ignored", 0.0, t.accumulate(fix(100.0, 6.0, acc = 31f)), 0.0)
        assertEquals("from the old anchor", 40.0, t.accumulate(fix(40.0, 8.0, acc = 10f)), 1e-6)
        assertEquals(40.0, t.distanceM, 1e-6)
    }

    @Test fun aGapOver30sMovesTheAnchorWithoutCounting() {
        val t = GpsTrack()
        t.accumulate(fix(0.0, 0.0))
        assertEquals(0.0, t.accumulate(fix(500.0, 31.0)), 0.0)
        assertEquals(20.0, t.accumulate(fix(520.0, 33.0)), 1e-6)
        assertEquals(20.0, t.distanceM, 1e-6)
    }

    @Test fun onlyFreshFixesNewerThanTheAnchorAreUsed() {
        val t = GpsTrack()
        val f = fix(0.0, 100.0)
        assertTrue(t.fresh(f, 100_000L))
        assertTrue(t.fresh(f, 112_000L))
        assertFalse("older than 12 s", t.fresh(f, 112_001L))
        assertFalse("from the future", t.fresh(f, 99_999L))
        t.accumulate(f)
        assertFalse("not newer than the anchor", t.fresh(fix(10.0, 100.0), 101_000L))
        assertTrue(t.fresh(fix(10.0, 100.5), 101_000L))
    }

    @Test fun derivedSpeedNeedsTwoGoodFixesAndARealDisplacement() {
        val t = GpsTrack()
        assertNull(t.derivedSpeed(fix(0.0, 0.0)))
        t.seen(fix(0.0, 0.0, acc = 5f))
        assertEquals(10.0, t.derivedSpeed(fix(20.0, 2.0, acc = 5f))!!, 1e-6)
        assertNull("not more than max(8 m, both accuracies)", t.derivedSpeed(fix(9.0, 2.0, acc = 5f)))
        assertNull("15 s or more apart", t.derivedSpeed(fix(200.0, 15.0)))
        assertNull("loose accuracy", t.derivedSpeed(fix(200.0, 2.0, acc = 31f)))
        t.reset()
        assertNull(t.previous)
        assertEquals(0.0, t.distanceM, 0.0)
    }

    @Test fun csvLineIsRawstatsOrder() {
        val f = GpsFix(1L, 0L, 40.5, 49.75, accuracy = 4.5f, speed = 3.25f, hasSpeed = true, bearing = 90.0f, altitude = 12.5)
        assertEquals("T,40.5,49.75,4.5,3.25,90.0,12.5", f.csvLine("T"))
        assertEquals("t,lat,lon,acc_m,speed_ms,bearing,alt_m", GpsFix.CSV_HEADER)
        assertEquals(HomeShift.Fix(0L, 40.5, 49.75, 4.5, 3.25), f.homeFix())
        assertEquals(HomeShift.Fix(0L, 40.5, 49.75, null, null), f.copy(hasAccuracy = false, hasSpeed = false).homeFix())
    }
}
