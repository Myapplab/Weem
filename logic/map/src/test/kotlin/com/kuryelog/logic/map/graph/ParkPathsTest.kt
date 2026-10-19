package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.TestGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/park_paths_test.dart` test 1–2 (park bayrağı); `MAP.md` 2.13. Ekim planı madde 10 (F6.4/F6.5, SORU-8): bütün parkların
 * (leisure=park|garden, landuse=recreation_ground) içindeki yaya yolları `WayFlag.PARK`; bicycle=no hariç.
 */
class ParkPathsTest {
    private fun flagAt(g: RoadGraph, north: Double, east: Double = 0.0): Int =
        g.flagsOf(g.nearestEdge(lat(north), lon(east), maxDist = 5.0)!!.edge)

    @Test
    fun `park içindeki yaya yolu bayrak alır - park yoksa almaz - başlık park bilgisini bildirir`() {
        val g = TestGraphs.park()
        assertNotEquals(0, flagAt(g, 100.0) and WayFlag.PARK)
        assertTrue(g.hasParkData)
        for (kind in listOf("garden", "recreation")) {
            val h = TestGraphs.park(park = kind)
            assertNotEquals(kind, 0, flagAt(h, 100.0) and WayFlag.PARK)
        }
        val none = TestGraphs.park(park = null)
        assertEquals(0, flagAt(none, 100.0) and WayFlag.PARK)
        // Parktaki araba yolu (residential) park bayrağı almaz: kural değişmez.
        assertEquals(0, flagAt(g, 200.0, 200.0) and WayFlag.PARK)
    }

    @Test
    fun `bicycle=no yaya yolu park içinde de bayrak almaz`() {
        val g = TestGraphs.park(pathTags = mapOf("highway" to "footway", "bicycle" to "no"))
        assertEquals(0, flagAt(g, 100.0) and WayFlag.PARK)
    }
}
