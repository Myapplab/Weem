package com.kuryelog.logic.map.ride

import com.kuryelog.logic.core.RangeBattery
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.map.geo.GeoPoint

/**
 * N5 menzil girdileri (MAP.md 10.6): ekonomi v2'nin beklediği ama bugün hiç doldurulmayan alanlar. Yeni hesap yok; yalnız [RangePort] sonuçları v2 alan adlarına çevrilir.
 * Ev, bırakış noktası ya da batarya yoksa ilgili alan null kalır (v2 `BATTERY_UNKNOWN` / `CHARGER_DISTANCE_UNKNOWN` danışması sürer).
 *
 * @property blePct taze scooter %'si (`source == ble_fresh`), [bleAtMs] ölçüm anı
 * @property estimatorWh Y9 tahmininden kalan Wh (`source == estimator`)
 * @property destToChargerKm bırakış → şarj yeri (ev) sürüş km; [destToHomeMin] aynı bacağın süresi; [hereToHomeMin] konum → ev
 */
data class RangeInputs(
    val blePct: Double? = null,
    val bleAtMs: Long? = null,
    val estimatorWh: Double? = null,
    val destToChargerKm: Double? = null,
    val destToHomeMin: Double? = null,
    val hereToHomeMin: Double? = null,
) {
    /** `state.scooter` nesnesinin alanları (`EconomicsV2.availableWh`): taze % varsa `blePct` + `bleAtMs`, yoksa `estimatorWh`. */
    val hasScooter: Boolean get() = blePct != null || estimatorWh != null

    companion object {
        /** Bırakış ([dropoff]) ve konum ([here]) için girdileri toplar; şarj yeri = [home]. */
        suspend fun build(port: RangePort, here: GeoPoint?, home: GeoPoint?, dropoff: GeoPoint?): RangeInputs {
            val battery = port.battery()
            val toHome = if (dropoff != null && home != null) port.rideTo(dropoff.lat, dropoff.lon, home.lat, home.lon) else null
            val fromHere = if (here != null && home != null) port.rideTo(here.lat, here.lon, home.lat, home.lon) else null
            return of(battery, toHome?.km, toHome?.minutes, fromHere?.minutes)
        }

        fun of(battery: RangeBattery?, destToChargerKm: Double?, destToHomeMin: Double?, hereToHomeMin: Double?): RangeInputs = RangeInputs(
            blePct = battery?.takeIf { it.source == RangeBattery.BLE_FRESH }?.pct,
            bleAtMs = battery?.takeIf { it.source == RangeBattery.BLE_FRESH }?.atMs,
            estimatorWh = battery?.takeIf { it.source == RangeBattery.ESTIMATOR }?.wh,
            destToChargerKm = destToChargerKm?.takeIf { it.isFinite() && it >= 0 },
            destToHomeMin = destToHomeMin?.takeIf { it.isFinite() && it >= 0 },
            hereToHomeMin = hereToHomeMin?.takeIf { it.isFinite() && it >= 0 },
        )
    }
}
