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
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.WayFlag
import com.kuryelog.logic.map.ui.BikewayLayerRules
import com.kuryelog.logic.map.ui.BikewayMenu
import com.kuryelog.logic.map.ui.BikewayMenuAction
import com.kuryelog.logic.map.ui.PoiLayerRules
import com.kuryelog.logic.map.ui.PressedPlace
import com.kuryelog.logic.map.ui.PressedRoad
import com.kuryelog.logic.map.ui.PlaceText
import com.kuryelog.logic.map.ui.Toasts
import com.kuryelog.logic.map.ui.LongPressAction
import com.kuryelog.logic.map.ui.LongPressMenu
import com.kuryelog.logic.map.ui.MapPalette
import com.kuryelog.logic.map.ui.RouteLayer
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.graph.MapDataPhase
import com.kuryelog.logic.map.graph.MapDataState
import com.kuryelog.logic.map.ui.LocateAction
import com.kuryelog.logic.map.ui.LocateButtonRules
import com.kuryelog.logic.map.ui.MapCamera
import com.kuryelog.logic.map.ui.MapStyles
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kuryelog.logic.map.route.RideMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Harita sekmesi (MAP.md 6.2, 6.3, 6.9): graf yokken vardiya kartı + kurulum kartı (harita yüzeyi yok); graf hazırsa harita yüzeyi (altlık `mapStyle`, tema) + HUD.
 * Kamera takip ve konum düğmesi kuralları 6.5; ekran açık = vardiya açık ya da durak var. Navigasyon (A6.6; MAP.md 7.10, 7.11, 8.8): rota çizgileri ve rozetler, yeni rotada
 * sığdırma, yaklaşma zoom'u, varış odağı, dokunmadan sonra takibe dönüş, rota paneli, bildirim şeridi, arama, uzun basış hedef satırları, Eve dön. Gün motoru (A6.8; MAP.md 11):
 * gün kartı gerçek durumdan, toast ve titreşim, yer ve ışık işaretleri, bisiklet yolu katmanı, uzun basış ışık/kaldırım/bisiklet/işletme satırları. Sol göstergeler ve hava A6.7,
 * İstatistik A6.9'da bağlanır.
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
    LiveMap(lang, dark, data, settings, shift.active, shift.startedAtMs, shift.interrupted)
}

private object MapScreenLoad {
    @Volatile var loadedRegion: String? = null
}

