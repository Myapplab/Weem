package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.geo.LocalProjection
import kotlin.math.abs

class RoutePoint(val lat: Double, val lon: Double, val ele: Double)

/** Rotadaki bir yol parçası (kavşaktan kavşağa; "Yol yok" işaretlemek için). [walk]: scooter itilerek yürünür (çizimde kesikli). */
class RouteSeg(
    /** Graftaki kenar. */
    val edge: Int,
    /** Sürüş başından itibaren (m). */
    val startM: Double,
    val lenM: Double,
    val name: String,
    val walk: Boolean = false,
)

/** Rotadaki bir dönüş: [angle] işaretli, + sağa, − sola (derece); [name] dönülen sokak (boş olabilir). */
class RouteTurn(val atM: Double, val angle: Double, val name: String, val lat: Double, val lon: Double) {
    val right get() = angle > 0

    /** 0: hafif, 1: normal, 2: keskin / geri dönüş. */
    val sharpness: Int get() = if (abs(angle) < 60) 0 else if (abs(angle) < 135) 1 else 2
}

/** Bir durağa giden tek bacak: sürüş + (varsa) son yürüme — YolGo `route.dart` (MAP.md 3.9). */
class RouteLeg(
    /** Sürüş geometrisi (yükseklikli). */
    val ride: List<RoutePoint>,
    /** Park noktası → kapı (boş olabilir). */
    val walk: List<RoutePoint>,
    /** Sürüş çizgisinin boyu; itilen metreyi içerir. */
    val rideM: Double,
    val walkM: Double,
    val timeS: Double,
    val energyWh: Double,
    val climbM: Double,
    /** Onaylı sokaklarda ters yön metresi. */
    val wrongWayM: Double,
    /** Seçilen moddaki toplam maliyet. */
    val cost: Double,
    /** Geçilen sokak adları (sırayla, art arda tekrar yok). */
    val streets: List<String>,
    val destination: GeoPoint,
    val warnings: List<RouteMsg> = emptyList(),
    /** Sürüş boyunca uzunluk ağırlıklı yol kalitesi (0–1). */
    val quality: Double = 1.0,
    /** Ters yön kısımlarının ortalama sorun oranı (0–1, ters yön yoksa 0). */
    val wrongWayRisk: Double = 0.0,
    val segs: List<RouteSeg> = emptyList(),
    /** Kavşaklardaki belirgin dönüşler (sırayla). */
    val turns: List<RouteTurn> = emptyList(),
    /** Yaya yolundan en yakın sürülebilir yola yürüyüş (konum → sürüşün başı; çoğu zaman boş). */
    val startWalk: List<RoutePoint> = emptyList(),
    val startWalkM: Double = 0.0,
    /** [ride] çizgisinin scooter itilerek yürünen kısmı (m; [RouteSeg.walk] parçaları). */
    val pushM: Double = 0.0,
) {
    /** Sürüş geometrisindeki yükseklik düşüşü; DEM verisinden yaklaşık değer. */
    val descentM: Double
        get() {
            var down = 0.0
            for (i in 1 until ride.size) {
                val delta = ride[i - 1].ele - ride[i].ele
                if (delta.isFinite() && delta > 0) down += delta
            }
            return down
        }

    val parkPoint: GeoPoint get() = if (ride.isEmpty()) destination else GeoPoint(ride.last().lat, ride.last().lon)

    fun withWarnings(w: List<RouteMsg>) = RouteLeg(
        ride, walk, rideM, walkM, timeS, energyWh, climbM, wrongWayM, cost, streets, destination, w, quality, wrongWayRisk, segs, turns,
        startWalk, startWalkM, pushM,
    )
}

