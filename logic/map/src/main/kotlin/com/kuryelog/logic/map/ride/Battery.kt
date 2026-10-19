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
) {
    val live: Boolean get() = livePercent != null
    val percent: Double? get() = livePercent ?: setPercent?.let { Geo.clampD(it - consumedWh / capacityWh * 100, 0.0, 100.0) }
    val remainingWh: Double? get() = percent?.let { capacityWh * it / 100 }
}

/**
 * GPS'ten enerji harcama tahmini (`EnergyOdometer`, MAP.md 10.3): duruş titremesi ve atlamalar sayılmaz, tırmanış yalnız aynı kaynaktan.
 * Vardiya kilometresiyle aynı süzgeçler; tırmanış 30 m'lik pencerelerle ölçülür.
 */
class EnergyOdometer {
    private var anchor: GeoPoint? = null
    private var anchorAtMs = 0L
    private var winDist = 0.0
    private var winAlt: AltitudeSample? = null

    fun reset() {
        anchor = null
        anchorAtMs = 0
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
        if (!accuracy.isFinite() || accuracy < 0 || accuracy > MAX_ACCURACY_M || !point.lat.isFinite() || !point.lon.isFinite()) return 0.0
        val a = anchor
        if (a == null) {
            anchor = point
            anchorAtMs = atMs
            winDist = 0.0
            winAlt = altitude?.invoke()
            return 0.0
        }
        val dt = (atMs - anchorAtMs) / 1000.0
        if (dt <= 0) return 0.0
        val d = Geo.haversine(a.lat, a.lon, point.lat, point.lon)
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
        const val WINDOW_M = 30.0
        const val MIN_MOVING_KMH = 2.5
        const val MAX_ACCURACY_M = 25.0
    }
}

/**
 * Saf batarya tahmin durum makinesi (MAP.md 10.3, 10.13): girdiler canlı scooter değeri, fix + yükseklik; çapa değişince `battery.setPct`,
 * `battery.consumed`, `battery.setAt` tercihleri (en çok 30 sn'de bir) yazılır, açılışta okunur. `battery_set`/`setPercent` yazılmaz (ölü yol, 1.7.0).
 */
class BatteryEstimator(private val prefs: MapPrefs, private val capacityWh: Double, private val nowMs: () -> Long) {
    private val odometer = EnergyOdometer()
    private var lastSaveMs = Long.MIN_VALUE
    private var dirty = false
    private var setAtMs: Long? = prefs.getLong(KEY_AT)

    private val _state = MutableStateFlow(
        BatteryState(setPercent = prefs.getDouble(KEY_PCT), consumedWh = prefs.getDouble(KEY_WH) ?: 0.0, capacityWh = capacityWh),
    )
    val state: StateFlow<BatteryState> get() = _state

    /** Taze canlı değer (yoksa null): iç % varsa çapa olur; bağlantı koparsa tahmin son canlı %'den sürer. */
    fun onLive(live: ScooterLive?) {
        val internal = live?.scooter?.internalPct
        val external = live?.scooter?.externalPct
        val cur = _state.value
        if (cur.livePercent == internal && cur.liveExternalPercent == external) return
        if (internal == null) {
            _state.value = cur.copy(livePercent = null, liveExternalPercent = external)
            return
        }
        // Canlı değer tahminin çapası olur: bağlantı koparsa tahmin son canlı %'den sürer.
        odometer.reset()
        setAtMs = nowMs()
        _state.value = BatteryState(setPercent = internal, consumedWh = 0.0, capacityWh = capacityWh, livePercent = internal, liveExternalPercent = external)
        dirty = true
        save()
    }

    fun onFix(point: GeoPoint, atMs: Long, accuracy: Double, speedMps: Double, flatWhPerM: Double, climbWhPerM: Double, altitude: AltitudeSample?) {
        val cur = _state.value
        // Canlıyken tahmin yürütülmez (değer scooter'dan gelir).
        if (cur.setPercent == null || cur.live) return
        val wh = odometer.feed(point, atMs, accuracy, speedMps, flatWhPerM, climbWhPerM) { altitude }
        if (wh <= 0) {
            save() // kısıtlama yüzünden bekleyen kayıt varsa süre dolunca yazılır
            return
        }
        _state.value = cur.copy(consumedWh = cur.consumedWh + wh)
        dirty = true
        save()
    }

    /** Bekleyen değişikliği kısıtlamadan yazar (uygulama kapanırken). */
    fun flush() {
        dirty = true
        save(force = true)
    }

    private fun save(force: Boolean = false) {
        if (!dirty) return
        val now = nowMs()
        if (!force && lastSaveMs != Long.MIN_VALUE && now - lastSaveMs < SAVE_EVERY_MS) return
        lastSaveMs = now
        dirty = false
        val s = _state.value
        s.setPercent?.let { prefs.setDouble(KEY_PCT, it) }
        prefs.setDouble(KEY_WH, s.consumedWh)
        setAtMs?.let { prefs.setLong(KEY_AT, it) }
    }

    companion object {
        const val KEY_PCT = "battery.setPct"
        const val KEY_WH = "battery.consumed"
        const val KEY_AT = "battery.setAt"
        const val SAVE_EVERY_MS = 30_000L
    }
}
