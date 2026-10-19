package com.kuryelog.logic.map

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.buildRoadGraph

/** Sentetik graflar: YolGo testlerindeki `_graph`, `footGraph`, `plazaGraph`, … (`MAP.md` 16.1). Metre → enlem/boylam [LocalMeters]. */
object TestGraphs {
    private fun build(raw: OsmRaw) = buildRoadGraph(raw) { _, _ -> 0.0 }

    /**
     * `park_paths_test`: A(0,0) → B(0,400): parkın içinden düz yaya yolu way 100 (400 m) ve çevreden residential dolambaç (~800 m).
     * [park]: park alanı (leisure=park) way 900 yolu içine alır (`"recreation"` → landuse=recreation_ground); [pathTags] yaya yolu etiketleri.
     */
    fun park(park: String? = "park", pathTags: Map<String, String> = mapOf("highway" to "footway")): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(200.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(0.0))
        raw.addNode(4, lat(0.0), lon(200.0))
        raw.addNode(5, lat(400.0), lon(200.0))
        raw.addWay(OsmWay(100, listOf(1, 2, 3), pathTags))
        raw.addWay(OsmWay(201, listOf(1, 4), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(202, listOf(4, 5), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(203, listOf(5, 3), mapOf("highway" to "residential")))
        if (park != null) {
            // Parkın sınırı yolun biraz dışından (yol uçları kapıda).
            raw.addNode(11, lat(-10.0), lon(-60.0)); raw.addNode(12, lat(-10.0), lon(60.0))
            raw.addNode(13, lat(410.0), lon(60.0)); raw.addNode(14, lat(410.0), lon(-60.0))
            raw.addWay(
                OsmWay(
                    900, listOf(11, 12, 13, 14, 11),
                    if (park == "recreation") mapOf("landuse" to "recreation_ground") else mapOf("leisure" to park),
                ),
            )
        }
        return build(raw)
    }

    /**
     * `push_segments_test`: A(0,0) —yol 300 m— X(0,300) —yaya yolu 60 m— Y(0,360) —yol 300 m— B(0,660). [detourEastM] verilirse
     * X'ten Y'ye doğuya açılan yasal araç yolu (yaklaşık 2 × açılma + 60 m).
     */
    fun push(detourEastM: Double? = null, footTags: Map<String, String> = mapOf("highway" to "footway")): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(300.0), lon(0.0))
        raw.addNode(3, lat(360.0), lon(0.0))
        raw.addNode(4, lat(660.0), lon(0.0))
        raw.addWay(OsmWay(10, listOf(1, 2), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(20, listOf(2, 3), footTags))
        raw.addWay(OsmWay(30, listOf(3, 4), mapOf("highway" to "residential")))
        if (detourEastM != null) {
            raw.addNode(5, lat(300.0), lon(detourEastM))
            raw.addNode(6, lat(360.0), lon(detourEastM))
            raw.addWay(OsmWay(40, listOf(2, 5, 6, 3), mapOf("highway" to "residential")))
        }
        return build(raw)
    }
}
