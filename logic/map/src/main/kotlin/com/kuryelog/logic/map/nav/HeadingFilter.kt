package com.kuryelog.logic.map.nav

/** Dairesel açı filtresi (`heading_filter.dart`; MAP.md 7.6): 359→1 geçişini 358 derecelik dönüş saymaz. İskelet (A6.2b). */
class HeadingFilter {
    var value: Double? = null
        private set

    /** Bilinmeyen (null, NaN, <0, ≥360) yönü uydurmaz: null döner ve değeri sıfırlar. */
    fun update(heading: Double?, weight: Double = 0.4): Double? = TODO("A6.6")
}
