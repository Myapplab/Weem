package com.kuryelog.app.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ui.BaseMap
import com.kuryelog.logic.map.ui.MapCamera
import com.kuryelog.logic.map.ui.MapStyles
import com.kuryelog.logic.map.ui.PuckStyle
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

/**
 * İkinci harita görünümü (Y2, S4; MAP.md 6.4): MapLibre Native, iki stil — OSM raster (`tile.openstreetmap.org`, koyuda ters çevrilmiş) ve vektör. Yakınlık 11–19,
 * döndürme ve eğme jesti kapalı; atıf HUD'da (sol alt, MAP.md 6.4), MapLibre'nin kendi logosu ve atıf düğmesi kapalı. Disk önbelleği açık, çevrimdışı paket yok.
 * Çizgiler, işaretler ve konum işareti kendi kaynak ve katmanlarında (`weem-*`); stil değişince yeniden kurulur.
 */
@Composable
fun MapLibreSurface(model: MapSurfaceModel, events: MapSurfaceEvents, modifier: Modifier = Modifier, userAgent: String = "", live: MapLive? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapLibreHolder(context.applicationContext, userAgent) }
    val currentEvents = rememberUpdatedState(events)
    DisposableEffect(lifecycle, holder) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> holder.view?.onStart()
                Lifecycle.Event.ON_RESUME -> holder.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view?.onPause()
                Lifecycle.Event.ON_STOP -> holder.view?.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> holder.create(ctx) { currentEvents.value } },
        // [live] durumları burada okunur: değişince yalnız bu blok yeniden çalışır (sekme yeniden oluşturulmaz).
        update = { holder.apply(model, live?.puck?.value ?: model.puck, live?.camera?.value ?: model.camera) },
    )
}

/**
 * MapView'in yaşam döngüsü ve çizim durumu (Compose dışı; tek iş parçacığı: ana). S38: her kaynak yalnız içeriği değişince yeniden yüklenir (önceden her karede
 * bütün çizgiler `setGeoJson` ile baştan yükleniyordu: MapLibre karoları bitiremiyor, rota ve yollar karo sınırında kesik/yarım görünüyordu; ana iş parçacığı ve
 * pil boşa yanıyordu). Alt çizgiler (bisiklet yolu …) ve rota ayrı kaynakta: rota değişince binlerce bisiklet yolu noktası yeniden karolanmaz.
 */
internal class MapLibreHolder(private val app: Context, private val userAgent: String) {
    var view: MapView? = null
        private set
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var styleKey: String? = null
    private var pending: MapSurfaceModel? = null
    private var pendingPuck: MapPuck? = null
    private var pendingCamera: MapCameraCommand? = null
    private var appliedCameraSeq = -1
    private val density = app.resources.displayMetrics.density
    // Son çizilenler (stil yeniden kurulunca sıfırlanır): aynıysa kaynak yeniden yüklenmez.
    private var drawnBase: List<MapLine>? = null
    private var drawnLines: List<MapLine>? = null
    private var drawnMarkers: List<MapMarker>? = null
    private var drawnMarkersDark: Boolean? = null
    private var drawnPuck: MapPuck? = null
    private var puckDrawn = false
    private var drawnCircle: Pair<GeoPoint, Double>? = null
    private var circleDrawn = false

