package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * YolGo `home_day_end_rule_test.dart` (5) → WEEM `HomeShift` (CORE.md 6.2). F4.5b: eve 50 m içinde, 5 km/s altında, en az 30 sn;
 * açık siparişte bitmez. Dört senaryo aynen; "50 m içinde 30 sn dur" farklı: Rawstats sıçrama kuralı (50 m/s) 300 m → 21 m geçişini
 * 5. sn'ye kadar sıçrama sayar, pencere 6. sn'de açılır → ilk `END` 36. sn (CORE.md 1.9).
 */
class HomeDayEndRuleTest {
    private val homeLat = 40.4093
    private val homeLon = 49.8671
    private fun fix(s: Long, northM: Double, kmh: Double = 0.0, acc: Double = 6.0) =
        HomeShift.Fix(s * 1000, homeLat + northM / 111195.08, homeLon, acc, kmh / 3.6)

    /** Gün kurulsun (evden 300 m uzakta görülsün), sonra [northM]'de [kmh] ile [secs] sn kal; ilk `END`'in saniyesi. */
    private fun endAfter(r: HomeShift, northM: Double, kmh: Double, secs: Int, busy: Boolean = false): Int? {
        r.observe(fix(0, 300.0, kmh = 15.0), true)
        for (s in 1..secs) {
            if (r.observe(fix(s.toLong(), northM + (s % 3), kmh = kmh), true, busy) == HomeShift.Decision.END) return s
        }
        return null
    }

    private fun home() = HomeShift(homeLat, homeLon)

    @Test fun defaultsAreFiftyMetresFiveKmhThirtySecondsWithTheSameStart() {
        assertEquals(50.0, HomeShift.RADIUS_M, 0.0)
        assertEquals(5.0 / 3.6, HomeShift.END_SPEED_MPS, 1e-12)
        assertEquals(30_000L, HomeShift.STILL_MS)
        assertEquals(50.0, HomeShift.START_RADIUS_M, 0.0)
        assertEquals(10.0 / 3.6, HomeShift.START_SPEED_MPS, 1e-12)
    }

    @Test fun thirtySecondsStillWithinFiftyMetresEndsTheDay() {
        assertEquals(36, endAfter(home(), 20.0, 0.0, 120))
    }

    @Test fun twentyNineSecondsAreNotEnoughAndSixKmhWalkingIsNotStanding() {
        assertNull(endAfter(home(), 20.0, 0.0, 29))
        assertNull(endAfter(home(), 30.0, 6.0, 180))
    }

    @Test fun waitingAtALightSixtyMetresFromHomeNeverEnds() {
        assertNull(endAfter(home(), 60.0, 0.0, 300))
    }

    @Test fun anOpenOrderNeverEnds() {
        assertNull(endAfter(home(), 20.0, 0.0, 300, busy = true))
    }
}
