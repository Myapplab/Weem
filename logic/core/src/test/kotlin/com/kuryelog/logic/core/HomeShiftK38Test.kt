package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** K38 + "K38/Y8 ek": shift starts on leaving home and ends after 2 min still (<10 m) back home (200 m). E6 (Faz 4) changed the
 *  start to ≥50 m at ≥10 km/h of the GPS's own speed on two consecutive ≤20 m fixes (HomeShiftE6Test); the two start tests
 *  below follow it, the end rule and the other tests are unchanged. */
class HomeShiftK38Test {
    private val homeLat = 40.4093
    private val homeLon = 49.8671
    /** A fix [northM] metres north of home (1 deg latitude = 111195 m with R = 6371.0088 km). */
    private fun fix(atS: Long, northM: Double, acc: Double? = 8.0, speedKmh: Double? = null) =
        HomeShift.Fix(atS * 1000, homeLat + northM / 111_195.08, homeLon, acc, speedKmh?.let { it / 3.6 })

    @Test fun leavingHomeFastStartsAfterTwoConsistentFixes() {
        val h = HomeShift(homeLat, homeLon)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(0, 20.0), false))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(30, 150.0, speedKmh = 18.0), false)) // E6: first fix ≥50 m at ≥10 km/h
        assertEquals(HomeShift.Decision.START, h.observe(fix(60, 260.0, speedKmh = 18.0), false)) // E6: the second one starts
        assertTrue(h.endArmed)
    }

    @Test fun speedBetweenFixesIsNotUsedWithoutGpsSpeed() {
        // E6: only the GPS's own speed counts (K38 also took the speed between two fixes).
        val h = HomeShift(homeLat, homeLon)
        h.observe(fix(0, 300.0, speedKmh = null), false)
        // 70 m in 30 s = 8.4 km/h between the fixes, no GPS speed
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(30, 370.0, speedKmh = null), false))
        val slow = HomeShift(homeLat, homeLon)
        slow.observe(fix(0, 300.0), false)
        assertEquals(HomeShift.Decision.NONE, slow.observe(fix(30, 340.0), false)) // 4.8 km/h walking
    }

    @Test fun gpsJumpAndPoorAccuracyNeverStart() {
        val h = HomeShift(homeLat, homeLon)
        h.observe(fix(0, 10.0), false)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(2, 5_000.0, speedKmh = 30.0), false)) // 5 km in 2 s
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(4, 5_010.0, speedKmh = 30.0), false)) // jump fix is not a basis
        val p = HomeShift(homeLat, homeLon)
        p.observe(fix(0, 300.0, acc = 120.0, speedKmh = 20.0), false)
        assertEquals(HomeShift.Decision.NONE, p.observe(fix(30, 450.0, acc = 120.0, speedKmh = 20.0), false))
        assertEquals(HomeShift.Decision.NONE, p.observe(fix(31, 455.0, acc = null, speedKmh = 20.0), false))
    }

    @Test fun longGapBetweenFixesIsNotConsistent() {
        val h = HomeShift(homeLat, homeLon)
        h.observe(fix(0, 300.0, speedKmh = 20.0), false)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(600, 2_000.0, speedKmh = 20.0), false))
        assertEquals(HomeShift.Decision.START, h.observe(fix(630, 2_150.0, speedKmh = 20.0), false))
    }

    @Test fun manualStartAtHomeDoesNotEndUntilLeftOnce() {
        val h = HomeShift(homeLat, homeLon)
        h.onManualStart()
        for (s in 0L..600L step 2) assertEquals(HomeShift.Decision.NONE, h.observe(fix(s, 3.0, speedKmh = 0.0), true))
        assertFalse(h.endArmed)
        h.observe(fix(700, 600.0, speedKmh = 25.0), true) // 597 m in 100 s
        assertTrue(h.endArmed)
        assertEquals(HomeShift.Decision.END, returnAndStay(h, 1_000))
    }

    /** Ride in below 10 km/h, then fixes within a few metres for two minutes. */
    private fun returnAndStay(h: HomeShift, t0: Long): HomeShift.Decision {
        h.observe(fix(t0, 400.0, speedKmh = 20.0), true)
        h.observe(fix(t0 + 30, 150.0, speedKmh = 12.0), true) // inside but too fast: no window
        var last = HomeShift.Decision.NONE
        var s = t0 + 60
        while (s <= t0 + 60 + 130 && last == HomeShift.Decision.NONE) {
            last = h.observe(fix(s, 5.0 + (s % 3), speedKmh = 0.5), true); s += 2
        }
        return last
    }

    /** F4.5b: K38's 2 min / 200 m became 30 s / 50 m; the gap, area and accuracy guards are K38's. */
    @Test fun autoEndNeedsThirtySecondsStillInsideTenMetres() {
        val h = HomeShift(homeLat, homeLon, endArmed = true)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(0, 20.0, speedKmh = 3.0), true))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(100, 22.0, speedKmh = 0.0), true)) // gap > 45 s restarts the window
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(110, 21.0, speedKmh = 0.0), true))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(120, 23.0, speedKmh = 0.0), true))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(125, 40.0, speedKmh = 0.0), true)) // moved 18 m: new window from here
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(135, 41.0, speedKmh = 0.0), true))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(145, 42.0, speedKmh = 0.0), true))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(150, 41.0, acc = 60.0, speedKmh = 0.0), true)) // poor fix ignored
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(152, 40.0, speedKmh = 0.0), true))
        assertEquals(HomeShift.Decision.END, h.observe(fix(155, 41.0, speedKmh = 0.0), true)) // 30 s after 125
        assertFalse(h.endArmed)
        assertFalse(h.startBlocked)
    }

    @Test fun stillOutsideTheRadiusNeverEnds() {
        val h = HomeShift(homeLat, homeLon, endArmed = true)
        for (s in 0L..600L step 2) assertEquals(HomeShift.Decision.NONE, h.observe(fix(s, 260.0, speedKmh = 0.0), true)) // waiting at a restaurant nearby
    }

    @Test fun manualStopOutsideBlocksAutoStartUntilHomeSeen() {
        val h = HomeShift(homeLat, homeLon, endArmed = true)
        h.onManualStop()
        h.observe(fix(0, 3_000.0, speedKmh = 30.0), false)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(30, 2_750.0, speedKmh = 30.0), false)) // driving home after a manual stop
        assertTrue(h.startBlocked)
        h.observe(fix(400, 15.0), false) // home
        assertFalse(h.startBlocked)
        h.observe(fix(5_000, 260.0, speedKmh = 15.0), false)
        assertEquals(HomeShift.Decision.START, h.observe(fix(5_030, 380.0, speedKmh = 15.0), false))
    }

    @Test fun manualStopInsideHomeThenLeavingStarts() {
        val h = HomeShift(homeLat, homeLon)
        h.onManualStop()
        h.observe(fix(0, 5.0), false)
        h.observe(fix(30, 250.0, speedKmh = 20.0), false)
        assertEquals(HomeShift.Decision.START, h.observe(fix(60, 420.0, speedKmh = 20.0), false))
    }

    @Test fun storedHomeIsParsedWithoutGuessing() {
        assertNull(HomeShift.parseHome(null, "49.8"))
        assertNull(HomeShift.parseHome("40.4", ""))
        assertNull(HomeShift.parseHome("NaN", "49.8"))
        assertNull(HomeShift.parseHome("91", "49.8"))
        assertEquals(40.4093 to 49.8671, HomeShift.parseHome("40.4093", "49.8671"))
    }

    @Test fun reasonsHaveNoCoordinates() {
        assertEquals(listOf("manual", "home_departure", "home_arrival"),
            listOf(HomeShift.REASON_MANUAL, HomeShift.REASON_DEPARTURE, HomeShift.REASON_ARRIVAL))
        assertEquals(200.0, HomeShift.distM(homeLat, homeLon, homeLat + 200.0 / 111_195.08, homeLon), 0.5)
    }
}
