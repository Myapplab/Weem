package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.route.RoutePoint
import org.json.JSONObject

/** Rotadan sapma: [maxOffM] en büyük uzaklık; [endMs] null = sürüyor. */
class RouteDeviation(val startMs: Long, var endMs: Long?, var maxOffM: Double)

/**
 * Rota kaydı (`duzyol-route-v1`, MAP.md 12.5): planlanan çizgi, sapmalar, ortalama hız; adres metni yazılmaz. Sapma iki fix sonra açılır, dönünce ya da yeniden
 * hesapta kapanır. İskelet (A6.2b): gövdeler A6.9'da.
 */
class RouteRecorder(
    val startedAtMs: Long,
    val mode: String,
    val planned: List<RoutePoint>,
    val destination: GeoPoint,
    val plannedM: Double,
    val plannedS: Double,
) {
    val deviations: List<RouteDeviation> get() = TODO("A6.9")
    val plans: List<List<RoutePoint>> get() = TODO("A6.9")

    fun addFix(atMs: Long, lat: Double, lon: Double, accuracy: Double = 5.0, speedMps: Double = 0.0, off: Double = 0.0): Unit = TODO("A6.9")

    fun replanned(atMs: Long, reason: String, line: List<RoutePoint>): Unit = TODO("A6.9")

    /** Ortalama sürüş hızı (km/s); yeterli veri yoksa null. */
    fun avgKmh(nowMs: Long): Double? = TODO("A6.9")

    fun toJson(nowMs: Long, reason: String): JSONObject = TODO("A6.9")
}
