package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats ArrivalDistance.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * Ekim planı madde 18–20 (16 Oct): how far the courier is from the restaurant / customer of the current stage, for the arrival
 * rules (restaurant ≤5 m, customer ≤10 m, both below [SLOW_KMH]). Sources: YolGo's E8 navigation payload (remaining route metres,
 * [YOLGO]) and Google Maps' navigation notification ([MAPS], [NavDistance.metres]); when both are fresh the newer one wins. The
 * courier's own GPS distance ([GPS]) is always the fallback, with the accuracy allowance chosen for madde 18: distance −
 * min(accuracy, [MAX_ALLOWANCE_M]) ≤ limit, and a fix worse than [MAX_ACCURACY_M] says nothing. Pure.
 */
object ArrivalDistance {
    const val YOLGO = "nav_yolgo"
    const val MAPS = "nav_google_maps"
    const val GPS = "gps"
    const val FRESH_MS = 30_000L
    const val SLOW_KMH = 10.0
    const val MAX_ALLOWANCE_M = 20.0
    const val MAX_ACCURACY_M = 30.0
    const val RESTAURANT_M = 5.0
    const val CUSTOMER_M = 10.0

    /** [metres] to the target at [at]; [accuracyM] only for [GPS]. */
    data class Source(val metres: Double, val at: Long, val kind: String, val accuracyM: Double? = null)

    /** The newer fresh navigation value, else the GPS one. */
    fun pick(yolgo: Source?, maps: Source?, gps: Source?, now: Long): Source? =
        listOfNotNull(yolgo, maps).filter { now - it.at in 0..FRESH_MS }.maxByOrNull { it.at } ?: gps

    fun near(s: Source, limitM: Double): Boolean {
        if (!s.metres.isFinite() || s.metres < 0) return false
        if (s.kind != GPS) return s.metres <= limitM
        val acc = s.accuracyM ?: return s.metres <= limitM
        if (!acc.isFinite() || acc > MAX_ACCURACY_M) return false
        return maxOf(0.0, s.metres - minOf(acc, MAX_ALLOWANCE_M)) <= limitM
    }

    /** A fix without a speed is taken as not moving (as in [VenueArrival]). */
    fun slow(speedKmh: Double?): Boolean = speedKmh == null || speedKmh < SLOW_KMH
}
