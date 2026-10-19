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

    /**
     * `router_test` `_graph`: A(0,0) → B(0,400) iki yol:
     *  - Doğrudan (way 100): 400 m, ortasında [hill] m tepe
     *  - Dolambaç (way 201-203): A→E(200,0)→F(200,400)→B, 800 m düz
     * [ramp]: doğrudan yol A→B boyunca düzgün yükselir (m).
     */
    fun main(
        hill: Double = 0.0,
        ramp: Double = 0.0,
        directTags: Map<String, String> = mapOf("highway" to "residential"),
        directReversed: Boolean = false,
    ): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)) // A
        raw.addNode(2, lat(200.0), lon(0.0)) // M
        raw.addNode(3, lat(400.0), lon(0.0)) // B
        raw.addNode(4, lat(0.0), lon(200.0)) // E
        raw.addNode(5, lat(400.0), lon(200.0)) // F
        raw.addWay(OsmWay(100, if (directReversed) listOf(3, 2, 1) else listOf(1, 2, 3), directTags))
        raw.addWay(OsmWay(201, listOf(1, 4), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(202, listOf(4, 5), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(203, listOf(5, 3), mapOf("highway" to "residential")))
        val elevation = { la: Double, lo: Double ->
            val east = (lo - LocalMeters.LON0) * 111320 * Math.cos(LocalMeters.LAT0 * Math.PI / 180)
            val north = (la - LocalMeters.LAT0) * 111320
            val slope = ramp * (north / 400).coerceIn(0.0, 1.0) // her yerde kuzeye doğru yükselir
            if (Math.abs(east) > 50) slope else hill * Math.max(0.0, 1 - Math.abs(north - 200) / 200) + slope
        }
        return buildRoadGraph(raw, elevation)
    }

    /** `footGraph`: yol 100 (A→M) + yaya yolu 300; [connected] değilse yaya yolu yola bağlı değil. */
    fun foot(connected: Boolean = true): RoadGraph {
        val raw = OsmRaw()
            .addNode(1, lat(0.0), lon(0.0))
            .addNode(2, lat(400.0), lon(0.0))
            .addNode(3, lat(400.0), lon(if (connected) -80.0 else -20.0))
            .addNode(4, lat(440.0), lon(-80.0))
            .addWay(OsmWay(100, listOf(1, 2), mapOf("highway" to "residential")))
            .addWay(OsmWay(300, if (connected) listOf(2, 3, 4) else listOf(3, 4), mapOf("highway" to "footway")))
        return build(raw)
    }

    /** `plazaGraph` (Y7): yaya bölgesi; [stub] yakında kopuk servis yolu ekler. */
    fun plaza(stub: Boolean = false): RoadGraph {
        val raw = OsmRaw()
            .addNode(10, lat(0.0), lon(0.0))
            .addNode(13, lat(120.0), lon(0.0))
            .addNode(11, lat(250.0), lon(0.0))
            .addNode(12, lat(250.0), lon(400.0))
            .addWay(OsmWay(500, listOf(10, 13, 11), mapOf("highway" to "pedestrian")))
            .addWay(OsmWay(100, listOf(11, 12), mapOf("highway" to "residential")))
        if (stub) {
            raw.addNode(20, lat(-30.0), lon(30.0))
                .addNode(21, lat(-30.0), lon(80.0))
                .addWay(OsmWay(400, listOf(20, 21), mapOf("highway" to "service")))
        }
        return build(raw)
    }

    /** `router_test` yürüme limiti: yola bağlı 600 m yaya yolu. */
    fun walkLimit(): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(0.0), lon(200.0))
        raw.addNode(3, lat(600.0), lon(200.0))
        raw.addWay(OsmWay(1, listOf(1, 2), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(2, listOf(2, 3), mapOf("highway" to "footway")))
        return build(raw)
    }

    /**
     * `v140_features_test` `_turnGraph`: A(0,0) → B(300,300): merdiven (way 1–6) 600 m, 5 kavşakta dönüş (her kavşakta çıkmaz kol var);
     * L (way 20–21) A → C(400,0) → B, ~716 m, kavşaksız tek kıvrım.
     */
    fun turn(): RoadGraph {
        val raw = OsmRaw()
        val stairs = listOf(0.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0, 100.0 to 200.0, 200.0 to 200.0, 200.0 to 300.0, 300.0 to 300.0)
        for (i in stairs.indices) raw.addNode(10L + i, lat(stairs[i].second), lon(stairs[i].first))
        for (i in 0 until stairs.size - 1) raw.addWay(OsmWay(1L + i, listOf(10L + i, 11L + i), mapOf("highway" to "residential")))
        // Çıkmaz kollar: merdiven köşeleri gerçek kavşak olsun
        for (i in 1 until stairs.size - 1) {
            raw.addNode(40L + i, lat(stairs[i].second + 30), lon(stairs[i].first - 30))
            raw.addWay(OsmWay(40L + i, listOf(10L + i, 40L + i), mapOf("highway" to "residential")))
        }
        raw.addNode(30, lat(0.0), lon(400.0)) // C
        raw.addWay(OsmWay(20, listOf(10, 30), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(21, listOf(30, 16), mapOf("highway" to "residential")))
        return build(raw)
    }

    /** `v140_features_test` `_mainOneway`: A(0,0) → B(0,400): doğrudan ana cadde (tek yön ters) ya da 1200 m dolambaç. */
    fun mainOneway(): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(200.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(0.0))
        raw.addNode(4, lat(0.0), lon(400.0))
        raw.addNode(5, lat(400.0), lon(400.0))
        raw.addWay(OsmWay(100, listOf(3, 2, 1), mapOf("highway" to "primary", "oneway" to "yes")))
        raw.addWay(OsmWay(201, listOf(1, 4), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(202, listOf(4, 5), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(203, listOf(5, 3), mapOf("highway" to "residential")))
        return build(raw)
    }

    /** `bikeway_e13_test` `_single`: tek yol (way 500) A(0,0)→B(0,400), çizim yönü kuzeye. */
    fun single(tags: Map<String, String>): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(200.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(0.0))
        raw.addWay(OsmWay(500, listOf(1, 2, 3), tags))
        return build(raw)
    }

    /**
     * `bikeway_e13_test` `_parallel`: A(0,0) → B(0,400) iki paralel yol, ikisinde de iki dönüş, doğrudan yol yok:
     * batı x=−20 (440 m, ways 301–303) ve doğu x=+40 (480 m, ways 401–403).
     */
    fun parallel(east: Map<String, String> = mapOf("highway" to "residential")): RoadGraph {
        val res = mapOf("highway" to "residential")
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)) // A
        raw.addNode(2, lat(400.0), lon(0.0)) // B
        raw.addNode(3, lat(0.0), lon(-20.0))
        raw.addNode(4, lat(400.0), lon(-20.0))
        raw.addNode(5, lat(0.0), lon(40.0))
        raw.addNode(6, lat(400.0), lon(40.0))
        raw.addWay(OsmWay(301, listOf(1, 3), res))
        raw.addWay(OsmWay(302, listOf(3, 4), res))
        raw.addWay(OsmWay(303, listOf(4, 2), res))
        raw.addWay(OsmWay(401, listOf(1, 5), east))
        raw.addWay(OsmWay(402, listOf(5, 6), east))
        raw.addWay(OsmWay(403, listOf(6, 2), east))
        return build(raw)
    }

    /** `graph` içinde OSM way id'sinin ilk kenarı. */
    fun edge(g: RoadGraph, way: Long): Int = (0 until g.edgeCount).first { g.osmIdOf(it) == way }
}
