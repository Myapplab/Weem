package com.kuryelog.app.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import com.kuryelog.logic.map.input.ApiText
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.MapText

/**
 * Harita ayarları → "Hava durumu" bölümü (MAP.md 10.9): "Günde bir hava bildirimi" (M-S19), bilgi düğmesi (`weather-info`: pencere "Son bilinen konuma göre; konum yoksa Bakı. …"),
 * "Haritada hava şeridini göster" anahtarı (`weather-strip-toggle`, varsayılan açık), "8 saatlik tahmini göster". İskelet (A6.2b): gövde A6.7'de.
 */
@Composable
fun MapWeatherSettings(stripOn: Boolean, onStripChange: (Boolean) -> Unit, lang: Lang, modifier: Modifier = Modifier) {
    TODO("A6.7")
}

/**
 * Harita ayarları ekranı (6.9): bölümler "Harita görünümü", "Harita verisi", Google Maps (A6.5), "API ve bağlantılar" (A6.6, 8.9), "Hava durumu" (A6.7), "Yollarım", …
 * Rawstats bilgileri bölümü yok (WEEM).
 * Daha fazla → Harita ayarları; [onBack] varsa üstte geri düğmesi.
 */
@Composable
fun MapSettingsScreen(lang: Lang, weatherStrip: Boolean, onWeatherStripChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
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
        // A6.7: MapWeatherSettings(weatherStrip, onWeatherStripChange, lang) burada ("Hava durumu").
        MapDataSection(lang)
        MapsKeySection(lang)
        // A6.6: "API ve bağlantılar" (MAP.md 8.9).
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { api = true }.testTag("open-api-settings")) {
            Text(ApiText.OPEN.get(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
    }
}
