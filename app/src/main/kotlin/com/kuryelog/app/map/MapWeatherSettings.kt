package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ride.RideFormat
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.ui.RideText
import com.kuryelog.logic.map.weather.WeatherReport
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Harita ayarları → "Hava durumu" bölümü (MAP.md 10.9): "Günde bir hava bildirimi" (M-S19), bilgi düğmesi (`weather-info`: pencere "Son bilinen konuma göre; konum yoksa Bakı. …"),
 * "Haritada hava şeridini göster" anahtarı (`weather-strip-toggle`, varsayılan açık), "8 saatlik tahmini göster". [onForecast] ağdan 8 saatlik raporu çeker (hata null).
 */
@Composable
fun MapWeatherSettings(
    stripOn: Boolean,
    onStripChange: (Boolean) -> Unit,
    lang: Lang,
    modifier: Modifier = Modifier,
    notifyOn: Boolean = true,
    onNotifyChange: (Boolean) -> Unit = {},
    onForecast: (suspend () -> WeatherReport?)? = null,
) {
    var info by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<WeatherReport?>(null) }
    var shownAt by remember { mutableStateOf<LocalTime?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { info = true }, modifier = Modifier.testTag("weather-info")) { Text("ⓘ", fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(RideText.WEATHER_SETTINGS_NOTIFY.get(lang))
                Text(RideText.WEATHER_SETTINGS_NOTIFY_SUB.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = notifyOn, onCheckedChange = onNotifyChange, modifier = Modifier.testTag("weather-notify-toggle"))
        }
        Row(
            Modifier.fillMaxWidth().testTag("weather-strip-toggle").toggleable(value = stripOn, role = Role.Switch, onValueChange = onStripChange).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(RideText.WEATHER_SETTINGS_STRIP.get(lang))
                Text(RideText.WEATHER_SETTINGS_STRIP_SUB.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = stripOn, onCheckedChange = null)
        }
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !busy && onForecast != null) {
                busy = true
                error = null
                scope.launch {
                    val next = runCatching { onForecast?.invoke() }.getOrNull()
                    if (next == null) error = RideText.WEATHER_SETTINGS_FORECAST_ERROR.get(lang) else { report = next; shownAt = LocalTime.now() }
                    busy = false
                }
            }.padding(vertical = 12.dp).testTag("weather-forecast"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(RideText.WEATHER_SETTINGS_FORECAST.get(lang), Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("⟳", fontSize = 18.sp)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        report?.let { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val t = shownAt
                Text(
                    RideText.WEATHER_SETTINGS_UPDATED.get(lang) + (t?.let { "${it.hour}:${String.format(java.util.Locale.ROOT, "%02d", it.minute)}" } ?: ""),
                    Modifier.weight(1f), fontSize = 13.sp,
                )
                TextButton(onClick = { report = null }, modifier = Modifier.testTag("weather-report-close")) { Text("✕  " + RideText.WEATHER_SETTINGS_CLOSE.get(lang)) }
            }
            for (h in r.hours) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${String.format(java.util.Locale.ROOT, "%02d:%02d", h.at.hour, h.at.minute)} · ${RideFormat.value(h.temperature, "°C")} · " +
                            "${RideText.WEATHER_SETTINGS_ROW_FEELS.get(lang)} ${RideFormat.value(h.feelsLike, "°")}",
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${RideText.WEATHER_SETTINGS_ROW_RAIN.get(lang)} %${RideFormat.value(h.rainChance, "")} · ${RideFormat.value(h.rainMm, "", 1)} mm\n" +
                            "${RideText.WEATHER_SETTINGS_ROW_WIND.get(lang)} ${RideFormat.value(h.wind, "")} km/h · ${RideText.WEATHER_SETTINGS_ROW_GUST.get(lang)} ${RideFormat.value(h.gust, "")} km/h",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(RideText.WEATHER_SETTINGS_INFO_TITLE.get(lang)) },
            text = { Text(RideText.WEATHER_SETTINGS_INFO.get(lang)) },
            confirmButton = { TextButton(onClick = { info = false }) { Text(RideText.WEATHER_SETTINGS_CLOSE.get(lang)) } },
        )
    }
}

/**
 * Harita ayarları ekranı (6.9): bölümler "Harita görünümü", "Harita verisi", Google Maps (A6.5), "Hava durumu" (A6.7, katlanır), "Yollarım", … Rawstats bilgileri bölümü yok (WEEM).
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
    onWeatherForecast: (suspend () -> WeatherReport?)? = null,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("←") }
            Text(MapText.SETTINGS_TITLE.get(lang), style = MaterialTheme.typography.headlineMedium)
        }
        MapBaseSection(lang)
        CollapsibleSection(RideText.WEATHER_SETTINGS_TITLE.get(lang)) {
            MapWeatherSettings(weatherStrip, onWeatherStripChange, lang, notifyOn = weatherNotify, onNotifyChange = onWeatherNotifyChange, onForecast = onWeatherForecast)
        }
        MapDataSection(lang)
        MapsKeySection(lang)
    }
}

/** Katlanır bölüm (YolGo `_Accordion`): başlığa dokununca açılır; kapalıyken yalnız başlık. */
@Composable
private fun CollapsibleSection(title: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (open) "⌃" else "⌄")
            }
            if (open) content()
        }
    }
}
