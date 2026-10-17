package com.kuryelog.logic.core

/** Taze GPS okumalarından ev seçimi (CORE.md 2.2); koordinat kullanıcı onaylayana dek bellekte kalır. */
class HomeFixes {
    data class Sample(val lat: Double, val lon: Double, val accuracyM: Double?, val atMs: Long)
    private val samples = ArrayList<Sample>()
    val best: Sample? get() = samples.minWithOrNull(compareBy<Sample> { it.accuracyM }.thenByDescending { it.atMs })
    fun bestAt(nowMs: Long): Sample? {
        samples.removeAll { nowMs - it.atMs !in 0..MAX_AGE_MS }
        return best
    }

    fun add(s: Sample, nowMs: Long): Boolean {
        val a = s.accuracyM ?: return false
        if (!a.isFinite() || a < 0 || nowMs - s.atMs !in 0..MAX_AGE_MS ||
            HomeShift.parseHome(s.lat.toString(), s.lon.toString()) == null) return false
        bestAt(nowMs); samples.add(s)
        return true
    }

    companion object {
        const val WARN_ACCURACY_M = 25.0
        const val MAX_AGE_MS = 15_000L
        const val COLLECT_MS = 6_000L
        fun needsWarning(s: Sample) = (s.accuracyM ?: Double.POSITIVE_INFINITY) > WARN_ACCURACY_M
    }
}
