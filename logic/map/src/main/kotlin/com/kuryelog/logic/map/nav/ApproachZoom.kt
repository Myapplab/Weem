package com.kuryelog.logic.map.nav

/** Elle dokunmadan sonra kameranın dönüşü (MAP.md 7.10). */
enum class Refollow { NONE, FOLLOW_AUTO_ZOOM, FOLLOW_KEEP_ZOOM }

/** `lib/nav/approach_zoom.dart` (MAP.md 7.10): hız tabanı, hedefe ~1 km kala ~17,5'e yumuşak (smoothstep) yaklaşma, elle dokunma sonrası takip. İskelet (A6.2b). */
object ApproachZoom {
    const val APPROACH_MAX_ZOOM = 17.5
    const val AUTO_ZOOM_PAUSE_S = 10

    /** `<6 km/s → 16.6`, `<16 → 16.2`, `<24 → 15.9`, aksi `15.6`. */
    fun speedZoom(kmh: Double): Double = TODO("A6.6")

    /** Kalan ≥1 000 m ya da bilinmiyorsa taban; ≤100 m'de [APPROACH_MAX_ZOOM]; arası smoothstep. */
    fun zoomFor(speedKmh: Double, remainingM: Double?): Double = TODO("A6.6")

    /** Rota açıkken [AUTO_ZOOM_PAUSE_S] sn sonra takip + otomatik zoom; rota yokken hareketteyken 8 sn sonra takip (ölçek korunur); duruyorken hiç. */
    fun refollowAfterGesture(route: Boolean, moving: Boolean, sinceMs: Long): Refollow = TODO("A6.6")
}
