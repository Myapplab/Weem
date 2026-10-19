package com.kuryelog.logic.map.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** YolGo `test/map_data_update_test.dart` test 1 (güncelleme hatası eldeki haritayı korur); `MAP.md` 2.13. */
class MapDataStateTest {
    @Test
    fun `güncelleme hatası eldeki haritayı korur`() {
        val raw = OsmRaw()
            .addNode(1, 40.40, 49.85)
            .addNode(2, 40.401, 49.85)
            .addWay(OsmWay(10, listOf(1, 2), mapOf("highway" to "residential")))
        val graph = buildRoadGraph(raw) { _, _ -> 0.0 }
        val failed = MapDataState.updateFailed("Yol verisi indirilemedi", graph = graph)
        assertEquals(MapDataPhase.ERROR, failed.phase)
        assertEquals("Yol verisi indirilemedi", failed.error)
        assertSame(graph, failed.graph)
        assertFalse(failed.ready) // hata görünür; navigasyon grafiği yine elde
    }
}
