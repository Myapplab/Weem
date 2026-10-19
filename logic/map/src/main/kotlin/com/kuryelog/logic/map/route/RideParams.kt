package com.kuryelog.logic.map.route

import kotlin.math.max

/** Araç profili (batarya kapasitesi) — `lib/core/physics.dart` (MAP.md 3.2). */
class VehicleProfile(val id: String, val name: String, val capacityWh: Double)

enum class RideMode { BATTERY, TIME }

/** Rota motorunun kullandığı fizik ve kural sabitleri (MAP.md 3.2). Hepsi ayardan gelir; formüller A6.4'te sınanır. */
data class RideParams(
    val flatWhPerM: Double = 0.0135,
    val climbWhPerM: Double = 0.38,
    val climbSafety: Double = 1.0,
    val flatKmh: Double = 28.5,
    val climbKmh: Double = 18.5,
    val walkKmh: Double = 4.5,
    val maxGrade: Double = 0.18,
    val mainRoadPenalty: Double = 1.15,
    val wrongWayKmh: Double = 18.0,
    val walkRadius: Double = 150.0,
    val parkPenaltyS: Double = 15.0,
    val qualityWeight: Double = 0.10,
    val turnPenaltyS: Double = 12.0,
    /** E13: bisiklet yolu kenarının rota maliyeti çarpanı; 1 = tercih yok. */
    val bikewayFactor: Double = 0.85,
) {
    val flatMps get() = flatKmh / 3.6
    val walkMps get() = walkKmh / 3.6
    val wrongWayMps get() = wrongWayKmh / 3.6

    fun withMaxGrade(g: Double) = copy(maxGrade = g)

    fun climbMps(grade: Double): Double = TODO("A6.4")
    fun energyWh(lengthM: Double, climbM: Double): Double = flatWhPerM * lengthM + climbWhPerM * climbSafety * climbM
    fun combine(mode: RideMode, seconds: Double, wh: Double): Double =
        if (mode == RideMode.TIME) seconds + GAMMA_S_PER_WH * wh else wh + BETA_WH_PER_S * seconds

    companion object {
        const val BETA_WH_PER_S = 0.02
        const val GAMMA_S_PER_WH = 1.0
    }
}
