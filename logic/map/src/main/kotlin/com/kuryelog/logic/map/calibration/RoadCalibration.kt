package com.kuryelog.logic.map.calibration

import kotlin.math.*

/** Android-free SI signal engine. GPS speed intervals also exclude the preceding
 * accelerometer samples, so braking is not credited before the next GPS callback. */
fun calibrationQuality(level: Int): String {
    require(level in 1..10)
    return when(level) { 9, 10 -> "veryGood"; 7, 8 -> "good"; 5, 6 -> "middle"; 3, 4 -> "bad"; else -> "veryBad" }
}
data class CalibrationConfig(val quality: String, val seconds: Int, val mode: String, val profile: String,
    val level: Int? = null) {
    init {
        require(quality in listOf("veryGood", "good", "middle", "bad", "veryBad"))
        require(level == null || calibrationQuality(level) == quality)
        require(seconds in 1..600)
        require(mode in listOf("fixed", "variable"))
        require(profile.isNotBlank())
    }
}
data class CalibrationFix(val ns: Long, val utc: Long, val speed: Double, val lat: Double,
    val lon: Double, val accuracy: Double)
data class CalibrationSample(val ns: Long, val utc: Long, val acceleration: List<Double>,
    val gravity: List<Double>, val vertical: Double?, val filtered: Double?)
data class CalibrationPoint(val sample: CalibrationSample, val fix: CalibrationFix?,
    val speed: Double?, val longitudinal: Double?, val accepted: Boolean, val reason: String)
data class CalibrationResult(val validSeconds: Double, val rms: Double?, val averageKmh: Double?,
    val valid: Boolean, val speedNotConstant: Boolean, val points: List<CalibrationPoint>,
    val rejectionReason: String? = null, val targetKmh: Double? = null,
    val speedTrimmedSeconds: Double = 0.0,
    val roadRms: Double? = null, val roadRmsVersion: Int? = null,
    val roadValidSeconds: Double = 0.0, val roadMeters: Double = 0.0,
    val roadWindows: Int = 0, val roadRmsRejectionReason: String? = null)

class CalibrationHighPass(private val hz: Double = 2.0) {
    private var lastNs = 0L
    private var lastInput = 0.0
    private var output = 0.0
    fun feed(ns: Long, input: Double): Double? {
        if (!input.isFinite()) return null
        val dt = (ns - lastNs) / 1e9
        if (lastNs == 0L || dt <= 0 || dt > .1) {
            lastNs = ns; lastInput = input; output = 0.0; return null
        }
        val rc = 1 / (2 * PI * hz)
        output = rc / (rc + dt) * (output + input - lastInput)
        lastInput = input; lastNs = ns
        return output
    }
}

