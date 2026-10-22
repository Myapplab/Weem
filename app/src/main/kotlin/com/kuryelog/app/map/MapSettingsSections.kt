package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kuryelog.app.BuildConfig
import com.kuryelog.app.R
import com.kuryelog.app.safely
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.MapDataPhase
import com.kuryelog.logic.map.roads.RoadsText
import com.kuryelog.logic.map.ui.MapStyles
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.ui.PlaceText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Harita ayarları → "Harita görünümü" (MAP.md 6.9, SORU-N5 (a)): Vektör (varsayılan) · OpenStreetMap · Google Maps (yalnız anahtarlı derlemede). */
@Composable
fun MapBaseSection(lang: Lang) {
    val ctx = LocalContext.current
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val current = MapStyles.resolve(settings.mapStyle, BuildConfig.MAPS_ENABLED)
    SettingsGroup(MapText.BASE_TITLE.get(lang), footer = MapText.BASE_INFO.get(lang), dividerInset = 52.dp, selectableGroup = true) {
        for (b in MapStyles.choices(BuildConfig.MAPS_ENABLED)) {
            RadioRow(MapStyles.label(b).get(lang), b == current, { ctx.safely { MapSettingsRepo.update(ctx) { it.copy(mapStyle = b.key) } } })
        }
    }
}

/** Harita ayarları → "Harita verisi" (MAP.md 2.9): boyut ve yol parçası, güncelleme tarihi ve yükseklik, bölge, "İndir / güncelle", ilerleme, hata. */
@Composable
fun MapDataSection(lang: Lang) {
    val ctx = LocalContext.current
    val controller = remember { MapData.controller(ctx) }
    val state by controller.state.collectAsState()
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(false) }
    val building = state.phase == MapDataPhase.BUILDING
    SettingsGroup(MapText.DATA_TITLE.get(lang), footer = MapText.DATA_INFO.get(lang), dividerInset = 70.dp) {
        val g = state.graph
        val elev = state.elevation != null
        val has = if (elev) MapText.YES.get(lang) else MapText.NO.get(lang)
        ListRow(
            if (g != null) String.format(Locale.US, "%.1f MB · %d %s", state.sizeBytes / 1_048_576.0, g.edgeCount, MapText.DATA_EDGES.get(lang)) else MapText.DATA_NOT_READY.get(lang),
            subtitle = state.builtAtMs?.let { MapText.DATA_UPDATED.get(lang) + SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(it)) + " · " + MapText.DATA_ELEVATION_LO.get(lang) + has }
                ?: (MapText.DATA_ELEVATION.get(lang) + has),
            icon = WeemIcons.Layers, iconTone = if (g != null) WTone.POSITIVE else WTone.WARN,
        )
        ListRow(PlaceText.REGION_WHAT.get(lang), icon = WeemIcons.Info, chevron = true, onClick = { info = true })
        for ((key, label) in listOf("central" to MapText.REGION_CENTRAL.get(lang), "wide" to MapText.REGION_WIDE.get(lang))) {
            RadioRow(label, settings.region == key, {
                ctx.safely { MapSettingsRepo.update(ctx) { it.copy(region = key) } }
                scope.launch { runCatching { controller.load(key) } }
            }, enabled = !building)
        }
        if (building) Column(Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.m), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
            LinearProgressIndicator(progress = { state.progress.toFloat() }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
            Text("%${(state.progress * 100).roundToInt()} · ${state.stage}", style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ListRow(
            MapText.DATA_DOWNLOAD.get(lang), icon = WeemIcons.Download, iconTone = WTone.ACCENT, titleTone = WTone.ACCENT, enabled = !building,
            onClick = { scope.launch { runCatching { controller.download(settings.region) } } },
        )
        state.error?.let { ListRow(it, icon = WeemIcons.Error, iconTone = WTone.DANGER, titleTone = WTone.DANGER, titleStyle = WeemType.bodyM) }
    }
    if (info) RegionInfoSheet(lang) { info = false }
}

/** Google Maps durumu (YolGo `maps_help.dart`; MAP.md 6.10): anahtarın son 4'ü, Play Services, paket ve imza SHA-1, Cloud bağlantıları. */
@Composable
fun MapsKeySection(lang: Lang) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val key = stringResource(R.string.google_maps_key)
    val status = remember(key) { MapsKeyStatus.read(ctx, key) }
    SettingsGroup("Google Maps", footer = MapText.MAPS_HELP.get(lang), dividerInset = 70.dp) {
        ListRow(
            MapText.MAPS_KEY_APK.get(lang) + ": " + if (status.hasKey) "••••${status.last4}" else MapText.MAPS_KEY_NONE.get(lang),
            icon = WeemIcons.Key, iconTone = if (status.hasKey) WTone.POSITIVE else WTone.NEUTRAL, titleStyle = WeemType.bodyM,
        )
        ListRow(
            "Google Play Services: " + when (status.playServices) {
                MapsKeyStatus.PLAY_SERVICES_OK -> MapText.PLAY_OK.get(lang)
                -1 -> MapText.PLAY_UNKNOWN.get(lang)
                else -> MapText.PLAY_UPDATE.get(lang).format(status.playServices)
            },
            icon = WeemIcons.PhoneAndroid, titleStyle = WeemType.bodyM,
        )
        ListRow("Paket: ${status.packageName}", subtitle = "SHA-1: ${status.sha1}", icon = WeemIcons.Shield, titleStyle = WeemType.bodyM)
        ListRow(MapText.MAPS_SHA_COPY.get(lang), icon = WeemIcons.ContentCopy, onClick = { clipboard.setText(AnnotatedString(status.sha1)) })
        ListRow(MapText.MAPS_KEY_OPEN.get(lang), icon = WeemIcons.OpenInNew, onClick = { runCatching { uri.openUri("https://console.cloud.google.com/apis/credentials") } })
        ListRow(MapText.MAPS_SDK_OPEN.get(lang), icon = WeemIcons.OpenInNew, onClick = {
            runCatching { uri.openUri("https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com") }
        })
    }
}

/**
 * Harita ayarları → "Yollarım" (MAP.md 9.8.1, 9.5; S26): "Yol kalitesi öğrenme" ana anahtarı (varsayılan kapalı), ses tuşu anahtarı (öğrenme kapalıyken
 * pasif) ve "Yolları görüntüle" (Kalite · ters yön · yol yok · bisiklet).
 */
@Composable
fun RoadsSettingsSection(lang: Lang, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    SettingsGroup(RoadsText.settingsGroup(), dividerInset = 70.dp) {
        SwitchRow(
            RoadsText.learningSwitch(lang), settings.roadLearning, { ctx.safely { MapSettingsRepo.update(ctx) { it.copy(roadLearning = !it.roadLearning) } } },
            subtitle = RoadsText.learningSwitchSub(lang), icon = WeemIcons.Route, tag = "road-learning",
        )
        SwitchRow(
            RoadsText.volumeKeys(lang), settings.roadLearning && settings.volumeKeys, { ctx.safely { MapSettingsRepo.update(ctx) { it.copy(volumeKeys = !it.volumeKeys) } } },
            subtitle = RoadsText.volumeKeysSub(lang), icon = WeemIcons.VolumeUp, enabled = settings.roadLearning,
        )
        ListRow(RoadsText.viewRoads(lang), Modifier.testTag("open-roads"), subtitle = RoadsText.viewRoadsSub(lang), icon = WeemIcons.Map, chevron = true, onClick = onOpen)
    }
}
