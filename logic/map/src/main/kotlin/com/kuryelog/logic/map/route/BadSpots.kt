package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.Passability
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark

/** Bir kenar üzerindeki kötü nokta aralığı (kenar başından kesir olarak). */
class SpotSpan(val edge: Int, val f0: Double, val f1: Double, val confirmed: Boolean) {
    fun overlap(a: Double, b: Double): Double {
        val lo = if (a < b) a else b
        val hi = if (a < b) b else a
        val o = (if (hi < f1) hi else f1) - (if (lo > f0) lo else f0)
        return if (o > 0) o else 0.0
    }
}

/**
 * Kötü nokta (Ses− tek basış) yalnız ±25 m etkiler — YolGo `lib/surface/bad_spots.dart` (MAP.md 3.5). Aynı yolda 30 m içinde
 * farklı Bakü günlerinden gelen işaretler noktayı doğrular: tek işaret yalnız cezalandırır, doğrulanmış nokta "kötü yollardan
 * kaçın" denemesinde o kesimi dışlar.
 */
object BadSpots {
    const val RADIUS_M = 25.0
    const val CONFIRM_M = 30.0

    private val ROUGH = setOf(
        SurfaceLabel.ROUGH, SurfaceLabel.POTHOLE, SurfaceLabel.RAISED_ASPHALT, SurfaceLabel.DIRT, SurfaceLabel.GRAVEL, SurfaceLabel.OBSTACLE,
    )

    fun isBadSpot(m: SurfaceMark): Boolean = !m.removed && m.source == "user" && m.access != Passability.CLOSED && m.label in ROUGH

    /** Etkin işaretlerin aralıkları (işaret sırasıyla); yolda (≤ 25 m) eşleşmeyen işaret atlanır. */
    fun spans(g: RoadGraph, marks: Iterable<SurfaceMark>): List<SpotSpan> {
        val active = marks.filter(::isBadSpot)
        val out = ArrayList<SpotSpan>()
        for (m in active) {
            val hit = g.nearestEdge(m.lat, m.lon, maxDist = 25.0, accept = { e -> g.osmIdOf(e) == m.way }) ?: continue
            val len = g.edgeLen[hit.edge].toDouble()
            if (len <= 0) continue
            val days = HashSet<Long>()
            for (o in active) {
                if (o.way == m.way && Geo.haversine(o.lat, o.lon, m.lat, m.lon) <= CONFIRM_M) days += bakuDay(o.at)
            }
            val r = RADIUS_M / len
            out += SpotSpan(hit.edge, (hit.fraction - r).coerceIn(0.0, 1.0), (hit.fraction + r).coerceIn(0.0, 1.0), confirmed = days.size >= 2)
        }
        return out
    }

    /** Bakü takvim günü (sabit UTC+4; telefon diliminden bağımsız, YolGo `bakuDay`): epoch gün numarası. */
    private fun bakuDay(atMs: Long): Long = Math.floorDiv(atMs + 4 * 3_600_000L, 86_400_000L)
}
