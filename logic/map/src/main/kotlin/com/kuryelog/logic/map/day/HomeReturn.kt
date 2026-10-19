package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint

/** Eve dönüş ölçümü (Dart `startHomeReturn`, `_finishHomeReturn`, ev varışı; MAP.md 11.13). Ev koordinatı hiçbir olaya/rapora yazılmaz; yalnız mesafe ve süre. */
object HomeReturn {
    const val ARRIVE_M = 35.0
    const val ARRIVE_ACCURACY_M = 20.0
    const val ARRIVE_MAX_MPS = 1.3

    /** Gün açık, süren sipariş yok ve ölçüm sürmüyorsa başlar; olay `home_return_start`. */
    fun start(s: DayState, nowMs: Long): List<DayEvent> {
        if (!s.active || s.current != null || s.homeReturnAtMs != null) return emptyList()
        s.homeReturnAtMs = nowMs
        s.homeStartM = s.distM
        return listOf(DayEvent("home_return_start", nowMs))
    }

    /** Ölçümü bitirir: süre ve mesafe birikir (birden çok eve dönüş toplanır). */
    fun finish(s: DayState, nowMs: Long) {
        val start = s.homeReturnAtMs ?: return
        s.homeTimeS += Math.max(0L, (nowMs - start) / 1000).toDouble()
        s.homeDistM += Math.max(0.0, s.distM - s.homeStartM)
        s.homeReturnAtMs = null
    }

    /** Ölçüm sürerken eve varış: ev var, doğruluk ≤ 20 m, `0 ≤ hız < 1,3 m/sn`, eve ≤ 35 m → ölçümü fix zamanında bitirir, `home_return_arrived` döner. */
    fun arrived(s: DayState, fix: DayFix, home: GeoPoint?): DayEvent? {
        if (s.homeReturnAtMs == null || home == null) return null
        if (fix.accuracy > ARRIVE_ACCURACY_M || fix.speedMps < 0 || fix.speedMps >= ARRIVE_MAX_MPS) return null
        if (Geo.haversine(fix.lat, fix.lon, home.lat, home.lon) > ARRIVE_M) return null
        finish(s, fix.atMs)
        return DayEvent("home_return_arrived", fix.atMs)
    }
}
