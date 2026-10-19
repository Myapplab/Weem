package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.BuildConfig
import com.kuryelog.app.ShiftCard
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.safely
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.graph.MapDataPhase
import com.kuryelog.logic.map.graph.MapDataState
import com.kuryelog.logic.map.ui.LocateAction
import com.kuryelog.logic.map.ui.LocateButtonRules
import com.kuryelog.logic.map.ui.MapCamera
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.ui.BaseMap
import com.kuryelog.logic.map.ui.BikewayLayerRules
import com.kuryelog.logic.map.ui.MapStyles
import com.kuryelog.logic.map.ui.PoiLayerRules
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Harita sekmesi (MAP.md 6.2, 6.3, 6.9): graf yokken vardiya kartı + kurulum kartı (harita yüzeyi yok); graf hazırsa harita yüzeyi (altlık `mapStyle`, tema) + HUD.
 * Kamera takip ve konum düğmesi kuralları 6.5; ekran açık = vardiya açık (durak/rota A6.6'da eklenir). Rota, arama, uzun basış eylemleri A6.6, sol göstergeler ve
 * hava A6.7, gün motoru ve İstatistik A6.8/A6.9'da bağlanır.
 */
@Composable
fun MapScreen(lang: Lang, dark: Boolean) {
    val ctx = LocalContext.current
    val controller = remember { MapData.controller(ctx) }
    val data by controller.state.collectAsState()
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings.region) {
        if (MapScreenLoad.loadedRegion != settings.region && data.phase != MapDataPhase.BUILDING) {
            MapScreenLoad.loadedRegion = settings.region
            runCatching { controller.load(settings.region) }
        }
    }
    val shift by ShiftHub.state.collectAsState()
    // Ekran açık: vardiya açıkken (YolGo `gün açık || durak var`; duraklar A6.6).
    val view = LocalView.current
    DisposableEffect(shift.active) {
        view.keepScreenOn = shift.active
        onDispose { view.keepScreenOn = false }
    }
    if (data.graph == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ShiftCard(lang)
            MapSetupCard(data, settings.region, lang,
                onRegion = { r -> ctx.safely { MapSettingsRepo.update(ctx) { it.copy(region = r) } } },
                onDownload = { scope.launch { runCatching { controller.download(settings.region) } } })
        }
        return
    }
    LiveMap(lang, dark, data, settings.mapStyle, shift.active, shift.startedAtMs, shift.interrupted, settings.showBikewayLayer)
}

private object MapScreenLoad {
    @Volatile var loadedRegion: String? = null
}

