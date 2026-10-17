package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

class HomePickResultTest {
    @Test fun successfulDoubleResultBecomesADraftWithUnknownAccuracy() {
        val draft = HomePickResult.parse(true, 40.4, 49.8)!!
        assertEquals(40.4, draft.lat, 0.0)
        assertEquals(49.8, draft.lon, 0.0)
        assertNull(draft.accuracyM)
    }
    @Test fun cancellationNeverSelectsAPoint() {
        assertNull(HomePickResult.parse(false, 40.4, 49.8))
    }
    @Test fun missingOrPartialResultIsRejected() {
        assertNull(HomePickResult.parse(true, null, null))
        assertNull(HomePickResult.parse(true, 40.4, null))
        assertNull(HomePickResult.parse(true, null, 49.8))
    }
    @Test fun extrasMustBeDoublesWithoutStringOrNumericCoercion() {
        for (value in listOf<Any>("40.4", 40, 40.4f, 40L, true, doubleArrayOf(40.4))) {
            assertNull(HomePickResult.parse(true, value, 49.8))
            assertNull(HomePickResult.parse(true, 40.4, value))
        }
    }
    @Test fun nonFiniteOrOutsideEarthResultIsRejected() {
        for ((lat, lon) in listOf(Double.NaN to 49.8, 40.4 to Double.NEGATIVE_INFINITY,
            90.1 to 49.8, 40.4 to -180.1))
            assertNull(HomePickResult.parse(true, lat, lon))
    }
    @Test fun zeroAndBoundaryResultsAreNotTreatedAsMissing() {
        assertNotNull(HomePickResult.parse(true, 0.0, 0.0))
        assertNotNull(HomePickResult.parse(true, -90.0, 180.0))
    }
}
