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
fun MapLibreSurface(model: MapSurfaceModel, events: MapSurfaceEvents, modifier: Modifier = Modifier, userAgent: String = "") {
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
        update = { holder.apply(model) },
    )
}

/** MapView'in yaşam döngüsü ve çizim durumu (Compose dışı; tek iş parçacığı: ana). */
internal class MapLibreHolder(private val app: Context, private val userAgent: String) {
    var view: MapView? = null
        private set
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var styleKey: String? = null
    private var pending: MapSurfaceModel? = null
    private var appliedCameraSeq = -1
    private val density = app.resources.displayMetrics.density

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
            pending?.let { apply(it) }
        }
        view = v
        return v
    }

    fun destroy() {
        view?.let { it.onPause(); it.onStop(); it.onDestroy() }
        view = null; map = null; style = null; styleKey = null
    }

    fun apply(model: MapSurfaceModel) {
        pending = model
        val m = map ?: return
        m.uiSettings.setRotateGesturesEnabled(model.rotateGestures)
        val key = "${model.base}|${model.dark}"
        if (key != styleKey) {
            styleKey = key
            style = null
            val builder = if (model.base == BaseMap.OSM) Style.Builder().fromJson(MapStyles.osmRasterStyle(model.dark))
            else Style.Builder().fromUri(MapStyles.vectorStyleUrl(model.dark))
            m.setStyle(builder) { s ->
                if (styleKey != key) return@setStyle
                installLayers(s)
                style = s
                pending?.let { draw(s, it) }
            }
        } else {
            style?.let { draw(it, model) }
        }
        model.camera?.let { cam ->
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
        for (id in listOf(SRC_ACC, SRC_CIRCLE, SRC_LINES, SRC_MARKERS, SRC_PUCK)) s.addSource(GeoJsonSource(id))
        s.addLayer(FillLayer("weem-circle-fill", SRC_CIRCLE).withProperties(PropertyFactory.fillColor(Expression.toColor(Expression.get("color"))), PropertyFactory.fillOpacity(.15f)))
        s.addLayer(LineLayer("weem-circle-line", SRC_CIRCLE).withProperties(PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))), PropertyFactory.lineWidth(2f)))
        for ((pattern, dash) in listOf("solid" to null, "dash" to arrayOf(2f, 1.5f), "dot" to arrayOf(0f, 2f))) {
            val l = LineLayer("weem-lines-$pattern", SRC_LINES).withProperties(
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

    private fun draw(s: Style, model: MapSurfaceModel) {
        s.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(GeoJson.collection(model.lines.map { l ->
            GeoJson.line(l.points, mapOf(
                "color" to rgbCss(l.color), "opacity" to ((l.color ushr 24) / 255.0), "width" to l.widthDp, "z" to l.z,
                "pattern" to l.pattern.name.lowercase(Locale.ROOT),
            ))
        }))
        val markerFeatures = model.markers.map { mk ->
            val icon = MarkerBitmaps.key(mk, model.dark)
            if (s.getImage(icon) == null) s.addImage(icon, MarkerBitmaps.bitmap(mk, density, model.dark))
            GeoJson.point(mk.point, mapOf("icon" to icon))
        }
        s.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(GeoJson.collection(markerFeatures))
        val p = model.puck
        val puck = buildList {
            if (p != null) {
                if (!(p.arrow && p.course != null) && p.beam != null) add(GeoJson.point(p.point, mapOf("icon" to IMG_BEAM, "rot" to p.beam)))
                if (p.arrow && p.course != null) add(GeoJson.point(p.point, mapOf("icon" to IMG_ARROW, "rot" to p.course)))
                else add(GeoJson.point(p.point, mapOf("icon" to IMG_DOT, "rot" to 0)))
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC_PUCK)?.setGeoJson(GeoJson.collection(puck))
        val acc = p?.takeIf { PuckStyle.showsAccuracy(it.accuracyM) }?.let { listOf(GeoJson.polygon(circlePolygon(it.point, it.accuracyM!!), emptyMap())) } ?: emptyList()
        s.getSourceAs<GeoJsonSource>(SRC_ACC)?.setGeoJson(GeoJson.collection(acc))
        val circle = model.circle?.let { (c, r) -> listOf(GeoJson.polygon(circlePolygon(c, r), mapOf("color" to rgbCss(model.circleColor)))) } ?: emptyList()
        s.getSourceAs<GeoJsonSource>(SRC_CIRCLE)?.setGeoJson(GeoJson.collection(circle))
    }

    companion object {
        private const val SRC_LINES = "weem-lines"
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

/** Küçük GeoJSON yazıcı (geojson kütüphanesinin nesneleri yerine metin; MapLibre `setGeoJson(String)`). */
internal object GeoJson {
    private fun coords(p: GeoPoint) = String.format(Locale.US, "[%.7f,%.7f]", p.lon, p.lat)

    private fun props(m: Map<String, Any?>) = m.entries.joinToString(",", "{", "}") { (k, v) ->
        "\"$k\":" + when (v) {
            null -> "null"
            is Number, is Boolean -> v.toString()
            else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }

    fun point(p: GeoPoint, properties: Map<String, Any?>) = """{"type":"Feature","geometry":{"type":"Point","coordinates":${coords(p)}},"properties":${props(properties)}}"""

    fun line(points: List<GeoPoint>, properties: Map<String, Any?>) =
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":${points.joinToString(",", "[", "]") { coords(it) }}},"properties":${props(properties)}}"""

    fun polygon(ring: List<GeoPoint>, properties: Map<String, Any?>) =
        """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[${ring.joinToString(",", "[", "]") { coords(it) }}]},"properties":${props(properties)}}"""

    fun collection(features: List<String>) = features.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}")
}
