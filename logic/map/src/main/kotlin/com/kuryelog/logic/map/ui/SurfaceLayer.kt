package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.SurfaceSummary

/** Haritada bir ölçülmüş yüzey çizgisi: [rgb] yeşil/turuncu/kırmızı, [alpha] kesim kesinse 0,8 değilse 0,5, kesinse düz değilse kesikli. */
class SurfaceLine(val key: String, val points: List<GeoPoint>, val rgb: Int, val alpha: Double, val width: Double, val dashed: Boolean)

/**
 * "Haritada yüzey renkleri" katmanı (YolGo `map_view.dart` `surfaceLines`/`surfaceColor`): yalnız sensörle ölçülmüş 50 m kesimler (tahmin ya da bütün cadde
 * değil). Her özet kendi yolunun (way) en yakın kenarına oturtulur ve ±yarım kesim çizilir; kalibre ve güvenli (güven ≥ 0,3) kesim düz ve kalın, öbürleri kesikli.
 */
object SurfaceLayerRules {
    const val MIN_CONFIDENCE = 0.05

    /** Kesim puanı (0–100): ≥75 yeşil, ≥50 turuncu, altı kırmızı. */
    fun rgb(score: Double): Int = if (score >= 75) 0x2EBD59 else if (score >= 50) 0xFF9800 else 0xE53935

    fun lines(g: RoadGraph, summaries: List<SurfaceSummary>, show: Boolean, minConfidence: Double = MIN_CONFIDENCE): List<SurfaceLine> {
        if (!show) return emptyList()
        val out = ArrayList<SurfaceLine>()
        for (s in summaries) {
            if (s.meters < 10 || s.confidence < minConfidence) continue
            val hit = g.nearestEdge(s.lat, s.lon, maxDist = 30.0, accept = { e -> g.osmIdOf(e) == s.way }) ?: continue
            val len = g.edgeLen[hit.edge].toDouble()
            if (len <= 0) continue
            val half = (s.segmentLength / 2) / len
            val f0 = (hit.fraction - half).coerceIn(0.0, 1.0)
            val f1 = (hit.fraction + half).coerceIn(0.0, 1.0)
            val pts = g.geometry(hit.edge, f0, f1).map { GeoPoint(it.lat, it.lon) }
            if (pts.size < 2) continue
            val sure = s.calibrated && s.confidence >= .3
            out += SurfaceLine(s.key, pts, rgb(s.score), if (sure) 0.8 else 0.5, if (sure) 6.0 else 4.0, dashed = !sure)
        }
        return out
    }
}
