package com.kuryelog.logic.scooter

import java.util.ArrayDeque

/**
 * Per-second scooter samples and the energy block of the KuryePlus `economics-energy.jsonl` schema (schemaVersion 1, keys of
 * EnergySegments.energy() on the `kuryeplus` branch). Android-free so the local tests cover it. (SCOOTER.md 8.1–8.2; NB
 * `ScooterEnergy.java`, line by line.)
 * Missing data stays null, never an invented value. Energy is consumption-positive: Wh taken out of the packs (discharge
 * positive; energy flowing into a pack lowers it), the opposite sign of the V×A power shown on the panel.
 * The G3 has no regenerative braking: inflow is a charger, or the external pack feeding the internal one while only the
 * internal pack is counted (then external_feeding is set and the figure is short).
 */
class ScooterEnergy {
    /** One 1 Hz sample from fresh register values; any field may be null. */
    class Sample(
        val wallMs: Long,
        val monoMs: Long,
        val intPct: Double?,
        val intV: Double?,
        val intA: Double?,
        val extPct: Double?,
        val extV: Double?,
        val extA: Double?,
        val powerW: Double?,
        val tempC: Double?,
        val speedKmh: Double?,
        val odometerKm: Double?,
        /** D10: the link reads an external pack (a board found by the probe), whether or not its values are fresh right now. */
        val externalKnown: Boolean,
    ) {
        /** externalKnown follows from any external value present (tests and old callers). */
        constructor(
            wall: Long, mono: Long, ip: Double?, iv: Double?, ia: Double?, ep: Double?, ev: Double?, ea: Double?, w: Double?, t: Double?,
            s: Double?, o: Double?,
        ) : this(wall, mono, ip, iv, ia, ep, ev, ea, w, t, s, o, ep != null || ev != null || ea != null)

        /** D10: this sample's power is both packs' (or the scooter has no known external pack). */
        fun bothPacks(): Boolean = !externalKnown || extV != null && extA != null

        fun empty(): Boolean = intV == null && intA == null && powerW == null && speedKmh == null && intPct == null && extV == null
    }

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(s: Sample?) {
        if (s == null || s.empty()) return
        val last = samples.peekLast()
        if (last != null && s.monoMs <= last.monoMs) return
        samples.addLast(s)
        while (samples.size > CAPACITY) samples.removeFirst()
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    val size: Int get() = synchronized(this) { samples.size }

    /** Energy block for [fromWallMs, toWallMs], keys and nesting as in KuryePlus EnergySegments.energy(). */
    @Synchronized
    fun block(fromWallMs: Long, toWallMs: Long): Map<String, Any?> {
        val inWindow = ArrayList<Sample>()
        for (s in samples) if (s.wallMs >= fromWallMs && s.wallMs <= toWallMs) inWindow.add(s)
        val out = LinkedHashMap<String, Any?>()
        out["source"] = if (inWindow.isEmpty()) "not_measured" else SOURCE
        out["power_verified"] = false
        val span = maxOf(0L, toWallMs - fromWallMs)
        var covered = 0L
        var wh = 0.0
        var wSum = 0.0
        var wMs = 0L
        // D10: a step counts only with both packs' power (or no known external pack): the internal pack alone, fed by the external
        // one, gave negative or zero figures; such steps leave a coverage gap, so a mostly unread external pack gives null Wh.
        for (i in 1 until inWindow.size) {
            val a = inWindow[i - 1]
            val b = inWindow[i]
            val dt = b.monoMs - a.monoMs
            if (dt <= 0 || dt > MAX_STEP_MS || a.powerW == null || b.powerW == null || !a.bothPacks() || !b.bothPacks()) continue
            covered += dt
            val avg = (a.powerW + b.powerW) / 2
            wh += -avg * dt / 3600000.0
            wSum += avg * dt
            wMs += dt
        }
        val enough = span > 0 && covered >= span * MIN_COVERAGE
        out["internal"] = pack(inWindow, true, enough, fromWallMs, toWallMs)
        out["external"] = pack(inWindow, false, enough, fromWallMs, toWallMs)
        var anyExt = false
        var feeding = false
        for (s in inWindow) {
            if (s.extV != null || s.extA != null || s.extPct != null) anyExt = true
            if (s.extA != null && s.extA < FEEDING_A) feeding = true
        }
        out["external_attached"] = if (anyExt) true else null
        out["external_feeding"] = if (anyExt) feeding else null
        out["vxa_w_avg"] = if (enough && wMs > 0) -wSum / wMs else null
        out["wh"] = if (enough) wh else null
        out["esc_w_avg"] = null
        out["temp_c"] = mean(inWindow) { it.tempC }
        out["scooter_speed_kmh_avg"] = if (enough) mean(inWindow) { it.speedKmh } else null
        out["odometer_km_start"] = edge(inWindow, { it.odometerKm }, false, fromWallMs)
        out["odometer_km_end"] = edge(inWindow, { it.odometerKm }, true, toWallMs)
        return out
    }

    companion object {
        const val SOURCE = "surus_paneli"

        /** Two samples further apart than this are a gap: nothing is integrated across it. */
        const val MAX_STEP_MS = 3000L

        /** A window whose samples cover less than this share of its length reports null Wh/averages instead of a partial figure. */
        const val MIN_COVERAGE = .8

        /** About 12 hours of 1 Hz samples; older ones fall off. */
        const val CAPACITY = 43200

        /** External pack current below this (A, discharge negative) means it is feeding the scooter. */
        const val FEEDING_A = -.1

        /** A start/end value (battery %, odometer) needs a sample within this of the window's edge: a link that connected 15 minutes
         *  into an order leg must not report that minute's battery as the leg's start (slow registers refresh within ~15 s). */
        const val EDGE_MS = 15000L

        /** Builds a sample from the session's register values (the same freshness rules as the panel). */
        fun sample(values: Collection<NinebotMetrics.Value>, wallMs: Long, monoMs: Long, externalBoard: Int): Sample {
            val fast = NinebotMetrics.FAST_FRESH_MS
            val slow = NinebotMetrics.SLOW_FRESH_MS
            val own = NinebotMetrics.PRIMARY_BMS
            var ip = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(own, "percent"), monoMs, slow)
            if (ip == null) ip = NinebotMetrics.freshValue(values, "battery_percent", monoMs, slow)
            var ep: Double? = null
            var ev: Double? = null
            var ea: Double? = null
            if (externalBoard > 0) {
                ep = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(externalBoard, "percent"), monoMs, slow)
                ev = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(externalBoard, "voltage_v"), monoMs, fast)
                ea = NinebotMetrics.freshValue(values, NinebotMetrics.packKey(externalBoard, "current_a_charge_positive"), monoMs, fast)
            }
            return Sample(
                wallMs, monoMs, ip, NinebotMetrics.freshValue(values, NinebotMetrics.packKey(own, "voltage_v"), monoMs, fast),
                NinebotMetrics.freshValue(values, NinebotMetrics.packKey(own, "current_a_charge_positive"), monoMs, fast),
                ep, ev, ea, NinebotMetrics.powerW(values, monoMs, fast), NinebotMetrics.freshValue(values, "vehicle_temperature_c", monoMs, slow),
                NinebotMetrics.freshValue(values, "scooter_speed_kmh", monoMs, fast), NinebotMetrics.freshValue(values, "odometer_km", monoMs, slow),
                externalBoard > 0,
            )
        }

        private fun pack(inWindow: List<Sample>, internal: Boolean, enough: Boolean, from: Long, to: Long): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            m["pct_start"] = edge(inWindow, { if (internal) it.intPct else it.extPct }, false, from)
            m["pct_end"] = edge(inWindow, { if (internal) it.intPct else it.extPct }, true, to)
            m["v_avg"] = if (enough) mean(inWindow) { if (internal) it.intV else it.extV } else null
            m["a_avg"] = if (enough) mean(inWindow) { if (internal) it.intA else it.extA } else null
            return m
        }

        /** The value nearest the window's start (or end), or null when the nearest sample carrying it is more than EDGE_MS away. */
        private fun edge(inWindow: List<Sample>, f: (Sample) -> Double?, fromEnd: Boolean, edgeWallMs: Long): Double? {
            for (i in inWindow.indices) {
                val s = inWindow[if (fromEnd) inWindow.size - 1 - i else i]
                val v = f(s)
                if (v != null) return if (Math.abs(s.wallMs - edgeWallMs) <= EDGE_MS) v else null
            }
            return null
        }

        private fun mean(inWindow: List<Sample>, f: (Sample) -> Double?): Double? {
            var sum = 0.0
            var n = 0
            for (s in inWindow) {
                val v = f(s)
                if (v != null) { sum += v; n++ }
            }
            return if (n == 0) null else sum / n
        }
    }
}
