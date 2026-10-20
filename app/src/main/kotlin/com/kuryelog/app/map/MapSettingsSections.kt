package com.kuryelog.app.map

import com.kuryelog.logic.map.roads.RoadsText
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kuryelog.app.BuildConfig
import com.kuryelog.app.R
import com.kuryelog.app.safely
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.ui.PlaceText
import com.kuryelog.logic.map.graph.MapDataPhase
import com.kuryelog.logic.map.ui.MapStyles
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Harita ayarları → "Harita görünümü" (MAP.md 6.9, SORU-N5 (a)): Vektör (varsayılan) · OpenStreetMap · Google Maps (yalnız anahtarlı derlemede). */
@Composable
fun MapBaseSection(lang: Lang) {
    val ctx = LocalContext.current
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val current = MapStyles.resolve(settings.mapStyle, BuildConfig.MAPS_ENABLED)
    SectionCard(MapText.BASE_TITLE.get(lang)) {
        for (b in MapStyles.choices(BuildConfig.MAPS_ENABLED)) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = b == current, role = Role.RadioButton) { ctx.safely { MapSettingsRepo.update(ctx) { it.copy(mapStyle = b.key) } } }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = b == current, onClick = null)
                Text(MapStyles.label(b).get(lang), modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(
            MapText.BASE_INFO.get(lang),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    SectionCard(MapText.DATA_TITLE.get(lang)) {
        val g = state.graph
        Text(
            if (g != null) String.format(Locale.US, "%.1f MB · %d %s", state.sizeBytes / 1_048_576.0, g.edgeCount, MapText.DATA_EDGES.get(lang))
            else MapText.DATA_NOT_READY.get(lang),
        )
        val elev = state.elevation != null
        val has = if (elev) MapText.YES.get(lang) else MapText.NO.get(lang)
        Text(
            state.builtAtMs?.let { MapText.DATA_UPDATED.get(lang) + SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(it)) + " · " + MapText.DATA_ELEVATION_LO.get(lang) + has }
                ?: (MapText.DATA_ELEVATION.get(lang) + has),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val building = state.phase == MapDataPhase.BUILDING
        var info by remember { mutableStateOf(false) }
        TextButton(onClick = { info = true }) { Text("ⓘ ${PlaceText.REGION_WHAT.get(lang)}") }
        if (info) RegionInfoSheet(lang) { info = false }
        for ((key, label) in listOf("central" to MapText.REGION_CENTRAL.get(lang), "wide" to MapText.REGION_WIDE.get(lang))) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = settings.region == key, enabled = !building, role = Role.RadioButton) {
                    ctx.safely { MapSettingsRepo.update(ctx) { it.copy(region = key) } }
                    scope.launch { runCatching { controller.load(key) } }
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = settings.region == key, onClick = null)
                Text(label, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (building) {
            LinearProgressIndicator(progress = { state.progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("%${(state.progress * 100).roundToInt()} · ${state.stage}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { scope.launch { runCatching { controller.download(settings.region) } } }, enabled = !building, modifier = Modifier.padding(top = 8.dp)) {
            Text(MapText.DATA_DOWNLOAD.get(lang))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            MapText.DATA_INFO.get(lang),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Google Maps durumu (YolGo `maps_help.dart`; MAP.md 6.10): anahtarın son 4'ü, Play Services, paket ve imza SHA-1, Cloud bağlantıları. */
@Composable
fun MapsKeySection(lang: Lang) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val key = stringResource(R.string.google_maps_key)
    val status = remember(key) { MapsKeyStatus.read(ctx, key) }
    SectionCard("Google Maps") {
        Text(MapText.MAPS_KEY_APK.get(lang) + ": " + if (status.hasKey) "••••${status.last4}" else MapText.MAPS_KEY_NONE.get(lang))
        Text(
            "Google Play Services: " + when (status.playServices) {
                MapsKeyStatus.PLAY_SERVICES_OK -> MapText.PLAY_OK.get(lang)
                -1 -> MapText.PLAY_UNKNOWN.get(lang)
                else -> MapText.PLAY_UPDATE.get(lang).format(status.playServices)
            },
        )
        Text(
            MapText.MAPS_HELP.get(lang),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
        )
        Text("Paket: ${status.packageName}", style = MaterialTheme.typography.bodySmall)
        Text("SHA-1: ${status.sha1}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { clipboard.setText(AnnotatedString(status.sha1)) }) { Text(MapText.MAPS_SHA_COPY.get(lang)) }
        TextButton(onClick = { runCatching { uri.openUri("https://console.cloud.google.com/apis/credentials") } }) { Text(MapText.MAPS_KEY_OPEN.get(lang)) }
        TextButton(onClick = { runCatching { uri.openUri("https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com") } }) {
            Text(MapText.MAPS_SDK_OPEN.get(lang))
        }
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
    SectionCard(RoadsText.settingsGroup()) {
        Row(
            Modifier.fillMaxWidth().clickable { ctx.safely { MapSettingsRepo.update(ctx) { it.copy(roadLearning = !it.roadLearning) } } }.testTag("road-learning"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(RoadsText.learningSwitch(lang), style = MaterialTheme.typography.bodyLarge)
                Text(RoadsText.learningSwitchSub(lang), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.roadLearning, onCheckedChange = null)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clickable(enabled = settings.roadLearning) {
                ctx.safely { MapSettingsRepo.update(ctx) { it.copy(volumeKeys = !it.volumeKeys) } }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(RoadsText.volumeKeys(lang), style = MaterialTheme.typography.bodyMedium)
                Text(RoadsText.volumeKeysSub(lang), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.roadLearning && settings.volumeKeys, onCheckedChange = null, enabled = settings.roadLearning)
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onOpen).testTag("open-roads")) {
            Text(RoadsText.viewRoads(lang), style = MaterialTheme.typography.bodyLarge)
            Text(RoadsText.viewRoadsSub(lang), style = MaterialTheme.typography.bodySmall)
        }
    }
}
