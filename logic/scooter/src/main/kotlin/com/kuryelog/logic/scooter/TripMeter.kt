package com.kuryelog.logic.scooter

/**
 * Running totals since the last reset: energy used, battery percent used, moving average speed and the latest power.
 * The live panel resets it when the scooter link starts; the merged KuryePlus app resets it per order (SurusPaneli.resetTrip
 * at acceptance, SurusPaneli.endTrip at delivery). Incremental and Android-free; same gap rule as ScooterEnergy (nothing
 * integrated over 3 s gaps). (SCOOTER.md 8.3; NB `TripMeter.java`, line by line.)
 * D10 (13 Ekim): energy and percent used are never negative. Each step counts only when its power is both packs' (or the
 * scooter has no known external pack); energy flowing into a pack (charger, or the external pack feeding the internal one)
 * is its own figure, and the time the external pack was known but not read is counted apart (measurement coverage).
 */
class TripMeter {
    class Stats internal constructor(
        val label: String,
        val startedWallMs: Long,
        /** Energy taken out of the packs, Wh (≥ 0); null before two linked samples with both packs' power. */
        val wh: Double?,
        /** D10: energy flowing into the packs over the same steps, Wh (≥ 0): charger, or the external pack feeding the internal one. */
        val inWh: Double?,
        /** D10: time counted in [wh]. */
        val coveredMs: Long,
        /** D10: time the external pack was known but not read (not counted). */
        val partialMs: Long,
        /** Internal + external pack percent points used since the reset (≥ 0); null while unknown (also when an external pack
         *  is known but its percent was not read: the internal pack alone rises while being fed). */
        val pctUsed: Double?,
        /** Average speed while moving (≥ MOVING_KMH), km/h; null before any movement. */
        val avgMovingKmh: Double?,
        /** Distance from the speed register while moving, km. */
        val km: Double,
        val movingMs: Long,
        /** Latest V×A power, sign as on the panel (+charge / −discharge). */
        val powerW: Double?,
    )

    private var label = ""
    private var startedWallMs = 0L
    private var last: ScooterEnergy.Sample? = null
    private var wh = 0.0
    private var inWh = 0.0
    private var km = 0.0
    private var movingMs = 0L
    private var coveredMs = 0L
    private var partialMs = 0L
    private var energySeen = false
    private var externalKnown = false
    private var intStart: Double? = null
    private var extStart: Double? = null
    private var intNow: Double? = null
    private var extNow: Double? = null
    private var powerW: Double? = null

    @Synchronized
    fun reset(label: String?, wallMs: Long) {
        this.label = label ?: ""
        startedWallMs = wallMs
        last = null
        wh = 0.0
        inWh = 0.0
        km = 0.0
        movingMs = 0
        coveredMs = 0
        partialMs = 0
        energySeen = false
        externalKnown = false
        intStart = null
        extStart = null
        intNow = null
        extNow = null
        powerW = null
    }

    /** The order [label] ended: count from here without an order label, so the ride back is not added to it. Another order's
     *  meter (accepted later, still running) is left alone. True when the meter was reset. */
    @Synchronized
    fun end(label: String?, wallMs: Long): Boolean {
        if (label == null || label.isEmpty() || label != this.label) return false
        reset("", wallMs)
        return true
    }

    @Synchronized
    fun add(s: ScooterEnergy.Sample?) {
        if (s == null || s.empty()) return
        val prev = last
        if (prev != null && s.monoMs <= prev.monoMs) return
        if (startedWallMs == 0L) startedWallMs = s.wallMs
        if (s.intPct != null) { if (intStart == null) intStart = s.intPct; intNow = s.intPct }
        if (s.extPct != null) { if (extStart == null) extStart = s.extPct; extNow = s.extPct }
        if (s.externalKnown) externalKnown = true
        powerW = s.powerW
        if (prev != null) {
            val dt = s.monoMs - prev.monoMs
            if (dt <= ScooterEnergy.MAX_STEP_MS) {
                if (prev.powerW != null && s.powerW != null) {
                    if (prev.bothPacks() && s.bothPacks()) {
                        val e = -(prev.powerW + s.powerW) / 2 * dt / 3600000.0
                        if (e >= 0) wh += e else inWh -= e
                        coveredMs += dt
                        energySeen = true
                    } else {
                        partialMs += dt
                    }
                }
                if (prev.speedKmh != null && s.speedKmh != null && prev.speedKmh >= MOVING_KMH && s.speedKmh >= MOVING_KMH) {
                    movingMs += dt
                    km += (prev.speedKmh + s.speedKmh) / 2 * dt / 3600000.0
                }
            }
        }
        last = s
    }

    @Synchronized
    fun stats(): Stats {
        var pct: Double? = null
        val iS = intStart
        val iN = intNow
        val eS = extStart
        val eN = extNow
        if (iS != null && iN != null) {
            if (eS != null && eN != null) pct = iS - iN + eS - eN else if (!externalKnown) pct = iS - iN
        }
        if (pct != null) pct = Math.max(0.0, pct)
        return Stats(
            label, startedWallMs, if (energySeen) wh else null, if (energySeen) inWh else null, coveredMs, partialMs, pct,
            if (movingMs > 0) km / (movingMs / 3600000.0) else null, km, movingMs, powerW,
        )
    }

    companion object {
        /** Below this the scooter counts as standing still (time not in the moving average). */
        const val MOVING_KMH = 1.0
    }
}
