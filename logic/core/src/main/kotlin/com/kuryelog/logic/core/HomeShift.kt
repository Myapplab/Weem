package com.kuryelog.logic.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * G1/G2 ev kuralı (CORE.md 1.6, 1.9). Başlangıç: evden ≥[START_RADIUS_M] düz çizgi uzaklıkta, GPS'in kendi hızı (iki konumdan
 * türetilmez) ≥[START_SPEED_MPS], doğruluk ≤[START_MAX_ACCURACY_M], iki ardışık fix. Bitiş (F4.5b): eve [RADIUS_M] içinde,
 * [END_SPEED_MPS] altında, [STILL_MS] boyunca; açık siparişte hiç bitmez. İki uygulamanın ortak kuralı budur (S2).
 *
 * Ev noktası yalnız bellekte ve tercihlerde durur; burada hiçbir yere koordinat yazılmaz. Çağıran [startBlocked] ve [endArmed]
 * düz bool'larını saklar (süreç ölse de koruyucular kalır).
 */
class HomeShift(val homeLat: Double, val homeLon: Double, var startBlocked: Boolean = false, var endArmed: Boolean = false) {
    /** One location fix; [at] is a monotonic clock in ms (elapsed realtime), [speedMps] null when the provider gave none. */
    data class Fix(val at: Long, val lat: Double, val lon: Double, val accuracyM: Double?, val speedMps: Double?)
    enum class Decision { NONE, START, END }

    /** E6: the last fix that met the start rule (the first of the pair), and the last usable fix (the basis of the jump check). */
    private var startPrev: Fix? = null
    private var idlePrev: Fix? = null
    private var endPrev: Fix? = null
    private var anchor: Fix? = null
    private var windowLastAt = -1L
    private var windowFixes = 0

    fun distanceToHomeM(f: Fix): Double = distM(homeLat, homeLon, f.lat, f.lon)

    /** Feed every fix; [shiftActive] is the current shift state, [busy] an order is open (F4.5b: the home rule then never
     *  ends the shift). Returns the action the caller should take. */
    fun observe(f: Fix, shiftActive: Boolean, busy: Boolean = false): Decision {
        if (!f.lat.isFinite() || !f.lon.isFinite() || f.lat !in -90.0..90.0 || f.lon !in -180.0..180.0 ||
            f.accuracyM == null || !f.accuracyM.isFinite() || f.accuracyM < 0) {
            startPrev = null
            return Decision.NONE
        }
        return if (shiftActive) observeShift(f, busy) else observeIdle(f)
    }

    private fun observeIdle(f: Fix): Decision {
        endPrev = null; clearWindow()
        val acc = f.accuracyM!!
        // A fix too loose to place the courier says nothing and breaks a pair.
        if (acc > HOME_SEEN_ACCURACY_M) { startPrev = null; return Decision.NONE }
        val last = idlePrev
        if (last != null && (f.at <= last.at || isJump(last, f))) { startPrev = null; return Decision.NONE }
        idlePrev = f
        val d = distanceToHomeM(f)
        if (d <= START_RADIUS_M) startBlocked = false // seen at home: the next departure may start a shift
        val meets = acc <= START_MAX_ACCURACY_M && d >= START_RADIUS_M && (validSpeed(f.speedMps) ?: -1.0) >= START_SPEED_MPS
        if (!meets || startBlocked) { startPrev = null; return Decision.NONE }
        val prev = startPrev
        if (prev == null || f.at - prev.at > MAX_PAIR_GAP_MS) { startPrev = f; return Decision.NONE }
        startPrev = null
        endArmed = true // left home: the shift may end on return
        return Decision.START
    }

    private fun observeShift(f: Fix, busy: Boolean): Decision {
        startPrev = null; idlePrev = null
        if (f.accuracyM!! > END_MAX_ACCURACY_M) return Decision.NONE
        val prev = endPrev
        if (prev != null && (f.at <= prev.at || isJump(prev, f))) return Decision.NONE
        endPrev = f
        if (distanceToHomeM(f) > RADIUS_M) { endArmed = true; clearWindow(); return Decision.NONE }
        if (!endArmed) return Decision.NONE
        if (busy) { clearWindow(); return Decision.NONE }
        val speed = if (prev != null) speedOf(prev, f) else validSpeed(f.speedMps)
        if (speed == null || speed >= END_SPEED_MPS) { clearWindow(); return Decision.NONE }
        val a = anchor
        if (a == null || f.at - windowLastAt > MAX_GAP_MS || distM(a.lat, a.lon, f.lat, f.lon) >= STILL_AREA_M) {
            anchor = f; windowLastAt = f.at; windowFixes = 1
            return Decision.NONE
        }
        windowLastAt = f.at; windowFixes++
        if (f.at - a.at >= STILL_MS && windowFixes >= MIN_WINDOW_FIXES) {
            clearWindow(); endPrev = null
            endArmed = false; startBlocked = false // at home now: the next departure may start a shift
            return Decision.END
        }
        return Decision.NONE
    }

    /** A shift started by hand (possibly at home): it ends automatically only after leaving the radius once. */
    fun onManualStart() { endArmed = false; endPrev = null; clearWindow() }

    /** A shift stopped by hand: no automatic start until a fix inside the home radius was seen. */
    fun onManualStop() { startBlocked = true; startPrev = null; idlePrev = null; endPrev = null; clearWindow() }

