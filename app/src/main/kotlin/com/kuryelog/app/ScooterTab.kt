package com.kuryelog.app

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kuryelog.app.scooter.BleScan
import com.kuryelog.app.scooter.DashboardView
import com.kuryelog.app.scooter.FloatingPanel
import com.kuryelog.app.scooter.LoggerPreferences
import com.kuryelog.app.scooter.PairAttention
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.FloatRules
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.ScooterPanel
import com.kuryelog.logic.scooter.ScooterUiText as T
import kotlinx.coroutines.delay

/**
 * Yüzen panelden gelen açma isteği ve PiP durumu (A5.4): [MainActivity] yazar, [WeemApp] ve Scooter sekmesi okur. Ana iş parçacığında.
 */
object ScooterNav {
    /** [Scooter.OPEN_PANEL] ya da [Scooter.OPEN_SETTINGS]; sekme açınca tüketir. */
    var open by mutableStateOf<String?>(null)

    /** Etkinlik resim içinde resimde: yalnız küçük ızgara çizilir. */
    var pip by mutableStateOf(false)

    /** Scooter sekmesi görünür ve PiP yedek olarak isteniyor (Android 12 öncesi `onUserLeaveHint` okur). */
    var pipWanted = false

    fun pipParams(auto: Boolean): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 10))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(auto).setSeamlessResizeEnabled(false)
        return b.build()
    }
}

private sealed interface Ask {
    data class Message(val text: String) : Ask
    data object RecordSwitch : Ask
    data object HowStart : Ask
    data object Pair : Ask
    data object ExportChoices : Ask
    data class ExportWarn(val choice: ScooterPanel.ExportChoice) : Ask
    data object Scan : Ask
}

/**
 * Scooter sekmesi (S2, A5.4; NB `DashboardActivity`): başlık ve durum satırı, eylem çubuğu (ayarlar, eşleştir, ara, başlat, durdur, ZIP),
 * eşleştirme uyarısı ve canlı gösterge. Göstergeye dokunma tam ekran (başlık, çubuk ve alt menü kayar; geri tuşu çıkar). Görünürken
 * `Scooter.Viewers.dashboard` (hızlı okuma temposu). Ayarlar ve tanı [ScooterSettingsScreen]'de.
 */
