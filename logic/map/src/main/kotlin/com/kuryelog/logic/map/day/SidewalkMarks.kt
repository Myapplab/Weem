package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/** Elle kaldırım işaretleri (`sidewalk.zones.v1` = `[{lat, lon}]`, MAP.md 11.12): 12 m içindeki işaret "kaldırım beklemesi" sayılır. */
class SidewalkMarks(private val points: MutableList<GeoPoint> = ArrayList()) {
    val all: List<GeoPoint> get() = points

    fun contains(lat: Double, lon: Double): Boolean = points.any { Geo.haversine(lat, lon, it.lat, it.lon) <= RADIUS_M }

    /** 12 m içindeki işaretler silinir; [remove] değilse nokta eklenir. */
    fun mark(point: GeoPoint, remove: Boolean) {
        points.removeAll { Geo.haversine(point.lat, point.lon, it.lat, it.lon) <= RADIUS_M }
        if (!remove) points += point
    }

    fun toJsonString(): String = JSONArray().also { a -> points.forEach { a.put(JSONObject().put("lat", it.lat).put("lon", it.lon)) } }.toString()

    companion object {
        const val RADIUS_M = 12.0

        /** Bozuk öğe atlanır, bozuk JSON → boş liste. */
        fun fromJsonString(s: String?): SidewalkMarks {
            if (s.isNullOrEmpty()) return SidewalkMarks()
            return try {
                val a = JSONArray(s)
                val list = ArrayList<GeoPoint>()
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    if (!o.has("lat") || !o.has("lon")) continue
                    list += GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                }
                SidewalkMarks(list)
            } catch (_: Exception) {
                SidewalkMarks()
            }
        }
    }
}
