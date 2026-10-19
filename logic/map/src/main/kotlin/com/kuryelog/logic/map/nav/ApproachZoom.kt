package com.kuryelog.logic.map.nav

/** Elle dokunmadan sonra kameranın dönüşü (MAP.md 7.10). */
enum class Refollow { NONE, FOLLOW_AUTO_ZOOM, FOLLOW_KEEP_ZOOM }

/** `lib/nav/approach_zoom.dart` (MAP.md 7.10): hız tabanı, hedefe ~1 km kala ~17,5'e yumuşak (smoothstep) yaklaşma, elle dokunma sonrası takip. */
object ApproachZoom {
    const val APPROACH_START_M = 1000.0
    const val APPROACH_END_M = 100.0
    const val APPROACH_MAX_ZOOM = 17.5
    const val AUTO_ZOOM_PAUSE_S = 10

    /** `<6 km/s → 16.6`, `<16 → 16.2`, `<24 → 15.9`, aksi `15.6`. */
    fun speedZoom(kmh: Double): Double = if (kmh < 6) 16.6 else if (kmh < 16) 16.2 else if (kmh < 24) 15.9 else 15.6

    /** Kalan ≥1 000 m ya da bilinmiyorsa taban; ≤100 m'de [APPROACH_MAX_ZOOM]; arası smoothstep. */
    fun zoomFor(speedKmh: Double, remainingM: Double?): Double {
        val base = speedZoom(speedKmh)
        val d = remainingM
        if (d == null || !d.isFinite() || d >= APPROACH_START_M) return base
        val t = (APPROACH_START_M - d.coerceIn(APPROACH_END_M, APPROACH_START_M)) / (APPROACH_START_M - APPROACH_END_M)
        val eased = t * t * (3 - 2 * t) // smoothstep: uçlarda yavaş
        return base + (APPROACH_MAX_ZOOM - base) * eased
    }

    /** Rota açıkken [AUTO_ZOOM_PAUSE_S] sn sonra takip + otomatik zoom; rota yokken hareketteyken 8 sn sonra takip (ölçek korunur); duruyorken hiç. */
    fun refollowAfterGesture(route: Boolean, moving: Boolean, sinceMs: Long): Refollow {
        val s = sinceMs / 1000
        if (route) return if (s >= AUTO_ZOOM_PAUSE_S) Refollow.FOLLOW_AUTO_ZOOM else Refollow.NONE
        return if (moving && s >= 8) Refollow.FOLLOW_KEEP_ZOOM else Refollow.NONE
    }
}
