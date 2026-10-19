package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** `lib/input/plus_code.dart` (MAP.md 8.5): açık konum kodu (Plus Code), saf ve ağsız. */
object PlusCode {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private val PAIR_VALUES = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

    val pattern: Regex = Regex(
        "(?<![0-9A-Z])([23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3})(?![0-9A-Z])",
        RegexOption.IGNORE_CASE,
    )

    private val letter = Regex("[CFGHJMPQRVWX]", RegexOption.IGNORE_CASE)

    /** Tam kod → hücre merkezi; geçersizse null. */
    fun decode(code: String): GeoPoint? {
        val c = code.uppercase().replace("+", "")
        if (c.length < 10) return null
        var lat = -90.0
        var lon = -180.0
        for (i in 0 until 5) {
            val la = ALPHABET.indexOf(c[i * 2])
            val lo = ALPHABET.indexOf(c[i * 2 + 1])
            if (la < 0 || lo < 0) return null
            lat += la * PAIR_VALUES[i]
            lon += lo * PAIR_VALUES[i]
        }
        var latCell = PAIR_VALUES[4]
        var lonCell = PAIR_VALUES[4]
        for (i in 10 until c.length) {
            val d = ALPHABET.indexOf(c[i])
            if (d < 0) return null
            latCell /= 5
            lonCell /= 4
            lat += (d / 4) * latCell
            lon += (d % 4) * lonCell
        }
        return GeoPoint(lat + latCell / 2, lon + lonCell / 2)
    }

    /** Referans noktadan ilk [digits] hane. */
    private fun prefix(lat: Double, lon: Double, digits: Int): String {
        var la = min(max(lat, -90.0), 89.9999999) + 90
        var lo = ((lon + 180) % 360 + 360) % 360
        val sb = StringBuilder()
        var i = 0
        while (i < 5 && sb.length < digits) {
            val pv = PAIR_VALUES[i]
            val a = Geo.clampInt(floor(la / pv).toInt(), 0, 19)
            val b = Geo.clampInt(floor(lo / pv).toInt(), 0, 19)
            la -= a * pv
            lo -= b * pv
            sb.append(ALPHABET[a]).append(ALPHABET[b])
            i++
        }
        return sb.substring(0, digits)
    }

    /** Kısa kodu [ref] konumuna en yakın tam koda tamamlar (`+` yoksa null). */
    fun recover(code: String, ref: GeoPoint): GeoPoint? {
        val up = code.uppercase()
        val plus = up.indexOf('+')
        if (plus < 0) return null
        if (plus >= 8) return decode(up)
        val padding = 8 - plus
        if (padding % 2 == 1) return null
        val p = decode(prefix(ref.lat, ref.lon, padding) + up) ?: return null
        // Referans hücre sınırına yakınsa en yakın hücre.
        val resolution = 20.0.pow(2 - padding / 2)
        var lat = p.lat
        var lon = p.lon
        if (ref.lat + resolution / 2 < lat) lat -= resolution else if (ref.lat - resolution / 2 > lat) lat += resolution
        if (ref.lon + resolution / 2 < lon) lon -= resolution else if (ref.lon - resolution / 2 > lon) lon += resolution
        return GeoPoint(lat, lon)
    }

    /** "2345+67" gibi sayıları eler: en az bir harf `[CFGHJMPQRVWX]`. */
    fun looksReal(code: String): Boolean = letter.containsMatchIn(code)
}
