package com.kuryelog.app.map

import androidx.compose.foundation.layout.Column
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

/**
 * Harita ayarları → "Hava durumu" bölümü (MAP.md 10.9): "Günde bir hava bildirimi" (M-S19), bilgi düğmesi (`weather-info`: pencere "Son bilinen konuma göre; konum yoksa Bakı. …"),
 * "Haritada hava şeridini göster" anahtarı (`weather-strip-toggle`, varsayılan açık), "8 saatlik tahmini göster". İskelet (A6.2b): gövde A6.7'de.
 */
@Composable
fun MapWeatherSettings(stripOn: Boolean, onStripChange: (Boolean) -> Unit, lang: Lang, modifier: Modifier = Modifier) {
    TODO("A6.7")
}

/**
 * Harita ayarları ekranı (6.9): bölümler "Harita görünümü", "Harita verisi", Google Maps (A6.5), "Hava durumu" (A6.7), "Yollarım", … Rawstats bilgileri bölümü yok (WEEM).
 * Daha fazla → Harita ayarları; [onBack] varsa üstte geri düğmesi.
 */
@Composable
fun MapSettingsScreen(lang: Lang, weatherStrip: Boolean, onWeatherStripChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("←") }
            Text(tl(lang, "Harita ayarları", "Xəritə tənzimləmələri"), style = MaterialTheme.typography.headlineMedium)
        }
        MapBaseSection(lang)
        // A6.7: MapWeatherSettings(weatherStrip, onWeatherStripChange, lang) burada ("Hava durumu").
        MapDataSection(lang)
        MapsKeySection(lang)
    }
}