@Composable
fun ScooterTab(lang: Lang, dark: Boolean, immersive: Boolean, onImmersive: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var settings by rememberSaveable { mutableStateOf(false) }
    val open = ScooterNav.open
    LaunchedEffect(open) {
        if (open != null) { settings = open == Scooter.OPEN_SETTINGS; ScooterNav.open = null }
    }
    if (settings) {
        LaunchedEffect(Unit) { onImmersive(false) }
        ScooterSettingsScreen(lang) { settings = false }
        return
    }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(ScooterPanel.HEARTBEAT_MS); tick++ } }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var scanVersion by remember { mutableIntStateOf(0) }
    val scan = remember {
        BleScan(ctx, object : BleScan.Listener {
            override fun changed() { scanVersion++ }
            override fun finished(error: Int?) {
                scanVersion++
                if (error != null) ask = Ask.Message(T.SCAN_ERROR.get(lang).format(error.toString()))
            }
        })
    }
    var dashboard by remember { mutableStateOf<DashboardView?>(null) }
    var autoTried by rememberSaveable { mutableStateOf(false) }

    // Görünürlük: STARTED iken hızlı tempo (NB onStart/onStop; PiP penceresi duraklatılmış ama görünür).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val listener: (NinebotDashboard.Snapshot?) -> Unit = { f -> dashboard?.show(f) }
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> { Scooter.Viewers.dashboard = true; Scooter.frameListeners.add(listener); dashboard?.show(Scooter.frame) }
                Lifecycle.Event.ON_STOP -> { Scooter.Viewers.dashboard = false; Scooter.frameListeners.remove(listener) }
                Lifecycle.Event.ON_PAUSE -> { scan.stop(); if (ask == Ask.Scan) ask = null }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            Scooter.Viewers.dashboard = false
            Scooter.frameListeners.remove(listener)
            scan.stop()
        }
    }

    val state = remember(tick, scanVersion) { Scooter.state(ctx) }
    val dock = ScooterPanel.dock(state, Scooter.exporting, scan.scanning)

    // Ekran açık ve PiP yedeği (NB `syncToolbar`), değiştikçe.
    val view = LocalView.current
    val screenOn = ScooterPanel.keepScreenOn(state.active, LoggerPreferences.get(ctx).getBoolean("keep_screen_on", true))
    DisposableEffect(view, screenOn) {
        view.keepScreenOn = screenOn
        onDispose { view.keepScreenOn = false }
    }
    val pipSupported = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    val wantPip = state.active && FloatRules.pip(FloatingPanel.enabled(ctx), FloatingPanel.permitted(ctx), pipSupported)
    DisposableEffect(wantPip) {
        ScooterNav.pipWanted = wantPip
        if (Build.VERSION.SDK_INT >= 31) runCatching { activity?.setPictureInPictureParams(ScooterNav.pipParams(wantPip)) }
        onDispose {
            ScooterNav.pipWanted = false
            if (Build.VERSION.SDK_INT >= 31) runCatching { activity?.setPictureInPictureParams(ScooterNav.pipParams(false)) }
        }
    }

    // Tam ekran: sistem çubukları gizlenir, geri tuşu önce tam ekrandan çıkar.
    DisposableEffect(immersive) {
        val window = activity?.window
        if (window != null) {
            val bars = WindowCompat.getInsetsController(window, view)
            if (immersive) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            } else bars.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (window != null) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler(enabled = immersive) { onImmersive(false) }
    DisposableEffect(Unit) { onDispose { onImmersive(false) } }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ask = Ask.Message(T.PERMISSIONS_AGAIN.get(lang))
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun permissions(): Boolean {
        val missing = BleScan.missingPermissions(ctx)
        if (missing.isNotEmpty()) { askPermissions.launch(missing); return false }
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        return true
    }
    fun bump() { tick++ }

    LaunchedEffect(Unit) {
        if (!autoTried) { autoTried = true; ctx.safely("scooter_auto_live") { Scooter.autoLive(ctx) } }
    }

    val p = LocalWeemPalette.current
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !immersive, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
                    Text(T.TITLE.get(lang), fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.semantics { heading() })
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dot = when (ScooterPanel.dot(state)) {
                            ScooterPanel.Dot.WARN -> Color(p.warn)
                            ScooterPanel.Dot.RECORDING -> Color(p.danger)
                            ScooterPanel.Dot.LINKED -> Color(p.positive)
                            ScooterPanel.Dot.ACTIVE -> Color(p.accent)
                            ScooterPanel.Dot.IDLE -> Color(p.muted)
                        }
                        Box(Modifier.size(8.dp).background(dot, CircleShape))
                        Text(ScooterPanel.status(state, lang), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DockButton("⚙", T.DOCK_SETTINGS.get(lang), true) { settings = true }
                        DockButton("🔗", T.DOCK_PAIR.get(lang), dock.pair) {
                            ctx.safely("scooter_pair") {
                                if (Scooter.active || Scooter.exporting || Scooter.maintenance) return@safely
                                if (!permissions()) return@safely
                                ask = if (Scooter.device(ctx).isEmpty()) Ask.Message(T.PICK_FIRST.get(lang)) else Ask.Pair
                            }
                        }
                        DockButton("🔍", T.DOCK_SCAN.get(lang), dock.scan) {
                            ctx.safely("scooter_scan") {
                                if (Scooter.active || scan.scanning || !permissions()) return@safely
                                when (scan.start()) {
                                    BleScan.Start.STARTED -> ask = Ask.Scan
                                    BleScan.Start.BLUETOOTH_OFF -> ask = Ask.Message(T.BLUETOOTH_OFF.get(lang))
                                    BleScan.Start.NO_SCANNER -> ask = Ask.Message(T.SCANNER_NOT_READY.get(lang))
                                    BleScan.Start.FAILED -> ask = Ask.Message(T.SCAN_FAILED_START.get(lang).format(scan.failure))
                                }
                                scanVersion++
                            }
                        }
                        DockButton("▶", T.DOCK_START.get(lang), dock.start, accent = true) {
                            ctx.safely("scooter_start") {
                                if (Scooter.active) { if (!Scooter.recording) ask = Ask.RecordSwitch; return@safely }
                                if (Scooter.device(ctx).isEmpty()) { if (permissions()) Scooter.startRecording(ctx, false); bump(); return@safely }
                                ask = Ask.HowStart
                            }
                        }
                        DockButton("■", T.DOCK_STOP.get(lang), dock.stop, danger = true) { ctx.safely("scooter_stop") { Scooter.stop(ctx); bump() } }
                        DockButton("⤓", T.DOCK_EXPORT.get(lang), dock.export) {
                            ask = if (Scooter.recording || Scooter.maintenance) Ask.Message(T.EXPORT_BUSY.get(lang)) else Ask.ExportChoices
                        }
                    }
                }
            }
        }
        if (PairAttention.visible()) {
            Text(
                T.PAIR_ATTENTION.get(lang), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(p.warn),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(Color(p.warnBg), RoundedCornerShape(24.dp)).padding(16.dp),
            )
        }
        val fields = remember(tick) { FloatingPanel.fields(ctx) }
        AndroidView(
            factory = { c -> DashboardView(c).also { v -> dashboard = v; v.show(Scooter.frame) } },
            update = { v ->
                v.style(dark, lang)
                v.fields(fields)
                v.immersive(immersive)
                v.trip(if (state.active) Scooter.trip.stats() else null)
                v.refreshStale()
                v.setOnClickListener { onImmersive(!immersive) }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }

    when (val a = ask) {
        null -> {}
        is Ask.Message -> AlertDialog(
            onDismissRequest = { ask = null }, text = { Text(a.text) },
            confirmButton = { TextButton(onClick = { ask = null }) { Text(T.OK.get(lang)) } },
        )
        Ask.RecordSwitch -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.RECORD_TITLE.get(lang)) }, text = { Text(T.RECORD_TEXT.get(lang)) },
            confirmButton = { TextButton(onClick = { ask = null; ctx.safely("scooter_record") { Scooter.record(ctx); bump() } }) { Text(T.DOCK_START.get(lang)) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(T.CANCEL.get(lang)) } },
        )
        Ask.HowStart -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.HOW_START.get(lang)) },
            text = {
                Column {
                    ChoiceRow(T.START_LIVE.get(lang)) { ask = null; ctx.safely("scooter_start") { if (permissions()) Scooter.startLive(ctx); bump() } }
                    ChoiceRow(T.START_RECORD.get(lang)) { ask = null; ctx.safely("scooter_start") { if (permissions()) Scooter.startRecording(ctx, false); bump() } }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { ask = null }) { Text(T.CANCEL.get(lang)) } },
        )
        Ask.Pair -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.DOCK_PAIR.get(lang)) }, text = { Text(T.PAIR_TEXT.get(lang)) },
            confirmButton = { TextButton(onClick = { ask = null; ctx.safely("scooter_pair") { scan.stop(); Scooter.startRecording(ctx, true); bump() } }) { Text(T.PAIR_GO.get(lang)) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(T.CANCEL.get(lang)) } },
        )
        Ask.ExportChoices -> AlertDialog(
            onDismissRequest = { ask = null }, title = { Text(T.EXPORT_TITLE.get(lang)) },
            text = {
                Column {
                    for (c in ScooterPanel.ExportChoice.entries) ChoiceRow(c.text.get(lang)) {
                        if (c.warning() != null) ask = Ask.ExportWarn(c) else export(ctx, c, lang) { ask = it }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { ask = null }) { Text(T.CANCEL.get(lang)) } },
        )
        is Ask.ExportWarn -> {
            val (title, text, go) = a.choice.warning()!!
            AlertDialog(
                onDismissRequest = { ask = null }, title = title?.let { { Text(it.get(lang)) } }, text = { Text(text.get(lang)) },
                confirmButton = { TextButton(onClick = { ask = null; export(ctx, a.choice, lang) { ask = it } }) { Text(go.get(lang)) } },
                dismissButton = { TextButton(onClick = { ask = null }) { Text(T.CANCEL.get(lang)) } },
            )
        }
        Ask.Scan -> ScanDialog(scan, scanVersion, lang, onDismiss = { scan.stop(); ask = null; scanVersion++ }) { found ->
            scan.stop()
            ctx.safely("scooter_pick") { Scooter.select(ctx, found) }
            ask = Ask.Message(T.PICKED.get(lang).format(ScooterPanel.ScanList.shortName(found)))
        }
    }
    // Süre doldu, hiçbir cihaz yok: tarama penceresi mesaja döner (NB `scanDone`).
    if (ask == Ask.Scan && !scan.scanning && scan.list.isEmpty()) LaunchedEffect(scanVersion) { ask = Ask.Message(T.SCAN_EMPTY.get(lang)) }
}

