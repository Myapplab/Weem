package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONObject

/**
 * Elle yer düzeltmesi (`places.local.v1`, MAP.md 5.3): ekleme ya da ad/tür düzeltme; silme yok. [original…] düzeltilen harita noktasının bilgisi (yeni işletmede boş).
 */
class PoiFix(
    val poi: Poi,
    val originalRef: String = "",
    val originalLat: Double? = null,
    val originalLon: Double? = null,
    val originalKind: Char? = null,
) {
    /** `originalRef` doluysa `osmRef` eşitliği; değilse orijinal konum ve tür varken aynı tür ve ≤ 1 m. */
    fun replaces(p: Poi): Boolean {
        if (originalRef.isNotEmpty()) return p.osmRef == originalRef
        val lat = originalLat ?: return false
        val lon = originalLon ?: return false
        val kind = originalKind ?: return false
        return p.kind == kind && Geo.haversine(p.lat, p.lon, lat, lon) <= 1.0
    }

    fun toJson(): JSONObject {
        val j = JSONObject().put("lat", poi.lat).put("lon", poi.lon).put("kind", poi.kind.toString()).put("name", poi.name).put("osmRef", poi.osmRef)
        if (originalRef.isNotEmpty()) j.put("originalRef", originalRef)
        originalLat?.let { j.put("originalLat", it) }
        originalLon?.let { j.put("originalLon", it) }
        originalKind?.let { j.put("originalKind", it.toString()) }
        return j
    }

    companion object {
        /** `lat`/`lon` sonlu ve aralıkta olmalı; bozuk öğe null. */
        fun fromJson(s: String): PoiFix? = try {
            val j = JSONObject(s)
            val lat = j.getDouble("lat")
            val lon = j.getDouble("lon")
            if (!lat.isFinite() || !lon.isFinite() || Math.abs(lat) > 90 || Math.abs(lon) > 180) null
            else {
                val kind = j.optString("kind").firstOrNull() ?: 'r'
                PoiFix(
                    Poi(lat, lon, kind, j.optString("name", ""), j.optString("osmRef", "")),
                    j.optString("originalRef", ""),
                    if (j.has("originalLat")) j.getDouble("originalLat") else null,
                    if (j.has("originalLon")) j.getDouble("originalLon") else null,
                    j.optString("originalKind", "").firstOrNull(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

class PoiCorrections(private val prefs: MapPrefs) {
    companion object {
        const val KEY = "places.local.v1"
    }

    private val list = ArrayList<PoiFix>().apply { prefs.getStringList(KEY).orEmpty().mapNotNullTo(this) { PoiFix.fromJson(it) } }

    val fixes: List<PoiFix> get() = list

    /** Düzeltilmiş küme: harita noktalarından düzeltmenin yerini aldıkları çıkarılır + bütün düzeltmeler. */
    fun apply(base: List<Poi>): List<Poi> = base.filter { p -> list.none { it.replaces(p) } } + list.map { it.poi }

    /**
     * [original]: düzenlenen nokta (görünen; yeni işletmede null). Aynı orijinale ait eski düzeltme (aynı nesne, aynı `osmRef`, ya da aynı tür+ad ve ≤ 1 m)
     * değiştirilir; orijinal bilgileri ilk düzeltmeden korunur; liste sonuna eklenir.
     */
    fun save(poi: Poi, original: Poi?) {
        var oRef = original?.osmRef.orEmpty()
        var oLat = original?.lat
        var oLon = original?.lon
        var oKind = original?.kind
        if (original != null) {
            val old = list.firstOrNull {
                it.poi === original || (original.osmRef.isNotEmpty() && it.poi.osmRef == original.osmRef) ||
                    (it.poi.kind == original.kind && it.poi.name == original.name && Geo.haversine(it.poi.lat, it.poi.lon, original.lat, original.lon) <= 1.0)
            }
            if (old != null) {
                list.remove(old)
                oRef = old.originalRef; oLat = old.originalLat; oLon = old.originalLon; oKind = old.originalKind
            }
        }
        list += PoiFix(poi, oRef, oLat, oLon, oKind)
        prefs.setStringList(KEY, list.map { it.toJson().toString() })
    }
}
