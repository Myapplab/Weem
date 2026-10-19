package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ui.BaseMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Çizgi deseni: iki yüzey aynı (MAP.md 6.16 (2)): kesikli 8/6, noktalı. */
enum class LinePattern { SOLID, DASH, DOT }

/** Haritadaki bir çizgi; [z] büyük olan üstte. Renk ARGB. */
data class MapLine(val id: String, val points: List<GeoPoint>, val color: Int, val widthDp: Float, val pattern: LinePattern = LinePattern.SOLID, val z: Int = 0)

/** Durak, varılan hedef ve Y16 yer katmanı simgeleri (MAP.md 5.3, 6.6): [POI_FOOD]'un harfi `label`'da (r/f/c/t), diğerlerinde ad `name`'dedir. */
enum class MarkerKind { STOP_FIRST, STOP, REACHED, POI_METRO, POI_MALL, POI_LIGHT, POI_FOOD, LIGHT_WAIT }

/**
 * İşaret: duraklar (mor, numaralı; ilk tam, diğerleri %60) ve varılan hedef (yeşil); yer simgeleri [name] ile (ad simgenin altında; boşsa yok). [label]: durak numarası ya da
 * yemek noktasının tür harfi.
 */
data class MapMarker(val id: String, val point: GeoPoint, val kind: MarkerKind, val label: String = "", val name: String = "")

/** Konum işareti: [course] ok yönü, [beam] koni yönü (pusula dahil), [arrow] rota açık; doğruluk dairesi 8 < [accuracyM] < 80 m. */
data class MapPuck(val point: GeoPoint, val course: Double?, val beam: Double?, val arrow: Boolean, val accuracyM: Double?)

/** Kamera komutu: [seq] her yeni komutta artar (yüzey yalnız yeniyi uygular). [bearing] null ise yön değişmez. */
data class MapCameraCommand(val seq: Int, val center: GeoPoint, val zoom: Double? = null, val bearing: Double? = null, val animate: Boolean = false)

/** Bir yüzeye çizilecek her şey (MAP.md 6.4–6.6); yüzey yalnız görüntü katmanıdır, kararları vermez. */
data class MapSurfaceModel(
    val base: BaseMap,
    val dark: Boolean,
    val lines: List<MapLine> = emptyList(),
    val markers: List<MapMarker> = emptyList(),
    val puck: MapPuck? = null,
    val camera: MapCameraCommand? = null,
    /** Yalnız ev seçici: merkez çevresinde metre yarıçaplı daire (birincil renk %15 dolgu, 2 px kenar). */
    val circle: Pair<GeoPoint, Double>? = null,
    val circleColor: Int = 0xFF1765D1.toInt(),
    /** Döndürme jesti (MapLibre'de kapalı: harita yalnız kamera yönüyle döner). */
    val rotateGestures: Boolean = false,
)

/** Yüzeyden gelenler: dokunuş, uzun basış, elle hareket (parmak kaydırdı/yakınlaştırdı), kamera durdu. */
class MapSurfaceEvents(
    val onTap: (GeoPoint) -> Unit = {},
    val onLongPress: (GeoPoint) -> Unit = {},
    val onGesture: () -> Unit = {},
    val onCameraIdle: (center: GeoPoint, zoom: Double, bearing: Double) -> Unit = { _, _, _ -> },
)

/** Harita yüzeyi (MAP.md 6.15 `MapSurface`): altlığa göre Google ya da MapLibre. Altlık değişince eski görünüm kapanır (yeni AndroidView). */
@Composable
fun MapSurface(model: MapSurfaceModel, events: MapSurfaceEvents, modifier: Modifier = Modifier, userAgent: String = "") {
    when (model.base) {
        BaseMap.GOOGLE -> GoogleMapSurface(model, events, modifier)
        else -> MapLibreSurface(model, events, modifier, userAgent)
    }
}

/** Metre yarıçaplı daire çokgeni (doğruluk ve ev seçici dairesi). */
internal fun circlePolygon(center: GeoPoint, radiusM: Double, n: Int = 48): List<GeoPoint> = (0..n).map { i ->
    val a = 2 * PI * i / n
    GeoPoint(center.lat + radiusM * cos(a) / 111320.0, center.lon + radiusM * sin(a) / (111320.0 * cos(center.lat * PI / 180)))
}
