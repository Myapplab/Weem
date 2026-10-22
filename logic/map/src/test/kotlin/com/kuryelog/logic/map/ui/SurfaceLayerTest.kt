package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.surface.SurfaceKey
import com.kuryelog.logic.map.surface.SurfaceSummary
import org.junit.Assert.*
import org.junit.Test

/** Haritada yüzey renkleri (YolGo `surfaceLines`/`surfaceColor`): yalnız ölçülmüş kesimler; kesin kesim düz ve kalın, diğeri kesikli. */
class SurfaceLayerTest {
    private val g = TestGraphs.directAndDetour()
    private fun piece(score: Double, north: Double = 25.0, confidence: Double = .9, calibrated: Boolean = true, meters: Double = 200.0, way: Long = 100): SurfaceSummary {
        val from = g.edgeFrom[TestGraphs.edgeOf(g, 100)]
        return SurfaceSummary(SurfaceKey.of(100, SurfaceKey.anchor(g.nodeLat[from], g.nodeLon[from]), 0, 0), way, 0, 0, "p", score, confidence, meters, 0.0, 3, 0, 1.0, 4.0, 0, 0.0,
            lat(north), lon(0.0), calibrated = calibrated)
    }

    @Test fun colorsFollowTheScoreBands() {
        assertEquals(0x2EBD59, SurfaceLayerRules.rgb(75.0)); assertEquals(0xFF9800, SurfaceLayerRules.rgb(74.9)); assertEquals(0xFF9800, SurfaceLayerRules.rgb(50.0)); assertEquals(0xE53935, SurfaceLayerRules.rgb(49.9))
    }

    @Test fun layerIsEmptyWhenOffAndOnlyMeasuredSegmentsAreDrawn() {
        assertTrue(SurfaceLayerRules.lines(g, listOf(piece(80.0)), show = false).isEmpty())
        val sure = SurfaceLayerRules.lines(g, listOf(piece(80.0)), show = true).single()
        assertEquals(0x2EBD59, sure.rgb); assertEquals(0.8, sure.alpha, 0.0); assertEquals(6.0, sure.width, 0.0); assertFalse(sure.dashed)
        assertTrue(sure.points.size >= 2)
        // Segment çizgisi ±25 m: baştan 50 m'den kısa.
        val first = sure.points.first(); val last = sure.points.last()
        assertEquals(50.0, com.kuryelog.logic.map.geo.Geo.haversine(first.lat, first.lon, last.lat, last.lon), 3.0)
    }

    @Test fun uncalibratedOrWeakSegmentsAreDashedAndFaintAndTinyOnesAreSkipped() {
        val unsure = SurfaceLayerRules.lines(g, listOf(piece(20.0, calibrated = false)), true).single()
        assertEquals(0xE53935, unsure.rgb); assertEquals(0.5, unsure.alpha, 0.0); assertEquals(4.0, unsure.width, 0.0); assertTrue(unsure.dashed)
        assertTrue(SurfaceLayerRules.lines(g, listOf(piece(20.0, confidence = .2)), true).first().dashed)
        assertTrue(SurfaceLayerRules.lines(g, listOf(piece(20.0, confidence = .01)), true).isEmpty())
        assertTrue(SurfaceLayerRules.lines(g, listOf(piece(20.0, meters = 9.0)), true).isEmpty())
        assertTrue("başka yola oturmaz", SurfaceLayerRules.lines(g, listOf(piece(20.0, way = 999)), true).isEmpty())
    }
}
