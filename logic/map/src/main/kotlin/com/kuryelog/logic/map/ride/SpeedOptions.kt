package com.kuryelog.logic.map.ride

import com.kuryelog.logic.map.route.RideParams
import com.kuryelog.logic.map.route.RoutePoint
import java.time.LocalDateTime

/** Bir hızda rota tahmini (MAP.md 10.2): süre [seconds], enerji [wh]. */
data class SpeedOption(val kmh: Double, val seconds: Double, val wh: Double)

/** `lib/routing/speed_options.dart` (MAP.md 10.2). İskelet (A6.2b): gövdeler A6.7'de. */
object SpeedOptions {
    const val AERO_SHARE = 0.6

    /** `v = kmh/3,6`; düz Wh/m `flatWhPerM·((1−0,6) + 0,6·(v/ref)²)`; >%2 yokuşta hız `min(v, climbMps(grade)·clamp(v/ref, 0,5, 1,2))`. */
    fun speedOption(ride: List<RoutePoint>, p: RideParams, kmh: Double): SpeedOption = TODO("A6.7")

    /** Liste `speeds ?? [20, 25, round(flatKmh)]` → tekrarsız → artan sıra → her biri için [speedOption]. */
    fun speedOptions(ride: List<RoutePoint>, p: RideParams, speeds: List<Double>? = null): List<SpeedOption> = TODO("A6.7")

    /** `now + round(seconds)` → "HH:mm". */
    fun arrivalClock(now: LocalDateTime, seconds: Double): String = TODO("A6.7")
}
