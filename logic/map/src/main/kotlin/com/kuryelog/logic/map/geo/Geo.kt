package com.kuryelog.logic.map.geo

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONObject

/** `lib/core/geo.dart` (MAP.md 2.2). Saf formüller. */
object Geo {
    const val EARTH_R = 6371000.0

    fun rad(d: Double) = d * Math.PI / 180.0

    /** İki nokta arası mesafe (metre). */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLon = rad(lon2 - lon1)
        val s1 = sin(dLat / 2)
        val s2 = sin(dLon / 2)
        val a = s1 * s1 + cos(rad(lat1)) * cos(rad(lat2)) * s2 * s2
        return 2 * EARTH_R * asin(min(1.0, sqrt(a)))
    }

    /** Kuzeyden saat yönünde yön açısı (0–360°). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(rad(lon2 - lon1)) * cos(rad(lat2))
        val x = cos(rad(lat1)) * sin(rad(lat2)) - sin(rad(lat1)) * cos(rad(lat2)) * cos(rad(lon2 - lon1))
        val b = atan2(y, x) * 180 / Math.PI
        return (b + 360) % 360
    }

    /** İki açı arasındaki fark (0–180°). */
    fun angleDiff(a: Double, b: Double): Double {
        val d = (((a - b) % 360) + 360) % 360
        return if (d > 180) 360 - d else d
    }

    fun clampInt(v: Int, lo: Int, hi: Int) = if (v < lo) lo else if (v > hi) hi else v
    fun clampD(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

    /** P noktasının AB doğru parçasına uzaklığı ve parça üzerindeki oran (0–1); sıfır boylu parçada `t = 0`. */
    fun pointToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): SegmentHit {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0.0) 0.0 else ((px - ax) * dx + (py - ay) * dy) / len2
        if (t < 0) t = 0.0
        if (t > 1) t = 1.0
        val ex = px - (ax + t * dx)
        val ey = py - (ay + t * dy)
        return SegmentHit(sqrt(ex * ex + ey * ey), t)
    }
}

class SegmentHit(val dist: Double, val t: Double)

data class GeoPoint(val lat: Double, val lon: Double) {
    fun distanceTo(o: GeoPoint) = Geo.haversine(lat, lon, o.lat, o.lon)
    fun bearingTo(o: GeoPoint) = Geo.bearing(lat, lon, o.lat, o.lon)

    /** Azerbaycan kaba sınırı (Y5/Y6). Enlem ve boylam aralıkları çakışmadığından ters yazılmış (lon,lat) çiftler de ayırt edilir. */
    val isInAzerbaijan: Boolean get() = lat >= 38.3 && lat <= 42.0 && lon >= 44.7 && lon <= 51.0

    fun toJson(): JSONObject = JSONObject().put("lat", lat).put("lon", lon)

    /** Dart `toString`: 6 ondalık `lat,lon`. */
    override fun toString() = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)

    companion object {
        /** Yalnız sayısal `lat`/`lon` kabul edilir; değilse null. */
        fun fromJson(j: Any?): GeoPoint? {
            if (j !is JSONObject) return null
            val la = j.opt("lat")
            val lo = j.opt("lon")
            if (la !is Number || lo !is Number) return null
            return GeoPoint(la.toDouble(), lo.toDouble())
        }
    }
}

data class BBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(lat: Double, lon: Double) = lat >= south && lat <= north && lon >= west && lon <= east

    val center get() = GeoPoint((south + north) / 2, (west + east) / 2)

    /** Overpass sırası: (güney,batı,kuzey,doğu). */
    val overpass get() = "$south,$west,$north,$east"

    /** Kutuyu yaklaşık [maxDeg] boyutlu parçalara böler (Overpass'i yormamak için). */
    fun split(maxDeg: Double): List<BBox> {
        val ny = Geo.clampInt(ceil((north - south) / maxDeg).toInt(), 1, 50)
        val nx = Geo.clampInt(ceil((east - west) / maxDeg).toInt(), 1, 50)
        val dy = (north - south) / ny
        val dx = (east - west) / nx
        return buildList {
            for (i in 0 until ny) for (j in 0 until nx) {
                add(BBox(south + i * dy, west + j * dx, south + (i + 1) * dy, west + (j + 1) * dx))
            }
        }
    }
}

object Baku {
    val center = GeoPoint(40.4093, 49.8671)

    /** Badamdar/Bayıl – Yasamal – Nərimanov/Gənclik – Xətai/Ağ şəhər. */
    val centralBox = BBox(40.335, 49.760, 40.445, 49.960)

    /** Daha geniş Bakü (daha büyük indirme). */
    val wideBox = BBox(40.280, 49.580, 40.650, 50.250)
}

/** Küçük alanlar için hızlı düzlem izdüşümü (metre). */
class LocalProjection(val refLat: Double, val refLon: Double) {
    private val kx = Geo.EARTH_R * Math.PI / 180 * cos(Geo.rad(refLat))
    private val ky = Geo.EARTH_R * Math.PI / 180

    fun x(lon: Double) = (lon - refLon) * kx
    fun y(lat: Double) = (lat - refLat) * ky
}
