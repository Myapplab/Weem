package com.kuryelog.logic.map.surface

/**
 * Y14 hız bantları (YolGo `calibration/speed_band.dart`, PR #23). Kalibrasyon referansları 15/20/25/30 km/s; ortak sınırlar üst banda gider.
 * Referans aralığı (12,5–32,5 km/s) dışı ölçüm kendi bandında kalır ([roadMeasurementBand]).
 */
object SpeedBand {
    private val REFERENCE = intArrayOf(15, 20, 25, 30)

    fun calibration(kmh: Double?): Int? {
        if (kmh == null || !kmh.isFinite() || kmh < 12.5 || kmh > 32.5) return null
        return REFERENCE[Math.floor((kmh - 12.5) / 5).toInt().coerceIn(0, 3)]
    }

    /** 8 km/s altı yok; referans dışı hızlar 5'in katına yuvarlanır (yalnız açık yedek olarak kullanılır, E10 uyarır). */
    fun road(kmh: Double): Int? = calibration(kmh) ?: if (kmh.isFinite() && kmh >= 8) (Math.floor((kmh + 2.5) / 5) * 5).toInt() else null
}