@Composable
private fun LiveMap(lang: Lang, dark: Boolean, data: MapDataState, mapStyle: String, active: Boolean, startedAtMs: Long?, interrupted: Boolean, showBikeway: Boolean) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val base = MapStyles.resolve(mapStyle, BuildConfig.MAPS_ENABLED)
    val puck by rememberMapPuck(enabled = true, graph = data.graph)
    var follow by remember { mutableStateOf(true) }
    var headingUp by remember { mutableStateOf(true) }
    var autoZoom by remember { mutableStateOf(true) }
    var zoom by remember { mutableDoubleStateOf(MapCamera.STANDARD_ZOOM) }
    var cameraSeq by remember { mutableIntStateOf(0) }
    var lastMoveMs by remember { mutableLongStateOf(0L) }
    var camera by remember { mutableStateOf<MapCameraCommand?>(null) }
    var mapOnly by remember { mutableStateOf(false) }
    var askHome by remember { mutableStateOf(false) }
    var askEnd by remember { mutableStateOf(false) }
    // Yer katmanı kamerası: merkez 50 m ya da yakınlık 0,2 değişince yeniden seçilir (her hareket karesinde değil).
    var layerCam by remember { mutableStateOf<LayerCamera?>(null) }
    val cfg = LocalConfiguration.current
    val screenW = cfg.screenWidthDp.toDouble()
    val screenH = cfg.screenHeightDp.toDouble()
    val moveEveryMs = if (base == com.kuryelog.logic.map.ui.BaseMap.GOOGLE) MapCamera.GOOGLE_MOVE_MS else MapCamera.MAPLIBRE_MOVE_MS

    fun recenter(resetZoom: Boolean = true, bearingNow: Double? = null) {
        follow = true
        if (resetZoom) autoZoom = true
        val p = puck ?: return
        cameraSeq++
        camera = MapCameraCommand(cameraSeq, p.point, zoom = if (resetZoom) MapCamera.STANDARD_ZOOM else null, bearing = bearingNow ?: if (headingUp) p.course else 0.0)
    }
    // Takip: işaret her kayışta kamera da kayar (en sık 80/160 ms).
    LaunchedEffect(puck, follow, headingUp) {
        val p = puck ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastMoveMs < moveEveryMs) return@LaunchedEffect
        lastMoveMs = now
        cameraSeq++
        val bearing = if (headingUp) (p.course ?: p.cameraHeading) else 0.0
        camera = MapCameraCommand(cameraSeq, p.point, zoom = if (autoZoom && camera == null) MapCamera.STANDARD_ZOOM else null, bearing = bearing)
    }
    val mode = LocateButtonRules.cameraModeOf(follow, headingUp)
    // Gün görünümü gün motorundan (`DayRuntime`, MAP.md 11.14); motor henüz başlamadıysa vardiyadan türetilen boş görünüm.
    val live by DayRuntime.view.collectAsState()
    val day = live ?: remember(active, startedAtMs) { DayView(DayState().apply { this.active = active; this.startedAtMs = startedAtMs }, null) }
    val layerRev by MapLayers.rev.collectAsState()
    val bikeLines = remember(data.graph, layerRev, showBikeway, dark) { bikewayLines(ctx, data, showBikeway, dark) }
    val poiMarkers = remember(data.pois, layerRev, day.lightsRev, layerCam, base) { poiMarkers(ctx, data, day.lightsRev, layerCam, base, screenW, screenH) }
    val lightMark = day.lightAt?.let { listOf(MapMarker("light-wait", it, MarkerKind.LIGHT_WAIT)) } ?: emptyList()
    val model = MapSurfaceModel(
        base = base, dark = dark, camera = camera,
        lines = bikeLines, markers = poiMarkers + lightMark,
        puck = puck?.let { MapPuck(it.point, it.course, it.beam, arrow = false, accuracyM = it.accuracyM) },
    )
    val events = MapSurfaceEvents(
        onTap = { mapOnly = !mapOnly },
        onGesture = { follow = false },
        onCameraIdle = { c, z, b ->
            if (abs(z - zoom) > 0.01) { zoom = z; if (abs(z - MapCamera.STANDARD_ZOOM) > 0.05) autoZoom = false }
            val last = layerCam
            if (last == null || abs(last.zoom - z) >= 0.2 || last.center.distanceTo(c) > 50.0) layerCam = LayerCamera(c, z, b)
        },
    )
    MapTab(
        state = MapTabState(day = day, cameraMode = mode, mapOnly = mapOnly, weatherStrip = false, nowMs = System.currentTimeMillis()),
        lang = lang,
        mapSurface = {
            Box(Modifier.fillMaxSize()) {
                MapSurface(model, events, Modifier.fillMaxSize(), userAgent = MapStyles.userAgent(BuildConfigInfo.versionName(ctx)))
                MapStyles.attribution(base)?.let { a ->
                    Text(
                        a.text, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clickable { a.url?.let { u -> runCatching { uri.openUri(u) } } },
                    )
                }
            }
        },
        onHome = { askHome = true },
        onStats = {},
        onEnd = { askEnd = true },
        actions = MapTabActions(
            onLocate = {
                // Takipte ama elle yakınlaştırılmışsa yalnız standart yakınlığa döner (MAP.md 6.5).
                if (follow && !autoZoom) recenter()
                else when (LocateButtonRules.locateActionFor(mode)) {
                    LocateAction.RECENTER -> recenter()
                    LocateAction.HEADING_UP -> { headingUp = true; recenter() }
                    LocateAction.NORTH_UP -> { headingUp = false; recenter(bearingNow = 0.0) }
                }
            },
            onShowPanels = { mapOnly = false },
        ),
        toast = { DayToastHost() },
        // Sol göstergeler A6.7'de (MapMetrics); vardiya kapalıyken ya da yarıda kalmışken başlat/bitir kartı üstte.
        metrics = {},
        topCard = { if (!active || interrupted) ShiftCard(lang) },
    )
    if (askHome) {
        val hasHome = remember { Home.point(ctx) != null }
        AlertDialog(
            onDismissRequest = { askHome = false },
            title = { Text(MapText.HOME.get(lang)) },
            text = {
                Text(
                    if (hasHome) MapText.HOME_ASK.get(lang)
                    else MapText.HOME_MISSING.get(lang),
                )
            },
            // Eve rota (goHome) navigasyonla gelir (A6.6); onay şimdilik yalnız pencereyi kapatır.
            confirmButton = { if (hasHome) Button(onClick = { askHome = false }) { Text(MapText.HOME.get(lang)) } },
            dismissButton = { TextButton(onClick = { askHome = false }) { Text(MapText.CANCEL.get(lang)) } },
        )
    }
    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text(MapText.END_ASK.get(lang)) },
            text = { Text(MapText.END_TEXT.get(lang)) },
            confirmButton = { Button(onClick = { askEnd = false; ctx.safely { ShiftHub.stop() } }) { Text(MapText.END_CONFIRM.get(lang)) } },
            dismissButton = { TextButton(onClick = { askEnd = false }) { Text(MapText.CANCEL.get(lang)) } },
        )
    }
}

