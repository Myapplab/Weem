package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.day.LightSpot
import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.PoiSet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** Seçilen bir yer simgesi: [sizeDp] kutu boyu (metro 30, AVM 32, ışık 16, yemek 30). */
class PoiMarker(val poi: Poi, val sizeDp: Int)

/** Seçilen bir ad etiketi: [offsetDp] simgenin üstündeki uzaklık. */
class PoiLabel(val poi: Poi, val offsetDp: Int)

class PoiSelection(val markers: List<PoiMarker>, val labels: List<PoiLabel>)

/**
 * Y16 yer katmanı kuralları (YolGo `displayPoisProvider` + `PoiLayer`, `map_view.dart:897-1031`; MAP.md 5.3): düzeltilmiş küme + yerel ışık işaretleri;
 * yakınlık eşikleri ve öncelik [PoiZoomRules]'ta; burada seçim: görünür alanda öncelik sırası, yemek simgeleri 34 dp hücrede tek, en çok 180 simge ve 55 ad (çakışan ad atlanır).
 */
object PoiLayerRules {
    private const val LIGHT_NEAR_M = 25.0
    private const val CELL_DP = 34.0
    private const val LABEL_W = 145.0
    private const val LABEL_H = 35.0

    /** Gösterilecek küme: "yok" işaretinin 25 m içindeki OSM ışığı gizlenir; "var" işareti 25 m içinde OSM ışığı yoksa eklenir. */
    fun displayPois(pois: PoiSet?, local: List<LightSpot>): PoiSet? {
        val items = ArrayList<Poi>()
        for (p in pois?.items ?: emptyList()) {
            if (p.kind != 'l' || local.none { !it.exists && Geo.haversine(it.lat, it.lon, p.lat, p.lon) <= LIGHT_NEAR_M }) items += p
        }
        for (s in local) {
            if (s.exists && items.none { it.kind == 'l' && Geo.haversine(it.lat, it.lon, s.lat, s.lon) <= LIGHT_NEAR_M }) items += Poi(s.lat, s.lon, 'l', "")
        }
        return if (items.isEmpty() && pois == null) null else PoiSet(items)
    }

    /** Web Mercator piksel (256 · 2^z ölçeği), harita dönüşü uygulanmış (Dart `_mapPixel`). */
    fun mapPixel(lat: Double, lon: Double, zoom: Double, rotationDeg: Double): Pair<Double, Double> {
        val scale = 256 * 2.0.pow(zoom)
        val phi = lat * PI / 180
        val x = (lon + 180) / 360 * scale
        val y = (1 - ln(tan(phi) + 1 / cos(phi)) / PI) / 2 * scale
        val r = rotationDeg * PI / 180
        return Pair(x * cos(r) - y * sin(r), x * sin(r) + y * cos(r))
    }

    /**
     * Kamera çevresinde görünür olabilecek kutu (dönüşü kapsar: yarı kenar = ekranın uzun kenarının yarısı). [zoom] 256 piksellik karo ölçeğindedir (YolGo/Google); MapLibre
     * 512 piksellik dünya kullandığından çağıran `zoom + 1` verir. Piksel = dp.
     */
    fun visibleBox(center: GeoPoint, zoom: Double, widthDp: Double, heightDp: Double): BBox {
        val mpp = 156543.03392 * cos(center.lat * PI / 180) / 2.0.pow(zoom)
        val halfM = maxOf(widthDp, heightDp) / 2 * mpp
        val dLat = halfM / 111320.0
        val dLon = halfM / (111320.0 * cos(center.lat * PI / 180))
        return BBox(center.lat - dLat, center.lon - dLon, center.lat + dLat, center.lon + dLon)
    }

    /** Görünür alandaki noktalardan simge ve etiket seçimi. [visible] kamera kutusundaki noktalar; yakınlık < 12'de boş. */
    fun select(visible: List<Poi>, zoom: Double, rotationDeg: Double): PoiSelection {
        if (zoom < PoiZoomRules.HIDE_BELOW) return PoiSelection(emptyList(), emptyList())
        val markers = ArrayList<PoiMarker>()
        val labels = ArrayList<PoiLabel>()
        val rects = ArrayList<DoubleArray>() // left, top, right, bottom
        val cells = HashSet<String>()

        fun label(p: Poi, offset: Int) {
            if (p.name.isEmpty() || labels.size >= PoiZoomRules.MAX_LABELS) return
            val (px, py) = mapPixel(p.lat, p.lon, zoom, rotationDeg)
            val cy = py - offset
            val r = doubleArrayOf(px - LABEL_W / 2, cy - LABEL_H / 2, px + LABEL_W / 2, cy + LABEL_H / 2)
            if (rects.any { it[0] < r[2] && it[2] > r[0] && it[1] < r[3] && it[3] > r[1] }) return
            rects += r
            labels += PoiLabel(p, offset)
        }

        for (p in visible.sortedBy { PoiZoomRules.priority(it.kind) }) {
            when (p.kind) {
                's' -> {
                    if (zoom < 13) continue
                    markers += PoiMarker(p, 30)
                    if (zoom >= 16) label(p, 34)
                }
                'm' -> {
                    if (zoom < 15) continue
                    markers += PoiMarker(p, 32)
                    if (zoom >= 17) label(p, 36)
                }
                'l' -> {
                    if (zoom < 16.5) continue
                    markers += PoiMarker(p, 16)
                }
                else -> {
                    if (zoom < 16.5) continue
                    val (px, py) = mapPixel(p.lat, p.lon, zoom, rotationDeg)
                    if (!cells.add("${floor(px / CELL_DP).toInt()}:${floor(py / CELL_DP).toInt()}")) continue
                    markers += PoiMarker(p, 30)
                    if (zoom >= 17.6) label(p, 32)
                }
            }
            if (markers.size >= PoiZoomRules.MAX_MARKERS) break
        }
        return PoiSelection(markers, labels)
    }
}
