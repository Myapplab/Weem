package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.route.TestGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * YolGo `map_data_update_test.dart` "güncelleme hatası eldeki haritayı korur" (A6.2a, MAP.md 2.13).
 * Regresyon: harita güncellemesi başarısız olunca eldeki harita kaybolmamalı (ana ekran kurulum sayfasına atlamasın). Uygulama: A6.3.
 */
class MapDataStateTest {
    @Test
    fun `güncelleme hatası eldeki haritayı korur`() {
        val raw = OsmRaw()
        raw.addNode(1, 40.40, 49.85)
        raw.addNode(2, 40.401, 49.85)
        raw.addWay(OsmWay(10, listOf(1, 2), mapOf("highway" to "residential")))
        val graph = GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
        val failed = MapDataState.updateFailed("Yol verisi indirilemedi", graph = graph)
        assertEquals(MapDataPhase.ERROR, failed.phase)
        assertEquals("Yol verisi indirilemedi", failed.error)
        assertSame(graph, failed.graph)
        assertFalse(failed.ready) // hata görünür; navigasyon grafiği yine elde
    }
}
