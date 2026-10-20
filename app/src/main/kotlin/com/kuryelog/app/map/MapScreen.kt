package com.kuryelog.app.map

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import com.kuryelog.app.BuildConfig
import com.kuryelog.app.ShiftCard
import com.kuryelog.app.core.Home
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.safely
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.ApproachZoom
import com.kuryelog.logic.map.nav.ArrivalFocus
import com.kuryelog.logic.map.nav.ArrivalFocusTracker
import com.kuryelog.logic.map.nav.NavHaptic
import com.kuryelog.logic.map.nav.Refollow
import com.kuryelog.logic.map.ui.LongPressAction
import com.kuryelog.logic.map.ui.LongPressMenu
import com.kuryelog.logic.map.ui.MapPalette
import com.kuryelog.logic.map.ui.RouteLayer
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.graph.MapDataPhase
import com.kuryelog.logic.map.graph.MapDataState
import com.kuryelog.logic.map.ui.LocateAction
import com.kuryelog.logic.map.ui.LocateButtonRules
import com.kuryelog.logic.map.ui.MapCamera
import com.kuryelog.logic.map.ui.MapStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Harita sekmesi (MAP.md 6.2, 6.3, 6.9): graf yokken vardiya kartı + kurulum kartı (harita yüzeyi yok); graf hazırsa harita yüzeyi (altlık `mapStyle`, tema) + HUD.
 * Kamera takip ve konum düğmesi kuralları 6.5; ekran açık = vardiya açık ya da durak var. Navigasyon (A6.6; MAP.md 7.10, 7.11, 8.8): rota çizgileri ve rozetler, yeni rotada
 * sığdırma, yaklaşma zoom'u, varış odağı, dokunmadan sonra takibe dönüş, rota paneli, bildirim şeridi, arama, uzun basış hedef satırları, Eve dön. Sol göstergeler ve
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
    // Ekran açık: gün açık ya da durak var (YolGo `gün açık || durak var`).
    val navState by remember { NavRuntime.get(ctx) }.controller.state.collectAsState()
    val keepOn = shift.active || navState.stops.isNotEmpty()
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
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
    LiveMap(lang, dark, data, settings.mapStyle, shift.active, shift.startedAtMs, shift.interrupted)
}

private object MapScreenLoad {
    @Volatile var loadedRegion: String? = null
}

