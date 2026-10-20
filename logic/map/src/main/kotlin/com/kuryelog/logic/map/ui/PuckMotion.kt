package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.EdgeHit
import com.kuryelog.logic.map.nav.HeadingFilter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** İşaret akışına gelen konum okuması: [speedMps] < 0 bilinmiyor, [headingDeg] 0–360 dışı bilinmiyor. */
data class PuckFix(val lat: Double, val lon: Double, val accuracyM: Double, val speedMps: Double, val headingDeg: Double, val headingAccuracyDeg: Double = 0.0)

/**
 * Bir okumanın sonucu: işaret [from] → [to] arasında [slideMs] boyunca doğrusal kayar ([jump] ise kaymadan geçer). [course] ok ve takip yönü (hareket yoksa null);
 * [cameraHeading] yalnız ilk okumada (kamera yönü bu olur).
 */
data class PuckStep(val from: GeoPoint, val to: GeoPoint, val course: Double?, val slideMs: Long, val jump: Boolean, val turning: Boolean, val cameraHeading: Double?)

/**
 * Konum işaretinin akışı (YolGo `map_view.dart:306-370` `_onFix`; MAP.md 6.4): süzgeç, yön filtresi, kayma süresi, yola oturtma, ileri tahmin ve sıçrama.
 * [snap] graf varken en yakın kenarı verir (`RoadGraph.nearestEdge`, "yol yok" kenarları çağıran süzer); yoksa null.
 */
class PuckMotion(private val snap: ((lat: Double, lon: Double, maxDist: Double, heading: Double, headingTol: Double) -> EdgeHit?)? = null) {
    companion object {
        const val MAX_ACCURACY_M = 65.0
        const val MOVING_MPS = 1.5
        const val TURN_DEG = 25.0
        const val FILTER_ACCURACY_M = 25.0
        const val FILTER_HEADING_ACCURACY_DEG = 45.0
        const val FILTER_WEIGHT_TURNING = 0.85
        const val FILTER_WEIGHT = 0.4
        const val SLIDE_FACTOR = 0.85
        const val SLIDE_MIN_MS = 250L
        const val SLIDE_MAX_MS = 1000L
        const val SNAP_ACCURACY_M = 25.0
        const val SNAP_DIST_TURNING_M = 10.0
        const val SNAP_DIST_M = 15.0
        const val SNAP_TOL_TURNING_DEG = 25.0
        const val SNAP_TOL_DEG = 40.0
        const val ROAD_HEADING_DEG = 30.0
        const val LEAD_S_TURNING = 0.3
        const val LEAD_S = 0.7
        const val LEAD_MAX_M = 15.0
        const val JUMP_M = 120.0
        /** Okumalar arası bilinmiyorsa (ilk okuma) varsayılan aralık. */
        const val DEFAULT_GAP_MS = 1000L

        /** Kayma süresi: iki okuma arasının %85'i, 250–1000 ms. */
        fun slideMs(gapMs: Long): Long = Math.round(gapMs * SLIDE_FACTOR).coerceIn(SLIDE_MIN_MS, SLIDE_MAX_MS)

        /** GPS ~0,7 sn geride kalır: o kadar ileri (dönüşte 0,3 sn), en çok 15 m. */
        fun leadM(speedMps: Double, turning: Boolean): Double = min(speedMps * (if (turning) LEAD_S_TURNING else LEAD_S), LEAD_MAX_M)
    }

    /** Filtrelenmiş işaret yönü (koni için; ok GPS yönünü kullanır). */
    val heading = HeadingFilter()
    var speedMps = 0.0
        private set
    var accuracyM: Double? = null
        private set
    private var prevCourse: Double? = null
    private var lastFixAtMs: Long? = null

    /** [displayed] işaretin şu anki çizim yeri (kayma ortasında olabilir); geçersiz okuma null döner. */
    fun onFix(p: PuckFix, nowMs: Long, displayed: GeoPoint?): PuckStep? {
        if (!p.lat.isFinite() || !p.lon.isFinite() || !p.accuracyM.isFinite() || p.accuracyM > MAX_ACCURACY_M) return null
        speedMps = if (p.speedMps < 0) 0.0 else p.speedMps
        accuracyM = p.accuracyM
        val moving = speedMps >= MOVING_MPS && p.headingDeg.isFinite() && p.headingDeg >= 0 && p.headingDeg < 360
        var lat = p.lat
        var lon = p.lon
        var course: Double? = if (moving) p.headingDeg else null
        val prev = prevCourse
        val turning = moving && prev != null && Geo.angleDiff(prev, p.headingDeg) >= TURN_DEG
        prevCourse = if (moving) p.headingDeg else null
        heading.update(
            if (moving && p.accuracyM <= FILTER_ACCURACY_M && p.headingAccuracyDeg <= FILTER_HEADING_ACCURACY_DEG) p.headingDeg else null,
            if (turning) FILTER_WEIGHT_TURNING else FILTER_WEIGHT,
        )
        val gap = lastFixAtMs?.let { nowMs - it } ?: DEFAULT_GAP_MS
        lastFixAtMs = nowMs
        val slide = slideMs(gap)
        if (snap != null && moving && p.accuracyM <= SNAP_ACCURACY_M) {
            val hit = snap.invoke(lat, lon, if (turning) SNAP_DIST_TURNING_M else SNAP_DIST_M, p.headingDeg, if (turning) SNAP_TOL_TURNING_DEG else SNAP_TOL_DEG)
            if (hit != null) {
                lat = hit.lat
                lon = hit.lon
                // Yolun yönüne yakınsa onu kullan (GPS yönü titrek).
                val b = hit.segBearing
                val back = (b + 180) % 360
                val seg = if (Geo.angleDiff(p.headingDeg, b) <= Geo.angleDiff(p.headingDeg, back)) b else back
                if (Geo.angleDiff(seg, p.headingDeg) < ROAD_HEADING_DEG) course = seg
            }
        }
        if (moving && course != null) {
            val d = leadM(speedMps, turning)
            val r = course * PI / 180
            lat += cos(r) * d / 111320
            lon += sin(r) * d / (111320 * cos(lat * PI / 180))
        }
        val target = GeoPoint(lat, lon)
        if (displayed == null || Geo.haversine(displayed.lat, displayed.lon, lat, lon) > JUMP_M) {
            return PuckStep(target, target, course, slide, jump = true, turning = turning, cameraHeading = if (displayed == null) course else null)
        }
        return PuckStep(displayed, target, course, slide, jump = false, turning = turning, cameraHeading = null)
    }
}
