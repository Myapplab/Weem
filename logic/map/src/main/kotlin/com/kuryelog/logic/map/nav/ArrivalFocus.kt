package com.kuryelog.logic.map.nav

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Varış odağında kamera: ben + hedef aynı kareye, merkez orta nokta. */
data class FocusCamera(val lat: Double, val lon: Double, val zoom: Double)

/** `lib/nav/arrival_focus.dart` (MAP.md 7.10): kalan ≤100 m'de girer, >150 m'de çıkar (histerezis). */
object ArrivalFocus {
    const val ENTER_M = 100.0
    const val EXIT_M = 150.0
    const val FOCUS_MIN_ZOOM = 18.0
    const val FOCUS_MAX_ZOOM = 19.0

    /** Kalan bilinmiyorsa ([remainingM] null) durum değişmez; rota kalkınca ya da varılınca kapanır. */
    fun next(current: Boolean, hasRoute: Boolean, arrived: Boolean, remainingM: Double?): Boolean {
        if (!hasRoute || arrived) return false
        val d = remainingM
        if (d == null || !d.isFinite()) return current
        return if (current) d <= EXIT_M else d <= ENTER_M
    }

    /** İki nokta [viewportPx] kısa kenarın %70'ine sığacak zoom, [FOCUS_MIN_ZOOM, FOCUS_MAX_ZOOM] aralığına kıstırılır. */
    fun focusCamera(meLat: Double, meLon: Double, destLat: Double, destLon: Double, viewportPx: Int): FocusCamera {
        val lat = (meLat + destLat) / 2
        val lon = (meLon + destLon) / 2
        val dy = (destLat - meLat) * 111195.08
        val dx = (destLon - meLon) * 111195.08 * cos(lat * PI / 180)
        val span = max(sqrt(dx * dx + dy * dy), 1.0)
        val px = max(viewportPx * .7, 1.0)
        val z = ln(156543.03392 * cos(lat * PI / 180) * px / span) / ln(2.0)
        return FocusCamera(lat, lon, z.coerceIn(FOCUS_MIN_ZOOM, FOCUS_MAX_ZOOM))
    }
}

/** Dart `arrivalFocusProvider`: rota, varış ve kalan yoldan türeyen açık/kapalı durumu tutar. */
class ArrivalFocusTracker {
    var on: Boolean = false
        private set

    /** [remainingM]: rota üstündeyse ölçülen kalan; rota dışında ya da ölçüm yoksa null (durum korunur). */
    fun update(hasRoute: Boolean, arrived: Boolean, remainingM: Double?): Boolean {
        on = ArrivalFocus.next(on, hasRoute, arrived, remainingM)
        return on
    }
}
