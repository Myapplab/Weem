package com.kuryelog.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.CrashLog
import com.kuryelog.app.core.Store
import com.kuryelog.app.map.MapScreen
import com.kuryelog.app.map.DailyToolsScreen
import com.kuryelog.app.map.MapSettingsScreen
import com.kuryelog.app.map.RoadInfoScreen
import com.kuryelog.logic.scooter.ScooterUiText
import com.kuryelog.app.map.NavRuntime
import com.kuryelog.app.map.ReminderCard
import com.kuryelog.app.map.ReminderRuntime
import com.kuryelog.app.map.TaskAlertCard
import com.kuryelog.app.map.TaskRuntime
import com.kuryelog.app.map.WeatherRuntime
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.NavItem
import com.kuryelog.app.ui.SectionLabel
import com.kuryelog.app.ui.Segmented
import com.kuryelog.app.ui.ScreenTitle
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemNavBar
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.isDark
import com.kuryelog.logic.app.Permissions
import com.kuryelog.logic.app.SetupText
import com.kuryelog.logic.app.Tab
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.app.ScreenOrientation
import com.kuryelog.logic.app.ThemeMode
import com.kuryelog.logic.app.label
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.LinkText

/** Alt menü simgeleri (A10.4d, `docs/TASARIM.md` 7): seçiliyken dolu. */
private fun tabIcons(tab: Tab): Pair<ImageVector, ImageVector> = when (tab) {
    Tab.MAP -> WeemIcons.Map to WeemIcons.MapFilled
    Tab.ORDERS -> WeemIcons.ReceiptLong to WeemIcons.ReceiptLongFilled
    Tab.SCOOTER -> WeemIcons.ElectricScooter to WeemIcons.ElectricScooterFilled
    Tab.REMOTE -> WeemIcons.Devices to WeemIcons.DevicesFilled
    Tab.MORE -> WeemIcons.Menu to WeemIcons.Menu
}

/**
 * Alt menü kabuğu (A1.3). Her sekmenin kendi durumu (kaydırma, sayaç…) sekme değişince saklanır. Her sekme kendi hata sınırında
 * ([GuardedPage], CORE.md 4.1): çizilemeyen sekme yerine kart, alt menü çalışır. Son çökme gizlenene dek en üstte kart.
 */
@Composable
fun WeemApp(
    lang: Lang,
    langSetting: String,
    theme: ThemeMode,
    versionName: String,
    onLangSetting: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSetupAgain: () -> Unit = {},
    orientation: ScreenOrientation = ScreenOrientation.FREE,
    onOrientation: (ScreenOrientation) -> Unit = {},
) {
    var selectedKey by rememberSaveable { mutableStateOf(Tab.start.key) }
    val selected = Tab.fromKey(selectedKey)
    val holder = rememberSaveableStateHolder()
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(Store.guarded(ctx, "crash_card") { CrashLog.unseen(ctx) }) }
    val dark = theme.isDark(isSystemInDarkTheme())
    // A5.4: yüzen panelden açma isteği Scooter sekmesine götürür; PiP penceresinde yalnız küçük ızgara; tam ekranda alt menü yok.
    val openScooter = ScooterNav.open
    LaunchedEffect(openScooter) { if (openScooter != null) selectedKey = Tab.SCOOTER.key }
    // Gelen navigasyon bağlantısı harita sekmesini açar (Y5).
    val openMap by NavRuntime.get(ctx).openMap.collectAsState()
    LaunchedEffect(openMap) { if (openMap > 0) selectedKey = Tab.MAP.key }
    var scooterImmersive by remember { mutableStateOf(false) }
    if (ScooterNav.pip) {
        GuardedPage("scooter_pip", lang, build = {}) { ScooterPip(lang, dark) }
        return
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!(selected == Tab.SCOOTER && scooterImmersive)) {
                val items = Tab.entries.map { tab -> tabIcons(tab).let { (icon, on) -> NavItem(tab.key, tab.label.get(lang), icon, on, tag = "tab_${tab.key}") } }
                WeemNavBar(items, selected.key, { selectedKey = it })
            }
        },
    ) { padding ->
        // A6.9: hatırlatıcı kartı ve görev uyarısı uygulama kabuğunda, her sekmenin üstünde (CORE.md 3.1: WEEM öndeyken sistem bildirimi yok).
        val reminders = remember { ReminderRuntime.get(ctx) }
        val taskRt = remember { TaskRuntime.get(ctx) }
        val reminder by reminders.active.collectAsState()
        val tasks by taskRt.view.collectAsState()
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            crash?.let { j -> CrashCard(j, lang) { CrashLog.hide(ctx, j); crash = null } }
            ReminderCard(reminder, lang, onDone = reminders::done)
            holder.SaveableStateProvider(selected.key) {
                GuardedPage(selected.key, lang, build = {}) {
                    when (selected) {
                        // A3.8: Siparişler sekmesi.
                        Tab.ORDERS -> Column {
                            // A8.3: Receiver'da Provider'ın verisi (LINK.md 4.10).
                            RemoteOrdersCard(lang)
                            OrdersTab(lang)
                        }
                        Tab.MORE -> MoreScreen(lang, langSetting, theme, versionName, onLangSetting, onTheme, onSetupAgain, orientation, onOrientation)
                        // A6.5: Harita sekmesi (graf yokken vardiya kartı + kurulum kartı; G1 vardiya düğmesi açılış sekmesinde).
                        Tab.MAP -> MapScreen(lang, dark)
                        // A5.4: Scooter sekmesi (canlı panel, tarama, eşleştirme, ayarlar, tanı, ZIP).
                        Tab.SCOOTER -> Column {
                            // A8.3: Provider'da scooter Receiver'da (S11); batarya özeti.
                            if (!scooterImmersive) RemoteScooterCard(lang)
                            ScooterTab(lang, dark, scooterImmersive) { scooterImmersive = it }
                        }
                        // A9.1: Uzaktan sekmesi (öteki telefonun ekranı, bu telefonun paylaşımı).
                        Tab.REMOTE -> RemoteScreen(lang)
                    }
                }
            }
        }
        TaskAlertCard(tasks.alerts, tasks.lastPoint, lang, onRoute = { t -> taskRt.openRoute(t) }, onDismiss = { t -> taskRt.dismiss(t.id) })
        // A8.4: Receiver'da hırsızlık koruması geri sayımı (LINK.md 5.1).
        TheftOverlay(lang)
        }
    }
    // A8.4: uyarı/alarm ekranı alt menünün de üstünde; dokunuş alttakine geçmez (LINK.md 5.1).
    TheftAlarm(lang)
    }
}