@Composable
private fun LiveMap(lang: Lang, dark: Boolean, data: MapDataState, settings: com.kuryelog.logic.map.settings.MapSettings, active: Boolean, startedAtMs: Long?, interrupted: Boolean) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val runtime = remember { NavRuntime.get(ctx) }
    val navCtl = runtime.controller
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val nav by navCtl.state.collectAsState()
    val progress by navCtl.progress.collectAsState()
    val toast by navCtl.toast.collectAsState()
    val searchView by runtime.search.view.collectAsState()
    // Hava (Y10) ve batarya (Y9): çalışma zamanları uygulama açılışında kurulur; harita görünürlüğü `NavRuntime.setVisible` ile hava yenilemesine de iletilir.
    val weather = remember { WeatherRuntime.get(ctx) }
    val batteryRt = remember { BatteryRuntime.get(ctx) }
    val weatherState by weather.state.collectAsState()
    val weatherStrip by weather.strip.collectAsState()
    val battery by batteryRt.state.collectAsState()
    val scooter by batteryRt.live.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    // Bildirim izni (Android 13+): gün açık ve hava bildirimi açıkken bir kez sorulur.
    val needsPermission by weather.needsPermission.collectAsState()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { weather.permissionAsked() }
    LaunchedEffect(needsPermission) {
        if (needsPermission) {
            weather.permissionAsked()
            askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val speeds = remember(nav.plan, settings, battery.percent) { speedOptionsFor(nav.plan, settings.paramsFor(battery.percent), settings.speedChoices) }
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
    val base = MapStyles.resolve(settings.mapStyle, BuildConfig.MAPS_ENABLED)
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
    var statsOpen by remember { mutableStateOf(false) }
    var taskAt by remember { mutableStateOf<GeoPoint?>(null) }
    val routeSpeed by RideLogRuntime.get(ctx).routeSpeed.collectAsState()
    var panelOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf<GeoPoint?>(null) }
    val dayRt = runtime.day
    val dayView by dayRt.view.collectAsState()
    var banner by remember { mutableStateOf<DayBanner?>(null) }
    var bannerSeq by remember { mutableLongStateOf(0L) }
    var bikeRev by remember { mutableIntStateOf(0) }
    var lightsRev by remember { mutableIntStateOf(0) }
    var poiRev by remember { mutableIntStateOf(0) }
    var center by remember { mutableStateOf<GeoPoint?>(null) }
    var poiEdit by remember { mutableStateOf<PoiEditTarget?>(null) }
    // Gün motoru: toast ve titreşim yalnız harita görünürken (kaçan toast sonradan gösterilmez; MAP.md 11.14).
    LaunchedEffect(dayRt) {
        dayRt.steps.collect { step ->
            step.toasts.lastOrNull()?.let { t -> banner = DayBanner(++bannerSeq, t.text, t.tone.toTone(), 5) }
            if (step.haptic) vibrate(ctx, NavHaptic.HEAVY)
        }
    }
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
    val day = dayView
    val lines = remember(nav.plans, nav.mode, dark) { RouteLayer.strokes(nav, MapPalette.of(dark)).map { it.toMapLine() } }
    val routeMarkers = remember(nav.plans, nav.mode, nav.reached) { RouteLayer.pins(nav).map { it.toMarker() } }
    // Bisiklet yolu katmanı (MAP.md 5.1): OSM düz, elle işaretli kesikli; rota çizgilerinin altında.
    val graph = data.graph
    val bikeLines = remember(graph, bikeRev, settings.showBikewayLayer, dark) {
        if (graph == null) emptyList()
        else BikewayLayerRules.lines(graph, runtime.bikeway.marks, settings.showBikewayLayer, dark).map { l ->
            MapLine("bike-${l.edge}", graph.geometry(l.edge).map { GeoPoint(it.lat, it.lon) }, (0xD9 shl 24) or l.rgb, l.width.toFloat(), if (l.dashed) LinePattern.DASH else LinePattern.SOLID, -1)
        }
    }
    // Yer ve ışık işaretleri (MAP.md 5.3): düzeltilmiş yerler + ışık işaretleri, yakınlığa ve görünür kutuya göre süzülür.
    val poiMarkers = remember(center, zoom, viewport, poiRev, lightsRev, data.pois) {
        val c = center ?: return@remember emptyList<MapMarker>()
        if (viewport == IntSize.Zero) return@remember emptyList<MapMarker>()
        val wDp = with(density) { viewport.width.toDp().value.toDouble() }
        val hDp = with(density) { viewport.height.toDp().value.toDouble() }
        val box = PoiLayerRules.bounds(c.lat, c.lon, wDp, hDp, zoom)
        val all = PoiLayerRules.withLights(MapPois.all(ctx), dayRt.lightSpots())
        PoiLayerRules.select(all, box, zoom).mapIndexed { i, p -> p.toMarker(i) }
    }
    val markers = routeMarkers + poiMarkers
    val model = MapSurfaceModel(
        base = base, dark = dark, camera = camera, lines = bikeLines + lines, markers = markers,
        puck = puck?.let { MapPuck(it.point, it.course, it.beam, arrow = nav.hasRoute, accuracyM = it.accuracyM) },
    )
    val events = MapSurfaceEvents(
        onTap = { mapOnly = !mapOnly },
        onLongPress = { pressed = it },
        onGesture = { follow = false; autoZoom = false; freeByGesture = true; lastGestureMs = System.currentTimeMillis() },
        onCameraIdle = { c, z, _ -> zoom = z; center = c },
    )
    val panelActions = RoutePanelActions(
        acceptPending = { navCtl.acceptPending(it) }, dismissPending = navCtl::dismissPending, retry = { navCtl.requestReplan() },
        clear = { navCtl.clearRoute(); panelOpen = false }, dismissArrived = navCtl::dismissArrived, goHome = runtime::goHome,
        dismissHomeSuggest = navCtl::dismissHomeSuggest,
    )
    MapTab(
        state = MapTabState(
            day = day, nav = nav, cameraMode = mode, mapOnly = mapOnly, weatherStrip = weatherStrip, nowMs = nowMs, arrivalFocus = focusOn,
            weather = WeatherView(weatherState.report, weatherState.failed, weatherState.busy),
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
        onStats = { statsOpen = true },
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
            onWeatherRefresh = { weather.refresh(force = true) },
        ),
        toast = { NavToastBanner(toast, dark); DayToastBanner(banner, dark) },
        routePanel = { if (panelOpen && nav.hasPanelInfo()) RoutePanel(nav, lang, dark, panelActions) },
        // Sol göstergeler (A6.7); vardiya kapalıyken ya da yarıda kalmışken başlat/bitir kartı üstte.
        metrics = {
            MapMetrics(
                MapMetricsState(
                    plan = nav.plan, mode = nav.mode, measure = progress, battery = battery, scooter = scooter, speeds = speeds, avgKmh = routeSpeed.first, nowMs = nowMs,
                    speedChoices = settings.speedChoices, lowBatteryPct = settings.lowBatteryPct,
                ),
                lang,
                onMode = { m: RideMode -> navCtl.setMode(m) },
                onSpeedChoices = { list -> ctx.safely { MapSettingsRepo.update(ctx) { it.copy(speedChoices = list) } } },
            )
        },
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
        // Uzun basış (MAP.md 6.7): hedef satırları A6.6; ışık, kaldırım, bisiklet yolu ve işletme satırları A6.8; öteki yol satırları A6.9–A6.11.
        val g = data.graph
        val hit = remember(p, g, bikeRev) { g?.nearestEdge(p.lat, p.lon, LongPressMenu.ROAD_RADIUS_M) }
        val place = remember(p, poiRev) { MapPois.nearestBusiness(ctx, p, LongPressMenu.PLACE_RADIUS_M) }
        val road = remember(hit, bikeRev) {
            if (g == null || hit == null) null else {
                val flags = g.flagsOf(hit.edge)
                PressedRoad(
                    g.nameOf(hit.edge), g.classOf(hit.edge).isMainRoad, (flags and (WayFlag.ONEWAY_FWD or WayFlag.ONEWAY_BWD)) != 0, false, false,
                    BikewayMenu.action(g, hit.edge, runtime.bikeway.marks),
                )
            }
        }
        val rows = remember(lang, road, place) { LongPressMenu.rows(road, place?.let { PressedPlace(it.kind, it.name) }, lang).filter { it.action in DAY_ROWS } }
        LongPressSheet(rows, onDismiss = { pressed = null }, onPick = { a ->
            pressed = null
            when (a) {
                LongPressAction.GO_TO -> navCtl.goTo(p)
                LongPressAction.ADD_STOP -> navCtl.addStop(p)
                LongPressAction.PICK_AS_RESTAURANT -> navCtl.goTo(p, restaurant = true)
                LongPressAction.GO_TO_RESTAURANT -> place?.let { navCtl.goTo(GeoPoint(it.lat, it.lon), it.name, restaurant = true) }
                LongPressAction.CURB_WAIT -> dayRt.markSidewalk(p, remove = false)
                LongPressAction.CURB_REMOVE -> dayRt.markSidewalk(p, remove = true)
                LongPressAction.LIGHT_HERE -> { dayRt.markLight(p.lat, p.lon, true); lightsRev++ }
                LongPressAction.NO_LIGHT -> {
                    // "Işık yok": 25 m içindeki ışığın yeri, yoksa basılan nokta.
                    val spot = dayRt.lightSpots().filter { Geo.haversine(p.lat, p.lon, it.lat, it.lon) <= LongPressMenu.LIGHT_RADIUS_M }
                        .minByOrNull { Geo.haversine(p.lat, p.lon, it.lat, it.lon) }?.let { GeoPoint(it.lat, it.lon) }
                        ?: MapPois.base(ctx)?.nearest('l', p.lat, p.lon, LongPressMenu.LIGHT_RADIUS_M)?.let { GeoPoint(it.lat, it.lon) }
                        ?: p
                    dayRt.markLight(spot.lat, spot.lon, false); lightsRev++
                }
                LongPressAction.BIKEWAY -> if (g != null && hit != null) {
                    val toasts = Toasts()
                    BikewayMenu.toggleWithUndo(runtime.bikeway, toasts, g, hit.edge, g.nameOf(hit.edge), lang)
                    runtime.reloadBikeway(); bikeRev++
                    toasts.current?.let { t ->
                        val undo = t.onAction
                        banner = DayBanner(++bannerSeq, t.text, t.tone, t.seconds, t.actionLabel, undo?.let { u -> { u(); runtime.reloadBikeway(); bikeRev++ } })
                    }
                }
                LongPressAction.POI_EDIT -> poiEdit = PoiEditTarget(p, place)
                LongPressAction.ADD_TASK -> taskAt = p
                else -> Unit
            }
        })
    }
    poiEdit?.let { t ->
        PoiEditDialog(
            initial = t.place, lang = lang, onDismiss = { poiEdit = null },
            onSave = { name, kind ->
                val orig = t.place
                MapPois.save(ctx, if (orig == null) Poi(t.point.lat, t.point.lon, kind, name) else Poi(orig.lat, orig.lon, kind, name, orig.osmRef), orig)
                poiRev++
                poiEdit = null
            },
        )
    }
    if (statsOpen) {
        // İstatistik (A6.9): tam ekran; elle günü bitirince o güne odaklı açılır.
        StatsHost(lang, focusStartMs = dayRt.activeDay()?.startedAtMs ?: dayRt.lastDay()?.startedAtMs, onBack = { statsOpen = false })
    }
    taskAt?.let { p ->
        // Uzun basış satırı 4 (MAP.md 6.7): düzeltilmiş nokta ile günlük iş düzenleyicisi.
        val rt = remember { TaskRuntime.get(ctx) }
        val label = String.format(java.util.Locale.US, "%.5f, %.5f", p.lat, p.lon)
        TaskEditorSheet(
            lang, java.time.ZoneId.systemDefault(), System.currentTimeMillis(),
            TaskActions(onAdd = rt::add, onDelete = rt::delete, resolve = { text -> runtime.resolvePlace(text)?.let { FoundPlace(it.label, it.point) } }),
            FoundPlace(label, p), onDismiss = { taskAt = null },
        )
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
private val DAY_ROWS = setOf(
    LongPressAction.GO_TO, LongPressAction.ADD_STOP, LongPressAction.GO_TO_RESTAURANT, LongPressAction.PICK_AS_RESTAURANT, LongPressAction.CURB_WAIT,
    LongPressAction.CURB_REMOVE, LongPressAction.BIKEWAY, LongPressAction.LIGHT_HERE, LongPressAction.NO_LIGHT, LongPressAction.POI_EDIT,
    LongPressAction.ADD_TASK,
)

/** Yer düzeltme penceresinin hedefi: basılan nokta ve (varsa) düzeltilen yer. */
private class PoiEditTarget(val point: GeoPoint, val place: Poi?)

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
            var info by remember { mutableStateOf(false) }
            TextButton(onClick = { info = true }) { Text("ⓘ ${PlaceText.REGION_WHAT.get(lang)}") }
            if (info) RegionInfoSheet(lang) { info = false }
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
