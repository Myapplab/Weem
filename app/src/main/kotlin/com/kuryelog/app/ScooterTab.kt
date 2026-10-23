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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.Dot
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.NoticeDialog
import com.kuryelog.app.ui.ScreenTitle
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSize
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.app.ui.container
import com.kuryelog.app.ui.ink
import com.kuryelog.app.ui.mark
import com.kuryelog.app.scooter.BleScan
import com.kuryelog.app.scooter.DashboardView
import com.kuryelog.app.scooter.FloatingPanel
import com.kuryelog.app.scooter.LoggerPreferences
import com.kuryelog.app.scooter.PairAttention
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.app.scooter.ScooterShiftLink
import com.kuryelog.app.core.Store
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
    data object Pair : Ask
    data object ExportChoices : Ask
    data class ExportWarn(val choice: ScooterPanel.ExportChoice) : Ask
    data object Scan : Ask
}

/**
 * Scooter sekmesi (S2, A5.4; NB `DashboardActivity`): başlık ve durum satırı, eylem çubuğu (ayarlar, eşleştir, ara, ZIP; S37: "Kaydı başlat"
 * yerine kayıt durumu — kayıt vardiyayla başlar ve biter — ve yalnız vardiya dışı bağlantıyı kesen durdur),
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
    val shiftOn = remember(tick) { Store.shiftOn(ctx) }
    val dock = ScooterPanel.dock(state, Scooter.exporting, scan.scanning, shiftOn)
    val badge = remember(tick, scanVersion) {
        ScooterPanel.badge(state, Scooter.selected(ctx), Scooter.paired(ctx), shiftOn, ScooterShiftLink.providerRole())
    }

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

    val p = palette()
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !immersive, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            // A10.4d (`docs/TASARIM.md` 12): sekme başlığı + renkli nokta ve durum satırı.
            Column(Modifier.padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.s, bottom = WeemSpace.xs)) {
                ScreenTitle(T.TITLE.get(lang))
                Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalAlignment = Alignment.Top) {
                    val dot = when (ScooterPanel.dot(state)) {
                        ScooterPanel.Dot.WARN -> p.warn
                        ScooterPanel.Dot.RECORDING -> p.danger
                        ScooterPanel.Dot.LINKED -> p.positive
                        ScooterPanel.Dot.ACTIVE -> p.accent
                        ScooterPanel.Dot.IDLE -> p.muted
                    }
                    Dot(dot.color(), Modifier.padding(top = 5.dp))
                    Text(ScooterPanel.status(state, lang), style = WeemType.bodyS, color = p.muted.color())
                }
            }
        }
        if (PairAttention.visible()) PairAttentionBanner(T.PAIR_ATTENTION.get(lang))
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
        // K4 #7: eylem çubuğu başparmak bölgesinde — dört simge düğmesi, altında kayıt durumu (S37) ve yuvarlak durdur.
        AnimatedVisibility(visible = !immersive, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.xs, bottom = WeemSpace.m),
                verticalArrangement = Arrangement.spacedBy(WeemSpace.s),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                    ToolButton(WeemIcons.Settings, T.DOCK_SETTINGS.get(lang), true) { settings = true }
                    ToolButton(WeemIcons.Link, T.DOCK_PAIR.get(lang), dock.pair) {
                        ctx.safely("scooter_pair") {
                            if (Scooter.active || Scooter.exporting || Scooter.maintenance) return@safely
                            if (!permissions()) return@safely
                            ask = if (Scooter.device(ctx).isEmpty()) Ask.Message(T.PICK_FIRST.get(lang)) else Ask.Pair
                        }
                    }
                    ToolButton(WeemIcons.BluetoothSearching, T.DOCK_SCAN.get(lang), dock.scan) {
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
                    ToolButton(WeemIcons.FolderZip, T.DOCK_EXPORT.get(lang), dock.export) {
                        ask = if (Scooter.recording || Scooter.maintenance) Ask.Message(T.EXPORT_BUSY.get(lang)) else Ask.ExportChoices
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalAlignment = Alignment.CenterVertically) {
                    // S37: vardiya açık, kayıt yok → dokununca vardiya kaydına bağlanır (ana karttaki "Scooter'a şimdi bağlan" ile aynı).
                    val connect: (() -> Unit)? = if (badge == ScooterPanel.Badge.NOT_CONNECTED && ScooterShiftLink.canConnectNow(ctx)) {
                        { ctx.safely("scooter_connect") { ScooterShiftLink.connectNow(ctx); bump() } }
                    } else null
                    RecordBadge(badge, lang, connect)
                    val haptics = LocalHapticFeedback.current
                    WIconButton(
                        WeemIcons.StopFilled, T.DOCK_STOP.get(lang),
                        { haptics.performHapticFeedback(HapticFeedbackType.Confirm); ctx.safely("scooter_stop") { Scooter.stop(ctx); bump() } },
                        size = WeemSize.ride, tone = WTone.DANGER, enabled = dock.stop, iconSize = WeemSize.icon,
                    )
                }
            }
        }
    }

    when (val a = ask) {
        null -> {}
        is Ask.Message -> NoticeDialog(a.text, T.OK.get(lang), onDismiss = { ask = null })
        // K4: çok seçenekli pencereler alttan açılan sayfada.
        Ask.Pair -> ConfirmDialog(
            title = T.DOCK_PAIR.get(lang), text = T.PAIR_TEXT.get(lang), confirm = T.PAIR_GO.get(lang), dismiss = T.CANCEL.get(lang),
            // S37: eşleştirme kayıt başlatmaz (kayıtsız canlı bağlantı); eşleşince "Eşleşildi · kayda hazır".
            onConfirm = { ask = null; ctx.safely("scooter_pair") { scan.stop(); Scooter.pair(ctx); bump() } }, onDismiss = { ask = null },
        )
        Ask.ExportChoices -> WSheet(onDismiss = { ask = null }, title = T.EXPORT_TITLE.get(lang)) {
            SettingsGroup(null, dividerInset = 70.dp) {
                for (c in ScooterPanel.ExportChoice.entries) ListRow(c.text.get(lang), icon = WeemIcons.FolderZip, chevron = true, onClick = {
                    if (c.warning() != null) ask = Ask.ExportWarn(c) else export(ctx, c, lang) { ask = it }
                })
            }
            WButton(T.CANCEL.get(lang), { ask = null }, kind = BtnKind.TEXT, fill = true)
        }
        is Ask.ExportWarn -> {
            val (title, text, go) = a.choice.warning()!!
            ConfirmDialog(
                title = (title ?: a.choice.text).get(lang), text = text.get(lang), confirm = go.get(lang), dismiss = T.CANCEL.get(lang),
                onConfirm = { ask = null; export(ctx, a.choice, lang) { ask = it } }, onDismiss = { ask = null },
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

/** Eşleştirme sırasında "ŞİMDİ güç düğmesine…" büyük uyarı bandı (uyarı tonu, yuvarlak güç simgesi). */
@Composable
private fun PairAttentionBanner(text: String) {
    val p = palette()
    Row(
        Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.s).fillMaxWidth()
            .background(p.warnBg.color(), RoundedCornerShape(WeemRadius.panel)).padding(start = 14.dp, top = 12.dp, end = 18.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(p.warn.color(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(WeemIcons.PowerSettingsNew, contentDescription = null, tint = p.warnBg.color(), modifier = Modifier.size(WeemSize.iconRide))
        }
        Text(text, style = WeemType.titleL.copy(fontWeight = FontWeight.ExtraBold), color = p.warn.color(), modifier = Modifier.weight(1f))
    }
}

/** S37: kayıt durumu hapı (eski "Kaydı başlat" yerinde, 56 dp): tonlu zemin, nokta ve metin; [onClick] varsa dokunulur. */
@Composable
private fun RowScope.RecordBadge(badge: ScooterPanel.Badge, lang: Lang, onClick: (() -> Unit)?) {
    val p = palette()
    val tone = when (badge.tone) {
        null -> WTone.NEUTRAL
        ScooterPanel.Tone.ACCENT -> WTone.ACCENT
        ScooterPanel.Tone.POWER -> WTone.POWER
        ScooterPanel.Tone.POSITIVE -> WTone.POSITIVE
        ScooterPanel.Tone.WARN -> WTone.WARN
        ScooterPanel.Tone.DANGER -> WTone.DANGER
    }
    val modifier = Modifier.weight(1f).heightIn(min = WeemSize.ride).testTag("scooter-record-badge")
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot((if (tone == WTone.NEUTRAL) p.muted else p.mark(tone)).color())
            Text(badge.text.get(lang), style = WeemType.labelL.copy(fontSize = 16.sp, lineHeight = 20.sp), maxLines = 2)
        }
    }
    val bg = p.container(tone).color()
    val ink = p.ink(tone).color()
    if (onClick != null) Surface(onClick = onClick, shape = CircleShape, color = bg, contentColor = ink, modifier = modifier) { Box(contentAlignment = Alignment.CenterStart) { body() } }
    else Surface(shape = CircleShape, color = bg, contentColor = ink, modifier = modifier) { Box(contentAlignment = Alignment.CenterStart) { body() } }
}

