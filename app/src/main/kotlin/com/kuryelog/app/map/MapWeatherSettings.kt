package com.kuryelog.app.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.input.ApiText
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.ui.WeatherUiText
import com.kuryelog.logic.map.weather.WeatherReport
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun num(n: Double?, digits: Int = 0) = if (n == null) "—" else String.format(Locale.US, "%.${digits}f", n)

@Composable
private fun SwitchRow(tag: String?, icon: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Harita ayarları → "Hava durumu" bölümünün gövdesi (MAP.md 10.9): "Günde bir hava bildirimi" (M-S19; bilgi düğmesi `weather-info` → pencere "Son bilinen konuma göre; konum yoksa Bakı. …"),
 * "Haritada hava şeridini göster" anahtarı (`weather-strip-toggle`, varsayılan açık), "8 saatlik tahmini göster" ([onFetch] ağdan zorla alır, hata metni, "Güncellendi H:mm" + "Kapat" + saat listesi).
 */
@Composable
fun MapWeatherSettings(
    stripOn: Boolean,
    onStripChange: (Boolean) -> Unit,
    lang: Lang,
    modifier: Modifier = Modifier,
    notifyOn: Boolean = true,
    onNotifyChange: (Boolean) -> Unit = {},
    onFetch: ((WeatherReport?) -> Unit) -> Unit = { it(null) },
) {
    var info by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<WeatherReport?>(null) }
    Column(modifier.fillMaxWidth()) {
        SwitchRow(
            null, "🔔", WeatherUiText.NOTIFY_TITLE.get(lang), WeatherUiText.NOTIFY_SUBTITLE.get(lang), notifyOn, onNotifyChange,
            trailing = { TextButton(onClick = { info = true }, modifier = Modifier.testTag("weather-info")) { Text("ⓘ", fontSize = 18.sp) } },
        )
        SwitchRow("weather-strip-toggle", "🗺", WeatherUiText.STRIP_TITLE.get(lang), WeatherUiText.STRIP_SUBTITLE.get(lang), stripOn, onStripChange)
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                busy = true
                failed = false
                onFetch { r ->
                    busy = false
                    if (r == null) failed = true else report = r
                }
            }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("☁", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
            Text(WeatherUiText.SHOW_EIGHT.get(lang), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("↻", fontSize = 18.sp)
        }
        if (failed) Text(WeatherUiText.ERROR_FETCH.get(lang), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        report?.let { r ->
            val at = Instant.ofEpochMilli(r.fetchedAtMs).atZone(ZoneId.systemDefault())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${WeatherUiText.UPDATED.get(lang)} ${at.hour}:${String.format(Locale.US, "%02d", at.minute)}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { report = null }, modifier = Modifier.testTag("weather-report-close")) { Text("✕  " + WeatherUiText.CLOSE.get(lang)) }
            }
            for (h in r.hours) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "${String.format(Locale.US, "%02d:%02d", h.at.hour, h.at.minute)} · ${num(h.temperature)}°C · ${WeatherUiText.DETAIL_FEELS.get(lang).lowercase(Locale.ROOT)} ${num(h.feelsLike)}°",
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${WeatherUiText.DETAIL_RAIN.get(lang)} %${num(h.rainChance)} · ${num(h.rainMm, 1)} mm\n${WeatherUiText.DETAIL_WIND.get(lang)} ${num(h.wind)} km/h · ${WeatherUiText.DETAIL_GUST.get(lang).lowercase(Locale.ROOT)} ${num(h.gust)} km/h",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(WeatherUiText.ABOUT_TITLE.get(lang)) },
            text = { Text(WeatherUiText.ABOUT_TEXT.get(lang)) },
            confirmButton = { TextButton(onClick = { info = false }) { Text(WeatherUiText.CLOSE.get(lang)) } },
        )
    }
}

/**
 * Harita ayarları ekranı (6.9): bölümler "Harita görünümü", "Harita verisi", Google Maps (A6.5), "Hava durumu" (A6.7, başlığa dokununca açılır), "API ve bağlantılar" (A6.6, 8.9), "Yollarım", … Rawstats bilgileri bölümü yok (WEEM).
 * Daha fazla → Harita ayarları; [onBack] varsa üstte geri düğmesi.
 */
@Composable
fun MapSettingsScreen(
    lang: Lang,
    weatherStrip: Boolean,
    onWeatherStripChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    weatherNotify: Boolean = true,
    onWeatherNotifyChange: (Boolean) -> Unit = {},
    onWeatherFetch: ((WeatherReport?) -> Unit) -> Unit = { it(null) },
) {
    var weatherOpen by remember { mutableStateOf(false) }
    var api by remember { mutableStateOf(false) }
    if (api) {
        BackHandler { api = false }
        MapApiSettingsScreen(lang, onBack = { api = false })
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("←") }
            Text(MapText.SETTINGS_TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)
        }
        MapBaseSection(lang)
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    WeatherUiText.SECTION_TITLE.get(lang), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().clickable { weatherOpen = !weatherOpen },
                )
                if (weatherOpen) MapWeatherSettings(weatherStrip, onWeatherStripChange, lang, notifyOn = weatherNotify, onNotifyChange = onWeatherNotifyChange, onFetch = onWeatherFetch)
            }
        }
        MapDataSection(lang)
        MapsKeySection(lang)
        // A6.6: "API ve bağlantılar" (MAP.md 8.9).
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { api = true }.testTag("open-api-settings")) {
            Text(ApiText.OPEN.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
    }
}
