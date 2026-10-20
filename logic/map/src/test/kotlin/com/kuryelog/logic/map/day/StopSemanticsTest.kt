package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.day.StopSemantics.classifySurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * YolGo `assistant_test.dart` "kaldırım/geometri belirsizliği ışık yakınlığından önce gelir" (1; A6.2b, MAP.md 11.8, 11.16): `classifySurface` ve kaldırımda ölçer saymaz.
 */
class StopSemanticsTest {
    private val now = DayFixtures.ms("2026-10-02T14:00:00Z")

    @Test
    fun `kaldırım geometri belirsizliği ışık yakınlığından önce gelir`() {
        assertEquals(StopSurface.SIDEWALK, classifySurface(accuracy = 5.0, roadM = 12.0, footM = 1.0))
        assertEquals(StopSurface.UNKNOWN, classifySurface(accuracy = 20.0, roadM = 1.0))
        assertEquals(StopSurface.UNKNOWN, classifySurface(accuracy = 5.0, roadM = 1.0, footM = 2.0))
        assertEquals(StopSurface.SIDEWALK, classifySurface(accuracy = 30.0, roadM = 0.0, manualSidewalk = true))
        val tracker = LightTracker()
        val sidewalk = RoadInfo(onRoad = true, known = true, surface = StopSurface.SIDEWALK)
        for (i in 0 until 10) tracker.feed(DayFix(now + i * 1000L, 40.4, 49.8), { _, _ -> sidewalk })
        assertNull(tracker.sinceMs)
        assertNull(tracker.finish(now + 10_000))
    }
}
