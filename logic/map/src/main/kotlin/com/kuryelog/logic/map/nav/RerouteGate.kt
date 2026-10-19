package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint

/** Rotanın yeniden hesap nedeni (MAP.md 7.4). */
enum class ReplanReason { ROUTE, REROUTE, PREFS, HOURS, BATTERY }

/** H2: "kapı ~N m uzakta" uyarısı yeni rotada hep; sapma/batarya hesabında yalnız durak ya da uyarı değişince (anahtar `"<lat>,<lon>|<uyarı>"`). */
fun shouldShowRouteWarning(reason: ReplanReason, key: String, lastKey: String?): Boolean = TODO("A6.6")

/** H3: sapma/batarya hesabı başarısızsa eski rota korunur; yeni rota, kural ve saat değişince korunmaz. */
fun keepPlanOnFailure(reason: ReplanReason): Boolean = TODO("A6.6")

/**
 * `reroute_gate.dart` (MAP.md 7.7): eşik `clamp(1.5·doğruluk + 12, 25, 45)` m aşan **art arda 2** taze fix, son denemeden 2 sn; başarısız denemeden sonra
 * 10 sn geçmeden ve başarısız konumdan 30 m'den fazla gidilmeden yeniden denenmez. İskelet (A6.2b).
 */
class RerouteGate {
    /** Doğruluk 0–30 m ve fix yaşı ±5 sn değilse sayaç sıfırlanır. True = yeniden rota iste. */
    fun feed(distanceM: Double, accuracyM: Double, fixAtMs: Long, nowMs: Long, here: GeoPoint? = null): Boolean = TODO("A6.6")

    fun failed(nowMs: Long, here: GeoPoint?): Unit = TODO("A6.6")

    fun reset(): Unit = TODO("A6.6")
}
