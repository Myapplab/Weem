package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint

/** Rotanın yeniden hesap nedeni (MAP.md 7.4). */
enum class ReplanReason { ROUTE, REROUTE, PREFS, HOURS, BATTERY }

/** H2: "kapı ~N m uzakta" uyarısı yeni rotada hep; sapma/batarya hesabında yalnız durak ya da uyarı değişince (anahtar `"<lat>,<lon>|<uyarı>"`). */
fun shouldShowRouteWarning(reason: ReplanReason, key: String, lastKey: String?): Boolean = reason == ReplanReason.ROUTE || key != lastKey

/** H3: sapma/batarya hesabı başarısızsa eski rota korunur; yeni rota, kural ve saat değişince korunmaz. */
fun keepPlanOnFailure(reason: ReplanReason): Boolean = reason == ReplanReason.REROUTE || reason == ReplanReason.BATTERY

/**
 * `reroute_gate.dart` (MAP.md 7.7): eşik `clamp(1.5·doğruluk + 12, 25, 45)` m aşan **art arda 2** taze fix, son denemeden 2 sn; başarısız denemeden sonra
 * 10 sn geçmeden ve başarısız konumdan 30 m'den fazla gidilmeden yeniden denenmez.
 */
class RerouteGate {
    /** Doğruluk 0–30 m ve fix yaşı ±5 sn değilse sayaç sıfırlanır. True = yeniden rota iste. */
    private var hits = 0
    private var lastAttemptMs: Long? = null
    private var failedAtMs: Long? = null
    private var failedFrom: GeoPoint? = null

    fun feed(distanceM: Double, accuracyM: Double, fixAtMs: Long, nowMs: Long, here: GeoPoint? = null): Boolean {
        val age = nowMs - fixAtMs
        if (!accuracyM.isFinite() || accuracyM < 0 || accuracyM > 30 || age < -5000 || age > 5000) {
            hits = 0
            return false
        }
        val threshold = (accuracyM * 1.5 + 12).coerceIn(25.0, 45.0)
        hits = if (distanceM > threshold) hits + 1 else 0
        val last = lastAttemptMs
        if (hits < 2 || (last != null && nowMs - last < 2000)) return false
        val failedAt = failedAtMs
        val from = failedFrom
        if (failedAt != null && nowMs - failedAt < FAIL_HOLD_MS && (from == null || here == null || from.distanceTo(here) <= FAIL_MOVE_M)) return false
        hits = 0
        lastAttemptMs = nowMs
        return true
    }

    fun failed(nowMs: Long, here: GeoPoint?) {
        failedAtMs = nowMs
        failedFrom = here
    }

    fun reset() {
        hits = 0
        failedAtMs = null
        failedFrom = null
    }

    companion object {
        const val FAIL_HOLD_MS = 10_000L
        const val FAIL_MOVE_M = 30.0
    }
}
