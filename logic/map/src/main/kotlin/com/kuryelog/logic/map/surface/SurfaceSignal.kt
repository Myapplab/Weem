package com.kuryelog.logic.map.surface

import kotlin.math.*

/** Pure JVM signal processor. SI units; timestamps are Android sensor boot nanoseconds.
 * Frames stay on the sensor thread; only non-overlapping summaries leave it.
 * No speed division and no interpretation of traffic stops as road quality.
 */
class SurfaceSignal(
    val highPassHz: Double = .7, val lowPassHz: Double = 35.0,
    val impactThreshold: Double = 6.0, val windowNs: Long = 1_500_000_000L,
) {
    data class Impact(val atNs: Long, var peak: Double, val polarity: Int, var endNs: Long)
    data class Window(val startNs: Long, val endNs: Long, val hz: Double, val rms: Double,
        val jerk: Double, val gyro: Double?, val bands: List<Double>, val impacts: List<Impact>,
        val valid: Boolean, val reason: String, val horizontalRms: Double)
    private var lastNs = 0L
    private var startNs = 0L
    private var firstSampleNs = 0L
    private var hp = 0.0
    private var lp = 0.0
    private var previousInput = 0.0
    private var previousFiltered = 0.0
    private var bias = 0.0
    private var biasSum = 0.0
    private var biasN = 0
    private var stationarySince = 0L
    private var sum2 = 0.0
    private var jerk2 = 0.0
    private var horizontal2 = 0.0
    private var gyro2 = 0.0
    private var gyroN = 0
    private var samples = 0
    private var invalid = false
    private var invalidReason = ""
    private val values = ArrayList<Double>(200)
    private val hits = ArrayList<Impact>()
    private var pending: Impact? = null
    private var belowSince = 0L
    private var previousHit: Impact? = null
    var offsetReady = false; private set

    fun reset() {
        lastNs = 0; startNs = 0; hp = 0.0; lp = 0.0; previousInput = 0.0
        previousFiltered = 0.0; pending = null; previousHit = null; belowSince = 0
        stationarySince = 0; biasN = 0; biasSum = 0.0; offsetReady = false; bias = 0.0
        clearWindow()
    }
    fun restartWindowPreservingOffset() {
        val ready = offsetReady; val offset = bias
        reset(); offsetReady = ready; bias = offset
    }
    private fun clearWindow() {
        sum2 = 0.0; jerk2 = 0.0; horizontal2 = 0.0; gyro2 = 0.0
        gyroN = 0; samples = 0; firstSampleNs = 0L; invalid = false; invalidReason = ""; values.clear(); hits.clear()
    }
    /** up is rotation-matrix world Z row, or normalized gravity fallback.
     * Linear acceleration has gravity removed by Android: do not remove it twice.
     */
    fun feed(atNs: Long, acceleration: DoubleArray, up: DoubleArray?, linear: Boolean,
             stationary: Boolean, speed: Double, gyroMagnitude: Double? = null): Window? {
        if (atNs <= lastNs || acceleration.size < 3) return null
        val oldNs = lastNs
        val dt = if (oldNs == 0L) .01 else (atNs - oldNs) / 1e9
        if (dt > .12) { reset(); lastNs = atNs; return null }
        lastNs = atNs
        if (startNs == 0L) startNs = atNs
        val norm = if (up == null || up.size < 3) 0.0 else sqrt(up.take(3).sumOf { it * it })
        val reliable = norm in .85..1.15 && acceleration.all { it.isFinite() && abs(it) < 100 }
        val verticalWithGravity = if (reliable) (0..2).sumOf { acceleration[it] * up!![it] / norm } else 0.0
        val vertical = verticalWithGravity - if (linear) 0.0 else 9.80665
        // Only a physically stationary observation can calibrate sensor zero. This is
        // NOT the smooth-road reference; moving samples never change this offset.
        if (stationary && reliable) {
            if (stationarySince == 0L) { stationarySince = atNs; biasSum = 0.0; biasN = 0 }
            biasSum += vertical; biasN++
            if (atNs - stationarySince >= 2_000_000_000L) {
                bias = biasSum / biasN; offsetReady = true
            }
        } else { stationarySince = 0; biasN = 0; biasSum = 0.0 }
        val input = vertical - bias
        val hpAlpha = (1 / (2 * PI * highPassHz)) / (1 / (2 * PI * highPassHz) + dt)
        hp = hpAlpha * (hp + input - previousInput); previousInput = input
        val cutoff = min(lowPassHz, .4 / dt)
        val lpAlpha = dt / (1 / (2 * PI * cutoff) + dt)
        lp += lpAlpha * (hp - lp)
        val derivative = (lp - previousFiltered) / dt; previousFiltered = lp
        if (!reliable || stationary || !offsetReady) { invalid = true
            invalidReason = if (!reliable) "orientation_or_saturation" else if (stationary) "stationary" else "stationary_offset_missing" }
        // Horizontal maneuvers are diagnostic context; they never erase vertical shocks.
        val total2 = acceleration.take(3).sumOf { it * it }
        horizontal2 += max(0.0, total2 - verticalWithGravity * verticalWithGravity)
        if (samples == 0) firstSampleNs = atNs
        sum2 += lp * lp; jerk2 += derivative * derivative; values.add(lp); samples++
        if (gyroMagnitude != null && gyroMagnitude.isFinite()) { gyro2 += gyroMagnitude * gyroMagnitude; gyroN++ }
        detectImpact(atNs, lp, speed)
        if (atNs - startNs < windowNs) return null
        val elapsed = (atNs - startNs) / 1e9
        val hz = if (samples > 1) (samples - 1) / ((atNs - firstSampleNs) / 1e9) else 0.0
        val valid = !invalid && hz >= 40 && elapsed <= 2.2
        val w = Window(startNs, atNs, hz, sqrt(sum2 / samples), sqrt(jerk2 / samples),
            if (gyroN == 0) null else sqrt(gyro2 / gyroN), if (valid) bandPowers(values, hz) else emptyList(),
            hits.map { it.copy() }, valid, if (hz < 40) "sampling_too_slow" else invalidReason,
            sqrt(horizontal2 / samples))
        startNs = atNs; clearWindow(); return w
    }
    private fun detectImpact(at: Long, value: Double, speed: Double) {
        if (abs(value) >= impactThreshold) {
            val p = pending
            if (p == null) pending = Impact(at, abs(value), if (value >= 0) 1 else -1, at)
            else { p.peak = max(p.peak, abs(value)); p.endNs = at }
            belowSince = 0
        } else if (pending != null) {
            if (belowSince == 0L) belowSince = at
            if (at - belowSince >= 70_000_000L) {
                val hit = pending!!; pending = null
                val previous = previousHit
                val wheelDelay = if (speed >= 2.2) .95 / speed else 0.0
                val delta = if (previous == null) 10.0 else (hit.atNs - previous.atNs) / 1e9
                // Merge only a narrow, same-shape wheel-repeat candidate, never an
                // arbitrary string of peaks. IDs survive window boundaries.
                val repeat = previous != null && wheelDelay > .08 && wheelDelay < .44 &&
                    abs(delta - wheelDelay) < .04 && hit.polarity == previous.polarity &&
                    hit.peak / previous.peak in .75..1.33 && (hit.endNs - hit.atNs) < 120_000_000L
                if (repeat) {
                    previous!!.peak = max(previous.peak, hit.peak); previous.endNs = hit.endNs
                    // Re-emit the same event ID if its wheel repeat crosses a window.
                    // Consumers deduplicate the ID and retain the updated peak.
                    if (hits.none { it.atNs == previous.atNs }) hits.add(previous)
                }
                else { hits.add(hit); previousHit = hit }
            }
        }
    }
    private fun bandPowers(data: List<Double>, hz: Double): List<Double> {
        if (data.size < 32 || hz < 40) return emptyList()
        val n = data.size
        val out = DoubleArray(3)
        // Hann-windowed DFT for auxiliary 1–5 / 5–15 / 15–35 Hz powers.
        // It is deliberately not a universal asphalt/gravel classifier.
        for (k in 1..(n / 2)) {
            val frequency = k * hz / n
            val band = when { frequency in 1.0..<5.0 -> 0; frequency in 5.0..<15.0 -> 1
                frequency >= 15 && frequency <= min(35.0, hz / 2) -> 2; else -> -1 }
            if (band < 0) continue
            var real = 0.0; var imaginary = 0.0
            for (i in 0 until n) {
                val v = data[i] * (.5 - .5 * cos(2 * PI * i / (n - 1)))
                real += v * cos(2 * PI * k * i / n); imaginary -= v * sin(2 * PI * k * i / n)
            }
            out[band] += 2 * (real * real + imaginary * imaginary) / (n * n * .375)
        }
        return out.toList()
    }
}
