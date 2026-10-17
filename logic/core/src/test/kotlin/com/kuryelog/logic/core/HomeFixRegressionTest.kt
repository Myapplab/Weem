package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

class HomeFixRegressionTest {
    private fun fix(at: Long, accuracy: Double? = 8.0, lat: Double = 40.001) =
        HomeShift.Fix(at, lat, 49.0, accuracy, 10.0 / 3.6)

    @Test fun invalidReadingBreaksTheConsecutiveStartPair() {
        for (bad in listOf(fix(2_000, null), fix(2_000, -1.0), fix(2_000, Double.NaN),
            fix(2_000, lat = Double.NaN), fix(2_000, lat = 91.0), fix(2_000, lat = 41.0))) {
            val h = HomeShift(40.0, 49.0)
            assertEquals(HomeShift.Decision.NONE, h.observe(fix(1_000), false))
            assertEquals(HomeShift.Decision.NONE, h.observe(bad, false))
            assertEquals(HomeShift.Decision.NONE, h.observe(fix(3_000), false))
            assertEquals(HomeShift.Decision.START, h.observe(fix(4_000), false))
        }
    }

    @Test fun coordinatesMustBeFiniteAndInRange() {
        for ((lat, lon) in listOf("NaN" to "49", "40" to "Infinity", "91" to "49", "40" to "181"))
            assertNull(HomeShift.parseHome(lat, lon))
        assertNotNull(HomeShift.parseHome("0", "0"))
    }
}
