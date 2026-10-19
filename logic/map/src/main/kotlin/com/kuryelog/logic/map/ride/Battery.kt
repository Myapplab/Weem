package com.kuryelog.logic.map.ride

import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs
import kotlinx.coroutines.flow.StateFlow

enum class AltitudeSource { BARO, DEM }

/** Yükseklik örneği: kaynak karıştırılırsa fark tırmanış sayılmaz (MAP.md 10.3). */
data class AltitudeSample(val meters: Double, val source: AltitudeSource)

/**
 * Batarya durumu (MAP.md 10.3): [setPercent] çapa %, [consumedWh] çapadan beri tahmini harcama; [livePercent] yalnız taze canlı değerde.
 * `percent = livePercent ?? clamp(setPercent − consumedWh/capacityWh·100, 0, 100)`.
 */
data class BatteryState(
    val setPercent: Double? = null,
    val consumedWh: Double = 0.0,
    val capacityWh: Double = 598.0,
    val livePercent: Double? = null,
    val liveExternalPercent: Double? = null,
) {
    val live: Boolean get() = livePercent != null
    val percent: Double? get() = TODO("A6.7")
    val remainingWh: Double? get() = TODO("A6.7")
}

/** GPS'ten enerji harcama tahmini (`EnergyOdometer`, MAP.md 10.3): duruş titremesi ve atlamalar sayılmaz, tırmanış yalnız aynı kaynaktan. İskelet (A6.2b). */
class EnergyOdometer {
    /** Pencere kapanınca harcanan Wh, değilse 0. [speedMps] bilinmiyorsa negatif. */
    fun feed(
        point: GeoPoint,
        atMs: Long,
        accuracy: Double,
        speedMps: Double,
        flatWhPerM: Double,
        climbWhPerM: Double,
        altitude: (() -> AltitudeSample?)? = null,
    ): Double = TODO("A6.7")

    fun reset(): Unit = TODO("A6.7")
}

/**
 * Saf batarya tahmin durum makinesi (MAP.md 10.3, 10.13): girdiler canlı scooter değeri, fix + yükseklik; çapa değişince `battery.setPct`,
 * `battery.consumed`, `battery.setAt` tercihleri (en çok 30 sn'de bir) yazılır, açılışta okunur.
 */
class BatteryEstimator(private val prefs: MapPrefs, private val capacityWh: Double, private val nowMs: () -> Long) {
    val state: StateFlow<BatteryState> get() = TODO("A6.7")

    /** Taze canlı değer (yoksa null): iç % varsa çapa olur; bağlantı koparsa tahmin son canlı %'den sürer. */
    fun onLive(live: ScooterLive?): Unit = TODO("A6.7")

    fun onFix(point: GeoPoint, atMs: Long, accuracy: Double, speedMps: Double, flatWhPerM: Double, climbWhPerM: Double, altitude: AltitudeSample?): Unit = TODO("A6.7")
}
