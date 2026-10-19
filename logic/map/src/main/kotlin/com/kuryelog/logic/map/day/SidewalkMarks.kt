package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONArray

/** Elle "kaldırımda bekliyorum" işaretleri (MAP.md 11.12; tercih `sidewalk.zones.v1` = `[{lat, lon}]`): 12 m içi ışık değil, yüzey kaldırım. Haritada çizilmez. */
class SidewalkMarks(private val prefs: MapPrefs) {
    private val points = ArrayList<GeoPoint>()

    init {
        runCatching {
            val a = JSONArray(prefs.getString(KEY).takeUnless { it.isNullOrEmpty() } ?: "[]")
            for (i in 0 until a.length()) GeoPoint.fromJson(a.opt(i))?.let { points += it }
        }
    }

    val all: List<GeoPoint> get() = points.toList()

    /** [lat],[lon] 12 m içinde bir işaret var mı. */
    fun near(lat: Double, lon: Double): Boolean = points.any { it.distanceTo(GeoPoint(lat, lon)) <= RADIUS_M }

    /** 12 m içindeki işaretler silinir; [remove] değilse nokta eklenir. */
    fun mark(point: GeoPoint, remove: Boolean = false) {
        points.removeAll { it.distanceTo(point) <= RADIUS_M }
        if (!remove) points += point
        prefs.setString(KEY, JSONArray().also { a -> points.forEach { a.put(it.toJson()) } }.toString())
    }

    companion object {
        const val KEY = "sidewalk.zones.v1"
        const val RADIUS_M = 12.0
    }
}
