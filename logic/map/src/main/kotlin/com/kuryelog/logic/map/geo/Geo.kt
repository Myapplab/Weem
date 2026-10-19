package com.kuryelog.logic.map.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_R = 6_371_000.0

/** İki nokta arası mesafe (m); YolGo `haversine` (`MAP.md` 2.2). */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val s1 = sin(dLat / 2)
    val s2 = sin(dLon / 2)
    val a = s1 * s1 + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * s2 * s2
    return 2 * EARTH_R * asin(min(1.0, sqrt(a)))
}

data class GeoPoint(val lat: Double, val lon: Double) {
    fun distanceTo(o: GeoPoint): Double = haversine(lat, lon, o.lat, o.lon)
}

/** `BBox(south, west, north, east)`; `contains` kapalı aralık. */
data class BBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lon: Double): Boolean = lat in south..north && lon in west..east
    /** Overpass sınır kutusu dizgesi. */
    val overpass: String get() = "$south,$west,$north,$east"
}

object Baku {
    val centralBox = BBox(40.335, 49.760, 40.445, 49.960)
    val wideBox = BBox(40.280, 49.580, 40.650, 50.250)
}
