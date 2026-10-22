package com.kuryelog.app.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.DividedColumn
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.NoticeDialog
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.input.ApiText
import com.kuryelog.logic.map.ui.DatasetText
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.ui.ReminderText
import com.kuryelog.logic.map.ui.RideLogText
import com.kuryelog.logic.map.ui.TaskText
import com.kuryelog.logic.map.ui.WeatherUiText
import com.kuryelog.logic.map.weather.WeatherReport
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun num(n: Double?, digits: Int = 0) = if (n == null) "—" else String.format(Locale.US, "%.${digits}f", n)

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
    DividedColumn(MaterialTheme.colorScheme.outlineVariant, inset = 70.dp, modifier = modifier) {
        SwitchRow(
            WeatherUiText.NOTIFY_TITLE.get(lang), notifyOn, onNotifyChange, subtitle = WeatherUiText.NOTIFY_SUBTITLE.get(lang), icon = WeemIcons.Notifications,
            extra = { WIconButton(WeemIcons.Info, WeatherUiText.ABOUT_TITLE.get(lang), { info = true }, Modifier.testTag("weather-info")) },
        )
        SwitchRow(
            WeatherUiText.STRIP_TITLE.get(lang), stripOn, onStripChange, subtitle = WeatherUiText.STRIP_SUBTITLE.get(lang), icon = WeemIcons.Map,
            tag = "weather-strip-toggle",
        )
        ListRow(
            WeatherUiText.SHOW_EIGHT.get(lang), icon = WeemIcons.PartlyCloudyDay, enabled = !busy,
            trailing = {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(WeemIcons.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = {
                busy = true
                failed = false
                onFetch { r ->
                    busy = false
                    if (r == null) failed = true else report = r
                }
            },
        )
        if (failed) Banner(WeatherUiText.ERROR_FETCH.get(lang), Modifier.padding(WeemSpace.m), tone = WTone.DANGER)
        report?.let { r ->
            val at = Instant.ofEpochMilli(r.fetchedAtMs).atZone(ZoneId.systemDefault())
            Column(Modifier.padding(start = WeemSpace.l, end = WeemSpace.s, top = WeemSpace.xs, bottom = WeemSpace.m)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${WeatherUiText.UPDATED.get(lang)} ${at.hour}:${String.format(Locale.US, "%02d", at.minute)}", style = WeemType.labelM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WButton(WeatherUiText.CLOSE.get(lang), { report = null }, Modifier.testTag("weather-report-close"), kind = BtnKind.TEXT, icon = WeemIcons.Close)
                }
                for (h in r.hours) {
                    Row(Modifier.fillMaxWidth().padding(vertical = WeemSpace.xs, horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
                        Text(String.format(Locale.US, "%02d:%02d", h.at.hour, h.at.minute), style = WeemType.numS)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${num(h.temperature)}°C · ${WeatherUiText.DETAIL_FEELS.get(lang).lowercase(Locale.ROOT)} ${num(h.feelsLike)}°",
                                style = WeemType.titleS,
                            )
                            Text(
                                "${WeatherUiText.DETAIL_RAIN.get(lang)} %${num(h.rainChance)} · ${num(h.rainMm, 1)} mm\n${WeatherUiText.DETAIL_WIND.get(lang)} ${num(h.wind)} km/h · ${WeatherUiText.DETAIL_GUST.get(lang).lowercase(Locale.ROOT)} ${num(h.gust)} km/h",
                                style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (info) NoticeDialog(WeatherUiText.ABOUT_TEXT.get(lang), WeatherUiText.CLOSE.get(lang), { info = false }, title = WeatherUiText.ABOUT_TITLE.get(lang))
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
    var sub by remember { mutableStateOf<String?>(null) }
    if (api) {
        BackHandler { api = false }
        MapApiSettingsScreen(lang, onBack = { api = false })
        return
    }
    // A6.9: Sürüş kayıtları, Teklif veri seti, Günlük işler, Hatırlatıcılar (MAP.md 6.9, 12.10, 13.8, 13.14, 13.16).
    when (sub) {
        "ridelogs" -> { RideLogsHost(lang) { sub = null }; return }
        "dataset" -> { DatasetHost(lang, shiftDay = null) { sub = null }; return }
        "tasks" -> { TasksHost(lang) { sub = null }; return }
        "reminders" -> { ReminderSettingsHost(lang) { sub = null }; return }
        "roads" -> { RoadsHost(lang, onBack = { sub = null }); return }
    }
    SubScreen(MapText.SETTINGS_TITLE.get(lang), Str.BACK.get(lang), onBack, modifier, spacing = 0.dp) {
        MapBaseSection(lang)
        WCard(Modifier.padding(top = WeemSpace.xxl), padding = 0.dp, spacing = 0.dp) {
            ListRow(
                WeatherUiText.SECTION_TITLE.get(lang), icon = WeemIcons.PartlyCloudyDay, iconTone = WTone.ACCENT, onClick = { weatherOpen = !weatherOpen },
                trailing = { Icon(if (weatherOpen) WeemIcons.ExpandLess else WeemIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            AnimatedVisibility(weatherOpen) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    MapWeatherSettings(weatherStrip, onWeatherStripChange, lang, notifyOn = weatherNotify, onNotifyChange = onWeatherNotifyChange, onFetch = onWeatherFetch)
                }
            }
        }
        MapDataSection(lang)
        MapsKeySection(lang)
        RoadsSettingsSection(lang, onOpen = { sub = "roads" })
        // A6.9 ve A6.6: kayıt ve araç ekranları, "API ve bağlantılar" (MAP.md 8.9).
        SettingsGroup(null, Modifier.padding(top = WeemSpace.xxl), dividerInset = 70.dp) {
            for ((key, title, icon) in listOf(
                Triple("ridelogs", RideLogText.TITLE.get(lang), WeemIcons.History), Triple("dataset", DatasetText.TITLE.get(lang), WeemIcons.Analytics),
                Triple("tasks", TaskText.TITLE.get(lang), WeemIcons.TaskAlt), Triple("reminders", ReminderText.SECTION.get(lang), WeemIcons.Alarm),
            )) ListRow(title, Modifier.testTag("open-$key"), icon = icon, chevron = true, onClick = { sub = key })
            ListRow(ApiText.OPEN.get(lang), Modifier.testTag("open-api-settings"), icon = WeemIcons.Key, chevron = true, onClick = { api = true })
        }
    }
}
