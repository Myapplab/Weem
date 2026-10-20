package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.weather.WeatherReport

/** Hava görünümü: [report] yoksa "Hava yükleniyor…"/"Hava verisi yok"; [error] "Hava güncellenemedi". */
data class WeatherView(val report: WeatherReport? = null, val error: String? = null)

/**
 * Harita üstündeki hava şeridi (YolGo `LiveWeatherChip`; MAP.md 10.9): kapalıyken `live-weather` 62 dp yuvarlak (`weather-compact`: şimdiki saat simgesi, sıcaklık "23°",
 * yağış ihtimali ≥%10 ise "40%"); dokununca 4 saatlik şerit ("Şimdi" + saatler, `weather-strip-i`); `weather-toggle` 8 saatlik listeyi açar (`weather-hour-0…7`, `weather-refresh`);
 * `weather-collapse` küçültür; bir saate dokununca ayrıntı ([WeatherDetailSheet]). İskelet (A6.2b): gövde A6.7'de.
 */
@Composable
fun WeatherChip(view: WeatherView, lang: Lang, nowMs: Long, modifier: Modifier = Modifier, onRefresh: () -> Unit = {}) {
    TODO("A6.7")
}

/**
 * Saat ayrıntısı (`weather-detail`, `weather-detail-scroll`): ekranın %40'ı ([availableHeightDp] klavye düşülmüş yükseklik); başlık "Şimdi|HH:mm · <açıklama>" + sıcaklık;
 * alanlar (Hissedilen, Yağış ihtimali, …) kaydırılır.
 */
@Composable
fun WeatherDetailSheet(report: WeatherReport, index: Int, availableHeightDp: Double, lang: Lang, modifier: Modifier = Modifier) {
    TODO("A6.7")
}
