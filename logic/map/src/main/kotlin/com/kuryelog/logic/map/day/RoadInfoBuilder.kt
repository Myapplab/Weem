package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.LocalProjection
import com.kuryelog.logic.map.graph.PoiSet
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph

/**
 * Duruş noktasının yol bilgisi (Dart `_roadInfo`, MAP.md 11.8; saf): **düzeltilmemiş** harita POI'leri, öğrenilmiş ışıklar, elle kaldırım işaretleri ve (varsa) yol grafı.
 * [lastAccuracy] çağrıda verilir (son kabul edilen fix'in doğruluğu; duruşun kendi fix'i değil). [lightMaxM] `MapSettings.lightMaxM`.
 */
class RoadInfoBuilder(
    private val pois: () -> PoiSet?,
    private val graph: () -> RoadGraph?,
    private val lights: LightsDb,
    private val sidewalks: SidewalkMarks,
    private val lightMaxM: () -> Double = { 5.0 },
) {
    fun at(lat: Double, lon: Double, lastAccuracy: Double): RoadInfo {
        val p = pois()
        val sig = p?.nearest('l', lat, lon, SIGNAL_M)
        var known = lights.lookup(lat, lon)
        val local = lights.nearest(lat, lon, SIGNAL_M)
        var placeLat: Double? = null
        var placeLon: Double? = null
        if (local != null && local.exists) {
            known = true
            placeLat = local.lat; placeLon = local.lon
        } else if (sig != null && known != false) {
            known = lights.lookup(sig.lat, sig.lon) != false
            placeLat = sig.lat; placeLon = sig.lon
        } else if (known == true) {
            val learned = lights.nearest(lat, lon, LightsDb.NEAR_M + 0.0001)
            placeLat = learned?.lat ?: lat
            placeLon = learned?.lon ?: lon
        }
        val lightM = if (placeLat != null && placeLon != null) Geo.haversine(lat, lon, placeLat, placeLon) else null
        val mall = p?.nearest('m', lat, lon, StopSemantics.MALL_RADIUS_M) != null
        val manual = sidewalks.contains(lat, lon)
        val maxM = lightMaxM()
        val g = graph()
        if (g == null) {
            return RoadInfo(
                onRoad = !manual, known = known, lightLat = placeLat, lightLon = placeLon,
                surface = if (manual) StopSurface.SIDEWALK else StopSurface.UNKNOWN,
                lightM = lightM, maxLightM = maxM, mall = mall, manualSidewalk = manual,
            )
        }
        val hit = g.nearestEdge(lat, lon, EDGE_M, { e -> !g.classOf(e).isFootClass && g.classOf(e) != RoadClass.STEPS })
        val foot = g.nearestEdge(lat, lon, EDGE_M, { e -> g.classOf(e).isFootClass })
        val accuracy = lastAccuracy
        val surface = StopSemantics.classifySurface(accuracy, hit?.dist, foot?.dist, manual)
        val roadside = hit != null && StopSemantics.isRoadside(accuracy, hit.dist, g.classOf(hit.edge))
        return RoadInfo(
            onRoad = hit != null && hit.dist <= ON_ROAD_M && surface != StopSurface.SIDEWALK,
            junctionM = if (hit != null) junctionDistance(g, lat, lon) else null,
            known = known, lightLat = placeLat, lightLon = placeLon, surface = surface, lightM = lightM, maxLightM = maxM,
            mall = mall, roadside = roadside, manualSidewalk = manual, footM = foot?.dist,
        )
    }

    /** ±80 m kutusundaki, derecesi ≥ 3 olan kenar uçlarından en yakını (m); yoksa null. */
    private fun junctionDistance(g: RoadGraph, lat: Double, lon: Double): Double? {
        val d = BOX_M / 111320.0
        val proj = LocalProjection(lat, lon)
        var best: Double? = null
        for (e in g.edgesWithin(lat - d, lon - d * 1.4, lat + d, lon + d * 1.4)) {
            for (n in intArrayOf(g.edgeFrom[e], g.edgeTo[e])) {
                if (g.degree(n) < 3) continue
                val dx = proj.x(g.nodeLon[n])
                val dy = proj.y(g.nodeLat[n])
                if (Math.abs(dx) > BOX_M || Math.abs(dy) > BOX_M) continue
                val m = Math.sqrt(dx * dx + dy * dy)
                if (best == null || m < best) best = m
            }
        }
        return best
    }

    private companion object {
        const val SIGNAL_M = 55.0
        const val EDGE_M = 35.0
        const val ON_ROAD_M = 15.0
        const val BOX_M = 80.0
    }
}
