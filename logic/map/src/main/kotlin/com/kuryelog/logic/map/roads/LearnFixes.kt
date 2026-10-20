package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.MatchInfo
import com.kuryelog.logic.map.nav.NavFix

/** Öğrenme girdisi (MAP.md 9.3.1; `nav_controller.dart:781-804`): eşleşmeden sonra her fix. */
object LearnFixes {
    const val MATCH_MAX_M = 20.0
    const val STOP_NEAR_M = 60.0
    const val ARRIVED_NEAR_M = 80.0

    /**
     * [info] eşleşme (≤ 20 m ise sokak, aksi `null`/""), [approved] Y8 onaylı sokaklar, [stops] durak noktaları, [reached] varılan nokta,
     * [atPlace] sipariş aşaması restoranda/müşteride (A6.8 `OrderEvents`; yoksa false). `vib` 1.0'da hep 0 (Y13).
     */
    fun of(fix: NavFix, info: MatchInfo?, approved: Set<Long>, stops: List<GeoPoint>, reached: GeoPoint?, atPlace: Boolean = false): LearnFix {
        val matched = info?.takeIf { it.dist <= MATCH_MAX_M }
        val here = GeoPoint(fix.lat, fix.lon)
        val exclude = atPlace || stops.any { it.distanceTo(here) < STOP_NEAR_M } || (reached != null && reached.distanceTo(here) < ARRIVED_NEAR_M)
        return LearnFix(
            tMs = fix.atMs, lat = fix.lat, lon = fix.lon, kmh = (fix.speedMps ?: 0.0).let { if (it < 0) 0.0 else it * 3.6 },
            accuracy = fix.accuracyM, way = matched?.osmId, name = matched?.name.orEmpty(), wrongWay = info?.wrongWay == true,
            approved = matched != null && matched.osmId in approved, excludeStops = exclude, mainRoad = matched?.cls?.isMainRoad == true,
        )
    }
}
