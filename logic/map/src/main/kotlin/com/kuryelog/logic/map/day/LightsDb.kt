package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.settings.MapPrefs
import java.util.Locale

/** Öğrenilmiş ışık yeri: [exists] `yes > no`. */
data class LightSpot(val lat: Double, val lon: Double, val exists: Boolean)

/**
 * Öğrenilen ışık yerleri (Dart `LightsDb`, MAP.md 11.11; tercih `lights.v1`, öğe `"lat,lon,yes,no,waitS"`): gün sonu cevapları ve elle işaretler.
 * 25 m içindeki durma bir dahakine sorulmadan sınıflanır. Her değişiklikte bütün liste yazılır; liste sırası korunur.
 */
class LightsDb(private val prefs: MapPrefs) {
    private class Spot(val lat: Double, val lon: Double, var yes: Int, var no: Int, var waitS: Int)

    private val spotList = ArrayList<Spot>()

    init {
        for (s in prefs.getStringList(KEY) ?: emptyList()) {
            val f = s.split(',')
            if (f.size < 4) continue
            val la = f[0].toDoubleOrNull()
            val lo = f[1].toDoubleOrNull()
            val y = f[2].toIntOrNull()
            val n = f[3].toIntOrNull()
            val w = if (f.size > 4) f[4].toIntOrNull() ?: 0 else 0
            if (la != null && lo != null && y != null && n != null) spotList += Spot(la, lo, y, n, w)
        }
    }

    /** Işık olduğu doğrulanan nokta sayısı (gün raporundaki `lightsLearned`). */
    val confirmed: Int get() = spotList.count { it.yes > it.no }

    /** `yes ≠ no` noktalar (harita katmanı ve uzun basış). */
    val spots: List<LightSpot> get() = spotList.filter { it.yes != it.no }.map { LightSpot(it.lat, it.lon, it.yes > it.no) }

    /** Yalnız `yes ≠ no` noktalarda en yakın ([maxM]'den yakın). */
    fun nearest(lat: Double, lon: Double, maxM: Double = 55.0): LightSpot? {
        var best: LightSpot? = null
        var distance = maxM
        for (s in spots) {
            val d = Geo.haversine(lat, lon, s.lat, s.lon)
            if (d < distance) {
                best = s
                distance = d
            }
        }
        return best
    }

    /** 25 m içindeki nokta silinir; basılan yerde yeni nokta, `waitS` eskisinden. */
    fun setManual(lat: Double, lon: Double, exists: Boolean) {
        val old = near(lat, lon)
        if (old != null) spotList.remove(old)
        spotList += Spot(lat, lon, if (exists) 1 else 0, if (exists) 0 else 1, old?.waitS ?: 0)
        save()
    }

    private fun near(lat: Double, lon: Double): Spot? {
        var best: Spot? = null
        var bd = RADIUS_M
        for (s in spotList) {
            val d = Geo.haversine(lat, lon, s.lat, s.lon)
            if (d <= bd) {
                bd = d
                best = s
            }
        }
        return best
    }

    /** true: ışık · false: ışık değil · null: bilinmiyor (nokta yok ya da `yes == no`). */
    fun lookup(lat: Double, lon: Double): Boolean? {
        val s = near(lat, lon)
        if (s == null || s.yes == s.no) return null
        return s.yes > s.no
    }

    fun record(lat: Double, lon: Double, isLight: Boolean, waitS: Int = 0) {
        val s = near(lat, lon)
        if (s == null) {
            spotList += Spot(lat, lon, if (isLight) 1 else 0, if (isLight) 0 else 1, if (isLight) waitS else 0)
        } else if (isLight) {
            s.yes++
            s.waitS += waitS
        } else {
            s.no++
        }
        save()
    }

    private fun save() = prefs.setStringList(KEY, spotList.map { String.format(Locale.ROOT, "%.6f,%.6f,%d,%d,%d", it.lat, it.lon, it.yes, it.no, it.waitS) })

    companion object {
        const val KEY = "lights.v1"
        const val RADIUS_M = 25.0
    }
}
