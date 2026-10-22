package com.kuryelog.logic.map.calibration

import com.kuryelog.logic.map.surface.SurfaceSignal
import kotlin.math.sqrt

/** Separate E10 pipeline; PR #23 raw 50 Hz / HP 2 Hz is retained.
 * Countdown feeds SurfaceSignal: >=2 s GPS-confirmed standing with reliable
 * world-up prepares offsetReady. beginRun retains the offset and filter.
 * A sensor gap >0.12 s restarts the half window but keeps the offset (as
 * TelemetryService does); SurfaceSignal would otherwise reset offsetReady.
 * Only whole windows within trimmed accepted intervals contribute. */
class RoadCalibrationRoadRms(
    highPassHz: Double = .7, lowPassHz: Double = 35.0, impactThreshold: Double = 6.0,
) {
    data class Result(val rms: Double?, val seconds: Double, val meters: Double,
        val windows: Int, val rejectionReason: String?, val version: Int = 1)
    companion object {
        fun rejectionReason(offsetReady: Boolean, offsetSeen: Boolean, seconds: Double, requestedSeconds: Int): String? = when {
            !offsetReady && offsetSeen -> "Yol RMS: ofset kayıt sırasında kayboldu; yeniden kalibre et"
            !offsetReady -> "Yol RMS: durağan ofset hazırlanmadı; GPS açıkken en az 2 sn dur ve yeniden kalibre et"
            seconds + 1e-9 < 5 -> "Yol RMS: kırpılmış aralıkta en az 5 sn geçerli pencere gerekli (%.1f sn); yön/sensör/GPS verisini kontrol et".format(java.util.Locale.US, seconds)
            seconds + 1e-9 < requestedSeconds*.6 -> "Yol RMS: geçerli pencere süresi istenenin %%60 altında (%.1f / %d sn)".format(java.util.Locale.US, seconds, requestedSeconds)
            else -> null
        }
    }
    private val signal = SurfaceSignal(highPassHz, lowPassHz, impactThreshold)
    private val evidence = arrayListOf<Pair<SurfaceSignal.Window, Double>>()
    private var startedNs = Long.MAX_VALUE
    private var lastNs = 0L
    private var offsetSeen = false
    val offsetReady get() = signal.offsetReady
    fun beginRun(ns: Long) { startedNs = ns; evidence.clear() }
    fun feed(ns: Long, acceleration: DoubleArray, up: DoubleArray?, linear: Boolean,
        stationary: Boolean, speed: Double, gyro: Double? = null): SurfaceSignal.Window? {
        if (ns > lastNs && acceleration.size >= 3) { // SurfaceSignal.feed ile aynı kabul
            if (lastNs != 0L && (ns - lastNs) / 1e9 > .12) signal.restartWindowPreservingOffset()
            lastNs = ns
        }
        val w = signal.feed(ns, acceleration, up, linear, stationary, speed, gyro)
        if (signal.offsetReady) offsetSeen = true
        return w
    }
    fun window(window: SurfaceSignal.Window, meters: Double) {
        if(window.startNs >= startedNs) evidence.add(window to meters)
    }
    fun finish(points: List<CalibrationPoint>, requestedSeconds: Int): Result {
        val intervals = arrayListOf<Pair<Long,Long>>()
        for((a,b) in points.zipWithNext()) {
            val start = a.sample.ns; val end = b.sample.ns
            if(!a.accepted || !b.accepted || end-start !in 1..100_000_000L) continue
            if(intervals.lastOrNull()?.second == start) intervals[intervals.lastIndex] = intervals.last().first to end
            else intervals.add(start to end)
        }
        val windows = evidence.filter { (w,m) -> w.valid && m.isFinite() && m > 0 && m < 40 &&
            intervals.any { w.startNs >= it.first && w.endNs <= it.second } }
        val seconds = windows.sumOf { (w,_) -> (w.endNs-w.startNs)/1e9 }
        val meters = windows.sumOf { it.second }
        val rejection = rejectionReason(offsetReady, offsetSeen, seconds, requestedSeconds)
        return Result(if(rejection == null && meters > 0) sqrt(windows.sumOf { (w,m) -> w.rms*w.rms*m }/meters) else null,
            seconds, meters, windows.size, rejection)
    }
}