    /** GPS speed when present, otherwise speed between two consistent fixes; null for a jump or a long gap. */
    private fun speedOf(prev: Fix, f: Fix): Double? {
        val dt = (f.at - prev.at) / 1000.0
        if (dt <= 0 || dt * 1000 > MAX_PAIR_GAP_MS) return null
        val derived = distM(prev.lat, prev.lon, f.lat, f.lon) / dt
        if (derived > MAX_PLAUSIBLE_MPS) return null
        return validSpeed(f.speedMps) ?: derived
    }

    /** An implausible move within a short gap: the new fix is dropped and the previous one stays the basis. */
    private fun isJump(prev: Fix, f: Fix): Boolean {
        val dt = f.at - prev.at
        return dt in 1..MAX_PAIR_GAP_MS && distM(prev.lat, prev.lon, f.lat, f.lon) / (dt / 1000.0) > MAX_PLAUSIBLE_MPS
    }

    private fun validSpeed(s: Double?): Double? = s?.takeIf { it.isFinite() && it >= 0 }

    private fun clearWindow() { anchor = null; windowLastAt = -1L; windowFixes = 0 }

    companion object {
        /** F4.5b (Ekim planı, both apps): the end rule's home radius (K38 had 200 m). */
        const val RADIUS_M = 50.0
        /** E6: the start needs this straight-line distance from home ... */
        const val START_RADIUS_M = 50.0
        /** ... at this GPS speed (10 km/h; K38 had 8 km/h, from two positions too) ... */
        const val START_SPEED_MPS = 10.0 / 3.6
        /** F4.5b: below 5 km/h (K38: 10 km/h) for 30 s (K38: 2 min). */
        const val END_SPEED_MPS = 5.0 / 3.6
        const val STILL_AREA_M = 10.0
        const val STILL_MS = 30_000L
        const val MIN_WINDOW_FIXES = 3
        const val MAX_GAP_MS = 45_000L
        const val MAX_PAIR_GAP_MS = 120_000L
        /** ... on two consecutive fixes this accurate (K38: 50 m). */
        const val START_MAX_ACCURACY_M = 20.0
        /** A looser fix still says "at home" (it unblocks the start after a manual stop) and is the basis of the jump check. */
        const val HOME_SEEN_ACCURACY_M = 50.0
        const val END_MAX_ACCURACY_M = 30.0
        const val MAX_PLAUSIBLE_MPS = 50.0
        private const val EARTH_RADIUS_M = 6_371_008.8
        const val REASON_MANUAL = "manual"
        const val REASON_DEPARTURE = "home_departure"
        const val REASON_ARRIVAL = "home_arrival"

        fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val sp = sin((p2 - p1) / 2)
            val sl = sin(Math.toRadians(lon2 - lon1) / 2)
            val a = sp * sp + cos(p1) * cos(p2) * (sl * sl)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a))
        }

        /** A stored home point, or null when it is missing or not a valid coordinate (never a guessed value). */
        fun parseHome(lat: String?, lon: String?): Pair<Double, Double>? {
            val a = lat?.toDoubleOrNull() ?: return null
            val o = lon?.toDoubleOrNull() ?: return null
            if (!a.isFinite() || !o.isFinite() || a !in -90.0..90.0 || o !in -180.0..180.0) return null
            return a to o
        }
    }
}

/**
 * G1 ev izleyicisinin konum isteği (CORE.md 1.7): vardiya dışında evdeyken [Mode.LOW] (dengeli güç, 30 sn); düşük güçlü bir fix
 * ayrılmanın olası olduğunu söyleyince ([HomeShift.START_RADIUS_M]'den uzak doğruluk dairesi ya da ≥10 km/s) [highMs] boyunca
 * [Mode.HIGH] (GPS, 2 sn). Başlangıç kuralı GPS'in kendi hızını ve ≤20 m doğruluğu ister; düşük güçlü fix'lerde nadirdir, evde yatan
 * telefon GPS'i açmaz. Pencere başlatmadan biterse sonraki pencere [cooldownMs] bekler (hızlı gidiş hariç). Saf; süreler monoton saatte.
 */
class HomeWatchPlan(private val highMs: Long = HIGH_MS, private val cooldownMs: Long = COOLDOWN_MS) {
    enum class Mode { LOW, HIGH }
    var mode = Mode.LOW
        private set
    private var highSince = Long.MIN_VALUE / 2
    private var lastHighEnd = Long.MIN_VALUE / 2

    /** A fix of the current request; returns the mode to use now. */
    fun onFix(distanceM: Double, accuracyM: Double?, speedMps: Double?, now: Long): Mode {
        tick(now)
        if (mode == Mode.LOW) {
            val fast = speedMps != null && speedMps.isFinite() && speedMps >= HomeShift.START_SPEED_MPS
            val away = accuracyM != null && accuracyM.isFinite() && distanceM - accuracyM >= HomeShift.START_RADIUS_M
            if (fast || away && now - lastHighEnd >= cooldownMs) { mode = Mode.HIGH; highSince = now }
        }
        return mode
    }

    /** The GPS window ends after [highMs] even without fixes. */
    fun tick(now: Long): Mode {
        if (mode == Mode.HIGH && now - highSince >= highMs) { mode = Mode.LOW; lastHighEnd = now }
        return mode
    }

    /** Shift started or home changed: low power, no pause. */
    fun reset() { mode = Mode.LOW; lastHighEnd = Long.MIN_VALUE / 2 }

    companion object {
        const val HIGH_MS = 5 * 60_000L
        const val COOLDOWN_MS = 10 * 60_000L
        const val LOW_INTERVAL_MS = 30_000L
        const val HIGH_INTERVAL_MS = 2_000L
    }
}
