package com.kuryelog.logic.economics

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats OfferGeo.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Where an offer is, from what the offer card shows (SPEC 8.1, user goals H1/H4). Offline only: Plus Codes
 * (Open Location Code) and Wolt's DMS coordinates are decoded on the phone; a plain street address gives null
 * and is never geocoded. Zones and hour blocks are identical to `ref.zone_of` / `ref.hour_block`.
 *
 * The Plus Code arithmetic is a line-by-line port of `docs/ekonomi-v2/vektorler/olc_ref.py`, including Python's
 * float `//` and `%`, so codes match `economics/offer_geo_vectors.json` exactly. Unlike the reference, codes that
 * are not valid Open Location Codes (odd prefix length, separator misplaced, out-of-range first digits) give
 * null instead of a meaningless point. Pure Kotlin: no Android imports.
 */
object OfferGeo {
    data class LatLon(val lat: Double, val lon: Double)

    /** A coordinate read from card text; [source] is [SOURCE_DMS] or [SOURCE_PLUS_CODE]. */
    data class CardPoint(val lat: Double, val lon: Double, val source: String)

    const val SOURCE_DMS = "dms"
    const val SOURCE_PLUS_CODE = "plus_code"
    const val UNKNOWN = "unknown"
    const val EARTH_RADIUS_KM = 6371.0088

    /** Reference for short Plus Codes when the courier's GPS is unknown (Baku centre). */
    val BAKU_REFERENCE = LatLon(40.4093, 49.8671)

    /** Default zone hub (YolGo `Baku.center`); callers pass the pickup median instead once it exists (SPEC 8.1). */
    val HUB_DEFAULT = LatLon(40.4093, 49.8671)

    /** `ref.ZONES`: (zone, from km inclusive, to km exclusive). */
    val ZONES: List<Triple<String, Double, Double>> = listOf(
        Triple("Z1", 0.0, 2.5), Triple("Z2", 2.5, 6.0), Triple("Z3", 6.0, 12.0),
        Triple("Z4", 12.0, Double.POSITIVE_INFINITY))