@Composable
private fun LiveMap(lang: Lang, dark: Boolean, data: MapDataState, mapStyle: String, active: Boolean, startedAtMs: Long?, interrupted: Boolean) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val runtime = remember { NavRuntime.get(ctx) }
    val navCtl = runtime.controller
    val nav by navCtl.state.collectAsState()
    val progress by navCtl.progress.collectAsState()
    val toast by navCtl.toast.collectAsState()
    val searchView by runtime.search.view.collectAsState()
    // Konum: harita sekmesi önde (MAP.md 7.6); arka planda gün açık ya da yalnız-nav iken NavRuntime sürer.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, runtime) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) runtime.setVisible(true)
            if (e == Lifecycle.Event.ON_STOP) runtime.setVisible(false)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); runtime.setVisible(false) }
    }
    LaunchedEffect(navCtl) { navCtl.haptics.collect { vibrate(ctx, it) } }
    val base = MapStyles.resolve(mapStyle, BuildConfig.MAPS_ENABLED)
    val google = base == com.kuryelog.logic.map.ui.BaseMap.GOOGLE
    val puck by rememberMapPuck(enabled = true, graph = data.graph)
    var follow by remember { mutableStateOf(true) }
    var headingUp by remember { mutableStateOf(true) }
    var autoZoom by remember { mutableStateOf(true) }
    var zoom by remember { mutableDoubleStateOf(MapCamera.STANDARD_ZOOM) }
    var navZoom by remember { mutableDoubleStateOf(MapCamera.STANDARD_ZOOM) }
    var cameraSeq by remember { mutableIntStateOf(0) }
    var lastMoveMs by remember { mutableLongStateOf(0L) }
    var camera by remember { mutableStateOf<MapCameraCommand?>(null) }
    // Takip elle dokunmayla mı bırakıldı (yeni rotada sığdırma da bırakır; o 5 sn sonra kendisi döner).
    var freeByGesture by remember { mutableStateOf(false) }
    var lastGestureMs by remember { mutableLongStateOf(0L) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val focusTracker = remember { ArrivalFocusTracker() }
    var focusOn by remember { mutableStateOf(false) }
    var mapOnly by remember { mutableStateOf(false) }
    var askHome by remember { mutableStateOf(false) }
    var askEnd by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf<GeoPoint?>(null) }
    val moveEveryMs = if (google) MapCamera.GOOGLE_MOVE_MS else MapCamera.MAPLIBRE_MOVE_MS
    val navNow by rememberUpdatedState(nav)

    fun command(center: GeoPoint, z: Double?, bearing: Double?) {
        cameraSeq++
        camera = MapCameraCommand(cameraSeq, center, zoom = z, bearing = bearing)
    }
    fun recenter(resetZoom: Boolean = true, bearingNow: Double? = null) {
        follow = true
        freeByGesture = false
        if (resetZoom) { autoZoom = true; navZoom = zoom }
        val p = puck ?: return
        command(p.point, if (resetZoom && !navNow.hasRoute) MapCamera.STANDARD_ZOOM else null, bearingNow ?: if (headingUp) p.course else 0.0)
    }
    // Hata ya da bekleyen adres gelince rota paneli kendiliğinden açılır (YolGo `RouteFab`).
    LaunchedEffect(nav.error, nav.pending) { if (nav.error != null || nav.pending != null) panelOpen = true }
    // Kalan yol: rota üstündeyse ölçüm, değilse park noktasına kuş uçuşu (yaklaşma zoom'u); varış odağı yalnız ölçümle (MAP.md 7.10).
    val measured = progress?.takeIf { it.onRoute }?.remainingM
    LaunchedEffect(measured, nav.hasRoute) { focusOn = focusTracker.update(nav.hasRoute, false, measured) }
    // Yeni rota: bütün rota + konum ekrana sığar, takip durur, 5 sn sonra konuma dönülür.
    LaunchedEffect(nav.seq) {
        if (nav.seq == 0 || !nav.hasRoute || viewport == IntSize.Zero) return@LaunchedEffect
        val pts = RouteLayer.fitPoints(nav.plan, puck?.point ?: navCtl.here())
        val w = with(density) { viewport.width.toDp().value.toDouble() }
        val h = with(density) { viewport.height.toDp().value.toDouble() }
        val cam = if (google) {
            val gp = MapCamera.GOOGLE_PADDING_DP
            RouteLayer.fit(pts, w - gp[0] - gp[2], h - gp[1] - gp[3], IntArray(4) { RouteLayer.GOOGLE_FIT_PAD })
        } else {
            RouteLayer.fit(pts, w, h)
        } ?: return@LaunchedEffect
        follow = false
        freeByGesture = false
        command(GeoPoint(cam.lat, cam.lon), cam.zoom, 0.0)
        val fittedAt = System.currentTimeMillis()
        delay(RouteLayer.REFOLLOW_AFTER_FIT_MS)
        if (!follow && lastGestureMs < fittedAt) recenter()
    }
    // Elle dokunmadan sonra: rota açıkken 10 sn → takip + otomatik zoom; rota yokken hareketteyken 8 sn → takip (ölçek korunur).
    LaunchedEffect(follow, freeByGesture) {
        if (follow || !freeByGesture) return@LaunchedEffect
        while (true) {
            delay(1_000)
            when (ApproachZoom.refollowAfterGesture(navNow.hasRoute, runtime.speedKmh >= MOVING_KMH, System.currentTimeMillis() - lastGestureMs)) {
                Refollow.FOLLOW_AUTO_ZOOM -> { recenter(); break }
                Refollow.FOLLOW_KEEP_ZOOM -> { recenter(resetZoom = false); break }
                Refollow.NONE -> Unit
            }
        }
    }
    // Takip: işaret her kayışta kamera da kayar (en sık 80/160 ms); rota açıkken yaklaşma zoom'u, varış odağında ben + hedef aynı karede.
    LaunchedEffect(puck, follow, headingUp, focusOn) {
        val p = puck ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastMoveMs < moveEveryMs) return@LaunchedEffect
        lastMoveMs = now
        val bearing = if (headingUp) (p.course ?: p.cameraHeading) else 0.0
        val st = navNow
        val dest = st.activeStop?.point
        if (focusOn && dest != null && viewport != IntSize.Zero) {
            val shortPx = with(density) { minOf(viewport.width, viewport.height).toDp().value.toInt() }
            val f = ArrivalFocus.focusCamera(p.point.lat, p.point.lon, dest.lat, dest.lon, shortPx)
            navZoom += (f.zoom - navZoom) * FOCUS_EASE
            command(GeoPoint(f.lat, f.lon), navZoom, bearing)
            return@LaunchedEffect
        }
        val z = if (autoZoom && st.hasRoute) {
            val park = st.plan?.legs?.firstOrNull()?.parkPoint
            val remaining = measured ?: park?.let { Geo.haversine(p.point.lat, p.point.lon, it.lat, it.lon) }
            navZoom += (ApproachZoom.zoomFor(runtime.speedKmh, remaining) - navZoom) * ZOOM_EASE
            navZoom
        } else if (autoZoom && camera == null) MapCamera.STANDARD_ZOOM else null
        command(p.point, z, bearing)
    }
    val mode = LocateButtonRules.cameraModeOf(follow, headingUp)
    val day = remember(active, startedAtMs) {
        DayView(DayState().apply { this.active = active; this.startedAtMs = startedAtMs }, null)
    }
    val lines = remember(nav.plans, nav.mode, dark) { RouteLayer.strokes(nav, MapPalette.of(dark)).map { it.toMapLine() } }
    val markers = remember(nav.plans, nav.mode, nav.reached) { RouteLayer.pins(nav).map { it.toMarker() } }
    val model = MapSurfaceModel(
        base = base, dark = dark, camera = camera, lines = lines, markers = markers,
        puck = puck?.let { MapPuck(it.point, it.course, it.beam, arrow = nav.hasRoute, accuracyM = it.accuracyM) },
    )
    val events = MapSurfaceEvents(
        onTap = { mapOnly = !mapOnly },
        onLongPress = { pressed = it },
        onGesture = { follow = false; autoZoom = false; freeByGesture = true; lastGestureMs = System.currentTimeMillis() },
        onCameraIdle = { _, z, _ -> zoom = z },
    )
    val panelActions = RoutePanelActions(
        acceptPending = { navCtl.acceptPending(it) }, dismissPending = navCtl::dismissPending, retry = { navCtl.requestReplan() },
        clear = { navCtl.clearRoute(); panelOpen = false }, dismissArrived = navCtl::dismissArrived, goHome = runtime::goHome,
        dismissHomeSuggest = navCtl::dismissHomeSuggest,
    )
    MapTab(
        state = MapTabState(
            day = day, nav = nav, cameraMode = mode, mapOnly = mapOnly, weatherStrip = false, nowMs = System.currentTimeMillis(), arrivalFocus = focusOn,
            routeOpen = panelOpen && nav.hasPanelInfo(),
        ),
        lang = lang,
        mapSurface = {
            Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
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
            onRouteFab = { panelOpen = !panelOpen },
            onSearch = { searching = true },
            onShowPanels = { mapOnly = false },
        ),
        toast = { NavToastBanner(toast, dark) },
        routePanel = { if (panelOpen && nav.hasPanelInfo()) RoutePanel(nav, lang, dark, panelActions) },
        // Sol göstergeler A6.7'de (MapMetrics); vardiya kapalıyken ya da yarıda kalmışken başlat/bitir kartı üstte.
        metrics = {},
        topCard = { if (!active || interrupted) ShiftCard(lang) },
    )
    if (searching) {
        BackHandler { searching = false; runtime.search.clear() }
        SearchOverlay(
            searchView, lang,
            onQuery = runtime.search::query,
            onSubmit = { text ->
                searching = false
                runtime.search.clear()
                runtime.launch { navCtl.search(text) }
            },
            onPick = { item ->
                scope.launch {
                    val sel = runtime.search.select(item) ?: return@launch
                    val p = sel.point ?: return@launch
                    searching = false
                    navCtl.goTo(p, sel.title, restaurant = sel.restaurant, origin = NavOrigin.SEARCH)
                }
            },
            onClose = { searching = false; runtime.search.clear() },
        )
    }
    pressed?.let { p ->
        // A6.6: hedef satırları (Buraya git, durak ekle, restoran olarak seç); yol/POI/ışık/görev satırları sahip görevlerde (A6.8–A6.11).
        val rows = remember(lang) { LongPressMenu.rows(null, null, lang).filter { it.action in NAV_ROWS } }
        LongPressSheet(rows, onDismiss = { pressed = null }, onPick = { a ->
            pressed = null
            when (a) {
                LongPressAction.GO_TO -> navCtl.goTo(p)
                LongPressAction.ADD_STOP -> navCtl.addStop(p)
                LongPressAction.PICK_AS_RESTAURANT -> navCtl.goTo(p, restaurant = true)
                else -> Unit
            }
        })
    }
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
            confirmButton = { if (hasHome) Button(onClick = { askHome = false; runtime.goHome() }) { Text(MapText.HOME.get(lang)) } },
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

/** Hareket hâli (rota yokken dokunmadan sonra takibe dönüş). */
private const val MOVING_KMH = 3.0
/** Otomatik yakınlık her kamera adımında hedefe bu oranda yaklaşır (YolGo kare başı %4; WEEM 80/160 ms adımında). */
private const val ZOOM_EASE = 0.15
private const val FOCUS_EASE = 0.25
private val NAV_ROWS = setOf(LongPressAction.GO_TO, LongPressAction.ADD_STOP, LongPressAction.GO_TO_RESTAURANT, LongPressAction.PICK_AS_RESTAURANT)

/** Navigasyon titreşimi (YolGo `HapticFeedback`): seçim kısa, güçlü orta, uyarı uzun. */
private fun vibrate(ctx: android.content.Context, h: NavHaptic) {
    runCatching {
        val ms = when (h) {
            NavHaptic.SELECTION -> 15L
            NavHaptic.HEAVY -> 60L
            NavHaptic.VIBRATE -> 400L
        }
        ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
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
