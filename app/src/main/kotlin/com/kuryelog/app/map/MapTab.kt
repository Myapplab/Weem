package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.ui.CameraMode

/** Harita sekmesinin görünümü (MAP.md 6.1–6.12): gün, navigasyon, sol göstergeler, hava; [arrivalFocus] varış odağı (hedefe ≤100 m). */
data class MapTabState(
    val day: DayView,
    val nav: NavState = NavState(),
    val metrics: MapMetricsState = MapMetricsState(),
    val weather: WeatherView = WeatherView(),
    val weatherStrip: Boolean = true,
    val arrivalFocus: Boolean = false,
    val cameraMode: CameraMode = CameraMode.FOLLOW_HEADING,
    val nowMs: Long = 0,
)

/**
 * Harita sekmesi: harita yüzeyi + HUD (arama çubuğu `map-search`, hava şeridi, sol göstergeler, gün eylemleri, konum düğmesi, alt yer düğmesi); sekmede menü ve kayıt noktası
 * yok. Varış odağında yalnız harita, konum, bildirim şeridi (`toast-banner`), hatırlatıcı yuvası (`reminder-slot`), görev uyarıları (`task-alerts`), ters yön kartı ve alttaki
 * `arrival-address` kartı. Dışarı dokunuş açık gün kartını katlar. İskelet (A6.2b): gövde A6.5'te.
 */
@Composable
fun MapTab(state: MapTabState, lang: Lang, modifier: Modifier = Modifier, mapSurface: @Composable () -> Unit = {}, onHome: () -> Unit = {}, onStats: () -> Unit = {}, onEnd: () -> Unit = {}) {
    TODO("A6.5")
}
