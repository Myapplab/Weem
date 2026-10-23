package com.kuryelog.app.map

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.ReminderText
import com.kuryelog.logic.map.ui.RideLogText
import com.kuryelog.logic.map.ui.TaskText
import com.kuryelog.logic.map.ui.WeatherUiText
import com.kuryelog.logic.map.weather.WeatherReport

/**
 * Daha fazla → Yol bilgileri (S37): eski Harita ayarları → "Yollarım" (yol kalitesi öğrenme, ses tuşları, Yolları görüntüle) ve
 * Sürüş kayıtları bir arada.
 */
@Composable
fun RoadInfoScreen(lang: Lang, onBack: () -> Unit) {
    var sub by remember { mutableStateOf<String?>(null) }
    when (sub) {
        "roads" -> { RoadsHost(lang, onBack = { sub = null }); return }
        "ridelogs" -> { RideLogsHost(lang) { sub = null }; return }
    }
    SubScreen(Str.ROADINFO_TITLE.get(lang), Str.BACK.get(lang), onBack, spacing = 0.dp) {
        RoadsSettingsSection(lang, onOpen = { sub = "roads" })
        SettingsGroup(null, Modifier.padding(top = WeemSpace.xxl), dividerInset = 70.dp) {
            ListRow(RideLogText.TITLE.get(lang), Modifier.testTag("open-ridelogs"), icon = WeemIcons.History, chevron = true, onClick = { sub = "ridelogs" })
        }
    }
}

/**
 * Daha fazla → Hava ve günlük işler (S37): hava durumu ayarları (bildirim, haritadaki şerit, 8 saatlik tahmin; MAP.md 10.9), Günlük işler ve
 * Hatırlatıcılar tek ekranda (eskiden Harita ayarlarındaydı). Hava değerleri ve eylemleri çağıran bağlar (`WeatherRuntime`).
 */
@Composable
fun DailyToolsScreen(
    lang: Lang,
    weatherStrip: Boolean,
    onWeatherStripChange: (Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    weatherNotify: Boolean = true,
    onWeatherNotifyChange: (Boolean) -> Unit = {},
    onWeatherFetch: ((WeatherReport?) -> Unit) -> Unit = { it(null) },
) {
    var sub by remember { mutableStateOf<String?>(null) }
    when (sub) {
        "tasks" -> { TasksHost(lang) { sub = null }; return }
        "reminders" -> { ReminderSettingsHost(lang) { sub = null }; return }
    }
    SubScreen(Str.DAILY_TITLE.get(lang), Str.BACK.get(lang), onBack, spacing = 0.dp) {
        SettingsGroup(WeatherUiText.SECTION_TITLE.get(lang), divided = false) {
            MapWeatherSettings(weatherStrip, onWeatherStripChange, lang, notifyOn = weatherNotify, onNotifyChange = onWeatherNotifyChange, onFetch = onWeatherFetch)
        }
        SettingsGroup(null, Modifier.padding(top = WeemSpace.xxl), dividerInset = 70.dp) {
            ListRow(TaskText.TITLE.get(lang), Modifier.testTag("open-tasks"), icon = WeemIcons.TaskAlt, chevron = true, onClick = { sub = "tasks" })
            ListRow(ReminderText.SECTION.get(lang), Modifier.testTag("open-reminders"), icon = WeemIcons.Alarm, chevron = true, onClick = { sub = "reminders" })
        }
    }
}
