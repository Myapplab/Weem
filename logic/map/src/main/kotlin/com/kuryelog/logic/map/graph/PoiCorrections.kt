package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONObject

/** Bir yer düzeltmesi (Dart `PoiCorrection`, MAP.md 5.3): ekleme ya da ad/tür düzeltme; [original*] düzeltilen harita noktasının kimliği. */
class PoiCorrection(
    val poi: Poi,
    val originalLat: Double? = null,
    val originalLon: Double? = null,
    val originalKind: Char? = null,
    val originalRef: String = "",
) {
    /** Bu düzeltme harita noktası [p]'nin yerini alır mı: `originalRef` doluysa kimlik, değilse aynı tür ve ≤ 1 m. */
    fun replaces(p: Poi): Boolean = if (originalRef.isNotEmpty()) {
        p.osmRef == originalRef
    } else {
        originalLat != null && originalLon != null && p.kind == originalKind && Geo.haversine(p.lat, p.lon, originalLat, originalLon) <= 1
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", poi.lat); put("lon", poi.lon)
        put("kind", poi.kind.toString()); put("name", poi.name)
        put("osmRef", poi.osmRef); put("originalRef", originalRef)
        put("originalLat", originalLat ?: JSONObject.NULL)
        put("originalLon", originalLon ?: JSONObject.NULL)
        put("originalKind", originalKind?.toString() ?: JSONObject.NULL)
    }
}

/**
 * `places.local.v1` (StringList, her öğe bir JSON): kullanıcının işletme ekleme/ad düzeltmeleri; harita güncellense de korunur. Silme yok.
 * Düzeltilmiş küme = harita noktalarından düzeltmenin yerini aldıkları çıkarılır + bütün düzeltmeler ([corrected]).
 */
class PoiCorrections(private val prefs: MapPrefs) {
    private val list = ArrayList<PoiCorrection>()

    /** Her değişiklikte artar (arama dizini ve harita katmanı yenilenir). */
    var rev = 0
        private set

    init {
        for (raw in prefs.getStringList(KEY) ?: emptyList()) {
            runCatching {
                val j = JSONObject(raw)
                val lat = j.getDouble("lat")
                val lon = j.getDouble("lon")
                if (!lat.isFinite() || !lon.isFinite() || Math.abs(lat) > 90 || Math.abs(lon) > 180) return@runCatching
                val poi = Poi(lat, lon, j.getString("kind").first(), j.getString("name"), j.opt("osmRef") as? String ?: "")
                list += PoiCorrection(
                    poi,
                    originalLat = (j.opt("originalLat") as? Number)?.toDouble(),
                    originalLon = (j.opt("originalLon") as? Number)?.toDouble(),
                    originalKind = (j.opt("originalKind") as? String)?.firstOrNull(),
                    originalRef = j.opt("originalRef") as? String ?: "",
                )
            }
        }
    }

    val all: List<PoiCorrection> get() = list.toList()

    /** Ekler ya da düzeltir: aynı orijinale ait eski düzeltme değişir (orijinal bilgisi ilk düzeltmeden korunur), liste sonuna eklenir. */
    fun save(poi: Poi, original: Poi? = null) {
        val old = if (original == null) null else list.firstOrNull { c ->
            c.poi === original ||
                (original.osmRef.isNotEmpty() && c.poi.osmRef == original.osmRef) ||
                (c.poi.kind == original.kind && c.poi.name == original.name && Geo.haversine(c.poi.lat, c.poi.lon, original.lat, original.lon) <= 1)
        }
        val saved = PoiCorrection(
            poi,
            originalLat = old?.originalLat ?: original?.lat,
            originalLon = old?.originalLon ?: original?.lon,
            originalKind = old?.originalKind ?: original?.kind,
            originalRef = old?.originalRef ?: original?.osmRef ?: "",
        )
        list.removeAll { it === old }
        list += saved
        prefs.setStringList(KEY, list.map { it.toJson().toString() })
        rev++
    }

    /** Harita noktaları + düzeltmeler; ikisi de yoksa null. */
    fun corrected(original: PoiSet?): PoiSet? {
        if (original == null && list.isEmpty()) return null
        return PoiSet((original?.items ?: emptyList()).filter { p -> list.none { it.replaces(p) } } + list.map { it.poi })
    }

    companion object {
        const val KEY = "places.local.v1"
    }
}
