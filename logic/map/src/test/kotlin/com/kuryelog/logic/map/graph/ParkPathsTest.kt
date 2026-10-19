package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `park_paths_test.dart` 1–2 (A6.2a, MAP.md 2.13). Park içindeki yaya yolları (`leisure=park|garden`,
 * `landuse=recreation_ground`) `WayFlag.PARK`; `bicycle=no` hariç. Eski harita dosyası okunur, park bilgisi olmadığı bilinir.
 * Rota payı (3–4): `route.ParkPathsTest`. Uygulama: A6.3.
 */
class ParkPathsTest {
    private fun parkFlag(g: RoadGraph, north: Double, east: Double): Int =
        g.flagsOf(g.nearestEdge(lat(north), lon(east), maxDist = 5.0)!!.edge) and WayFlag.PARK

    @Test
    fun `park içindeki yaya yolu bayrak alır, park yoksa almaz, başlık park bilgisini bildirir`() {
        val g = TestGraphs.parkGraph()
        assertNotEquals(0, parkFlag(g, 100.0, 0.0))
        assertTrue(g.hasParkData)
        for (kind in listOf("garden", "recreation")) {
            assertNotEquals(kind, 0, parkFlag(TestGraphs.parkGraph(park = kind), 100.0, 0.0))
        }
        assertEquals(0, parkFlag(TestGraphs.parkGraph(park = null), 100.0, 0.0))
        // Parktaki araba yolu (residential) park bayrağı almaz: kural değişmez.
        assertEquals(0, parkFlag(g, 200.0, 200.0))
    }

    @Test
    fun `bicycle=no yaya yolu park içinde de bayrak almaz`() {
        val g = TestGraphs.parkGraph(pathTags = mapOf("highway" to "footway", "bicycle" to "no"))
        assertEquals(0, parkFlag(g, 100.0, 0.0))
    }
}
