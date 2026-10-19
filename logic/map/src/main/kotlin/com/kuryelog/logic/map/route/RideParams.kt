package com.kuryelog.logic.map.route

/** Sürüş kipi; planlayıcı bu sırayla dener (`MAP.md` 3.2). */
enum class RideMode { BATTERY, TIME }

/** Rota fiziği ve ayar katsayıları (YolGo `RideParams`, physics.dart:29–126; `MAP.md` 3.2). */
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
    val bikewayFactor: Double = 0.85,
) {
    val flatMps: Double get() = flatKmh / 3.6
    val walkMps: Double get() = walkKmh / 3.6
    val wrongWayMps: Double get() = wrongWayKmh / 3.6

    fun withMaxGrade(g: Double): RideParams = copy(maxGrade = g)
}
