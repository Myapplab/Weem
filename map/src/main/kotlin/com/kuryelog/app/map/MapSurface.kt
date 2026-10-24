package com.kuryelog.app.map

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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

enum class MarkerKind {
    STOP_FIRST, STOP, REACHED,

    /** Yer noktaları (MAP.md 5.3): ışık, metro, AVM, restoran, fast food, kafe, market. */
    POI_LIGHT, POI_METRO, POI_MALL, POI_RESTAURANT, POI_FASTFOOD, POI_CAFE, POI_MARKET,

    /** S38: rota açıkken rotanın üstündeki trafik ışığı (büyük). */
    POI_LIGHT_ROUTE,
}

/** İşaret: duraklar (mor, numaralı; ilk tam, diğerleri %60), varılan hedef (yeşil) ve yer noktaları. */
data class MapMarker(val id: String, val point: GeoPoint, val kind: MarkerKind, val label: String = "")

/** Konum işareti: [course] ok yönü, [beam] koni yönü (pusula dahil), [arrow] rota açık; doğruluk dairesi 8 < [accuracyM] < 80 m. */
data class MapPuck(val point: GeoPoint, val course: Double?, val beam: Double?, val arrow: Boolean, val accuracyM: Double?)

/** Kamera komutu: [seq] her yeni komutta artar (yüzey yalnız yeniyi uygular). [bearing] null ise yön değişmez. */
data class MapCameraCommand(val seq: Int, val center: GeoPoint, val zoom: Double? = null, val bearing: Double? = null, val animate: Boolean = false)

/**
 * Bir yüzeye çizilecek her şey (MAP.md 6.4–6.6); yüzey yalnız görüntü katmanıdır, kararları vermez. [baseLines] seyrek değişen alt katman (bisiklet yolu, yol yok,
 * yüzey renkleri), [lines] rota; yüzey ikisini ayrı tutar, yalnız değişeni yeniden çizer (S38).
 */
data class MapSurfaceModel(
    val base: BaseMap,
    val dark: Boolean,
    val lines: List<MapLine> = emptyList(),
    val baseLines: List<MapLine> = emptyList(),
    val markers: List<MapMarker> = emptyList(),
    val puck: MapPuck? = null,
    val camera: MapCameraCommand? = null,
    /** Yalnız ev seçici: merkez çevresinde metre yarıçaplı daire (birincil renk %15 dolgu, 2 px kenar). */
    val circle: Pair<GeoPoint, Double>? = null,
    val circleColor: Int = 0xFF1765D1.toInt(),
    /** Döndürme jesti (MapLibre'de kapalı: harita yalnız kamera yönüyle döner). */
    val rotateGestures: Boolean = false,
)

/**
 * Kare başına değişenler (S38): konum işareti ve kamera. Yüzey bunları görünüm güncellemesinde okur; değişince yalnız işaret ve kamera çizilir, harita sekmesi
 * yeniden oluşturulmaz (önceden saniyede ~60 kez bütün çizgiler baştan yükleniyordu: rota ve yollar yarım görünüyordu, pil gidiyordu).
 */
class MapLive(val puck: State<MapPuck?>, val camera: State<MapCameraCommand?>)

/** Yüzeyden gelenler: dokunuş, uzun basış, elle hareket (parmak kaydırdı/yakınlaştırdı), kamera durdu. */
class MapSurfaceEvents(
    val onTap: (GeoPoint) -> Unit = {},
    val onLongPress: (GeoPoint) -> Unit = {},
    val onGesture: () -> Unit = {},
    val onCameraIdle: (center: GeoPoint, zoom: Double, bearing: Double) -> Unit = { _, _, _ -> },
)

/**
 * Harita yüzeyi (MAP.md 6.15 `MapSurface`): altlığa göre Google ya da MapLibre. Altlık değişince eski görünüm kapanır (yeni AndroidView). [live] verilirse işaret
 * ve kamera ondan (modeldekiler yerine).
 */
@Composable
fun MapSurface(model: MapSurfaceModel, events: MapSurfaceEvents, modifier: Modifier = Modifier, userAgent: String = "", live: MapLive? = null) {
    when (model.base) {
        BaseMap.GOOGLE -> GoogleMapSurface(model, events, modifier, live)
        else -> MapLibreSurface(model, events, modifier, userAgent, live)
    }
}

/**
 * S40: harita motorunun yerel kütüphanesi yüklenemezse (`UnsatisfiedLinkError`: uyumsuz ABI, Robolectric testi) uygulama düşmez, boş alan çizilir.
 * Görünüm tutucusunda `view` null kalır; yaşam döngüsü ve çizim çağrıları boşa gider.
 */
internal fun mapViewOrBlank(ctx: Context, create: () -> View): View = try {
    create()
} catch (_: LinkageError) {
    FrameLayout(ctx)
}

/** Metre yarıçaplı daire çokgeni (doğruluk ve ev seçici dairesi). */
internal fun circlePolygon(center: GeoPoint, radiusM: Double, n: Int = 48): List<GeoPoint> = (0..n).map { i ->
    val a = 2 * PI * i / n
    GeoPoint(center.lat + radiusM * cos(a) / 111320.0, center.lon + radiusM * sin(a) / (111320.0 * cos(center.lat * PI / 180)))
}
