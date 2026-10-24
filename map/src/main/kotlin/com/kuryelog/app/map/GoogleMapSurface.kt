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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.ui.GoogleMapStyle
import com.kuryelog.logic.map.ui.MapCamera
import com.kuryelog.logic.map.ui.PuckStyle

/**
 * Google yüzeyi (YolGo `google_map_surface.dart`; MAP.md 6.4): yalnız görüntü katmanı — çizgiler WEEM'in, Map ID / Routes / Navigation / Places SDK yok. Renk şeması açık,
 * koyu harita JSON stille; yakınlık <17'de `poi.business` ve `transit` gizli. Yakınlık 11–20; eğme, pusula, yakınlık düğmeleri, araç çubuğu ve "konumum" kapalı;
 * iç boşluk 16/120/16/240 dp (HUD payı). Yalnız anahtarlı derlemede seçilir (`MapStyles.resolve`).
 */
@Composable
fun GoogleMapSurface(model: MapSurfaceModel, events: MapSurfaceEvents, modifier: Modifier = Modifier, live: MapLive? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { GoogleHolder(context.applicationContext) }
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
    // [live] durumları burada okunur: değişince yalnız bu blok yeniden çalışır (S38).
    AndroidView(
        modifier = modifier, factory = { ctx -> mapViewOrBlank(ctx) { holder.create(ctx) { currentEvents.value } } },
        update = { holder.apply(model, live?.puck?.value ?: model.puck, live?.camera?.value ?: model.camera) },
    )
}

/** S38: çizgi grupları (alt çizgiler, rota) ayrı tutulur; yalnız değişen grup yeniden eklenir. İşaret ve kamera [MapLive]'tan. */
internal class GoogleHolder(private val app: Context) {
    var view: MapView? = null
        private set
    private var map: GoogleMap? = null
    private var pending: MapSurfaceModel? = null
    private var pendingPuck: MapPuck? = null
    private var pendingCamera: MapCameraCommand? = null
    private var styleKey: String? = null
    private var appliedCameraSeq = -1
    private var baseLines: List<MapLine>? = null
    private val basePolylines = ArrayList<Polyline>()
    private var lines: List<MapLine>? = null
    private val polylines = ArrayList<Polyline>()
    private var markers: List<MapMarker>? = null
    // S39: işaretler içerikleriyle eşlenir; yalnız eklenen/kalkan Google nesnesi değişir (önceden her değişimde hepsi silinip yeniden ekleniyordu).
    private val markerHandles = HashMap<MapMarker, Marker>()
    private var markersDark: Boolean? = null
    private var puckMarker: Marker? = null
    private var beamMarker: Marker? = null
    private var accuracy: Circle? = null
    private var circle: Circle? = null
    private val icons = HashMap<String, BitmapDescriptor>()
    private val density = app.resources.displayMetrics.density

