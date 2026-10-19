package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.nav.RouteMeasure
import com.kuryelog.logic.map.ride.BatteryState
import com.kuryelog.logic.map.ride.SpeedOption
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode

/**
 * Sol gösterge sütununun girdisi (YolGo `ModePager`; MAP.md 7.11, 10.5): [plan] varsa rota süresi (`metric-time`), canlı ölçüm [measure] rotadaysa "Durağa kalan"
 * (`metric-remaining`, `x.x km`, alt yazı "yürüme dahil"), rota kaydı ortalaması [avgKmh] (`metric-avg`), batarya kutusu (`metric-battery`, yalnız rota varken), hız kutusu
 * (`speed-popup`, yalnız seçenek varken).
 */
data class MapMetricsState(
    val plan: Plan? = null,
    val mode: RideMode = RideMode.TIME,
    val measure: RouteMeasure? = null,
    val battery: BatteryState = BatteryState(),
    val scooter: ScooterLive? = null,
    val speeds: List<SpeedOption> = emptyList(),
    val avgKmh: Double? = null,
    val nowMs: Long = 0,
) {
    val hasRoute: Boolean get() = plan != null
}

/**
 * Sol gösterge sütunu (`vertical-metrics`, genişlik `HudLayout.metricsWidthDp`): rota süresine dokununca açılan panel `"<toplam km> · Kalan x.x km"` (rota dışında toplam kalır,
 * kalan gizlenir); batarya kutusuna dokununca `scooter-sheet` ("Scooter · canlı"; canlıysa `310 W` gibi değerler). TR/AZ metinler. İskelet (A6.2b): gövde A6.5/A6.7'de.
 */
@Composable
fun MapMetrics(state: MapMetricsState, lang: Lang, modifier: Modifier = Modifier) {
    TODO("A6.7")
}

/** "Farklı hızlarda rota" penceresi: her hız için satır (`speed-minus-i`, `speed-plus-i`, `"<kmh> km/s"`); ±1, 5–45 aralığı; sonuç [onChange] ile (`speedChoices`). */
@Composable
fun SpeedSheet(choices: List<Double>, options: List<SpeedOption>, capacityWh: Double, lang: Lang, onChange: (List<Double>) -> Unit, modifier: Modifier = Modifier) {
    TODO("A6.7")
}