    /** `ref.HOUR_BLOCKS`: (block, from minute of day inclusive, to minute exclusive). */
    val HOUR_BLOCKS: List<Triple<String, Int, Int>> = listOf(
        Triple("B0", 0, 480), Triple("B1", 480, 705), Triple("B2", 705, 900),
        Triple("B3", 900, 1080), Triple("B4", 1080, 1290), Triple("B5", 1290, 1440))

    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEP = 8
    private const val MAX_DIGITS = 15
    private const val GRID_ROWS = 5
    private const val GRID_COLS = 4
    private val PAIR_RES = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)
    private val DEG_TO_RAD = PI / 180.0

    // Explicit classes instead of \d and \s (JVM, Android ICU and Python differ on non-ASCII text).
    private val DMS = Regex(
        "([0-9]{1,2})°([0-9]{1,2})'([0-9]{1,2}(?:\\.[0-9]+)?)\"([NS])[\\s\\p{Z}]+" +
            "([0-9]{1,3})°([0-9]{1,2})'([0-9]{1,2}(?:\\.[0-9]+)?)\"([EW])")
    private val PLUS_TOKEN = Regex("[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}")

    /**
     * Centre of a full or short Plus Code. A short code is recovered nearest to the reference
     * ([refLat]/[refLon] = courier GPS, else [BAKU_REFERENCE]). Null when [code] is not a valid code.
     */
    fun plusCodeCenter(code: String, refLat: Double? = null, refLon: Double? = null): LatLon? {
        val ref = reference(refLat, refLon)
        val full = recoverNearest(code, ref.lat, ref.lon) ?: return null
        return center(full)
    }

    /**
     * Open Location Code "recover nearest" (`olc_ref.recover_nearest`, including the ±resolution shift when the
     * reference sits in the neighbouring cell). A full code comes back upper-cased; null when not a valid code.
     */
    fun recoverNearest(code: String, refLat: Double, refLon: Double): String? {
        val c = code.trim().uppercase()
        val sep = separatorOf(c) ?: return null
        if (sep == SEP) return c
        if (!refLat.isFinite() || !refLon.isFinite()) return null
        val pad = SEP - sep
        val res = PAIR_RES[pad / 2 - 1] // 20 ** (2 - pad / 2)
        val half = res / 2.0
        val full = encode(refLat, refLon, 10).substring(0, pad) + c
        val ctr = center(full)
        var clat = ctr.lat
        var clon = ctr.lon
        if (refLat + half < clat && clat - res >= -90) clat -= res
        else if (refLat - half > clat && clat + res <= 90) clat += res
        if (refLon + half < clon) clon -= res
        else if (refLon - half > clon) clon += res
        return encode(clat, clon, full.length - 1)
    }

    /** `olc_ref.encode`: full code of [length] digits (8 or 10..15). */
    fun encode(lat: Double, lon: Double, length: Int = 10): String {
        require(lat.isFinite() && lon.isFinite()) { "coordinate must be finite" }
        require(length == SEP || length in 10..MAX_DIGITS) { "unsupported code length $length" }
        var la = minOf(maxOf(lat, -90.0), 90.0)
        if (la == 90.0) la -= 0.000125 / 2
        val lng = pyMod(lon + 180.0, 360.0) - 180.0
        la += 90.0
        var ln = lng + 180.0
        val sb = StringBuilder()
        for (i in 0 until 5) {
            val r = PAIR_RES[i]
            val d1 = pyFloorDiv(la, r).toInt(); la -= d1 * r
            val d2 = pyFloorDiv(ln, r).toInt(); ln -= d2 * r
            sb.append(digit(d1)).append(digit(d2))
            if (sb.length == SEP) sb.append('+')
        }
        if (length <= 10) return sb.substring(0, length + 1)
        var lr = PAIR_RES[4]
        var gr = PAIR_RES[4]
        repeat(length - 10) {
            lr /= GRID_ROWS; gr /= GRID_COLS
            val row = minOf(pyFloorDiv(la, lr).toInt(), GRID_ROWS - 1); la -= row * lr
            val col = minOf(pyFloorDiv(ln, gr).toInt(), GRID_COLS - 1); ln -= col * gr
            sb.append(digit(row * GRID_COLS + col))
        }
        return sb.toString()
    }

    /** Wolt's "Customer drop-off" form `40°22'00.0"N 49°50'00.0"E`; null when absent or out of range. */
    fun dmsLatLon(text: String): LatLon? {
        val g = DMS.find(text)?.groupValues ?: return null
        val lat = g[1].toInt() + g[2].toInt() / 60.0 + g[3].toDouble() / 3600.0
        val lon = g[5].toInt() + g[6].toInt() / 60.0 + g[7].toDouble() / 3600.0
        if (lat > 90.0 || lon > 180.0) return null
        return LatLon(if (g[4] == "S") -lat else lat, if (g[8] == "W") -lon else lon)
    }

    /** DMS first, then the first valid Plus Code token (short codes near the reference), else null. */
    fun fromCardText(text: String, refLat: Double? = null, refLon: Double? = null): CardPoint? {
        dmsLatLon(text)?.let { return CardPoint(it.lat, it.lon, SOURCE_DMS) }
        for (m in PLUS_TOKEN.findAll(text)) {
            val p = plusCodeCenter(m.value, refLat, refLon) ?: continue
            return CardPoint(p.lat, p.lon, SOURCE_PLUS_CODE)
        }
        return null
    }

    /** `ref.haversine_km` (R = 6371.0088 km), same operation order. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1 * DEG_TO_RAD
        val p2 = lat2 * DEG_TO_RAD
        val dp = p2 - p1
        val dl = (lon2 - lon1) * DEG_TO_RAD
        val sp = sin(dp / 2)
        val sl = sin(dl / 2)
        val a = sp * sp + cos(p1) * cos(p2) * (sl * sl)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /** `ref.zone_of_radius`. */
    fun zoneOfRadius(km: Double): String {
        for ((zone, lo, hi) in ZONES) if (lo <= km && km < hi) return zone
        return "Z4"
    }

    /** `ref.zone_of` around [hub]; [UNKNOWN] when there is no (finite) coordinate. */
    fun coarseZone(lat: Double?, lon: Double?, hub: LatLon = HUB_DEFAULT): String {
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return UNKNOWN
        return zoneOfRadius(haversineKm(hub.lat, hub.lon, lat, lon))
    }

    /** `ref.hour_block`: [minuteOfDay] may be fractional or outside 0..1440 (Python `%` semantics). */
    fun hourBlock(minuteOfDay: Double): String {
        val m = pyMod(minuteOfDay, 1440.0)
        for ((block, lo, hi) in HOUR_BLOCKS) if (lo.toDouble() <= m && m < hi.toDouble()) return block
        return "B0"
    }

    private fun reference(refLat: Double?, refLon: Double?): LatLon =
        if (refLat != null && refLon != null && refLat.isFinite() && refLon.isFinite()) LatLon(refLat, refLon)
        else BAKU_REFERENCE

    /** Separator index of a valid full (8) or short (2, 4, 6) upper-case code, else null. */
    private fun separatorOf(c: String): Int? {
        val s = c.indexOf('+')
        if (s < 2 || s > SEP || s % 2 != 0 || c.indexOf('+', s + 1) >= 0) return null
        val suffix = c.length - s - 1
        if (suffix > MAX_DIGITS - SEP) return null
        if (s == SEP) {
            if (suffix == 1) return null
        } else if (suffix < 2) return null
        for (i in c.indices) if (i != s && ALPHABET.indexOf(c[i]) < 0) return null
        if (s == SEP && (ALPHABET.indexOf(c[0]) * 20 >= 180 || ALPHABET.indexOf(c[1]) * 20 >= 360)) return null
        return s
    }

    /** `olc_ref.center` of a validated full code. */
    private fun center(full: String): LatLon {
        val c = full.replace("+", "")
        var lat = -90.0
        var lng = -180.0
        var latRes = 0.0
        var lngRes = 0.0
        var i = 0
        while (i < minOf(c.length, 10)) {
            val r = PAIR_RES[i / 2]
            lat += ALPHABET.indexOf(c[i]) * r
            if (i + 1 < c.length) lng += ALPHABET.indexOf(c[i + 1]) * r
            latRes = r; lngRes = r
            i += 2
        }
        if (c.length > 10) {
            var lr = PAIR_RES[4]
            var gr = PAIR_RES[4]
            for (ch in c.substring(10)) {
                lr /= GRID_ROWS; gr /= GRID_COLS
                val v = ALPHABET.indexOf(ch)
                lat += (v / GRID_COLS) * lr
                lng += (v % GRID_COLS) * gr
            }
            latRes = lr; lngRes = gr
        }
        return LatLon((lat + (lat + latRes)) / 2, (lng + (lng + lngRes)) / 2)
    }

    /** Python sequence indexing (negative wraps); never throws. */
    private fun digit(i: Int): Char = ALPHABET[Math.floorMod(i, ALPHABET.length)]

    /** CPython float `a % b`. */
    private fun pyMod(a: Double, b: Double): Double {
        var mod = a % b
        if (mod != 0.0) {
            if ((b < 0) != (mod < 0)) mod += b
        } else mod = Math.copySign(0.0, b)
        return mod
    }

    /** CPython float `a // b` (fmod-based, snapped to the nearest integral value). */
    private fun pyFloorDiv(a: Double, b: Double): Double {
        val mod = a % b
        var div = (a - mod) / b
        if (mod != 0.0 && (b < 0) != (mod < 0)) div -= 1.0
        if (div == 0.0) return Math.copySign(0.0, a / b)
        var f = floor(div)
        if (div - f > 0.5) f += 1.0
        return f
    }
}
