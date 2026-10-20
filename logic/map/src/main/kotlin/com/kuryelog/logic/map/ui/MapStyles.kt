package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.map.day.LightSpot
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint

/** Harita altlığı (S4, SORU-N5 (a)): Google (anahtarlı derlemede), MapLibre OSM raster, MapLibre vektör (varsayılan). */
enum class BaseMap(val key: String) { GOOGLE("google"), OSM("osm"), VECTOR("vector") }

/** Harita yüzeyinin ortak ölçüleri ve iki yüzeyin (Google / MapLibre) sınırları (MAP.md 6.4). */
object MapCamera {
    /** Merkez işaret ?? son konum ?? Bakü. */
    val BAKU = GeoPoint(40.4093, 49.8671)
    const val STANDARD_ZOOM = 16.2
    const val MAPLIBRE_MIN_ZOOM = 11.0
    const val MAPLIBRE_MAX_ZOOM = 19.0
    const val GOOGLE_MIN_ZOOM = 11.0
    const val GOOGLE_MAX_ZOOM = 20.0
    /** Google iç boşluğu (sol, üst, sağ, alt; dp): HUD payı. */
    val GOOGLE_PADDING_DP = listOf(16, 120, 16, 240)
    /** Kamera hareketinin en sık aralığı: MapLibre 80 ms; Google 160 ms ve önceki hareket bitmeden yenisi yok. */
    const val MAPLIBRE_MOVE_MS = 80L
    const val GOOGLE_MOVE_MS = 160L
    /** Konum işaretinin en sık çizimi (Google). */
    const val PUCK_DRAW_MS = 120L
    /** Parmak bu kadar kayarsa (dp) "elle hareket" (MAP.md 7.10). */
    const val GESTURE_SLOP_DP = 8.0
}

/** Atıf metni ve dokununca açılan sayfa. */
data class Attribution(val text: String, val url: String?)

/**
 * Altlık seçimi ve kaynakları (YolGo `map_view.dart:161-200, 625-690`; MAP.md 6.4, SORU-N5 (a)). YolGo'nun `minimal`, `esri`, `sat` değerleri ve CARTO anahtarı
 * kalktı: eski ya da bilinmeyen değer vektör altlığa düşer; anahtarsız derlemede `google` da vektöre.
 */
object MapStyles {
    const val DEFAULT = "vector"

    const val OSM_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val OSM_TILE_SIZE = 256
    const val OSM_MAX_NATIVE_ZOOM = 19
    const val OSM_COPYRIGHT = "https://www.openstreetmap.org/copyright"
    const val OPENMAPTILES_URL = "https://www.openmaptiles.org/"

    /**
     * Vektör altlık (SORU-N5 (ii)): kendi Bakü PMTiles dosyası yokken OpenFreeMap'in anahtarsız stilleri (MAP.md 6.4; PMTiles dosyası A6.5'te üretilemedi, bkz. 6.16).
     */
    const val VECTOR_LIGHT_STYLE = "https://tiles.openfreemap.org/styles/positron"
    const val VECTOR_DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

    /** OSM karo sunucusunun istediği tanıtıcı User-Agent (karo kullanım politikası). */
    fun userAgent(versionName: String) = "WEEM/$versionName (personal courier navigation)"

    /** Kayıtlı `mapStyle` değeri → altlık. */
    fun resolve(mapStyle: String, mapsEnabled: Boolean): BaseMap = when (mapStyle) {
        BaseMap.GOOGLE.key -> if (mapsEnabled) BaseMap.GOOGLE else BaseMap.VECTOR
        BaseMap.OSM.key -> BaseMap.OSM
        else -> BaseMap.VECTOR
    }

    /** Ayarda gösterilen seçenekler (anahtarsız derlemede Google seçilemez). */
    fun choices(mapsEnabled: Boolean): List<BaseMap> = if (mapsEnabled) listOf(BaseMap.VECTOR, BaseMap.OSM, BaseMap.GOOGLE) else listOf(BaseMap.VECTOR, BaseMap.OSM)

    fun label(base: BaseMap): Bilingual = when (base) {
        BaseMap.VECTOR -> Txt("Vektör · WEEM", "Vektor · WEEM")
        BaseMap.OSM -> Txt("OpenStreetMap", "OpenStreetMap")
        BaseMap.GOOGLE -> Txt("Google Maps", "Google Maps")
    }

    /** Atıf (sol alt, 9 pt): gerçek kaynağı söyler (MAP.md 6.16 (1)); Google kendi logosunu çizer. */
    fun attribution(base: BaseMap): Attribution? = when (base) {
        BaseMap.OSM -> Attribution("© OpenStreetMap contributors", OSM_COPYRIGHT)
        BaseMap.VECTOR -> Attribution("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors", OSM_COPYRIGHT)
        BaseMap.GOOGLE -> null
    }

    /** MapLibre vektör stil adresi. */
    fun vectorStyleUrl(dark: Boolean): String = if (dark) VECTOR_DARK_STYLE else VECTOR_LIGHT_STYLE

    /**
     * MapLibre OSM raster stili (JSON): koyu temada karolar ters çevrilir (`raster-brightness-min = 1`, `raster-brightness-max = 0`; ev seçiciyle aynı, MAP.md 6.11).
     */
    fun osmRasterStyle(dark: Boolean): String {
        val paint = if (dark) ""","paint":{"raster-brightness-min":1,"raster-brightness-max":0,"raster-saturation":-0.6}""" else ""
        return """{"version":8,"sources":{"osm":{"type":"raster","tiles":["$OSM_TILES"],"tileSize":$OSM_TILE_SIZE,"maxzoom":$OSM_MAX_NATIVE_ZOOM,""" +
            """"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm"$paint}]}"""
    }

