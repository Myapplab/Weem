package com.kuryelog.logic.map.ride

import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs
import kotlinx.coroutines.flow.MutableStateFlow
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
    val setAtMs: Long? = null,
) {
    val live: Boolean get() = livePercent != null
    val percent: Double?
        get() {
            livePercent?.let { return it }
            val s = setPercent ?: return null
            return Geo.clampD(s - consumedWh / capacityWh * 100, 0.0, 100.0)
        }
    val remainingWh: Double? get() = percent?.let { capacityWh * it / 100 }
}

/** GPS'ten enerji harcama tahmini (`EnergyOdometer`, MAP.md 10.3): duruş titremesi ve atlamalar sayılmaz, tırmanış yalnız aynı kaynaktan. */
class EnergyOdometer {
    private var anchor: GeoPoint? = null
    private var anchorAtMs = 0L
    private var winDist = 0.0
    private var winAlt: AltitudeSample? = null

    fun reset() {
        anchor = null
        anchorAtMs = 0L
        winDist = 0.0
        winAlt = null
    }

    /** Pencere kapanınca harcanan Wh, değilse 0. [speedMps] bilinmiyorsa negatif. */
    fun feed(
        point: GeoPoint,
        atMs: Long,
        accuracy: Double,
        speedMps: Double,
        flatWhPerM: Double,
        climbWhPerM: Double,
        altitude: (() -> AltitudeSample?)? = null,
    ): Double {
        if (!accuracy.isFinite() || accuracy < 0 || accuracy > 25 || !point.lat.isFinite() || !point.lon.isFinite()) return 0.0
        val from = anchor
        if (from == null) {
            anchor = point
            anchorAtMs = atMs
            winDist = 0.0
            winAlt = altitude?.invoke()
            return 0.0
        }
        val dt = (atMs - anchorAtMs) / 1000.0
        if (dt <= 0) return 0.0
        val d = from.distanceTo(point)
        if (dt > 30 || d / dt > 16) {
            // Sinyal kesintisi/sıçraması: aradaki düz çizgi kat edilen yol değildir.
            anchor = point
            anchorAtMs = atMs
            return 0.0
        }
        if (speedMps >= 0 && speedMps * 3.6 < MIN_MOVING_KMH) {
            // Duruyor: titreşim yol sayılmaz, çapa güncel konuma taşınır.
            anchor = point
            anchorAtMs = atMs
            return 0.0
        }
        // Yavaş hareket birikir; GPS hatasından küçük adımlar sayılmaz.
        if (d < 3 || d < accuracy * 0.35) return 0.0
        anchor = point
        anchorAtMs = atMs
        winDist += d
        if (winDist < WINDOW_M) return 0.0
        val alt = altitude?.invoke()
        val start = winAlt
        // Kaynak değiştiyse (barometre ↔ DEM) iki değerin farkı tırmanış değildir.
        val climb = if (alt != null && start != null && alt.source == start.source && alt.meters > start.meters) alt.meters - start.meters else 0.0
        val wh = flatWhPerM * winDist + climbWhPerM * climb
        winDist = 0.0
        winAlt = alt
        return wh
    }

    companion object {
        /** Tırmanış bu uzunluktaki pencerelerle ölçülür. */
        const val WINDOW_M = 30.0

        /** Altı: duruyor (titreşim). */
        const val MIN_MOVING_KMH = 2.5
    }
}

/**
 * Saf batarya tahmin durum makinesi (MAP.md 10.3, 10.13): girdiler canlı scooter değeri, fix + yükseklik; çapa değişince `battery.setPct`,
 * `battery.consumed`, `battery.setAt` tercihleri (en çok 30 sn'de bir) yazılır, açılışta okunur. Elle % girişi yok (1.7.0'da kalktı).
 */
class BatteryEstimator(private val prefs: MapPrefs, capacityWh: Double, private val nowMs: () -> Long) {
    private val odometer = EnergyOdometer()
    private var lastSaveMs: Long? = null
    private val _state = MutableStateFlow(
        BatteryState(
            setPercent = prefs.getDouble(K_PCT),
            consumedWh = prefs.getDouble(K_WH) ?: 0.0,
            capacityWh = capacityWh,
            setAtMs = prefs.getLong(K_AT),
        ),
    )
    val state: StateFlow<BatteryState> get() = _state

    /** Taze canlı değer (yoksa null): iç % varsa çapa olur; bağlantı koparsa tahmin son canlı %'den sürer. */
    @Synchronized
    fun onLive(live: ScooterLive?) {
        val fresh = live?.takeIf { it.fresh }
        val internal = fresh?.scooter?.internalPct
        val external = fresh?.scooter?.externalPct
        val s = _state.value
        if (s.livePercent == internal && s.liveExternalPercent == external) return
        if (internal == null) {
            _state.value = s.copy(livePercent = null, liveExternalPercent = external)
            return
        }
        // Canlı değer tahminin çapası olur: bağlantı koparsa tahmin son canlı %'den sürer.
        odometer.reset()
        _state.value = s.copy(setPercent = internal, consumedWh = 0.0, setAtMs = nowMs(), livePercent = internal, liveExternalPercent = external)
        save()
    }

    @Synchronized
    fun onFix(point: GeoPoint, atMs: Long, accuracy: Double, speedMps: Double, flatWhPerM: Double, climbWhPerM: Double, altitude: AltitudeSample?) {
        // Canlıyken tahmin yürütülmez (değer scooter'dan gelir).
        val s = _state.value
        if (s.setPercent == null || s.live) return
        val wh = odometer.feed(point, atMs, accuracy, speedMps, flatWhPerM, climbWhPerM) { altitude }
        if (wh <= 0) return
        _state.value = s.copy(consumedWh = s.consumedWh + wh)
        save()
    }

    private fun save() {
        val now = nowMs()
        val last = lastSaveMs
        if (last != null && now - last < SAVE_EVERY_MS) return
        lastSaveMs = now
        val s = _state.value
        s.setPercent?.let { prefs.setDouble(K_PCT, it) }
        prefs.setDouble(K_WH, s.consumedWh)
        s.setAtMs?.let { prefs.setLong(K_AT, it) }
    }

    companion object {
        const val K_PCT = "battery.setPct"
        const val K_WH = "battery.consumed"
        const val K_AT = "battery.setAt"
        private const val SAVE_EVERY_MS = 30_000L
    }
}