class RoadCalibration(val config: CalibrationConfig, val startedNs: Long) {
    private val hp = CalibrationHighPass()
    private val samples = ArrayList<CalibrationSample>()
    private val fixes = ArrayList<CalibrationFix>()
    fun fix(f: CalibrationFix) {
        if (listOf(f.speed, f.lat, f.lon, f.accuracy).all { it.isFinite() } &&
            f.speed >= 0 && f.accuracy in 0.0..25.0 &&
            (fixes.isEmpty() || f.ns > fixes.last().ns)) fixes.add(f)
    }
    fun sample(ns: Long, utc: Long, a: List<Double>, gravity: List<Double>?) {
        if (ns < startedNs || ns > startedNs + config.seconds * 1_000_000_000L ||
            a.size != 3 || !a.all { it.isFinite() } || samples.size >= 65000) return
        val g = gravity?.takeIf { it.size == 3 && it.all { v -> v.isFinite() } }
        val norm = g?.let { sqrt(it.sumOf { v -> v*v }) } ?: 0.0
        val vertical = if (norm in 7.0..12.0) a.indices.sumOf { a[it] * g!![it] / norm } - norm else null
        val filtered = vertical?.let { hp.feed(ns, it) }
        samples.add(CalibrationSample(ns, utc, a.toList(), g?.toList() ?: emptyList(), vertical, filtered))
    }
    fun finish(): CalibrationResult {
        var index = 0
        val candidates = samples.map { s ->
            while (index + 1 < fixes.size && fixes[index + 1].ns <= s.ns) index++
            val before = fixes.getOrNull(index)?.takeIf { it.ns <= s.ns }
            val after = fixes.getOrNull(index + 1)
            val old = fixes.getOrNull(index - 1)
            val pair = if (before != null && after != null && after.ns-before.ns <= 2_500_000_000L)
                before to after else if (before != null && old != null && before.ns-old.ns <= 2_500_000_000L)
                old to before else null
            val fresh = before != null && s.ns-before.ns <= 2_000_000_000L && pair != null
            val accel = pair?.let { (it.second.speed-it.first.speed) / ((it.second.ns-it.first.ns)/1e9) }
            val speed = if (fresh && after != null && pair?.second == after) {
                before!!.speed + (after.speed-before.speed)*(s.ns-before.ns).toDouble()/(after.ns-before.ns)
            } else if (fresh) before!!.speed else null
            val reason = when {
                s.filtered == null || s.ns-startedNs < 500_000_000L -> "sensor_warmup"
                !fresh -> "gps_missing_or_stale"
                speed!! * 3.6 < 8 -> "below_8_kmh"
                abs(accel!!) > 1.5 -> "braking_or_acceleration"
                else -> "accepted"
            }
            CalibrationPoint(s, before, speed, accel, reason == "accepted", reason)
        }
        // Time-weighted median of eligible intervals is the run's steady target.
        // Both modes use the same ±10% gate. No rejected interval/gap is bridged.
        fun interval(a: CalibrationPoint, b: CalibrationPoint): Double {
            val dt = (b.sample.ns - a.sample.ns) / 1e9
            return if (a.accepted && b.accepted && dt > 0 && dt <= .1) dt else 0.0
        }
        val weighted = candidates.zipWithNext().mapNotNull { (a, b) ->
            val dt = interval(a, b)
            if (dt > 0) ((a.speed!! + b.speed!!) * 1.8) to dt else null
        }.sortedBy { it.first }
        val eligibleSeconds = weighted.sumOf { it.second }
        var accumulated = 0.0
        val target = weighted.firstOrNull { (_, dt) ->
            accumulated += dt; accumulated >= eligibleSeconds / 2
        }?.first
        val points = candidates.map { p ->
            if (p.accepted && target != null && abs(p.speed!! * 3.6 - target) > target * .1 + 1e-9)
                p.copy(accepted = false, reason = "speed_outside_10_percent") else p
        }
        var duration = 0.0; var energy = 0.0; var speedSum = 0.0
        for ((a, b) in points.zipWithNext()) {
            val dt = interval(a, b)
            if (dt <= 0) continue
            duration += dt
            val z = b.sample.filtered!!; val prevZ = a.sample.filtered!!
            energy += (z * z + prevZ * prevZ) * .5 * dt
            speedSum += (a.speed!! + b.speed!!) * 1.8 * dt
        }
        val rejection = when {
            duration + 1e-9 < 5 -> "En az 5 sn geçerli veri gerekli (%.1f sn)".format(java.util.Locale.US, duration)
            duration + 1e-9 < config.seconds * .6 ->
                "Sabit hızda geçerli süre istenenin %%60 altında (%.1f / %d sn)".format(java.util.Locale.US, duration, config.seconds)
            else -> null
        }
        return CalibrationResult(duration, if (duration > 0) sqrt(energy/duration) else null,
            if (duration > 0) speedSum/duration else null, rejection == null,
            points.any { it.reason == "speed_outside_10_percent" }, points, rejection, target,
            max(0.0, eligibleSeconds - duration))
    }
}
