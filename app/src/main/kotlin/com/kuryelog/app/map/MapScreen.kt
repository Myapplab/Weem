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
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.graph.MapDataPhase
import com.kuryelog.logic.map.graph.MapDataState
import com.kuryelog.logic.map.ui.LocateAction
import com.kuryelog.logic.map.ui.LocateButtonRules
import com.kuryelog.logic.map.ui.MapCamera
import com.kuryelog.logic.map.ui.MapStyles
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
    LiveMap(lang, dark, data, settings.mapStyle, shift.active, shift.startedAtMs, shift.interrupted)
}

private object MapScreenLoad {
    @Volatile var loadedRegion: String? = null
}

@Composable
private fun LiveMap(lang: Lang, dark: Boolean, data: MapDataState, mapStyle: String, active: Boolean, startedAtMs: Long?, interrupted: Boolean) {
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
    val day = remember(active, startedAtMs) {
        DayView(DayState().apply { this.active = active; this.startedAtMs = startedAtMs }, null)
    }
    val model = MapSurfaceModel(
        base = base, dark = dark, camera = camera,
        puck = puck?.let { MapPuck(it.point, it.course, it.beam, arrow = false, accuracyM = it.accuracyM) },
    )
    val events = MapSurfaceEvents(
        onTap = { mapOnly = !mapOnly },
        onGesture = { follow = false },
        onCameraIdle = { _, z, _ -> if (abs(z - zoom) > 0.01) { zoom = z; if (abs(z - MapCamera.STANDARD_ZOOM) > 0.05) autoZoom = false } },
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
        // Sol göstergeler A6.7'de (MapMetrics); vardiya kapalıyken ya da yarıda kalmışken başlat/bitir kartı üstte.
        metrics = {},
        topCard = { if (!active || interrupted) ShiftCard(lang) },
    )
    if (askHome) {
        val hasHome = remember { Home.point(ctx) != null }
        AlertDialog(
            onDismissRequest = { askHome = false },
            title = { Text(tl(lang, "Eve dön", "Evə qayıt")) },
            text = {
                Text(
                    if (hasHome) tl(lang, "Evine rota çizilsin mi? Aktif gün devam eder.", "Evə marşrut çəkilsin? Aktiv gün davam edir.")
                    else tl(lang, "Ev konumu ayarlanmamış — Daha fazla → Ev konumu", "Ev məkanı təyin edilməyib — Daha çox → Ev məkanı"),
                )
            },
            // Eve rota (goHome) navigasyonla gelir (A6.6); onay şimdilik yalnız pencereyi kapatır.
            confirmButton = { if (hasHome) Button(onClick = { askHome = false }) { Text(tl(lang, "Eve dön", "Evə qayıt")) } },
            dismissButton = { TextButton(onClick = { askHome = false }) { Text(tl(lang, "Vazgeç", "Ləğv et")) } },
        )
    }
    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text(tl(lang, "Günü bitir?", "Günü tamamlamaq istəyirsən?")) },
            text = { Text(tl(lang, "Kayıt durur ve günün istatistiği açılır.", "Günün qeydi tamamlanır və statistika açılır.")) },
            confirmButton = { Button(onClick = { askEnd = false; ctx.safely { ShiftHub.stop() } }) { Text(tl(lang, "Bitir", "Tamamla")) } },
            dismissButton = { TextButton(onClick = { askEnd = false }) { Text(tl(lang, "Vazgeç", "Ləğv et")) } },
        )
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
                Text(tl(lang, "Bakü haritası hazırlanıyor…", "Bakı xəritəsi hazırlanır…"), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                return@Column
            }
            Text(tl(lang, "Bakü yol haritası", "Bakı yol xəritəsi"), style = MaterialTheme.typography.titleMedium)
            Text(
                tl(lang, "Rota, yokuş ve batarya tahmini için Bakü yolları bir kez indirilir.", "Marşrut, yoxuş və batareya hesabı üçün Bakı yolları bir dəfə yüklənir."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val building = state.phase == MapDataPhase.BUILDING
            for ((key, label) in listOf("central" to tl(lang, "Merkez Bakü", "Mərkəzi Bakı"), "wide" to tl(lang, "Geniş Bakü", "Böyük Bakı"))) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !building) { onRegion(key) }) {
                    RadioButton(selected = region == key, onClick = { onRegion(key) }, enabled = !building)
                    Text(label)
                }
            }
            if (building) {
                LinearProgressIndicator(progress = { state.progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("%${(state.progress * 100).roundToInt()} · ${state.stage}", style = MaterialTheme.typography.bodySmall)
                Text(tl(lang, "Ekranı açık tut. Uygulamayı kapatma.", "Ekranı açıq saxla. Tətbiqi bağlama."), style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = onDownload, modifier = Modifier.padding(top = 8.dp)) { Text(tl(lang, "İndir ve başla", "Yüklə və başla")) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Text(
                tl(lang, "Harita © OpenStreetMap katkıcıları · Yükseklik: SRTM", "Xəritə © OpenStreetMap iştirakçıları · Hündürlük: SRTM"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