    private class Txt(override val tr: String, override val az: String) : Bilingual
}

/** Yer işaretlerinin yakınlık kuralları (YolGo `PoiLayer`, `map_view.dart:972-1031`; MAP.md 6.6). Çizimi A6.8 (Y16). */
object PoiZoomRules {
    const val HIDE_BELOW = 12.0
    const val MAX_MARKERS = 180
    const val MAX_LABELS = 55
    /** Yemek ve dükkân işaretleri ekranın 34 dp ızgarasında hücre başına bir. */
    const val FOOD_GRID_DP = 34.0
    /** Google yüzeyi: kamera merkezinin 900 m içi, en yakından en çok 100; ışık dışındakilere ≥17,6'da ilk 35'e ad. */
    const val GOOGLE_RADIUS_M = 900.0
    const val GOOGLE_MAX = 100
    const val GOOGLE_LABELS = 35

    /** Öncelik: ışık < metro < AVM < diğerleri (küçük önce çizilir, büyük üstte). */
    fun priority(kind: Char): Int = when (kind) { 'l' -> 0; 's' -> 1; 'm' -> 2; else -> 3 }

    fun markerMinZoom(kind: Char): Double = when (kind) { 's' -> 13.0; 'm' -> 15.0; else -> 16.5 }

    /** Ad etiketi; ışığın adı yok (null). */
    fun labelMinZoom(kind: Char): Double? = when (kind) { 's' -> 16.0; 'm' -> 17.0; 'l' -> null; else -> 17.6 }

    fun markerVisible(kind: Char, zoom: Double): Boolean = zoom >= HIDE_BELOW && zoom >= markerMinZoom(kind)

    fun labelVisible(kind: Char, zoom: Double): Boolean = markerVisible(kind, zoom) && labelMinZoom(kind)?.let { zoom >= it } == true
}

/**
 * Harita yer katmanının seçimi (MAP.md 5.3, `displayPoisProvider` + `PoiLayer`; saf): düzeltilmiş yerler + ışık işaretleri → görünür kutu ve yakınlığa göre süzülür;
 * öncelik ışık → metro → AVM → öteki; restoran/fast food/kafe/market ekranın 34 dp ızgarasında hücre başına bir; en çok 180 simge.
 */
object PoiLayerRules {
    /** "Yok" işaretinin 25 m içindeki OSM ışığı gizlenir; "var" işareti 25 m içinde OSM ışığı yoksa eklenir. */
    fun withLights(base: List<Poi>, spots: List<LightSpot>): List<Poi> {
        val hidden = spots.filter { !it.exists }
        val osmLights = base.filter { it.kind == 'l' }
        val kept = osmLights.filter { l -> hidden.none { Geo.haversine(l.lat, l.lon, it.lat, it.lon) <= LIGHT_NEAR_M } }
        val added = spots.filter { s -> s.exists && osmLights.none { Geo.haversine(it.lat, it.lon, s.lat, s.lon) <= LIGHT_NEAR_M } }.map { Poi(it.lat, it.lon, 'l', "") }
        return base.filter { it.kind != 'l' } + kept + added
    }

    /** 256 dp'lik dünya (MapLibre/Google standart ölçeği): bir dp'nin boylam derecesi. */
    fun degPerDp(zoom: Double): Double = 360.0 / (256.0 * Math.pow(2.0, zoom))

    /** Görünür kutu: merkez ± yarım ekran (dp), enlemde `cos` düzeltmeli. Sıra (güney, batı, kuzey, doğu). */
    fun bounds(centerLat: Double, centerLon: Double, widthDp: Double, heightDp: Double, zoom: Double): BBox {
        val d = degPerDp(zoom)
        val dLat = heightDp / 2 * d * Math.cos(Math.toRadians(centerLat))
        val dLon = widthDp / 2 * d
        return BBox(centerLat - dLat, centerLon - dLon, centerLat + dLat, centerLon + dLon)
    }

    /** Yakınlığa göre görünür simgeler (en çok [PoiZoomRules.MAX_MARKERS]); girdi sırası eşit önceliklerde korunur. */
    fun select(pois: List<Poi>, box: BBox, zoom: Double, lat: Double = (box.south + box.north) / 2): List<Poi> {
        if (zoom < PoiZoomRules.HIDE_BELOW) return emptyList()
        val cell = PoiZoomRules.FOOD_GRID_DP * degPerDp(zoom)
        val cellLat = cell * Math.cos(Math.toRadians(lat))
        val taken = HashSet<Long>()
        val out = ArrayList<Poi>()
        val visible = pois.filter { it.lat in box.south..box.north && it.lon in box.west..box.east && PoiZoomRules.markerVisible(it.kind, zoom) }
        for (p in visible.sortedBy { PoiZoomRules.priority(it.kind) }) {
            if (out.size >= PoiZoomRules.MAX_MARKERS) break
            if (p.kind in GRID_KINDS) {
                val key = Math.floor(p.lat / cellLat).toLong() * 1_000_003L + Math.floor(p.lon / cell).toLong()
                if (!taken.add(key)) continue
            }
            out += p
        }
        return out
    }

    private const val LIGHT_NEAR_M = 25.0
    private val GRID_KINDS = setOf('r', 'f', 'c', 't')
}
