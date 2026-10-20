package com.kuryelog.logic.map.nav

/** Dairesel açı filtresi (`heading_filter.dart`; MAP.md 7.6): 359→1 geçişini 358 derecelik dönüş saymaz. */
class HeadingFilter {
    var value: Double? = null
        private set

    /** Bilinmeyen (null, NaN, <0, ≥360) yönü uydurmaz: null döner ve değeri sıfırlar. */
    fun update(heading: Double?, weight: Double = 0.4): Double? {
        if (heading == null || !heading.isFinite() || heading < 0 || heading >= 360) {
            value = null
            return null
        }
        val old = value
        value = if (old == null) heading else (old + ((heading - old + 540) % 360 - 180) * weight + 360) % 360
        return value
    }
}
