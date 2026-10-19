package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint

class RoutePoint(val lat: Double, val lon: Double, val ele: Double)

/** Rotadaki bir yol parçası (kavşaktan kavşağa). [walk]: scooter itilerek yürünür (çizimde kesikli). */
class RouteSeg(val edge: Int, val startM: Double, val lenM: Double, val name: String, val walk: Boolean = false)

/** Rotadaki bir dönüş: [angle] işaretli, + sağa, − sola (derece). */
class RouteTurn(val atM: Double, val angle: Double, val name: String, val lat: Double, val lon: Double) {
    val right get() = angle > 0
}

/** Bir durağa giden tek bacak: sürüş + (varsa) son yürüme (MAP.md 3.9). */
class RouteLeg(
    val ride: List<RoutePoint>,
    val walk: List<RoutePoint>,
    val rideM: Double,
    val walkM: Double,
    val timeS: Double,
    val energyWh: Double,
    val climbM: Double,
    val wrongWayM: Double,
    val cost: Double,
    val streets: List<String>,
    val destination: GeoPoint,
    val warnings: List<String> = emptyList(),
    val quality: Double = 1.0,
    val wrongWayRisk: Double = 0.0,
    val segs: List<RouteSeg> = emptyList(),
    val turns: List<RouteTurn> = emptyList(),
    val startWalk: List<RoutePoint> = emptyList(),
    val startWalkM: Double = 0.0,
    val pushM: Double = 0.0,
) {
    /** Sürüş geometrisindeki yükseklik düşüşü; DEM verisinden yaklaşık değer. */
    val descentM: Double get() = TODO("A6.4")
    val parkPoint: GeoPoint get() = TODO("A6.4")
}

/** Bir mod için tüm duraklar (sıralanmış). */
class Plan(val mode: RideMode, val order: List<Int>, val legs: List<RouteLeg>) {
    val rideM get() = legs.sumOf { it.rideM }
    val walkM get() = legs.sumOf { it.walkM + it.startWalkM }
    val timeS get() = legs.sumOf { it.timeS }
    val energyWh get() = legs.sumOf { it.energyWh }
    val climbM get() = legs.sumOf { it.climbM }
    val descentM get() = legs.sumOf { it.descentM }
    val wrongWayM get() = legs.sumOf { it.wrongWayM }
}

object RouteGeometry {
    /** Noktanın çizgi boyunca ilerlemesi (m): en yakın izdüşümün başlangıca uzaklığı. */
    fun progressAlong(line: List<RoutePoint>, p: GeoPoint): Double = TODO("A6.4")

    /** [leg] çizgisinin scooter itilerek yürünen kısımları (haritada kesikli). */
    fun pushedRuns(leg: RouteLeg): List<List<RoutePoint>> = TODO("A6.4")
}
