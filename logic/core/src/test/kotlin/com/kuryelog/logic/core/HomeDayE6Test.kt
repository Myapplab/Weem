package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * YolGo `home_day_e6_test.dart` (10) → WEEM `HomeShift` (CORE.md 6.2). Dokuzu aynen; "elle bitirdikten sonra 50–200 m içinde hızlı
 * fixler…" Rawstats kuralında farklıdır (CORE.md 1.9): evde (≤50 m) fix görülmediği için başlatma engeli kalkmaz.
 */
class HomeDayE6Test {
    private val homeLat = 40.4
    private val homeLon = 49.85

    /** [northM] metre kuzeyde, [seconds] sn'de bir fix; [kmh] null = sağlayıcı hız vermedi. */
    private fun fix(northM: Double, seconds: Long, kmh: Double? = 10.0, acc: Double = 8.0) =
        HomeShift.Fix(seconds * 1000, homeLat + northM / 111195, homeLon, acc, kmh?.let { it / 3.6 })

    private fun home() = HomeShift(homeLat, homeLon)
    private fun HomeShift.idle(f: HomeShift.Fix) = observe(f, false)

    private val none = HomeShift.Decision.NONE
    private val start = HomeShift.Decision.START

    @Test fun twoConsecutiveFixesOutsideFiftyMetresAtTenKmhAndTwentyMetresAccuracyStart() {
        val r = home()
        assertEquals(none, r.idle(fix(51.0, 0, acc = 20.0)))
        assertEquals(start, r.idle(fix(57.0, 2, acc = 20.0)))
    }

    @Test fun fastGpsFixesFortyNineMetresAwayAreNotADeparture() {
        val r = home()
        for (s in 0L until 10) assertEquals(none, r.idle(fix(49.0, s, kmh = 20.0)))
    }

    @Test fun gpsSpeedZeroMissingNaNOrInfiniteNeverStartsFromTwoPositions() {
        for (speed in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, null)) {
            val r = home()
            for (s in 0L..8 step 2) assertEquals("$speed", none, r.idle(fix(250 + s * 4.0, s, kmh = speed)))
        }
    }

    @Test fun gpsNinePointNineKmhDoesNotStartEvenIfThePositionsMoveFaster() {
        val r = home()
        for (s in 0L..8 step 2) assertEquals(none, r.idle(fix(250 + s * 4.0, s, kmh = 9.9)))
    }

    @Test fun twentyOneMetresAccuracyIsNotEnoughToStart() {
        val r = home()
        for (s in 0L until 6) assertEquals(none, r.idle(fix(250 + s * 4.0, s, kmh = 15.0, acc = 21.0)))
    }

    @Test fun aFixWithAccuracy21_35_36NegativeOrNaNBreaksThePair() {
        for (accuracy in listOf(21.0, 35.0, 36.0, -1.0, Double.NaN)) {
            val r = home()
            assertEquals("$accuracy", none, r.idle(fix(250.0, 0, kmh = 15.0)))
            assertEquals("$accuracy", none, r.idle(fix(254.0, 1, kmh = 15.0, acc = accuracy)))
            assertEquals("$accuracy", none, r.idle(fix(258.0, 2, kmh = 15.0)))
            assertEquals("$accuracy", start, r.idle(fix(262.0, 3, kmh = 15.0)))
        }
    }

    @Test fun aReturnInsideFiftyMetresOrASlowFixBreaksThePair() {
        val near = home()
        assertEquals(none, near.idle(fix(51.0, 0)))
        assertEquals(none, near.idle(fix(49.0, 1)))
        assertEquals(none, near.idle(fix(53.0, 2)))
        assertEquals(start, near.idle(fix(57.0, 3)))
        val slow = home()
        assertEquals(none, slow.idle(fix(250.0, 0)))
        assertEquals(none, slow.idle(fix(254.0, 1, kmh = 9.9)))
        assertEquals(none, slow.idle(fix(258.0, 2)))
        assertEquals(start, slow.idle(fix(262.0, 3)))
    }

    @Test fun anInvalidCoordinateAlsoBreaksTheConsecutiveStartFixes() {
        val r = home()
        assertEquals(none, r.idle(fix(250.0, 0)))
        assertEquals(none, r.idle(HomeShift.Fix(1000, Double.NaN, homeLon, 8.0, 10 / 3.6)))
        assertEquals(none, r.idle(fix(258.0, 2)))
        assertEquals(start, r.idle(fix(262.0, 3)))
    }

    /** Fark (CORE.md 1.9): Rawstats `startBlocked` evde (≤50 m) bir fix görülene kadar sürer; YolGo 200 m kuralı başlatıyordu. */
    @Test fun afterAManualStopFastFixesBetweenFiftyAndTwoHundredMetresNeverStartAndTheBlockStays() {
        val r = home().also { it.onManualStop() }
        assertEquals(none, r.idle(fix(80.0, 0)))
        assertEquals(none, r.idle(fix(120.0, 10)))
        assertEquals(none, r.idle(fix(190.0, 20)))
        assertEquals(none, r.idle(fix(205.0, 30)))
        assertEquals(none, r.idle(fix(209.0, 31)))
        assertTrue(r.startBlocked)
    }

    @Test fun theEndKeepsTheOldAccuracyAllowance() {
        val r = home().also { it.onManualStart() }
        fun day(f: HomeShift.Fix) = r.observe(f, true)
        assertEquals(none, day(fix(900.0, 0, kmh = 20.0)))
        assertEquals(none, day(fix(30.0, 60, kmh = 0.0, acc = 30.0)))
        for (s in 61L until 90) assertEquals(none, day(fix(30.0, s, kmh = 0.0, acc = 30.0)))
        assertEquals(HomeShift.Decision.END, day(fix(30.0, 90, kmh = 0.0, acc = 30.0)))
    }
}
