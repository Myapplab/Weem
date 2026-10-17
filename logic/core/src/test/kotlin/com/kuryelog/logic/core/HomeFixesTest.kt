package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

class HomeFixesTest {
    private fun sample(accuracy: Double? = 8.0, at: Long = 1_000L) = HomeFixes.Sample(40.0, 49.0, accuracy, at)
    @Test fun collectAllReadingsAndKeepBestAccuracy() {
        val f = HomeFixes()
        f.add(sample(60.0), 1_000); f.add(sample(9.0, 2_000), 2_000); f.add(sample(30.0, 3_000), 3_000)
        assertEquals(9.0, f.best!!.accuracyM!!, 0.0)
        assertFalse(HomeFixes.needsWarning(f.best!!))
    }
    @Test fun rejectStaleFutureInvalidAndUnknownAccuracy() {
        for (s in listOf(sample(at = 0), sample(at = 50_000), sample(-1.0), sample(Double.NaN), sample(null), sample().copy(lat = 91.0))) {
            val f = HomeFixes(); assertFalse(f.add(s, 20_000)); assertNull(f.best)
        }
    }
    @Test fun anInitiallyGoodCachedFixCannotExpireWhileCollecting() {
        val f = HomeFixes()
        f.add(sample(3.0, 1_000), 15_000)
        f.add(sample(8.0, 15_000), 15_000)
        assertEquals(8.0, f.bestAt(21_000)!!.accuracyM!!, 0.0)
        assertNull(f.bestAt(31_000))
    }
    @Test fun twentyFiveMetresIsWarningBoundary() {
        assertFalse(HomeFixes.needsWarning(sample(25.0)))
        assertTrue(HomeFixes.needsWarning(sample(25.1)))
        assertEquals(6_000L, HomeFixes.COLLECT_MS)
    }
}
