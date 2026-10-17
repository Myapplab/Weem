package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * E6 (Faz 4): the automatic shift starts when the courier is ≥50 m from home in a straight line at ≥10 km/h, the speed being
 * the GPS's own value, on two consecutive fixes of ≤20 m accuracy. The end rule (back within 200 m, 2 min still) does not
 * change (HomeShiftK38Test). Outside a shift the watcher asks for GPS fixes only after leaving home is likely
 * ([HomeWatchPlan]), so a phone lying at home does not keep its GPS on. Synthetic positions north of a made-up home.
 */
/* WEEM: `watcherAndSettingsFollowTheRule` WEEM'in Android izleyicisini tarar; ayar metninin sayıları logic/app `HomeTextsTest`'te (CORE.md 6.1). */
class HomeShiftE6Test {
    private val homeLat = 40.4093
    private val homeLon = 49.8671
    private fun fix(atS: Long, northM: Double, acc: Double? = 8.0, speedKmh: Double? = null) =
        HomeShift.Fix(atS * 1000, homeLat + northM / 111_195.08, homeLon, acc, speedKmh?.let { it / 3.6 })

    @Test fun rule() {
        assertEquals(50.0, HomeShift.START_RADIUS_M, 0.0)
        assertEquals(10.0 / 3.6, HomeShift.START_SPEED_MPS, 1e-12)
        assertEquals(20.0, HomeShift.START_MAX_ACCURACY_M, 0.0)
        assertEquals(50.0, HomeShift.RADIUS_M, 0.0) // F4.5b: the end rule's radius is 50 m in both apps
    }

