package com.kuryelog.app.map

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
    SectionCard(tl(lang, "Harita görünümü", "Xəritənin görünüşü")) {
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
            tl(lang, "Rota ve sokak kararlarını her altlıkta WEEM hesaplar; Google Maps yalnız görüntüdür.", "Marşrutu və küçə qaydalarını hər altlıqda WEEM hesablayır; Google Maps yalnız görüntüdür."),
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
    SectionCard(tl(lang, "Harita verisi", "Xəritə məlumatları")) {
        val g = state.graph
        Text(
            if (g != null) String.format(Locale.US, "%.1f MB · %d %s", state.sizeBytes / 1_048_576.0, g.edgeCount, tl(lang, "yol parçası", "yol hissəsi"))
            else tl(lang, "Hazır değil", "Hazır deyil"),
        )
        val elev = state.elevation != null
        val has = if (elev) tl(lang, "var", "var") else tl(lang, "yok", "yoxdur")
        Text(
            state.builtAtMs?.let { tl(lang, "Güncelleme: ", "Yenilənib: ") + SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(it)) + " · " + tl(lang, "yükseklik ", "hündürlük ") + has }
                ?: (tl(lang, "Yükseklik: ", "Hündürlük: ") + has),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val building = state.phase == MapDataPhase.BUILDING
        for ((key, label) in listOf("central" to tl(lang, "Merkez Bakü", "Mərkəzi Bakı"), "wide" to tl(lang, "Geniş Bakü", "Böyük Bakı"))) {
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
            Text(tl(lang, "İndir / güncelle", "Yüklə / yenilə"))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            tl(lang, "OSM yolları ayda bir güncellemek yeterli. Geniş bölge daha büyük indirme demek.", "Xəritə tətbiqin içində gəlir; burdan yalnız yeniləmək olar. Böyük Bakı daha çox yer tutur."),
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
        Text(tl(lang, "APK harita anahtarı", "APK xəritə açarı") + ": " + if (status.hasKey) "••••${status.last4}" else tl(lang, "Anahtar eklenmemiş", "Açar əlavə edilməyib"))
        Text(
            "Google Play Services: " + when (status.playServices) {
                MapsKeyStatus.PLAY_SERVICES_OK -> tl(lang, "Hazır", "Hazırdır")
                -1 -> tl(lang, "Kontrol edilemedi", "Yoxlama tamamlanmadı")
                else -> tl(lang, "Güncelleme veya etkinleştirme gerekiyor (kod ${status.playServices})", "Yeniləmək və ya aktivləşdirmək lazımdır (kod ${status.playServices})")
            },
        )
        Text(
            tl(lang,
                "Google Cloud’da bu anahtarın projesinde Maps SDK for Android ve faturalandırma açık olmalı. Anahtar kısıtlaması Android uygulamaları olmalı; paket ve SHA-1 aşağıdaki değerlerle eşleşmeli.",
                "Google Cloud-da bu açarın layihəsində Maps SDK for Android və ödəniş hesabı aktiv olmalıdır. Açar Android tətbiqləri üçün olmalı, paket və SHA-1 aşağıdakı məlumatlarla uyğun gəlməlidir."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp),
        )
        Text("Paket: ${status.packageName}", style = MaterialTheme.typography.bodySmall)
        Text("SHA-1: ${status.sha1}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { clipboard.setText(AnnotatedString(status.sha1)) }) { Text(tl(lang, "SHA-1 kopyala", "SHA-1-i kopyala")) }
        TextButton(onClick = { runCatching { uri.openUri("https://console.cloud.google.com/apis/credentials") } }) { Text(tl(lang, "Anahtar ayarlarını aç", "Açar parametrlərini aç")) }
        TextButton(onClick = { runCatching { uri.openUri("https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com") } }) {
            Text(tl(lang, "Maps SDK ayarlarını aç", "Maps SDK parametrlərini aç"))
        }
    }
}
