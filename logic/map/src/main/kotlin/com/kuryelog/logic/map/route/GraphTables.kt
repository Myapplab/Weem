package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag
import java.util.WeakHashMap

/**
 * Grafa bağlı, sorgudan bağımsız rota tabloları (MAP.md 3.13 "CostModel önbelleği"): kenar yönü başına giriş/çıkış açısı, way
 * başına OSM ön kalitesi ve OSM way id → way dizinleri. Bir kez hesaplanır, aynı grafın bütün [CostModel]'leri paylaşır; değerler
 * YolGo'nun her sorguda yeniden hesapladığıyla bit bit aynıdır (açılar f32). Bakü geniş haritasında kurulumun ~%75'i açılardı.
 */
internal class GraphTables private constructor(g: RoadGraph) {
    val startB = FloatArray(g.edgeCount * 2)
    val endB = FloatArray(g.edgeCount * 2)

    /** Way başına `priorQuality(sınıf, bayrak)` (öğrenilmiş değer yokken kalite). */
    val prior = DoubleArray(g.wayOsmId.size)

    /** Grafta OSM bisiklet yolu bayraklı way var mı (A* sezgi çarpanı). */
    val anyBikeway: Boolean

    private val waysByOsm = HashMap<Long, IntArray>(g.wayOsmId.size * 2)

    init {
        var bike = false
        for (w in g.wayOsmId.indices) {
            val flags = g.wayFlags[w].toInt() and 0xFFFF
            prior[w] = Quality.priorQuality(RoadClass.entries[g.wayClass[w].toInt() and 0xFF], flags)
            if ((flags and WayFlag.BIKEWAY) != 0) bike = true
            val osm = g.wayOsmId[w]
            val old = waysByOsm[osm]
            waysByOsm[osm] = if (old == null) intArrayOf(w) else old + w
        }
        anyBikeway = bike
        for (e in 0 until g.edgeCount) {
            val s = g.edgeGeomStart[e]
            val n = g.edgeGeomCount[e]
            if (n < 2) continue
            val a = s
            val b = s + 1
            val y = s + n - 2
            val z = s + n - 1
            startB[e * 2] = Geo.bearing(g.geomLat[a], g.geomLon[a], g.geomLat[b], g.geomLon[b]).toFloat()
            endB[e * 2] = Geo.bearing(g.geomLat[y], g.geomLon[y], g.geomLat[z], g.geomLon[z]).toFloat()
            startB[e * 2 + 1] = Geo.bearing(g.geomLat[z], g.geomLon[z], g.geomLat[y], g.geomLon[y]).toFloat()
            endB[e * 2 + 1] = Geo.bearing(g.geomLat[b], g.geomLon[b], g.geomLat[a], g.geomLon[a]).toFloat()
        }
    }

    /** Bu OSM id'yi taşıyan way dizinleri (way sırasıyla); yoksa null. */
    fun waysOf(osm: Long): IntArray? = waysByOsm[osm]

    companion object {
        private val cache = WeakHashMap<RoadGraph, GraphTables>()

        fun of(g: RoadGraph): GraphTables = synchronized(cache) { cache.getOrPut(g) { GraphTables(g) } }
    }
}
