package com.kuryelog.logic.map.nav

/** Varış odağında kamera: ben + hedef aynı kareye, merkez orta nokta. */
data class FocusCamera(val lat: Double, val lon: Double, val zoom: Double)

/** `lib/nav/arrival_focus.dart` (MAP.md 7.10): kalan ≤100 m'de girer, >150 m'de çıkar (histerezis). İskelet (A6.2b). */
object ArrivalFocus {
    const val FOCUS_MIN_ZOOM = 18.0
    const val FOCUS_MAX_ZOOM = 19.0

    /** Kalan bilinmiyorsa ([remainingM] null) durum değişmez; rota kalkınca ya da varılınca kapanır. */
    fun next(current: Boolean, hasRoute: Boolean, arrived: Boolean, remainingM: Double?): Boolean = TODO("A6.6")

    /** İki nokta [viewportPx] kısa kenarın %70'ine sığacak zoom, [FOCUS_MIN_ZOOM, FOCUS_MAX_ZOOM] aralığına kıstırılır. */
    fun focusCamera(meLat: Double, meLon: Double, destLat: Double, destLon: Double, viewportPx: Int): FocusCamera = TODO("A6.6")
}

/** Dart `arrivalFocusProvider`: rota, varış ve kalan yoldan türeyen açık/kapalı durumu tutar. */
class ArrivalFocusTracker {
    val on: Boolean get() = TODO("A6.6")

    fun update(hasRoute: Boolean, arrived: Boolean, remainingM: Double?): Boolean = TODO("A6.6")
}
