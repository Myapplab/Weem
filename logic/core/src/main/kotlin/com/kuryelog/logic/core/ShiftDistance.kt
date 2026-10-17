package com.kuryelog.logic.core

/**
 * D8 vardiya mesafesi (CORE.md 1.3; saha 12 Ekim: 4 sa 54 dk vardiyanın sonunda `km: 0`). Gerçek mesafenin iki alt sınırı; büyüğü
 * vardiya mesafesidir, ikisi de kayda yazılır:
 * - GPS: servisin birikimi. Süreç ölünce bellekteki 0 kayıtlı değeri (`dist`, 15 sn'de bir) eziyordu; artık kayıtlı değer okunur ([result]).
 * - Scooter: vardiyadaki canlı örneklerin son − ilk odometresi; süreç ölse de saklanır.
 */
object ShiftDistance {
    const val PREF_ODO_FIRST = "shiftOdoFirstKm"
    const val PREF_ODO_LAST = "shiftOdoLastKm"
    const val PREF_ODO_AT = "shiftOdoLastAt"
    /** A scooter is not faster than this between two readings; a larger odometer step is a misread. */
    const val MAX_KMH = 60.0

    /** Odometer of the shift's first and latest reading ([lastAt] wall ms of the latest). */
    data class Odo(val firstKm: Double, val lastKm: Double, val lastAt: Long) {
        val km get() = (lastKm - firstKm).coerceAtLeast(0.0)
    }

    /**
     * The shift's odometer span after one more reading [km] at [at]. Missing, not finite, negative or backwards readings change
     * nothing; a step faster than [MAX_KMH] since the latest reading is a misread, unless nothing was ridden yet (then the
     * first reading was the odd one and the span starts again here).
     */
    fun observe(prev: Odo?, km: Double?, at: Long): Odo? {
        if (km == null || !km.isFinite() || km < 0) return prev
        if (prev == null) return Odo(km, km, at)
        if (km < prev.lastKm) return if (prev.km == 0.0) Odo(km, km, at) else prev
        val hours = (at - prev.lastAt).coerceAtLeast(0) / 3_600_000.0
        if (km - prev.lastKm > maxOf(0.2, hours * MAX_KMH)) return if (prev.km == 0.0) Odo(km, km, at) else prev
        return Odo(prev.firstKm, km, at)
    }

    /** Written at the shift end: [km] the larger bound, [gpsKm] and [scooterKm] (null without odometer readings). */
    data class Result(val km: Double, val gpsKm: Double, val scooterKm: Double?, val fromScooter: Boolean)

    /** [liveM]: the GPS accumulator of this process; [savedM]: its last saved value (an earlier process of the same shift). */
    fun result(liveM: Double, savedM: Double, odo: Odo?): Result {
        fun ok(x: Double) = if (x.isFinite() && x > 0) x else 0.0
        val gps = maxOf(ok(liveM), ok(savedM)) / 1000.0
        val scooter = odo?.km
        return if (scooter != null && scooter > gps) Result(scooter, gps, scooter, true) else Result(gps, gps, scooter, false)
    }

    /** Saved as whole metres ([PREF_ODO_FIRST], [PREF_ODO_LAST]; −1 = none). */
    fun meters(km: Double): Long = Math.round(km * 1000.0)
    fun load(firstM: Long, lastM: Long, lastAt: Long): Odo? =
        if (firstM < 0 || lastM < firstM) null else Odo(firstM / 1000.0, lastM / 1000.0, lastAt)
}
