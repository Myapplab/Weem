package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import org.json.JSONArray

/** Öğrenilmiş ışık yeri (`LightSpot`): [exists] = ışık var. */
data class LightSpot(val lat: Double, val lon: Double, val exists: Boolean)

/**
 * Öğrenilmiş ışık noktaları (Dart `LightsDb`, MAP.md 11.11; tercih `lights.v1`): nokta = `(lat, lon, yes, no, waitS)`; saklama `"lat,lon,yes,no,waitS"` (lat/lon 6 hane).
 * Saf: girdi/çıktı dizge listesi; Android'de JSON dizi dizgesi ([toJsonString], [fromJsonString]).
 */
class LightsDb(lines: List<String> = emptyList()) {
    private class P(var lat: Double, var lon: Double, var yes: Int, var no: Int, var waitS: Int)

    private val pts = ArrayList<P>()

    init {
        for (l in lines) {
            val f = l.split(",")
            if (f.size < 4) continue
            val lat = f[0].toDoubleOrNull() ?: continue
            val lon = f[1].toDoubleOrNull() ?: continue
            val yes = f[2].toIntOrNull() ?: continue
            val no = f[3].toIntOrNull() ?: continue
            pts += P(lat, lon, yes, no, f.getOrNull(4)?.toIntOrNull() ?: 0)
        }
    }

    fun toLines(): List<String> = pts.map { "${r6(it.lat)},${r6(it.lon)},${it.yes},${it.no},${it.waitS}" }

    fun toJsonString(): String = JSONArray(toLines()).toString()

    /** 25 m (`≤`) içindeki en yakın nokta; yok ya da `yes == no` → null, değilse `yes > no`. */
    fun lookup(lat: Double, lon: Double): Boolean? {
        val p = nearestAny(lat, lon) ?: return null
        return if (p.yes == p.no) null else p.yes > p.no
    }

    /** Yalnız `yes ≠ no` noktalarda en yakın (`< maxM`). */
    fun nearest(lat: Double, lon: Double, maxM: Double = 55.0): LightSpot? {
        var best: P? = null
        var bd = Double.POSITIVE_INFINITY
        for (p in pts) {
            if (p.yes == p.no) continue
            val d = Geo.haversine(lat, lon, p.lat, p.lon)
            if (d < maxM && d < bd) { best = p; bd = d }
        }
        return best?.let { LightSpot(it.lat, it.lon, it.yes > it.no) }
    }

    val spots: List<LightSpot> get() = pts.filter { it.yes != it.no }.map { LightSpot(it.lat, it.lon, it.yes > it.no) }
    val confirmed: Int get() = pts.count { it.yes > it.no }

    fun record(lat: Double, lon: Double, isLight: Boolean, waitS: Int) {
        val p = nearestAny(lat, lon)
        if (p == null) {
            pts += if (isLight) P(lat, lon, 1, 0, waitS) else P(lat, lon, 0, 1, 0)
        } else if (isLight) {
            p.yes++; p.waitS += waitS
        } else {
            p.no++
        }
    }

    fun setManual(lat: Double, lon: Double, exists: Boolean) {
        val old = nearestAny(lat, lon)
        val wait = old?.waitS ?: 0
        if (old != null) pts.remove(old)
        pts += if (exists) P(lat, lon, 1, 0, wait) else P(lat, lon, 0, 1, wait)
    }

    private fun nearestAny(lat: Double, lon: Double): P? {
        var best: P? = null
        var bd = Double.POSITIVE_INFINITY
        for (p in pts) {
            val d = Geo.haversine(lat, lon, p.lat, p.lon)
            if (d <= NEAR_M && d < bd) { best = p; bd = d }
        }
        return best
    }

    private fun r6(v: Double) = Math.round(v * 1e6) / 1e6

    companion object {
        const val NEAR_M = 25.0

        fun fromJsonString(s: String?): LightsDb {
            if (s.isNullOrEmpty()) return LightsDb()
            return try {
                val a = JSONArray(s)
                LightsDb((0 until a.length()).map { a.optString(it) })
            } catch (_: Exception) {
                LightsDb()
            }
        }
    }
}