private fun export(ctx: android.content.Context, choice: ScooterPanel.ExportChoice, lang: Lang, show: (Ask?) -> Unit) {
    show(Ask.Message(T.EXPORT_PREPARING.get(lang)))
    ctx.safely("scooter_export") { Scooter.export(ctx, choice, lang) { msg -> show(Ask.Message(msg)) } }
}

@Composable
private fun ScanDialog(
    scan: BleScan,
    version: Int,
    lang: Lang,
    onDismiss: () -> Unit,
    onPick: (ScooterPanel.ScanList.Found) -> Unit,
) {
    val ctx = LocalContext.current
    val saved = remember(version) { Scooter.device(ctx) }
    val rows = remember(version) { scan.list.ordered(saved) }
    val p = LocalWeemPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rows.isEmpty() && scan.scanning) T.SCAN_TITLE.get(lang) else (if (scan.scanning) T.SCAN_RUNNING else T.SCAN_DONE).get(lang).format(rows.size.toString())) },
        text = {
            Column {
                Text(T.SCAN_HINT.get(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (scan.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp)) else Box(Modifier.height(14.dp))
                LazyColumn(Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp)) {
                    items(rows, key = { it.address }) { f ->
                        val isSaved = f.address == saved
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 10.dp)
                                .semantics { contentDescription = ScooterPanel.ScanList.label(f, lang) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (isSaved) "🛵" else "ᛒ", color = Color(if (isSaved) p.positive else if (f.named) p.accent else p.muted), modifier = Modifier.padding(end = 12.dp))
                            Column {
                                Text(if (f.named) f.name!! else T.UNNAMED.get(lang), style = MaterialTheme.typography.bodyLarge)
                                Text(ScooterPanel.ScanList.subtitle(f, isSaved, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.CLOSE.get(lang)) } },
    )
}

@Composable
private fun ChoiceRow(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp))
}

