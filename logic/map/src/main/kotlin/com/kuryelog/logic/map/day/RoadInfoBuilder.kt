package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.LocalProjection
import com.kuryelog.logic.map.graph.PoiSet
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Durma noktasının yol bilgisi (Dart `ShiftController._roadInfo`, MAP.md 11.8); saf. [pois] **düzeltilmemiş** harita noktaları (ışık ve AVM kararı düzeltmelerden
 * etkilenmez), [graph] yoksa 6. adım; [lastAccuracy] son kabul edilen fix'in doğruluğu (duruşun kendi fix'i değil; yoksa ∞ → yüzey bilinmiyor).
 */
class RoadInfoBuilder(
    private val pois: PoiSet?,
    private val graph: RoadGraph?,
    private val lights: LightsDb,
    private val sidewalks: SidewalkMarks,
    private val lightMaxM: Double,
    private val lastAccuracy: Double?,
) {
    fun build(lat: Double, lon: Double): RoadInfo {
        // Işık: haritadaki (OSM) ışık 55 m içinde ya da önceden öğrenilmiş.
        val sig = pois?.nearest('l', lat, lon, maxM = 55.0)
        var known: Boolean? = lights.lookup(lat, lon)
        var llat: Double? = sig?.lat
        var llon: Double? = sig?.lon
        val local = lights.nearest(lat, lon, maxM = 55.0)
        if (local != null && local.exists) {
            known = true
            llat = local.lat
            llon = local.lon
        } else if (sig != null && known != false) {
            known = lights.lookup(sig.lat, sig.lon) != false
        } else if (known == true) {
            // Öğrenilmiş ışık: kendi kayıtlı yeri (uzaklık ölçümü durma noktasına göre yapılır).
            val spot = lights.nearest(lat, lon, maxM = 25.0)
            llat = spot?.lat ?: lat
            llon = spot?.lon ?: lon
        }
        val lightM = if (llat == null || llon == null) null else Geo.haversine(lat, lon, llat, llon)
        // AVM'nin yanında beklemek ışık değildir (otopark, giriş, teslim alma).
        val mall = pois?.nearest('m', lat, lon, maxM = StopSemantics.MALL_RADIUS_M) != null
        val manual = sidewalks.near(lat, lon)
        val g = graph
            ?: return RoadInfo(
                onRoad = !manual, known = known, lightLat = llat, lightLon = llon,
                surface = if (manual) StopSurface.SIDEWALK else StopSurface.UNKNOWN, lightM = lightM,
                maxLightM = lightMaxM, mall = mall, manualSidewalk = manual,
            )
        val hit = g.nearestEdge(lat, lon, maxDist = 35.0, accept = { e -> val c = g.classOf(e); !c.isFootClass && c != RoadClass.STEPS })
        val foot = g.nearestEdge(lat, lon, maxDist = 35.0, accept = { e -> g.classOf(e).isFootClass })
        val accuracy = lastAccuracy ?: Double.POSITIVE_INFINITY
        val surface = StopSemantics.classifySurface(accuracy, hit?.dist, foot?.dist, manual)
        val roadside = hit != null && StopSemantics.isRoadside(accuracy, hit.dist, g.classOf(hit.edge))
        var jm: Double? = null
        if (hit != null) {
            val proj = LocalProjection(lat, lon)
            var best = Double.POSITIVE_INFINITY
            val nodes = HashSet<Int>()
            val d = 80 / 111320.0
            for (e in g.edgesWithin(lat - d, lon - d * 1.4, lat + d, lon + d * 1.4)) {
                nodes += g.edgeFrom[e]; nodes += g.edgeTo[e]
            }
            for (v in nodes) {
                val dx = proj.x(g.nodeLon[v])
                val dy = proj.y(g.nodeLat[v])
                if (abs(dx) > 80 || abs(dy) > 80 || g.degree(v) < 3) continue
                best = min(best, sqrt(dx * dx + dy * dy))
            }
            if (best.isFinite()) jm = best
        }
        // Belirsiz GPS yüzeyi tek başına bir ışığı silmez; belirgin/elle girilmiş kaldırım dışlanır.
        return RoadInfo(
            onRoad = hit != null && hit.dist <= 15 && surface != StopSurface.SIDEWALK, surface = surface,
            junctionM = jm, known = known, lightLat = llat, lightLon = llon, lightM = lightM,
            maxLightM = lightMaxM, mall = mall, roadside = roadside, manualSidewalk = manual, footM = foot?.dist,
        )
    }
}
