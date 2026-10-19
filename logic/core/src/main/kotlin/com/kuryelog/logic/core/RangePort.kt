package com.kuryelog.logic.core

/**
 * N5 menzil girdileri (MAP.md 10.6; `:map` uygular, `:economics` tüketir). Batarya: canlı BLE yüzdesi (`ble_fresh`, ≤15 sn taze) ya da Y9 tahmini (`estimator`);
 * [wh] kalan enerji (kapasite × yüzde). Veri yoksa `null`; hiçbir şey uydurulmaz.
 */
data class RangeBattery(val pct: Double, val wh: Double, val source: String, val atMs: Long) {
    companion object {
        const val BLE_FRESH = "ble_fresh"
        const val ESTIMATOR = "estimator"
    }
}

/** Sürüş rotası özeti (rota motoru, `RideMode.BATTERY`): [km] sürüş, [minutes] süre, [wh] tahmini enerji. */
data class RangeLeg(val km: Double, val minutes: Double, val wh: Double)

/** Menzil kapısı (`:map` → `:economics`). Harita ya da ev yoksa [rideTo] `null`. */
interface RangePort {
    fun battery(): RangeBattery?

    /** Rota motoruyla `from` → `to` sürüş rotası; hesaplanamazsa (harita yok, rota yok, zaman aşımı) null. */
    suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg?

    object NONE : RangePort {
        override fun battery(): RangeBattery? = null
        override suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg? = null
    }
}

/**
 * Ekonomi v2 motorunun batarya kaçışı kuralını besleyen girdiler (MAP.md 10.6): şarj yeri = ev. Yeni hesap yazılmaz; yalnız `EconomicsV2`'nin var olan girdi alanları doldurulur.
 * Ev, bırakış noktası ya da batarya yoksa ilgili alan boş kalır (motor `BATTERY_UNKNOWN` / `CHARGER_DISTANCE_UNKNOWN` uyarısını verir).
 */
data class RangeInputs(
    /** `state.scooter.blePct`/`bleAtMs` (kaynak `ble_fresh`). */
    val blePct: Double? = null,
    val bleAtMs: Long? = null,
    /** `state.scooter.estimatorWh` (kaynak `estimator`). */
    val estimatorWh: Double? = null,
    /** Bırakış → ev sürüş km (`offer.destToChargerKm`) ve dk (`offer.destToHomeMin`). */
    val destToChargerKm: Double? = null,
    val destToHomeMin: Double? = null,
    /** Şimdiki konum → ev dk (`state.hereToHomeMin`). */
    val hereToHomeMin: Double? = null,
) {
    val hasScooter: Boolean get() = blePct != null || estimatorWh != null

    companion object {
        /**
         * [home], [dropoff] ve [here] `lat to lon`; her biri yoksa null. Yollar bağımsız hesaplanır: biri başarısız olursa yalnız onun alanı boş kalır.
         */
        suspend fun of(range: RangePort, home: Pair<Double, Double>?, dropoff: Pair<Double, Double>?, here: Pair<Double, Double>?): RangeInputs {
            val battery = range.battery()
            val toHome = if (home != null && dropoff != null) range.rideTo(dropoff.first, dropoff.second, home.first, home.second) else null
            val fromHere = if (home != null && here != null) range.rideTo(here.first, here.second, home.first, home.second) else null
            return RangeInputs(
                blePct = battery?.takeIf { it.source == RangeBattery.BLE_FRESH }?.pct,
                bleAtMs = battery?.takeIf { it.source == RangeBattery.BLE_FRESH }?.atMs,
                estimatorWh = battery?.takeIf { it.source != RangeBattery.BLE_FRESH }?.wh,
                destToChargerKm = toHome?.km,
                destToHomeMin = toHome?.minutes,
                hereToHomeMin = fromHere?.minutes,
            )
        }
    }
}
