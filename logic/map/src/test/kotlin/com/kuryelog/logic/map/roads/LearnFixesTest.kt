package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.nav.MatchInfo
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WEEM eki (MAP.md 9.3.1): fix + eşleşme → öğrenme girdisi; 20 m eşiği, durak/varış yakını, onay, ana cadde. */
class LearnFixesTest {
    private val fix = NavFix(1_000, lat(0.0), lon(0.0), accuracyM = 8.0, speedMps = 5.0)
    private fun info(dist: Double, cls: RoadClass = RoadClass.RESIDENTIAL) = MatchInfo(3, 100, "Ad", cls, true, true, dist)

    @Test
    fun `20 m içindeki eşleşme sokak olur, uzak eşleşme sokaksız`() {
        val f = LearnFixes.of(fix, info(12.0), setOf(100L), emptyList(), null)
        assertEquals(100L, f.way)
        assertEquals("Ad", f.name)
        assertEquals(18.0, f.kmh, 1e-9)
        assertTrue(f.approved && f.wrongWay)
        assertFalse(f.excludeStops)
        val far = LearnFixes.of(fix, info(25.0, RoadClass.PRIMARY), setOf(100L), emptyList(), null)
        assertNull(far.way)
        assertFalse(far.approved || far.mainRoad)
        assertTrue(LearnFixes.of(fix, info(5.0, RoadClass.PRIMARY), emptySet(), emptyList(), null).mainRoad)
    }

    @Test
    fun `durak 60 m ya da varış 80 m yakını dur-kalk sayılmaz`() {
        assertTrue(LearnFixes.of(fix, null, emptySet(), listOf(GeoPoint(lat(50.0), lon(0.0))), null).excludeStops)
        assertFalse(LearnFixes.of(fix, null, emptySet(), listOf(GeoPoint(lat(70.0), lon(0.0))), null).excludeStops)
        assertTrue(LearnFixes.of(fix, null, emptySet(), emptyList(), GeoPoint(lat(70.0), lon(0.0))).excludeStops)
        assertTrue(LearnFixes.of(fix, null, emptySet(), emptyList(), null, atPlace = true).excludeStops)
        assertEquals(0.0, LearnFixes.of(fix.copy(speedMps = -1.0), null, emptySet(), emptyList(), null).kmh, 0.0)
    }
}
