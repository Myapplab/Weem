package com.kuryelog.logic.map.ride

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.route.RideParams
import com.kuryelog.logic.map.route.RoutePoint
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Bir hızda rota tahmini (MAP.md 10.2): süre [seconds], enerji [wh]. */
data class SpeedOption(val kmh: Double, val seconds: Double, val wh: Double)

/**
 * `lib/routing/speed_options.dart` (MAP.md 10.2). Scooter fiziği: düz yol tüketiminin ~%60'ı hava direnci (hızın karesiyle artar), ~%40'ı yuvarlanma direnci (sabit);
 * tırmanış enerjisi hızdan bağımsız; yokuşta hız motorun tırmanış hızını geçemez.
 */
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
            val speed = if (grade > 0.02) min(v, p.climbMps(grade) * (v / ref).coerceIn(0.5, 1.2)) else v
            t += d / speed
            wh += flatPerM * d + (if (up > 0) p.climbWhPerM * p.climbSafety * up else 0.0)
        }
        return SpeedOption(kmh, t, wh)
    }

    /** Liste `speeds ?? [20, 25, round(flatKmh)]` → tekrarsız → artan sıra → her biri için [speedOption]; rota noktası < 2 ise seçenek yok. */
    fun speedOptions(ride: List<RoutePoint>, p: RideParams, speeds: List<Double>? = null): List<SpeedOption> {
        if (ride.size < 2) return emptyList()
        val list = (speeds ?: listOf(20.0, 25.0, Math.round(p.flatKmh).toDouble())).toSortedSet().toList()
        return list.map { speedOption(ride, p, it) }
    }

    /** `now + round(seconds)` → "HH:mm". */
    fun arrivalClock(now: LocalDateTime, seconds: Double): String {
        val at = now.plusSeconds(seconds.roundToLong())
        return String.format(java.util.Locale.ROOT, "%02d:%02d", at.hour, at.minute)
    }
}