/** Eylem çubuğu düğmesi: en az 48 × 62 dp; başlat dolgun vurgu, durdur uyarı rengi (NB `icon`). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DockButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalWeemPalette.current
    val colors = if (accent) IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    else IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = if (danger) Color(p.danger) else MaterialTheme.colorScheme.onSurface)
    FilledTonalIconButton(
        onClick = onClick, enabled = enabled, colors = colors,
        modifier = Modifier.weight(1f).height(62.dp).padding(horizontal = 2.dp).semantics { contentDescription = description },
    ) { Text(glyph, fontSize = 20.sp) }
}

/** PiP penceresi (NB `compact`): yüzen panelin değerleri 2 × 2 ızgarada; dokunma yok. */
@Composable
fun ScooterPip(lang: Lang, dark: Boolean) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(ScooterPanel.HEARTBEAT_MS); tick++ } }
    var dashboard by remember { mutableStateOf<DashboardView?>(null) }
    DisposableEffect(Unit) {
        // PiP penceresi duraklatılmış ama görünür: hızlı tempo sürer (NB onStart–onStop).
        val listener: (NinebotDashboard.Snapshot?) -> Unit = { f -> dashboard?.show(f) }
        Scooter.frameListeners.add(listener)
        Scooter.Viewers.dashboard = true
        onDispose { Scooter.frameListeners.remove(listener); Scooter.Viewers.dashboard = false }
    }
    AndroidView(
        factory = { c -> DashboardView(c).also { v -> dashboard = v; v.compact(true); v.isClickable = false; v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } },
        update = { v ->
            v.style(dark, lang)
            v.fields(FloatingPanel.fields(ctx))
            v.show(Scooter.frame)
            v.trip(if (Scooter.active && tick >= 0) Scooter.trip.stats() else null)
            v.refreshStale()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