    fun create(ctx: Context, events: () -> MapSurfaceEvents): MapView {
        MapsInitializer.initialize(app)
        val v = MapView(ctx, GoogleMapOptions().compassEnabled(false).mapToolbarEnabled(false).tiltGesturesEnabled(false).zoomControlsEnabled(false))
        // onStart/onResume yaşam döngüsü gözlemcisinden gelir (eklenince o anki duruma kadar olaylar yeniden gönderilir).
        v.onCreate(null)
        v.getMapAsync { m ->
            map = m
            m.uiSettings.isMyLocationButtonEnabled = false
            m.setMinZoomPreference(MapCamera.GOOGLE_MIN_ZOOM.toFloat())
            m.setMaxZoomPreference(MapCamera.GOOGLE_MAX_ZOOM.toFloat())
            val p = MapCamera.GOOGLE_PADDING_DP.map { (it * density).toInt() }
            m.setPadding(p[0], p[1], p[2], p[3])
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(MapCamera.BAKU.lat, MapCamera.BAKU.lon), MapCamera.STANDARD_ZOOM.toFloat()))
            m.setOnMapClickListener { events().onTap(GeoPoint(it.latitude, it.longitude)) }
            m.setOnMapLongClickListener { events().onLongPress(GeoPoint(it.latitude, it.longitude)) }
            m.setOnCameraMoveStartedListener { reason -> if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) events().onGesture() }
            m.setOnCameraIdleListener {
                val c = m.cameraPosition
                events().onCameraIdle(GeoPoint(c.target.latitude, c.target.longitude), c.zoom.toDouble(), c.bearing.toDouble())
                pending?.let { applyStyle(m, it) }
            }
            pending?.let { apply(it, pendingPuck, pendingCamera) }
        }
        view = v
        return v
    }

    fun destroy() {
        view?.let { it.onPause(); it.onStop(); it.onDestroy() }
        view = null; map = null
        baseLines = null; basePolylines.clear(); lines = null; polylines.clear(); markers = null; markerHandles.clear(); markersDark = null
        puckMarker = null; beamMarker = null; accuracy = null; circle = null; styleKey = null; drawnPuck = null; puckArrow = null
    }

    private fun applyStyle(m: GoogleMap, model: MapSurfaceModel) {
        val hide = m.cameraPosition.zoom < GoogleMapStyle.HIDE_POIS_BELOW_ZOOM
        val key = "${model.dark}|$hide"
        if (key == styleKey) return
        styleKey = key
        m.setMapStyle(GoogleMapStyle.style(dark = model.dark, hidePois = hide)?.let { MapStyleOptions(it) })
    }

    fun apply(model: MapSurfaceModel, puck: MapPuck? = model.puck, camera: MapCameraCommand? = model.camera) {
        pending = model
        pendingPuck = puck
        pendingCamera = camera
        val m = map ?: return
        if (m.uiSettings.isRotateGesturesEnabled != model.rotateGestures) m.uiSettings.isRotateGesturesEnabled = model.rotateGestures
        applyStyle(m, model)
        if (model.baseLines != baseLines) {
            baseLines = model.baseLines
            replaceLines(m, basePolylines, model.baseLines)
        }
        if (model.lines != lines) {
            lines = model.lines
            replaceLines(m, polylines, model.lines)
        }
        if (model.markers != markers || model.dark != markersDark) {
            markers = model.markers
            if (model.dark != markersDark) { markerHandles.values.forEach { it.remove() }; markerHandles.clear(); markersDark = model.dark }
            val want = model.markers.toHashSet()
            val gone = markerHandles.keys.filter { it !in want }
            for (k in gone) markerHandles.remove(k)?.remove()
            for (mk in model.markers) {
                if (mk in markerHandles) continue
                val icon = icons.getOrPut(MarkerBitmaps.key(mk, model.dark)) { BitmapDescriptorFactory.fromBitmap(MarkerBitmaps.of(mk, density, model.dark)) }
                m.addMarker(MarkerOptions().position(LatLng(mk.point.lat, mk.point.lon)).icon(icon).anchor(.5f, .5f).title(mk.label))?.let { markerHandles[mk] = it }
            }
        }
        drawPuck(m, puck)
        val c = model.circle
        if (c == null) { circle?.remove(); circle = null } else {
            val ll = LatLng(c.first.lat, c.first.lon)
            circle?.let { it.center = ll; it.radius = c.second } ?: run {
                circle = m.addCircle(CircleOptions().center(ll).radius(c.second).fillColor((model.circleColor and 0xFFFFFF) or 0x26000000).strokeColor(model.circleColor).strokeWidth(2 * density))
            }
        }
        camera?.let { cam ->
            if (cam.seq == appliedCameraSeq) return@let
            appliedCameraSeq = cam.seq
            val cur = m.cameraPosition
            val pos = CameraPosition(LatLng(cam.center.lat, cam.center.lon), (cam.zoom ?: cur.zoom.toDouble()).toFloat(), 0f, (cam.bearing ?: cur.bearing.toDouble()).toFloat())
            m.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
        }
    }

    private fun replaceLines(m: GoogleMap, handles: ArrayList<Polyline>, list: List<MapLine>) {
        handles.forEach { it.remove() }; handles.clear()
        for (l in list) {
            val o = PolylineOptions().addAll(l.points.map { LatLng(it.lat, it.lon) }).color(l.color).width(l.widthDp * density).zIndex(l.z.toFloat())
            when (l.pattern) {
                LinePattern.DASH -> o.pattern(listOf(Dash(8 * density), Gap(6 * density)))
                LinePattern.DOT -> o.pattern(listOf(Dot(), Gap(4 * density)))
                LinePattern.SOLID -> Unit
            }
            handles += m.addPolyline(o)
        }
    }

    private var drawnPuck: MapPuck? = null
    private var puckArrow: Boolean? = null

    private fun drawPuck(m: GoogleMap, p: MapPuck?) {
        if (p == drawnPuck && (p == null || puckMarker != null)) return
        drawnPuck = p
        if (p == null) {
            puckMarker?.remove(); puckMarker = null; puckArrow = null; beamMarker?.remove(); beamMarker = null; accuracy?.remove(); accuracy = null
            return
        }
        val ll = LatLng(p.point.lat, p.point.lon)
        val arrow = PuckStyle.showsArrow(p.arrow, p.course)
        val icon = icons.getOrPut(if (arrow) "puck-arrow" else "puck-dot") { BitmapDescriptorFactory.fromBitmap(PuckBitmaps.render(density, arrow = arrow, beam = false)) }
        val pm = puckMarker ?: m.addMarker(MarkerOptions().position(ll).icon(icon).anchor(.5f, .5f).flat(true).zIndex(100f))?.also { puckMarker = it; puckArrow = arrow }
        pm?.position = ll
        if (puckArrow != arrow) { pm?.setIcon(icon); puckArrow = arrow }
        pm?.rotation = if (arrow) p.course!!.toFloat() else 0f
        val beam = if (!arrow) p.beam else null
        if (beam == null) { beamMarker?.remove(); beamMarker = null } else {
            val bi = icons.getOrPut("puck-beam") { BitmapDescriptorFactory.fromBitmap(PuckBitmaps.render(density, arrow = false, beam = true)) }
            val bm = beamMarker ?: m.addMarker(MarkerOptions().position(ll).icon(bi).anchor(.5f, .5f).flat(true).zIndex(99f))?.also { beamMarker = it }
            bm?.position = ll
            bm?.rotation = beam.toFloat()
        }
        val acc = p.accuracyM
        if (!PuckStyle.showsAccuracy(acc)) { accuracy?.remove(); accuracy = null } else {
            accuracy?.let { it.center = ll; it.radius = acc!! } ?: run {
                accuracy = m.addCircle(CircleOptions().center(ll).radius(acc!!).fillColor(PuckStyle.ACCURACY_FILL).strokeColor(PuckStyle.ACCURACY_STROKE).strokeWidth(density).zIndex(98f))
            }
        }
    }
}