/** Yer katmanının son seçildiği kamera. */
private class LayerCamera(val center: GeoPoint, val zoom: Double, val bearing: Double)

/** Bisiklet yolu çizgileri (Y15, MAP.md 5.1): OSM düz, elle işaretli kesikli; iki yüzeyde de en altta. */
private fun bikewayLines(ctx: android.content.Context, data: MapDataState, show: Boolean, dark: Boolean): List<MapLine> {
    val g = data.graph ?: return emptyList()
    val marks = MapLayers.bikeway(ctx).marks
    return BikewayLayerRules.lines(g, marks, show, dark).map { l ->
        MapLine(
            "bike-${l.edge}", g.geometry(l.edge).map { GeoPoint(it.lat, it.lon) },
            color = ((l.alpha * 255).toInt() shl 24) or l.rgb, widthDp = l.width.toFloat(), pattern = if (l.dashed) LinePattern.DASH else LinePattern.SOLID, z = 1,
        )
    }
}

/**
 * Yer işaretleri (Y16, MAP.md 5.3): düzeltilmiş POI'ler + yerel ışık işaretleri; kamera çevresinden `PoiLayerRules` seçer. MapLibre 512 piksellik dünya kullandığından
 * yakınlık kurallarına `zoom + 1` (256 piksellik YolGo ölçeği) verilir.
 */
private fun poiMarkers(ctx: android.content.Context, data: MapDataState, lightsRev: Int, cam: LayerCamera?, base: BaseMap, w: Double, h: Double): List<MapMarker> {
    cam ?: return emptyList()
    val lights = DayRuntime.lightSpots(ctx)
    val shown = PoiLayerRules.displayPois(MapLayers.corrections(ctx).corrected(data.pois), lights) ?: return emptyList()
    val ez = if (base == BaseMap.GOOGLE) cam.zoom else cam.zoom + 1
    val box = PoiLayerRules.visibleBox(cam.center, ez, w, h)
    val sel = PoiLayerRules.select(shown.within(box.south, box.west, box.north, box.east), ez, -cam.bearing)
    val named = sel.labels.map { it.poi }.toHashSet<Poi>()
    return sel.markers.mapIndexed { i, m ->
        val p = m.poi
        val kind = when (p.kind) { 's' -> MarkerKind.POI_METRO; 'm' -> MarkerKind.POI_MALL; 'l' -> MarkerKind.POI_LIGHT; else -> MarkerKind.POI_FOOD }
        MapMarker("poi-$i-${p.lat}-${p.lon}", GeoPoint(p.lat, p.lon), kind, label = if (kind == MarkerKind.POI_FOOD) p.kind.toString() else "", name = if (p in named) p.name else "")
    }
}

internal object BuildConfigInfo {
    fun versionName(ctx: android.content.Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
}

/**
 * Graf yokken kurulum kartı (YolGo kurulum ekranı adım 2; MAP.md 6.9, 2.9): bölge seçici, "İndir ve başla", indirme sürerken ilerleme + aşama + "Ekranı açık tut",
 * hata kırmızı, OSM/SRTM atfı; açılırken "Bakü haritası hazırlanıyor…".
 */
@Composable
fun MapSetupCard(state: MapDataState, region: String, lang: Lang, onRegion: (String) -> Unit, onDownload: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (state.phase == MapDataPhase.CHECKING || state.phase == MapDataPhase.UNPACKING) {
                Text(MapText.SETUP_PREPARING.get(lang), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                return@Column
            }
            Text(MapText.SETUP_TITLE.get(lang), style = MaterialTheme.typography.titleMedium)
            Text(
                MapText.SETUP_TEXT.get(lang),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val building = state.phase == MapDataPhase.BUILDING
            for ((key, label) in listOf("central" to MapText.REGION_CENTRAL.get(lang), "wide" to MapText.REGION_WIDE.get(lang))) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !building) { onRegion(key) }) {
                    RadioButton(selected = region == key, onClick = { onRegion(key) }, enabled = !building)
                    Text(label)
                }
            }
            if (building) {
                LinearProgressIndicator(progress = { state.progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("%${(state.progress * 100).roundToInt()} · ${state.stage}", style = MaterialTheme.typography.bodySmall)
                Text(MapText.SETUP_KEEP_ON.get(lang), style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = onDownload, modifier = Modifier.padding(top = 8.dp)) { Text(MapText.SETUP_DOWNLOAD.get(lang)) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Text(
                MapText.SETUP_ATTRIBUTION.get(lang),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
