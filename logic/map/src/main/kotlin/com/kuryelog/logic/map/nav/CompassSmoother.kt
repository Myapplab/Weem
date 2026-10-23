package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.Geo

/**
 * Pusula yumuşatması (`compass.dart` `CompassController.update`; MAP.md 7.6): ilk değer olduğu gibi, sonra `eski + fark·0,35` (360 sarması).
 * Ham değeri `:map` `CompassSensor` verir (telefon dikse arka kameranın, yataysa ekranın üst kenarının yönü; manyetik sapma eklenmiş, gerçek kuzey).
 */
class CompassSmoother(private val weight: Double = WEIGHT) {
    var value: Double? = null
        private set

    /** Sayı olmayan okuma değeri değiştirmez. */
    fun update(deg: Double): Double? {
        if (!deg.isFinite()) return value
        val old = value
        value = if (old == null) Geo.norm360(deg) else Geo.norm360(old + Geo.angleDelta(deg, old) * weight)
        return value
    }

    fun reset() {
        value = null
    }

    companion object {
        const val WEIGHT = 0.35
    }
}
