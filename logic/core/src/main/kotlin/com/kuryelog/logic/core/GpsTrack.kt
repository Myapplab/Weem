package com.kuryelog.logic.core

import kotlin.math.max

/**
 * Vardiyada bir GPS fix'i: Android `Location`'ın kullanılan alanları, aynı tiplerle (doğruluk/hız/yön Float; yoksa 0 ve `has*` false),
 * böylece `gps.csv` satırı Rawstats'takiyle aynı yazılır. [elapsedNanos] = `elapsedRealtimeNanos` (monoton), [timeMs] = duvar saati.
 */
data class GpsFix(
    val elapsedNanos: Long,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float = 0f,
    val hasAccuracy: Boolean = true,
    val speed: Float = 0f,
    val hasSpeed: Boolean = false,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
) {
    val elapsedMs: Long get() = elapsedNanos / 1_000_000
    /** Ev kuralının fix'i (RS `HomeLocation.observe`): doğruluk ve hız yalnız varsa. */
    fun homeFix() = HomeShift.Fix(elapsedMs, lat, lon, if (hasAccuracy) accuracy.toDouble() else null, if (hasSpeed) speed.toDouble() else null)
    /** RS `GpsService.kt:214-219` sırası. */
    fun csvLine(ts: String) = "$ts,$lat,$lon,$accuracy,$speed,$bearing,$altitude"

    companion object {
        const val CSV_HEADER = LogImporter.GPS_HEADER
        /** Saf testlerin mesafesi; Android `Location.distanceBetween` verir (Rawstats `distanceTo` ile aynı sonuç, CORE.md 1.11). */
        fun haversineM(a: GpsFix, b: GpsFix): Double = HomeShift.distM(a.lat, a.lon, b.lat, b.lon)
    }
}

/**
 * D8 GPS birikimi, fix yaşı ve türetilmiş hız (CORE.md 1.3 adım 1, 2, 6; RS `GpsService.kt:196-237`). [distance] metre verir
 * (Android'de `Location.distanceBetween`, testlerde haversine). Durum süreç içi: yeni süreç 0'dan başlar, kayıtlı `dist` ayrıca sayılır.
 */
class GpsTrack(private val distance: (GpsFix, GpsFix) -> Double = GpsFix::haversineM) {
    @Volatile var distanceM = 0.0
    /** Mesafe çapası (Rawstats `last`): yalnız doğruluğu ≤[MAX_ACCURACY_M] fix'ler. */
    private var anchor: GpsFix? = null
    /** Son işlenen fix (Rawstats `Store.lastLocation`): türetilmiş hızın tabanı. */
    var previous: GpsFix? = null
        private set

    fun reset() { distanceM = 0.0; anchor = null; previous = null }

    /** Adım 1: yaşı 0–[MAX_AGE_MS] ve çapadan yeni. */
    fun fresh(f: GpsFix, monoNowMs: Long): Boolean {
        val a = anchor
        return monoNowMs - f.elapsedMs in 0..MAX_AGE_MS && (a == null || f.elapsedNanos > a.elapsedNanos)
    }

    /** Adım 2: iki iyi fix arasında gerçek yer değişimi varsa hız (m/s), yoksa null. */
    fun derivedSpeed(f: GpsFix): Double? {
        val prev = previous ?: return null
        if (f.accuracy > MAX_ACCURACY_M || prev.accuracy > MAX_ACCURACY_M) return null
        val dt = (f.elapsedNanos - prev.elapsedNanos) / 1e9
        if (dt <= 0 || dt >= DERIVED_MAX_DT_S) return null
        val displacement = distance(prev, f)
        return if (displacement > max(DERIVED_MIN_M, (prev.accuracy + f.accuracy).toDouble())) displacement / dt else null
    }

    /** Adım 3: bu fix türetilmiş hızın yeni tabanı. */
    fun seen(f: GpsFix) { previous = f }

    /** Adım 6: yol sayılan metre (0: sayılmadı). Uzun boşluk (>30 sn) yol sayılmaz; sıçrama ve titreme sayılmaz. */
    fun accumulate(f: GpsFix): Double {
        if (f.accuracy > MAX_ACCURACY_M) return 0.0
        val prev = anchor ?: run { anchor = f; return 0.0 }
        val d = distance(prev, f)
        val dt = (f.elapsedNanos - prev.elapsedNanos) / 1e9
        if (dt > GAP_S) { anchor = f; return 0.0 }
        if (dt > 0 && d / dt <= MAX_MPS && d > max(MIN_STEP_M, ((prev.accuracy + f.accuracy) / 2).toDouble())) {
            distanceM += d
            anchor = f
            return d
        }
        return 0.0
    }

    companion object {
        const val MAX_AGE_MS = 12_000L
        const val MAX_ACCURACY_M = 30f
        const val DERIVED_MAX_DT_S = 15.0
        const val DERIVED_MIN_M = 8.0
        const val GAP_S = 30.0
        const val MAX_MPS = 25.0
        const val MIN_STEP_M = 4.0
    }
}