/** Eylem çubuğunun simge düğmesi: 48 dp yükseklik, köşe 16, `raised` zemin; ad ekran okuyucuya (NB `icon`). */
@Composable
private fun RowScope.ToolButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val p = palette()
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(WeemRadius.card), color = p.raised.color(),
        contentColor = (if (enabled) p.text else p.muted).color(),
        modifier = Modifier.weight(1f).height(WeemSize.touch).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(WeemSize.icon).alpha(if (enabled) 1f else .45f))
        }
    }
}

private fun export(ctx: android.content.Context, choice: ScooterPanel.ExportChoice, lang: Lang, show: (Ask?) -> Unit) {
    show(Ask.Message(T.EXPORT_PREPARING.get(lang)))
    ctx.safely("scooter_export") { Scooter.export(ctx, choice, lang) { msg -> show(Ask.Message(msg)) } }
}

/** Scooter tarama listesi (alttan açılan sayfa): ipucu, ilerleme, bulunan cihazlar (kayıtlı scooter yeşil simgeyle); dokununca seçilir. */
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
    val p = palette()
    val title = if (rows.isEmpty() && scan.scanning) T.SCAN_TITLE.get(lang) else (if (scan.scanning) T.SCAN_RUNNING else T.SCAN_DONE).get(lang).format(rows.size.toString())
    WSheet(onDismiss = onDismiss, title = title) {
        Text(T.SCAN_HINT.get(lang), style = WeemType.bodyM, color = p.muted.color())
        if (scan.scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.accent.color(), trackColor = p.raised.color(), strokeCap = StrokeCap.Round)
        LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
            items(rows, key = { it.address }) { f ->
                val isSaved = f.address == saved
                ListRow(
                    if (f.named) f.name!! else T.UNNAMED.get(lang),
                    Modifier.semantics(mergeDescendants = true) { contentDescription = ScooterPanel.ScanList.label(f, lang) },
                    subtitle = ScooterPanel.ScanList.subtitle(f, isSaved, lang),
                    icon = if (isSaved) WeemIcons.ElectricScooterFilled else WeemIcons.Bluetooth,
                    iconTone = if (isSaved) WTone.POSITIVE else if (f.named) WTone.ACCENT else WTone.NEUTRAL,
                    onClick = { onPick(f) },
                )
            }
        }
        WButton(T.CLOSE.get(lang), onDismiss, kind = BtnKind.TEXT, fill = true)
    }
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