@Composable
private fun MoreScreen(
    lang: Lang,
    langSetting: String,
    theme: ThemeMode,
    versionName: String,
    onLangSetting: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSetupAgain: () -> Unit,
    orientation: ScreenOrientation,
    onOrientation: (ScreenOrientation) -> Unit,
) {
    var records by rememberSaveable { mutableStateOf(false) }
    var home by rememberSaveable { mutableStateOf(false) }
    var ordersSettings by rememberSaveable { mutableStateOf(false) }
    var mapSettings by rememberSaveable { mutableStateOf(false) }
    var scooterSettings by rememberSaveable { mutableStateOf(false) }
    var roadInfo by rememberSaveable { mutableStateOf(false) }
    var daily by rememberSaveable { mutableStateOf(false) }
    var twoPhone by rememberSaveable { mutableStateOf(false) }
    if (twoPhone) {
        TwoPhoneScreen(lang, versionName) { twoPhone = false }
        return
    }
    var permissions by rememberSaveable { mutableStateOf(false) }
    if (permissions) {
        PermissionsScreen(lang, onSetupAgain) { permissions = false }
        return
    }
    // S37: Ev konumu Harita ayarlarının içinden açılır; geri dönünce Harita ayarları kalır.
    if (home) {
        HomeScreen(lang) { home = false }
        return
    }
    if (mapSettings) {
        MapSettingsScreen(lang, onBack = { mapSettings = false }, onHome = { home = true })
        return
    }
    if (daily) {
        val ctx = LocalContext.current
        val weather = remember { WeatherRuntime.get(ctx) }
        val strip by weather.strip.collectAsState()
        val notify by weather.enabled.collectAsState()
        DailyToolsScreen(
            lang, weatherStrip = strip, onWeatherStripChange = weather::setStrip, onBack = { daily = false },
            weatherNotify = notify, onWeatherNotifyChange = weather::setEnabled, onWeatherFetch = weather::fetchNow,
        )
        return
    }
    if (roadInfo) {
        RoadInfoScreen(lang) { roadInfo = false }
        return
    }
    if (scooterSettings) {
        ScooterSettingsScreen(lang) { scooterSettings = false }
        return
    }
    if (ordersSettings) {
        OrdersSettingsScreen(lang, onBack = { ordersSettings = false })
        return
    }
    if (records) {
        RecordsScreen(lang, onBack = { records = false })
        return
    }
    val perms = rememberPermissionState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = WeemSpace.l, vertical = WeemSpace.s)) {
        ScreenTitle(Str.SETTINGS_TITLE.get(lang), Modifier.padding(bottom = WeemSpace.m))
        // K4 (A10.4d): her satır tek dokunuşla açılır (simge + başlık + açıklama + ›). S37: konuya göre ayrılmış ayar ekranları.
        SettingsGroup(null, dividerInset = 70.dp) {
            // G5 (A7.2): izinler kartı ve pil rehberi her zaman burada; eksik izin rozette "! n/m".
            ListRow(
                SetupText.PERMS_TITLE.get(lang), subtitle = SetupText.PERMS_OPEN.get(lang), icon = WeemIcons.Shield, chevron = true,
                trailing = {
                    val ok = Permissions.allOk(perms)
                    StatusPill(Permissions.badge(perms), if (ok) WTone.POSITIVE else WTone.DANGER, dot = false)
                },
                onClick = { permissions = true },
            )
            ListRow(Str.MSET_TITLE.get(lang), subtitle = Str.MSET_OPEN.get(lang), icon = WeemIcons.Map, chevron = true, onClick = { mapSettings = true })
            ListRow(Str.OSET_TITLE.get(lang), subtitle = Str.OSET_OPEN.get(lang), icon = WeemIcons.ReceiptLong, chevron = true, onClick = { ordersSettings = true })
            ListRow(ScooterUiText.SETTINGS_TITLE.get(lang), Modifier.testTag("open-scooter-settings"), subtitle = Str.SSET_OPEN.get(lang), icon = WeemIcons.ElectricScooter,
                chevron = true, onClick = { scooterSettings = true })
            ListRow(Str.ROADINFO_TITLE.get(lang), Modifier.testTag("open-road-info"), subtitle = Str.ROADINFO_OPEN.get(lang), icon = WeemIcons.Route, chevron = true,
                onClick = { roadInfo = true })
            ListRow(Str.DAILY_TITLE.get(lang), Modifier.testTag("open-daily"), subtitle = Str.DAILY_OPEN.get(lang), icon = WeemIcons.PartlyCloudyDay, chevron = true,
                onClick = { daily = true })
            ListRow(Str.RECORDS_TITLE.get(lang), subtitle = Str.RECORDS_OPEN.get(lang), icon = WeemIcons.Folder, chevron = true, onClick = { records = true })
            // A8.3: 2 telefon modu (LINK.md 4.10).
            ListRow(LinkText.TITLE.get(lang), subtitle = LinkText.OPEN.get(lang), icon = WeemIcons.Devices, chevron = true, onClick = { twoPhone = true })
        }
        // S37: dil, tema ve ekran yönü en altta üçlü seçim (Segmented).
        SectionLabel(Str.SETTINGS_LANGUAGE.get(lang))
        val langKeys = listOf("", Lang.TR.key, Lang.AZ.key)
        Segmented(
            listOf(Str.LANG_SYSTEM.get(lang), Str.LANG_TR.get(lang), Str.LANG_AZ.get(lang)), langKeys.indexOf(langSetting).coerceAtLeast(0),
            { onLangSetting(langKeys[it]) }, tags = listOf("lang-system", "lang-tr", "lang-az"),
        )
        SectionLabel(Str.SETTINGS_THEME.get(lang))
        val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
        Segmented(
            listOf(Str.THEME_SYSTEM.get(lang), Str.THEME_LIGHT.get(lang), Str.THEME_DARK.get(lang)), themes.indexOf(theme).coerceAtLeast(0),
            { onTheme(themes[it]) }, tags = listOf("theme-system", "theme-light", "theme-dark"),
        )
        // A10.4g (SORU-N6): ekran yönü; varsayılan serbest.
        SectionLabel(Str.SETTINGS_ORIENTATION.get(lang))
        val orientations = listOf(ScreenOrientation.FREE, ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)
        Segmented(
            listOf(Str.ORIENTATION_FREE.get(lang), Str.ORIENTATION_PORTRAIT.get(lang), Str.ORIENTATION_LANDSCAPE.get(lang)), orientations.indexOf(orientation).coerceAtLeast(0),
            { onOrientation(orientations[it]) }, tags = listOf("orientation-free", "orientation-portrait", "orientation-landscape"),
        )
        Text(
            Str.VERSION.get(lang).format(versionName),
            style = WeemType.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = WeemSpace.xxl, bottom = WeemSpace.l),
        )
    }
}

/** Daha fazla → İzinler (G5): izinler kartı, pil rehberi ve kurulumu yeniden gösterme. */
@Composable
private fun PermissionsScreen(lang: Lang, onSetupAgain: () -> Unit, onBack: () -> Unit) {
    SubScreen(SetupText.PERMS_TITLE.get(lang), Str.BACK.get(lang), onBack) {
        PermissionsCard(lang)
        BatteryGuideCard(lang)
        WButton(SetupText.SETUP_AGAIN.get(lang), onSetupAgain, kind = BtnKind.TONAL, icon = WeemIcons.Refresh, fill = true)
    }
}