    fun create(ctx: Context, events: () -> MapSurfaceEvents): MapView {
        MapLibre.getInstance(app)
        if (userAgent.isNotEmpty() && !uaInstalled) {
            uaInstalled = true
            HttpRequestUtil.setOkHttpClient(
                OkHttpClient.Builder().addNetworkInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }.build(),
            )
        }
        val v = MapView(ctx)
        // onStart/onResume yaşam döngüsü gözlemcisinden gelir (eklenince o anki duruma kadar olaylar yeniden gönderilir).
        v.onCreate(null)
        v.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                setCompassEnabled(false); setLogoEnabled(false); setAttributionEnabled(false); setTiltGesturesEnabled(false)
            }
            m.setMinZoomPreference(MapCamera.MAPLIBRE_MIN_ZOOM)
            m.setMaxZoomPreference(MapCamera.MAPLIBRE_MAX_ZOOM)
            m.cameraPosition = CameraPosition.Builder().target(LatLng(MapCamera.BAKU.lat, MapCamera.BAKU.lon)).zoom(MapCamera.STANDARD_ZOOM).build()
            m.addOnMapClickListener { events().onTap(GeoPoint(it.latitude, it.longitude)); false }
            m.addOnMapLongClickListener { events().onLongPress(GeoPoint(it.latitude, it.longitude)); true }
            m.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) events().onGesture() }
            m.addOnCameraIdleListener {
                val c = m.cameraPosition
                c.target?.let { t -> events().onCameraIdle(GeoPoint(t.latitude, t.longitude), c.zoom, c.bearing) }
            }
            pending?.let { apply(it, pendingPuck, pendingCamera) }
        }
        view = v
        return v
    }

    fun destroy() {
        view?.let { it.onPause(); it.onStop(); it.onDestroy() }
        view = null; map = null; style = null; styleKey = null
        forget()
    }

    /** Stil yeniden kurulunca kaynaklar boştur: hepsi yeniden çizilir. */
    private fun forget() {
        drawnBase = null; drawnLines = null; drawnMarkers = null; drawnMarkersDark = null
        drawnPuck = null; puckDrawn = false; drawnCircle = null; circleDrawn = false
    }

    fun apply(model: MapSurfaceModel, puck: MapPuck? = model.puck, camera: MapCameraCommand? = model.camera) {
        pending = model
        pendingPuck = puck
        pendingCamera = camera
        val m = map ?: return
        if (m.uiSettings.isRotateGesturesEnabled != model.rotateGestures) m.uiSettings.setRotateGesturesEnabled(model.rotateGestures)
        val key = "${model.base}|${model.dark}"
        if (key != styleKey) {
            styleKey = key
            style = null
            forget()
            val builder = if (model.base == BaseMap.OSM) Style.Builder().fromJson(MapStyles.osmRasterStyle(model.dark))
            else Style.Builder().fromUri(MapStyles.vectorStyleUrl(model.dark))
            m.setStyle(builder) { s ->
                if (styleKey != key) return@setStyle
                installLayers(s)
                style = s
                pending?.let { draw(s, it, pendingPuck) }
            }
        } else {
            style?.let { draw(it, model, puck) }
        }
        camera?.let { cam ->
            if (cam.seq == appliedCameraSeq) return@let
            appliedCameraSeq = cam.seq
            val b = CameraPosition.Builder().target(LatLng(cam.center.lat, cam.center.lon))
            cam.zoom?.let { b.zoom(it) }
            cam.bearing?.let { b.bearing(it) }
            val u = CameraUpdateFactory.newCameraPosition(b.build())
            if (cam.animate) m.animateCamera(u, MapCamera.MAPLIBRE_MOVE_MS.toInt() * 4) else m.moveCamera(u)
        }
    }

    private fun installLayers(s: Style) {
        for (id in listOf(SRC_ACC, SRC_CIRCLE, SRC_BASE_LINES, SRC_LINES, SRC_MARKERS, SRC_PUCK)) s.addSource(GeoJsonSource(id))
        s.addLayer(FillLayer("weem-circle-fill", SRC_CIRCLE).withProperties(PropertyFactory.fillColor(Expression.toColor(Expression.get("color"))), PropertyFactory.fillOpacity(.15f)))
        s.addLayer(LineLayer("weem-circle-line", SRC_CIRCLE).withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))), PropertyFactory.lineWidth(2f)))
        // Alt çizgiler rotanın altında (iki kaynak; her biri üç desen katmanı).
        for (src in listOf(SRC_BASE_LINES, SRC_LINES)) for ((pattern, dash) in listOf("solid" to null, "dash" to arrayOf(2f, 1.5f), "dot" to arrayOf(0f, 2f))) {
            val l = LineLayer("$src-$pattern", src).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineOpacity(Expression.get("opacity")),
                PropertyFactory.lineWidth(Expression.get("width")),
                PropertyFactory.lineSortKey(Expression.get("z")),
                PropertyFactory.lineCap(if (pattern == "dot") Property.LINE_CAP_ROUND else Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            if (dash != null) l.setProperties(PropertyFactory.lineDasharray(dash))
            l.setFilter(Expression.eq(Expression.get("pattern"), pattern))
            s.addLayer(l)
        }
        s.addLayer(
            SymbolLayer("weem-markers", SRC_MARKERS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        s.addLayer(
            FillLayer("weem-accuracy", SRC_ACC).withProperties(
                PropertyFactory.fillColor(argbCss(PuckStyle.ACCURACY_FILL)), PropertyFactory.fillOutlineColor(argbCss(PuckStyle.ACCURACY_STROKE)),
            ),
        )
        s.addLayer(
            SymbolLayer("weem-puck", SRC_PUCK).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconRotate(Expression.get("rot")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        s.addImage(IMG_DOT, PuckBitmaps.render(density, arrow = false, beam = false))
        s.addImage(IMG_ARROW, PuckBitmaps.render(density, arrow = true, beam = false))
        s.addImage(IMG_BEAM, PuckBitmaps.render(density, arrow = false, beam = true))
    }

    private fun draw(s: Style, model: MapSurfaceModel, p: MapPuck?) {
        if (model.baseLines != drawnBase) {
            drawnBase = model.baseLines
            s.getSourceAs<GeoJsonSource>(SRC_BASE_LINES)?.setGeoJson(lineCollection(model.baseLines))
        }
        if (model.lines != drawnLines) {
            drawnLines = model.lines
            s.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(lineCollection(model.lines))
        }
        if (model.markers != drawnMarkers || model.dark != drawnMarkersDark) {
            drawnMarkers = model.markers
            drawnMarkersDark = model.dark
            val features = GeoJson.Builder()
            for (mk in model.markers) {
                val icon = MarkerBitmaps.key(mk, model.dark)
                if (s.getImage(icon) == null) s.addImage(icon, MarkerBitmaps.of(mk, density, model.dark))
                features.point(mk.point) { prop("icon", icon) }
            }
            s.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(features.build())
        }
        if (!puckDrawn || p != drawnPuck) {
            val accuracyChanged = !puckDrawn || p?.point != drawnPuck?.point || p?.accuracyM != drawnPuck?.accuracyM
            drawnPuck = p
            puckDrawn = true
            val puck = GeoJson.Builder()
            if (p != null) {
                val arrow = PuckStyle.showsArrow(p.arrow, p.course)
                if (!arrow && p.beam != null) puck.point(p.point) { prop("icon", IMG_BEAM); prop("rot", p.beam) }
                if (arrow) puck.point(p.point) { prop("icon", IMG_ARROW); prop("rot", p.course!!) }
                else puck.point(p.point) { prop("icon", IMG_DOT); prop("rot", 0.0) }
            }
            s.getSourceAs<GeoJsonSource>(SRC_PUCK)?.setGeoJson(puck.build())
            if (accuracyChanged) {
                val acc = GeoJson.Builder()
                if (p != null && PuckStyle.showsAccuracy(p.accuracyM)) acc.polygon(circlePolygon(p.point, p.accuracyM!!)) {}
                s.getSourceAs<GeoJsonSource>(SRC_ACC)?.setGeoJson(acc.build())
            }
        }
        if (!circleDrawn || model.circle != drawnCircle) {
            drawnCircle = model.circle
            circleDrawn = true
            val circle = GeoJson.Builder()
            model.circle?.let { (c, r) -> circle.polygon(circlePolygon(c, r)) { prop("color", rgbCss(model.circleColor)) } }
            s.getSourceAs<GeoJsonSource>(SRC_CIRCLE)?.setGeoJson(circle.build())
        }
    }

    private fun lineCollection(lines: List<MapLine>): String {
        val b = GeoJson.Builder()
        for (l in lines) b.line(l.points) {
            prop("color", rgbCss(l.color)); prop("opacity", (l.color ushr 24) / 255.0); prop("width", l.widthDp.toDouble()); prop("z", l.z.toDouble())
            prop("pattern", l.pattern.name.lowercase(Locale.ROOT))
        }
        return b.build()
    }

    companion object {
        private const val SRC_LINES = "weem-lines"
        private const val SRC_BASE_LINES = "weem-base-lines"
        private const val SRC_MARKERS = "weem-markers"
        private const val SRC_PUCK = "weem-puck"
        private const val SRC_ACC = "weem-accuracy"
        private const val SRC_CIRCLE = "weem-circle"
        private const val IMG_DOT = "puck-dot"
        private const val IMG_ARROW = "puck-arrow"
        private const val IMG_BEAM = "puck-beam"
        private var uaInstalled = false

        fun rgbCss(argb: Int) = String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
        fun argbCss(argb: Int) = String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", (argb shr 16) and 255, (argb shr 8) and 255, argb and 255, (argb ushr 24) / 255.0)
    }
}

/**
 * Küçük GeoJSON yazıcı (geojson kütüphanesinin nesneleri yerine metin; MapLibre `setGeoJson(String)`). S38: `String.format` yerine `StringBuilder`
 * (binlerce noktalı bisiklet yolu katmanı nokta başına biçimlendirme yüzünden ana iş parçacığını meşgul ediyordu).
 */
internal object GeoJson {
    class Props internal constructor(private val sb: StringBuilder) {
        private var first = true

        private fun key(k: String) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(k).append("\":")
        }

        fun prop(k: String, v: Double) { key(k); if (v.isFinite()) sb.append(v) else sb.append("null") }

        fun prop(k: String, v: String) {
            key(k)
            sb.append('"')
            for (c in v) when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                else -> sb.append(c)
            }
            sb.append('"')
        }
    }

    class Builder {
        private val sb = StringBuilder(256).append("""{"type":"FeatureCollection","features":[""")
        private var count = 0

        private fun coords(p: GeoPoint) {
            sb.append('[').append(p.lon).append(',').append(p.lat).append(']')
        }

        private fun feature(type: String, props: Props.() -> Unit, geometry: () -> Unit) {
            if (count++ > 0) sb.append(',')
            sb.append("""{"type":"Feature","geometry":{"type":"""").append(type).append("""","coordinates":""")
            geometry()
            sb.append("""},"properties":{""")
            Props(sb).props()
            sb.append("}}")
        }

        fun point(p: GeoPoint, props: Props.() -> Unit) = feature("Point", props) { coords(p) }

        fun line(points: List<GeoPoint>, props: Props.() -> Unit) = feature("LineString", props) {
            sb.append('[')
            points.forEachIndexed { i, p -> if (i > 0) sb.append(','); coords(p) }
            sb.append(']')
        }

        fun polygon(ring: List<GeoPoint>, props: Props.() -> Unit) = feature("Polygon", props) {
            sb.append("[[")
            ring.forEachIndexed { i, p -> if (i > 0) sb.append(','); coords(p) }
            sb.append("]]")
        }

        fun build(): String = sb.append("]}").toString()
    }
}
