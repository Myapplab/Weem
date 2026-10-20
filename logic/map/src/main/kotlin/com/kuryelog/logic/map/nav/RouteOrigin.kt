package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint

/**
 * Rota başlangıcı (MAP.md 7.6, `LocationService.routeOrigin`): [last] ve Android'in bilinen son konumu [known] arasından en yenisi;
 * doğruluk 0–150 m, geçerli ve (0,0) değil, yaş −5 sn…3 dk. Hiçbiri uygun değilse null.
 */
object RouteOrigin {
    fun routeOrigin(last: NavFix?, known: NavFix?, nowMs: Long, maxAgeMs: Long = 3 * 60_000L, maxAccuracyM: Double = 150.0): GeoPoint? {
        var best: NavFix? = null
        for (p in listOf(last, known)) {
            if (p == null || !p.accuracyM.isFinite() || p.accuracyM < 0 || p.accuracyM > maxAccuracyM) continue
            if (!p.lat.isFinite() || !p.lon.isFinite() || kotlin.math.abs(p.lat) > 90 || kotlin.math.abs(p.lon) > 180) continue
            if (p.lat == 0.0 && p.lon == 0.0) continue
            val dt = nowMs - p.atMs
            if (dt < -5000 || dt > maxAgeMs) continue
            if (best == null || p.atMs > best.atMs) best = p
        }
        return best?.let { GeoPoint(it.lat, it.lon) }
    }
}
