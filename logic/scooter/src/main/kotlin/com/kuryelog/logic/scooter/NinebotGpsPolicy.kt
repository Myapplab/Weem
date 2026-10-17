package com.kuryelog.logic.scooter

/** Conservative GPS request cadence: preserve 1 Hz while moving, uncertain or acquiring a fix (SCOOTER.md 7.2; NB `NinebotGpsPolicy.java`). */
class NinebotGpsPolicy {
    private var stillSince = -1L
    private var lastFix = -1L

    fun interval(now: Long, speedMps: Double?, accuracyM: Double?, speedAccuracyMps: Double?): Long {
        if (lastFix >= 0 && (now < lastFix || now - lastFix > 5000)) stillSince = -1
        lastFix = now
        val still = speedMps != null && speedMps >= 0 && speedMps <= .3 && accuracyM != null && accuracyM > 0 && accuracyM <= 15 &&
            speedAccuracyMps != null && speedAccuracyMps >= 0 && speedAccuracyMps <= .5
        if (!still) { stillSince = -1; return 1000 }
        if (stillSince < 0) stillSince = now
        return if (now - stillSince >= 15000) 3000 else 1000
    }

    fun reset() {
        stillSince = -1
        lastFix = -1
    }
}