    @Test fun twoConsecutiveFixesFiftyMetresOutAtTenKmhStart() {
        val h = HomeShift(homeLat, homeLon)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(0, 10.0), false))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(2, 51.0, speedKmh = 12.0), false)) // first
        assertEquals(HomeShift.Decision.START, h.observe(fix(4, 57.0, speedKmh = 12.0), false)) // second: start
        assertTrue(h.endArmed)
    }

    @Test fun fortyNineMetresIsStillHome() {
        val h = HomeShift(homeLat, homeLon)
        for (s in 0L..20L step 2) assertEquals(HomeShift.Decision.NONE, h.observe(fix(s, 49.0, speedKmh = 20.0), false))
    }

    @Test fun speedIsTheGpsOwnValue() {
        // 100 m in 4 s (90 km/h between the fixes) without a GPS speed: no start.
        val noSpeed = HomeShift(homeLat, homeLon)
        noSpeed.observe(fix(0, 60.0), false)
        assertEquals(HomeShift.Decision.NONE, noSpeed.observe(fix(4, 160.0), false))
        assertEquals(HomeShift.Decision.NONE, noSpeed.observe(fix(8, 260.0), false))
        // 9.9 km/h: no; 10 km/h: yes.
        val slow = HomeShift(homeLat, homeLon)
        slow.observe(fix(0, 60.0, speedKmh = 9.9), false)
        assertEquals(HomeShift.Decision.NONE, slow.observe(fix(4, 70.0, speedKmh = 9.9), false))
        val ten = HomeShift(homeLat, homeLon)
        ten.observe(fix(0, 60.0, speedKmh = 10.0), false)
        assertEquals(HomeShift.Decision.START, ten.observe(fix(4, 71.0, speedKmh = 10.0), false))
    }

    @Test fun accuracyTwentyMetresAtMost() {
        val loose = HomeShift(homeLat, homeLon)
        loose.observe(fix(0, 80.0, acc = 21.0, speedKmh = 20.0), false)
        assertEquals(HomeShift.Decision.NONE, loose.observe(fix(3, 97.0, acc = 21.0, speedKmh = 20.0), false))
        val tight = HomeShift(homeLat, homeLon)
        tight.observe(fix(0, 80.0, acc = 20.0, speedKmh = 20.0), false)
        assertEquals(HomeShift.Decision.START, tight.observe(fix(3, 97.0, acc = 20.0, speedKmh = 20.0), false))
    }

    @Test fun consecutiveMeansNoFailingFixBetween() {
        val h = HomeShift(homeLat, homeLon)
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(0, 80.0, speedKmh = 15.0), false))
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(2, 85.0, speedKmh = 4.0), false))   // stopped at the gate
        assertEquals(HomeShift.Decision.NONE, h.observe(fix(4, 90.0, speedKmh = 15.0), false))  // first again
        assertEquals(HomeShift.Decision.START, h.observe(fix(6, 98.0, speedKmh = 15.0), false))
        // A noisy fix between two good ones breaks the pair as well.
        val n = HomeShift(homeLat, homeLon)
        n.observe(fix(0, 80.0, speedKmh = 15.0), false)
        assertEquals(HomeShift.Decision.NONE, n.observe(fix(2, 88.0, acc = 35.0, speedKmh = 15.0), false))
        assertEquals(HomeShift.Decision.NONE, n.observe(fix(4, 96.0, speedKmh = 15.0), false))
    }

    @Test fun afterAManualStopOutsideHomeMustBeSeenFirst() {
        val h = HomeShift(homeLat, homeLon, endArmed = true)
        h.onManualStop()
        // Riding home after stopping by hand: no start on the way.
        for ((s, d) in listOf(0L to 900.0, 10L to 700.0, 20L to 500.0, 30L to 300.0, 40L to 120.0, 50L to 60.0))
            assertEquals("$d", HomeShift.Decision.NONE, h.observe(fix(s, d, speedKmh = 25.0), false))
        h.observe(fix(60, 20.0, speedKmh = 3.0), false)   // home
        assertFalse(h.startBlocked)
        h.observe(fix(4_000, 55.0, speedKmh = 14.0), false)
        assertEquals(HomeShift.Decision.START, h.observe(fix(4_002, 62.0, speedKmh = 14.0), false))
    }

    @Test fun gpsOnlyWhenLeavingIsLikely() {
        val p = HomeWatchPlan()
        val min = 60_000L
        // A low-power fix at home, even a loose one: stays low.
        assertEquals(HomeWatchPlan.Mode.LOW, p.onFix(80.0, 100.0, null, 0))
        // Away for sure (distance minus accuracy ≥ 50 m): GPS.
        assertEquals(HomeWatchPlan.Mode.HIGH, p.onFix(140.0, 60.0, null, 30_000))
        // The GPS window lasts 5 min, then low power again; another window only after a 10 min pause.
        assertEquals(HomeWatchPlan.Mode.HIGH, p.tick(30_000 + 4 * min))
        assertEquals(HomeWatchPlan.Mode.LOW, p.tick(30_000 + 5 * min))
        assertEquals(HomeWatchPlan.Mode.LOW, p.onFix(300.0, 30.0, null, 30_000 + 6 * min))
        assertEquals(HomeWatchPlan.Mode.HIGH, p.onFix(300.0, 30.0, null, 30_000 + 15 * min))
        // Moving fast on a low-power fix opens a window at once, pause or not.
        val q = HomeWatchPlan()
        q.onFix(140.0, 60.0, null, 0); q.tick(5 * min)
        assertEquals(HomeWatchPlan.Mode.HIGH, q.onFix(400.0, 40.0, 12.0 / 3.6, 6 * min))
        // Unknown accuracy says nothing about leaving.
        assertEquals(HomeWatchPlan.Mode.LOW, HomeWatchPlan().onFix(500.0, null, null, 0))
    }

    @Test fun watcherAndSettingsFollowTheRule() {
        val watch = RepoSource.text("core/src/main/kotlin/com/kuryelog/app/core/HomeWatch.kt")
        assertTrue(watch.contains("HomeWatchCore("))
        assertTrue(RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/HomeWatchCore.kt").contains("private val plan = HomeWatchPlan()"))
        assertTrue(watch.contains("LocationManager.GPS_PROVIDER, HomeWatchPlan.HIGH_INTERVAL_MS"))
        assertTrue(watch.contains("if (loc.hasSpeed()) loc.speed.toDouble() else null"))
        val ui = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/HomeScreen.kt")
        assertTrue(ui.contains("HomeTexts.rule(lang)"))
        assertFalse(ui.contains("200 m"))
    }
}
