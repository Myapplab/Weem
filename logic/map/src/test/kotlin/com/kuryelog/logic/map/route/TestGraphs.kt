package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.GraphBuilder
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadGraph
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * Sentetik graflar ve yerel metre → enlem/boylam (Bakü civarı) — YolGo `router_test`, `bikeway_e13_test`, `park_paths_test`,
 * `push_segments_test`, `ww_green_two_way_test`, `quality_test`, `v140_features_test` dosyalarındaki `_graph` yardımcıları
 * (A6.2a; `MAP.md` 16.1). Her yardımcının adı Dart'taki test dosyasını gösterir.
 */
object TestGraphs {
    const val LAT0 = 40.40
    const val LON0 = 49.85

    fun lat(northM: Double) = LAT0 + northM / 111320
    fun lon(eastM: Double) = LON0 + eastM / (111320 * cos(LAT0 * Math.PI / 180))
    fun p(east: Double, north: Double) = GeoPoint(lat(north), lon(east))

    val RESIDENTIAL = mapOf("highway" to "residential")
    val FOOTWAY = mapOf("highway" to "footway")

    private fun flat(raw: OsmRaw): RoadGraph = GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }

    fun edgeOf(g: RoadGraph, way: Long): Int = (0 until g.edgeCount).first { g.osmIdOf(it) == way }

    /**
     * `router_test` `_graph`: A(0,0) → B(0,400) iki yol — doğrudan (way 100, 400 m, ortasında [hill] m tepe) ve dolambaç
     * (way 201–203: A→E(200,0)→F(200,400)→B, 800 m düz). [ramp]: doğrudan yol A→B boyunca düzgün yükselir (m).
     */
    fun directAndDetour(
        hill: Double = 0.0,
        ramp: Double = 0.0,
        directTags: Map<String, String> = RESIDENTIAL,
        directReversed: Boolean = false,
    ): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)) // A
        raw.addNode(2, lat(200.0), lon(0.0)) // M
        raw.addNode(3, lat(400.0), lon(0.0)) // B
        raw.addNode(4, lat(0.0), lon(200.0)) // E
        raw.addNode(5, lat(400.0), lon(200.0)) // F
        raw.addWay(OsmWay(100, if (directReversed) listOf(3, 2, 1) else listOf(1, 2, 3), directTags))
        raw.addWay(OsmWay(201, listOf(1, 4), RESIDENTIAL))
        raw.addWay(OsmWay(202, listOf(4, 5), RESIDENTIAL))
        raw.addWay(OsmWay(203, listOf(5, 3), RESIDENTIAL))
        return GraphBuilder.buildRoadGraph(raw) { la, lo ->
            val east = (lo - LON0) * 111320 * cos(LAT0 * Math.PI / 180)
            val north = (la - LAT0) * 111320
            val slope = ramp * (north / 400).coerceIn(0.0, 1.0) // her yerde kuzeye doğru yükselir
            if (abs(east) > 50) slope else hill * max(0.0, 1 - abs(north - 200) / 200) + slope
        }
    }

    /** `router_test` `footGraph`: A→B araç yolu (way 100), B'den batıya yaya yolu (way 300). */
    fun footGraph(connected: Boolean = true): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(400.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(if (connected) -80.0 else -20.0))
        raw.addNode(4, lat(440.0), lon(-80.0))
        raw.addWay(OsmWay(100, listOf(1, 2), RESIDENTIAL))
        raw.addWay(OsmWay(300, if (connected) listOf(2, 3, 4) else listOf(3, 4), FOOTWAY))
        return flat(raw)
    }

    /** `router_test` `plazaGraph`: yaya bölgesinden (pedestrian, way 500) çıkış; [stub]: kopuk servis yolu (way 400). */
    fun plazaGraph(stub: Boolean = false): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(10, lat(0.0), lon(0.0))
        raw.addNode(13, lat(120.0), lon(0.0))
        raw.addNode(11, lat(250.0), lon(0.0))
        raw.addNode(12, lat(250.0), lon(400.0))
        raw.addWay(OsmWay(500, listOf(10, 13, 11), mapOf("highway" to "pedestrian")))
        raw.addWay(OsmWay(100, listOf(11, 12), RESIDENTIAL))
        if (stub) {
            raw.addNode(20, lat(-30.0), lon(30.0))
            raw.addNode(21, lat(-30.0), lon(80.0))
            raw.addWay(OsmWay(400, listOf(20, 21), mapOf("highway" to "service")))
        }
        return flat(raw)
    }

    /**
     * `park_paths_test` `_graph`: A(0,0) → B(0,400); parkın içinden düz yaya yolu way 100 (400 m) ve çevreden residential
     * dolambaç (~800 m). [park]: `park` | `garden` | `recreation` | null; [pathTags] yaya yolunun etiketleri.
     */
    fun parkGraph(park: String? = "park", pathTags: Map<String, String> = FOOTWAY): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(200.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(0.0))
        raw.addNode(4, lat(0.0), lon(200.0))
        raw.addNode(5, lat(400.0), lon(200.0))
        raw.addWay(OsmWay(100, listOf(1, 2, 3), pathTags))
        raw.addWay(OsmWay(201, listOf(1, 4), RESIDENTIAL))
        raw.addWay(OsmWay(202, listOf(4, 5), RESIDENTIAL))
        raw.addWay(OsmWay(203, listOf(5, 3), RESIDENTIAL))
        if (park != null) {
            // Parkın sınırı yolun biraz dışından (yol uçları kapıda).
            raw.addNode(11, lat(-10.0), lon(-60.0)); raw.addNode(12, lat(-10.0), lon(60.0))
            raw.addNode(13, lat(410.0), lon(60.0)); raw.addNode(14, lat(410.0), lon(-60.0))
            val tags = if (park == "recreation") mapOf("landuse" to "recreation_ground") else mapOf("leisure" to park)
            raw.addWay(OsmWay(900, listOf(11, 12, 13, 14, 11), tags))
        }
        return flat(raw)
    }

    /**
     * `push_segments_test` `_graph`: A(0,0) —yol 300 m— X(0,300) —yaya yolu 60 m— Y(0,360) —yol 300 m— B(0,660).
     * [detourEastM] verilirse X'ten Y'ye doğuya açılan yasal araç yolu (yaklaşık 2 × açılma + 60 m).
     */
    fun pushGraph(detourEastM: Double? = null, footTags: Map<String, String> = FOOTWAY): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(300.0), lon(0.0))
        raw.addNode(3, lat(360.0), lon(0.0))
        raw.addNode(4, lat(660.0), lon(0.0))
        raw.addWay(OsmWay(10, listOf(1, 2), RESIDENTIAL))
        raw.addWay(OsmWay(20, listOf(2, 3), footTags))
        raw.addWay(OsmWay(30, listOf(3, 4), RESIDENTIAL))
        if (detourEastM != null) {
            raw.addNode(5, lat(300.0), lon(detourEastM))
            raw.addNode(6, lat(360.0), lon(detourEastM))
            raw.addWay(OsmWay(40, listOf(2, 5, 6, 3), RESIDENTIAL))
        }
        return flat(raw)
    }

    /**
     * `ww_green_two_way_test` `_graph`: A(0,0) → B(0,400): doğrudan tek yönlü way 100 (B → A yönünde, A'dan B'ye ters; 400 m)
     * ve yasal dolambaç way 400 (doğuya 90 m açılır, ~439 m, yalnız %10 uzun).
     */
    fun wwGraph(cls: String = "residential"): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(400.0), lon(0.0))
        raw.addNode(4, lat(200.0), lon(90.0))
        raw.addWay(OsmWay(100, listOf(2, 1), mapOf("highway" to cls, "oneway" to "yes")))
        raw.addWay(OsmWay(400, listOf(1, 4, 2), RESIDENTIAL))
        return flat(raw)
    }

    /**
     * `quality_test` `_twoWays`: A(0,0) → B(0,400) iki yol: batıdan (way 300, "Batı küçəsi") ve doğudan (way 400,
     * "Şərq küçəsi"), ikisi de [westOffset]/[eastOffset] kadar açılır.
     */
    fun twoWays(westOffset: Double = 100.0, eastOffset: Double = 100.0): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)) // A
        raw.addNode(2, lat(400.0), lon(0.0)) // B
        raw.addNode(3, lat(200.0), lon(-westOffset)) // C (batı)
        raw.addNode(4, lat(200.0), lon(eastOffset)) // D (doğu)
        raw.addWay(OsmWay(300, listOf(1, 3, 2), mapOf("highway" to "residential", "name" to "Batı küçəsi")))
        raw.addWay(OsmWay(400, listOf(1, 4, 2), mapOf("highway" to "residential", "name" to "Şərq küçəsi")))
        return flat(raw)
    }

    /** `bikeway_e13_test` `_single`: tek yol (way 500) A(0,0)→B(0,400), çizim yönü kuzeye. */
    fun single(tags: Map<String, String>): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(200.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(0.0))
        raw.addWay(OsmWay(500, listOf(1, 2, 3), tags))
        return flat(raw)
    }

    /**
     * `bikeway_e13_test` `_parallel`: A(0,0) → B(0,400) iki paralel yol, ikisinde de iki dönüş, doğrudan yol yok:
     * batı x=−20 (440 m, ways 301–303) ve doğu x=+40 (480 m, ways 401–403).
     */
    fun parallel(east: Map<String, String> = RESIDENTIAL): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)) // A
        raw.addNode(2, lat(400.0), lon(0.0)) // B
        raw.addNode(3, lat(0.0), lon(-20.0))
        raw.addNode(4, lat(400.0), lon(-20.0))
        raw.addNode(5, lat(0.0), lon(40.0))
        raw.addNode(6, lat(400.0), lon(40.0))
        raw.addWay(OsmWay(301, listOf(1, 3), RESIDENTIAL))
        raw.addWay(OsmWay(302, listOf(3, 4), RESIDENTIAL))
        raw.addWay(OsmWay(303, listOf(4, 2), RESIDENTIAL))
        raw.addWay(OsmWay(401, listOf(1, 5), east))
        raw.addWay(OsmWay(402, listOf(5, 6), east))
        raw.addWay(OsmWay(403, listOf(6, 2), east))
        return flat(raw)
    }

    val EAST_WAYS = setOf(401L, 402L, 403L)

    /**
     * `v140_features_test` `_turnGraph`: A(0,0) → B(300,300): merdiven (way 1–6, 600 m, 5 kavşakta dönüş, her kavşakta çıkmaz
     * kol) ya da L (way 20–21, A → C(400,0) → B, ~716 m, kavşaksız tek kıvrım).
     */
    fun turnGraph(): RoadGraph {
        val raw = OsmRaw()
        val stairs = listOf(0.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0, 100.0 to 200.0, 200.0 to 200.0, 200.0 to 300.0, 300.0 to 300.0)
        for (i in stairs.indices) raw.addNode(10L + i, lat(stairs[i].second), lon(stairs[i].first))
        for (i in 0 until stairs.size - 1) raw.addWay(OsmWay(1L + i, listOf(10L + i, 11L + i), RESIDENTIAL))
        // Çıkmaz kollar: merdiven köşeleri gerçek kavşak olsun
        for (i in 1 until stairs.size - 1) {
            raw.addNode(40L + i, lat(stairs[i].second + 30), lon(stairs[i].first - 30))
            raw.addWay(OsmWay(40L + i, listOf(10L + i, 40L + i), RESIDENTIAL))
        }
        raw.addNode(30, lat(0.0), lon(400.0)) // C
        raw.addWay(OsmWay(20, listOf(10, 30), RESIDENTIAL))
        raw.addWay(OsmWay(21, listOf(30, 16), RESIDENTIAL))
        return flat(raw)
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
        raw.addWay(OsmWay(201, listOf(1, 4), RESIDENTIAL))
        raw.addWay(OsmWay(202, listOf(4, 5), RESIDENTIAL))
        raw.addWay(OsmWay(203, listOf(5, 3), RESIDENTIAL))
        return flat(raw)
    }

    /**
     * `ww_no_problem_green_test` `_graph`: tek yönlü (kuzeye) "Test küçəsi" OSM'de iki parça — 100 (150–300 m, daha önce Ses+ ile
     * onaylı) ve 101 (0–150 m, onaysız); 102 çift yönlü çıkış (batıya, "Yan küçə").
     */
    fun answerGraph(): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(150.0), lon(0.0))
        raw.addNode(3, lat(300.0), lon(0.0))
        raw.addNode(4, lat(0.0), lon(-150.0))
        val street = mapOf("highway" to "residential", "oneway" to "yes", "name" to "Test küçəsi")
        raw.addWay(OsmWay(100, listOf(2, 3), street))
        raw.addWay(OsmWay(101, listOf(1, 2), street))
        raw.addWay(OsmWay(102, listOf(1, 4), mapOf("highway" to "residential", "name" to "Yan küçə")))
        return flat(raw)
    }

    // ---- Büyük ızgara şehir (A6.4: RouterPerfTest, RouteEngineTest zaman aşımı) ----

    private const val GRID_LAT0 = 40.30
    private const val GRID_LON0 = 49.60
    private const val GRID_KX = 84822.0
    private const val GRID_KY = 111320.0
    private const val GRID_M = 100.0

    /** [gridCity]'nin kopuk adası: ızgaranın 300–400 m doğusunda, hiçbir yola bağlı değil (erişilemeyen hedef). */
    fun gridIsland(n: Int) = GeoPoint(GRID_LAT0 + (n * GRID_M / 2) / GRID_KY, GRID_LON0 + (n * GRID_M + 350) / GRID_KX)

    /**
     * Izgara şehir (n × n düğüm, 100 m, ±15 m sapma): satır/sütun boyunca 1–5 parçalık yollar (%30 yaya, %15 tek yön, sınıflar
     * residential…trunk), blok çaprazları yaya yolu, kopuk ada ([gridIsland]), tepeler (yalnız + − × ÷).
     */
    fun gridCity(n: Int, seed: Int): RoadGraph {
        val r = kotlin.random.Random(seed)
        val raw = OsmRaw()
        fun id(i: Int, j: Int) = (j * n + i + 1).toLong()
        for (j in 0 until n) for (i in 0 until n) {
            raw.addNode(
                id(i, j), GRID_LAT0 + (j * GRID_M + (r.nextDouble() - 0.5) * 30) / GRID_KY,
                GRID_LON0 + (i * GRID_M + (r.nextDouble() - 0.5) * 30) / GRID_KX,
            )
        }
        var way = 1L
        val classes = listOf("residential", "residential", "residential", "tertiary", "tertiary", "secondary", "primary", "trunk")
        fun street(line: List<Long>) {
            var k = 0
            while (k < line.size - 1) {
                if (r.nextDouble() < 0.05) {
                    k++
                    continue
                }
                val end = minOf(line.size - 1, k + 1 + r.nextInt(5))
                val tags = HashMap<String, String>()
                tags["highway"] = if (r.nextDouble() < 0.3) "footway" else classes[r.nextInt(classes.size)]
                if (r.nextDouble() < 0.15) tags["oneway"] = "yes"
                raw.addWay(OsmWay(way++, line.subList(k, end + 1).toList(), tags))
                k = end
            }
        }
        for (j in 0 until n) street((0 until n).map { id(it, j) })
        for (i in 0 until n) street((0 until n).map { id(i, it) })
        var extra = (n * n + 1).toLong()
        for (j in 0 until n - 1) for (i in 0 until n - 1) {
            if (r.nextDouble() >= 0.2) continue
            val mid = extra++
            raw.addNode(mid, GRID_LAT0 + ((j + 0.5) * GRID_M) / GRID_KY, GRID_LON0 + ((i + 0.5) * GRID_M) / GRID_KX)
            raw.addWay(OsmWay(way++, listOf(id(i, j), mid, id(i + 1, j + 1)), mapOf("highway" to "footway")))
        }
        val size = n * GRID_M
        raw.addNode(extra, GRID_LAT0 + (size / 2) / GRID_KY, GRID_LON0 + (size + 300) / GRID_KX)
        raw.addNode(extra + 1, GRID_LAT0 + (size / 2) / GRID_KY, GRID_LON0 + (size + 400) / GRID_KX)
        raw.addWay(OsmWay(way++, listOf(extra, extra + 1), mapOf("highway" to "residential")))
        val bumps = List(maxOf(4, n / 10)) { doubleArrayOf(r.nextDouble() * size, r.nextDouble() * size, r.nextDouble() * 60 - 15, 200 + r.nextDouble() * 600) }
        return GraphBuilder.buildRoadGraph(raw) { la, lo ->
            val x = (lo - GRID_LON0) * GRID_KX
            val y = (la - GRID_LAT0) * GRID_KY
            var e = 0.0
            for (b in bumps) {
                val dx = x - b[0]
                val dy = y - b[1]
                e += b[2] / (1 + (dx * dx + dy * dy) / (b[3] * b[3]))
            }
            e
        }
    }
}
