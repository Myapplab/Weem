package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang

/**
 * Harita ayarları → "Hava durumu" bölümü (MAP.md 10.9): "Günde bir hava bildirimi" (M-S19), bilgi düğmesi (`weather-info`: pencere "Son bilinen konuma göre; konum yoksa Bakı. …"),
 * "Haritada hava şeridini göster" anahtarı (`weather-strip-toggle`, varsayılan açık), "8 saatlik tahmini göster". İskelet (A6.2b): gövde A6.7'de.
 */
@Composable
fun MapWeatherSettings(stripOn: Boolean, onStripChange: (Boolean) -> Unit, lang: Lang, modifier: Modifier = Modifier) {
    TODO("A6.7")
}

/** Harita ayarları ekranı (6.9): bölümler "Hava durumu", "Yollarım", … Rawstats bilgileri bölümü yok (WEEM). */
@Composable
fun MapSettingsScreen(lang: Lang, weatherStrip: Boolean, onWeatherStripChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    TODO("A6.7")
}
