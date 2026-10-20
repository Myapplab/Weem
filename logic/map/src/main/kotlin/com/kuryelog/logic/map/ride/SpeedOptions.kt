package com.kuryelog.logic.map.ride

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.route.RideParams
import com.kuryelog.logic.map.route.RoutePoint
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

/** Bir hızda rota tahmini (MAP.md 10.2): süre [seconds], enerji [wh]. */
data class SpeedOption(val kmh: Double, val seconds: Double, val wh: Double)

/** `lib/routing/speed_options.dart` (MAP.md 10.2). */
object SpeedOptions {
    const val AERO_SHARE = 0.6

    /** `v = kmh/3,6`; düz Wh/m `flatWhPerM·((1−0,6) + 0,6·(v/ref)²)`; >%2 yokuşta hız `min(v, climbMps(grade)·clamp(v/ref, 0,5, 1,2))`. */
    fun speedOption(ride: List<RoutePoint>, p: RideParams, kmh: Double): SpeedOption {
        val v = kmh / 3.6
        val ref = p.flatKmh / 3.6
        val flatPerM = p.flatWhPerM * ((1 - AERO_SHARE) + AERO_SHARE * (v / ref) * (v / ref))
        var t = 0.0
        var wh = 0.0
        for (i in 1 until ride.size) {
            val a = ride[i - 1]
            val b = ride[i]
            val d = Geo.haversine(a.lat, a.lon, b.lat, b.lon)
            if (d <= 0) continue
            val up = b.ele - a.ele
            val grade = up / max(d, 20.0)
            val speed = if (grade > 0.02) min(v, p.climbMps(grade) * Geo.clampD(v / ref, 0.5, 1.2)) else v
            t += d / speed
            wh += flatPerM * d + (if (up > 0) p.climbWhPerM * p.climbSafety * up else 0.0)
        }
        return SpeedOption(kmh, t, wh)
    }

    /** Liste `speeds ?? [20, 25, round(flatKmh)]` → tekrarsız → artan sıra → her biri için [speedOption]. */
    fun speedOptions(ride: List<RoutePoint>, p: RideParams, speeds: List<Double>? = null): List<SpeedOption> =
        (speeds ?: listOf(20.0, 25.0, Math.round(p.flatKmh).toDouble())).toSortedSet().map { speedOption(ride, p, it) }

    /** `now + round(seconds)` → "HH:mm". */
    fun arrivalClock(now: LocalDateTime, seconds: Double): String {
        val at = now.plusSeconds(Math.round(seconds))
        return "%02d:%02d".format(at.hour, at.minute)
    }
}