/** Bir mod için tüm duraklar (sıralanmış); [order] durakların 0 tabanlı ziyaret sırası. */
class Plan(val mode: RideMode, val order: List<Int>, val legs: List<RouteLeg>) {
    val rideM get() = legs.fold(0.0) { a, l -> a + l.rideM }
    val walkM get() = legs.fold(0.0) { a, l -> a + l.walkM + l.startWalkM }
    val timeS get() = legs.fold(0.0) { a, l -> a + l.timeS }
    val energyWh get() = legs.fold(0.0) { a, l -> a + l.energyWh }
    val climbM get() = legs.fold(0.0) { a, l -> a + l.climbM }
    val descentM get() = legs.fold(0.0) { a, l -> a + l.descentM }
    val wrongWayM get() = legs.fold(0.0) { a, l -> a + l.wrongWayM }
    val warnings: List<RouteMsg> get() = legs.flatMap { it.warnings }

    /** Uzunluk ağırlıklı yol kalitesi (0–1). */
    val quality: Double
        get() {
            val m = rideM
            if (m <= 0) return 1.0
            return legs.fold(0.0) { a, l -> a + l.quality * l.rideM } / m
        }

    /** Ters yön metresi ağırlıklı sorun oranı (0–1). */
    val wrongWayRisk: Double
        get() {
            val m = wrongWayM
            if (m <= 0) return 0.0
            return legs.fold(0.0) { a, l -> a + l.wrongWayRisk * l.wrongWayM } / m
        }
}

object RouteGeometry {
    /** Noktanın çizgi boyunca ilerlemesi (m): en yakın izdüşümün başlangıca uzaklığı. */
    fun progressAlong(line: List<RoutePoint>, p: GeoPoint): Double = project(line, p).along

    /** Çizgiye izdüşüm (`RouteLine.project`): [Projection.along] başlangıca uzaklık (m), [Projection.dist] dik uzaklık (m), [Projection.length] çizgi uzunluğu (m). */
    class Projection(val along: Double, val dist: Double, val length: Double)

    fun project(line: List<RoutePoint>, p: GeoPoint): Projection {
        if (line.size < 2) return Projection(0.0, Double.POSITIVE_INFINITY, 0.0)
        val proj = LocalProjection(p.lat, p.lon)
        var best = Double.POSITIVE_INFINITY
        var bestAlong = 0.0
        var along = 0.0
        for (i in 0 until line.size - 1) {
            val a = line[i]
            val b = line[i + 1]
            val seg = Geo.haversine(a.lat, a.lon, b.lat, b.lon)
            val r = Geo.pointToSegment(0.0, 0.0, proj.x(a.lon), proj.y(a.lat), proj.x(b.lon), proj.y(b.lat))
            if (r.dist < best) {
                best = r.dist
                bestAlong = along + r.t * seg
            }
            along += seg
        }
        return Projection(bestAlong, best, along)
    }

    /** [leg] çizgisinin scooter itilerek yürünen kısımları ([RouteSeg.walk]; haritada kesikli); bitişik parçalar birleşir. */
    fun pushedRuns(leg: RouteLeg): List<List<RoutePoint>> {
        val spans = leg.segs.filter { it.walk }.map { doubleArrayOf(it.startM, it.startM + it.lenM) }
        if (spans.isEmpty() || leg.ride.size < 2) return emptyList()
        val merged = ArrayList<DoubleArray>()
        for (s in spans) {
            if (merged.isNotEmpty() && s[0] - merged.last()[1] < 0.5) merged[merged.size - 1] = doubleArrayOf(merged.last()[0], s[1]) else merged += s
        }
        val out = ArrayList<List<RoutePoint>>()
        for ((from, to) in merged.map { it[0] to it[1] }) {
            val run = ArrayList<RoutePoint>()
            var at = 0.0
            for (i in 1 until leg.ride.size) {
                val a = leg.ride[i - 1]
                val b = leg.ride[i]
                val d = Geo.haversine(a.lat, a.lon, b.lat, b.lon)
                val s0 = at
                val s1 = at + d
                at = s1
                if (s1 < from || s0 > to || d <= 0) continue
                fun lerp(m: Double): RoutePoint {
                    val t = ((m - s0) / d).coerceIn(0.0, 1.0)
                    return RoutePoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t, a.ele + (b.ele - a.ele) * t)
                }
                if (run.isEmpty()) run += if (from > s0) lerp(from) else a
                run += if (to < s1) lerp(to) else b
            }
            if (run.size >= 2) out += run
        }
        return out
    }
}
