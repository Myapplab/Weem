package com.kuryelog.logic.map.route

import com.kuryelog.logic.core.Bilingual
import kotlin.math.max

/** Araç profili (batarya kapasitesi) — `lib/core/physics.dart` (MAP.md 3.2). */
class VehicleProfile(val id: String, val name: String, val capacityWh: Double) {
    companion object {
        val ALL = listOf(
            VehicleProfile("g3", "Max G3 (598 Wh)", 598.0),
            VehicleProfile("g3x", "Max G3 + ek batarya (1066 Wh)", 1066.0),
        )

        /** Bilinmeyen kimlik → ilk profil. */
        fun byId(id: String): VehicleProfile = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}

/** Planlayıcı modları bu sırayla dener (MAP.md 3.10). */
enum class RideMode(val icon: String, val label: Bilingual) {
    BATTERY("🔋", Label("BATARYA", "BATAREYA")),
    TIME("⚡", Label("ZAMAN", "VAXT")),
    ;

    private class Label(override val tr: String, override val az: String) : Bilingual
}

/** Rota motorunun kullandığı fizik ve kural sabitleri (MAP.md 3.2). Hepsi ayardan gelir (`MapSettings.paramsFor`). */
data class RideParams(
    val flatWhPerM: Double = 0.0135,
    val climbWhPerM: Double = 0.38,
    /** 1.0 = fizik; 1.46 = şartnamedeki 41×Δh. */
    val climbSafety: Double = 1.0,
    val flatKmh: Double = 28.5,
    /** %8 eğimdeki hız. */
    val climbKmh: Double = 18.5,
    val walkKmh: Double = 4.5,
    /** O anki eğim limiti (batarya durumuna göre). */
    val maxGrade: Double = 0.18,
    /** Ana caddelerde süre çarpanı (güvenlik). */
    val mainRoadPenalty: Double = 1.15,
    /** Onaylı ters yönde hız tavanı. */
    val wrongWayKmh: Double = 18.0,
    /** Son yürüme için en fazla (m). */
    val walkRadius: Double = 150.0,
    /** Park et / kilitle süresi (sn). */
    val parkPenaltyS: Double = 15.0,
    /** Yol kalitesi toleransı (0,10 = %10): maliyet × (1 + w·(1 − kalite)). */
    val qualityWeight: Double = 0.10,
    /** Kavşakta belirgin dönüş (>35°) cezası (sn); gerçek süreye eklenmez, yalnız seçimde. */
    val turnPenaltyS: Double = 12.0,
    /** E13: bisiklet yolu kenarının rota maliyeti çarpanı; 1 = tercih yok. Yalnız seçimde, ters yönde uygulanmaz. */
    val bikewayFactor: Double = 0.85,
) {
    val flatMps get() = flatKmh / 3.6
    val walkMps get() = walkKmh / 3.6
    val wrongWayMps get() = wrongWayKmh / 3.6

    fun withMaxGrade(g: Double) = copy(maxGrade = g)

    /** Yokuş yukarı hız (m/s): %2'ye kadar düz hız, %8'de [climbKmh], daha dikte ~1/eğim, en az 10 km/s. */
    fun climbMps(grade: Double): Double {
        val g0 = 0.02
        val g1 = 0.08
        val kmh = if (grade <= g0) {
            flatKmh
        } else if (grade <= g1) {
            flatKmh - (flatKmh - climbKmh) * (grade - g0) / (g1 - g0)
        } else {
            max(10.0, climbKmh * g1 / grade)
        }
        return kmh / 3.6
    }

    fun energyWh(lengthM: Double, climbM: Double): Double = flatWhPerM * lengthM + climbWhPerM * climbSafety * climbM

    /** En ucuz sürüş maliyeti (metre başına): A* sezgiseli için alt sınır. */
    fun minRideCostPerM(mode: RideMode): Double =
        if (mode == RideMode.TIME) 1 / flatMps else flatWhPerM + BETA_WH_PER_S / flatMps

    fun minWalkCostPerM(mode: RideMode): Double = if (mode == RideMode.TIME) 1 / walkMps else BETA_WH_PER_S / walkMps

    fun combine(mode: RideMode, seconds: Double, wh: Double): Double =
        if (mode == RideMode.TIME) seconds + GAMMA_S_PER_WH * wh else wh + BETA_WH_PER_S * seconds

    companion object {
        /** Batarya modunda 1 saniyenin Wh karşılığı (zamanı tamamen yok saymasın). */
        const val BETA_WH_PER_S = 0.02

        /** Zaman modunda 1 Wh'nin saniye karşılığı (küçük eşitlik bozucu). */
        const val GAMMA_S_PER_WH = 1.0
    }
}
