package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.nav.RouteMeasure
import com.kuryelog.logic.map.ride.BatteryState
import com.kuryelog.logic.map.ride.RideFormat
import com.kuryelog.logic.map.ride.SpeedOption
import com.kuryelog.logic.map.ride.SpeedOptions
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.HudLayout
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.ui.RideText
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sol gösterge sütununun girdisi (YolGo `ModePager`; MAP.md 7.11, 10.5): [plan] varsa rota süresi (`metric-time`), canlı ölçüm [measure] rotadaysa "Durağa kalan"
 * (`metric-remaining`, `x.x km`, alt yazı "yürüme dahil"), rota kaydı ortalaması [avgKmh] (`metric-avg`), batarya kutusu (`metric-battery`, yalnız rota varken), hız kutusu
 * (`speed-popup`, yalnız seçenek varken). [speedChoices] hız penceresinin üç hızı; [lowBatteryPct] canlı batarya kutusunun turuncu eşiği.
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
    val speedChoices: List<Double> = MapSettings.DEFAULT_SPEED_CHOICES,
    val lowBatteryPct: Double = 50.0,
) {
    val hasRoute: Boolean get() = plan != null
}

private val INK = Color(0xFF17283A)
private val INK_SOFT = Color(0xFF3D5266)

private class Scene(val top: Color, val bottom: Color, val icon: Color)

private val GREY_SCENE = Scene(Color(0xFFE3EAF2), Color(0xFFB9C7D6), Color(0xFF3D5266))
private val ORANGE_SCENE = Scene(Color(0xFFFFE1C2), Color(0xFFFFB27A), Color(0xFFB4570F))
private val GREEN_SCENE = Scene(Color(0xFFDDF3E6), Color(0xFFA6DDBD), Color(0xFF1C7A47))

private fun localTime(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

/**
 * Sol gösterge sütunu (`vertical-metrics`, genişlik `HudLayout.metricsWidthDp`): rota süresine dokununca açılan panel `"<toplam km> · Kalan x.x km"` (rota dışında toplam kalır,
 * kalan gizlenir); batarya kutusuna dokununca `scooter-sheet` ("Scooter · canlı"; canlıysa `310 W` gibi değerler). TR/AZ metinler. Yüzey kutusu (Y13) sonra.
 * [onSpeedChoices] hız penceresindeki − / + ile değişen üç hızı kaydeder; [onMode] verilirse panelde BATARYA/ZAMAN seçici görünür (A6.6 bağlar).
 */
@Composable
fun MapMetrics(
    state: MapMetricsState,
    lang: Lang,
    modifier: Modifier = Modifier,
    onSpeedChoices: (List<Double>) -> Unit = {},
    onMode: ((RideMode) -> Unit)? = null,
) {
    val screenDp = LocalConfiguration.current.screenWidthDp.toDouble()
    val width = HudLayout.metricsWidthDp(screenDp)
    val big = width >= 84
    // Dil değişince panel kapanır (yeniden kurulum).
    var open by remember(lang) { mutableStateOf(false) }
    var scooterOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    val plan = state.plan
    val remainingKm = state.measure?.takeIf { it.onRoute }?.let { RideFormat.km(it.remainingM) }
    val battery = state.battery
    val pct = battery.percent
    val ext = battery.liveExternalPercent
    val toggle = { open = !open }
    val now = localTime(state.nowMs)
    // En az 1 dp yükseklik: boş sütun da ölçülebilsin (yerleşim denemeleri).
    Column(modifier.testTag("vertical-metrics").width((if (open) 250.0 else width).dp).heightIn(min = 1.dp)) {
        Column(Modifier.width(width.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Ekim planı madde 4 (SORU-3): batarya kutusu, canlı gelse de, yalnız rota varken.
            if (plan != null) {
                val scene = when {
                    !battery.live -> GREY_SCENE
                    pct != null && pct <= state.lowBatteryPct -> ORANGE_SCENE
                    else -> GREEN_SCENE
                }
                Metric(
                    "metric-battery", "🔋", RideText.BATTERY.get(lang), pct?.let { "${Math.round(it)}%" } ?: "—", scene.icon, { scooterOpen = true }, big,
                    sub = if (ext != null) "+${Math.round(ext)}%" else if (battery.live) null else RideText.BATTERY_ESTIMATE.get(lang), scene = scene,
                )
                Metric(
                    "metric-time", "⏱", RideText.ROUTE_TIME.get(lang), "${RideFormat.minutes(plan.timeS)} ${MapText.MIN.get(lang)}", MaterialTheme.colorScheme.primary, toggle, big,
                    sub = SpeedOptions.arrivalClock(now, plan.timeS),
                )
                if (remainingKm != null) {
                    Metric(
                        "metric-remaining", "⇢", RideText.REMAINING.get(lang), remainingKm, MaterialTheme.colorScheme.primary, toggle, big,
                        sub = RideText.REMAINING_SUB.get(lang), showTitle = true,
                    )
                }
                state.avgKmh?.let { avg ->
                    Metric("metric-avg", "⏲", RideText.AVG_SPEED.get(lang), "${Math.round(avg)}", MaterialTheme.colorScheme.tertiary, toggle, big, sub = RideText.AVG_UNIT.get(lang))
                }
            }
            if (state.speeds.isNotEmpty()) {
                Box(Modifier.testTag("speed-popup")) {
                    Surface(
                        onClick = { speedOpen = true },
                        shape = METRIC_SHAPE,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
                        shadowElevation = 1.dp,
                        modifier = Modifier.semantics { contentDescription = RideText.SPEED_POPUP.get(lang) },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text("⏱", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            for (o in state.speeds) {
                                Text(
                                    "${Math.round(o.kmh)} · ${RideFormat.minutes(o.seconds)}${MapText.MIN.get(lang)}", maxLines = 1, softWrap = false,
                                    fontSize = if (big) 13.sp else 12.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (open && plan != null) {
            RoutePanel(state, lang, plan, now, remainingKm, onMode)
        }
    }
    if (scooterOpen) {
        Dialog(onDismissRequest = { scooterOpen = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) { ScooterValuesSheet(state.scooter, lang) }
        }
    }
    if (speedOpen) {
        Dialog(onDismissRequest = { speedOpen = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                SpeedSheet(state.speedChoices, state.speeds, battery.capacityWh, lang, onChange = onSpeedChoices, nowMs = state.nowMs)
            }
        }
    }
}

private val METRIC_SHAPE = RoundedCornerShape(20.dp)

@Composable
private fun Metric(
    tag: String,
    icon: String,
    label: String,
    value: String,
    iconColor: Color,
    onClick: () -> Unit,
    big: Boolean,
    sub: String? = null,
    scene: Scene? = null,
    showTitle: Boolean = false,
) {
    // Etiket dış kutuda: tıklanabilir yüzey alt metinleri tek anlamda birleştirir (testler yüzeyin içindeki metinleri etikete göre arar).
    Box(Modifier.testTag(tag)) {
        val content: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, fontSize = if (big) 18.sp else 16.sp, color = iconColor, lineHeight = 20.sp)
                if (showTitle) {
                    Text(
                        label, maxLines = 2, textAlign = TextAlign.Center, fontSize = if (big) 12.sp else 11.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp, color = scene?.let { INK_SOFT } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    value, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, fontSize = if (big) 19.sp else 17.sp, fontWeight = FontWeight.ExtraBold,
                    lineHeight = 22.sp, color = if (scene != null) INK else MaterialTheme.colorScheme.onSurface,
                )
                if (sub != null) {
                    Text(
                        sub, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, fontSize = if (big) 13.sp else 12.sp, fontWeight = FontWeight.SemiBold,
                        lineHeight = 15.sp, color = if (scene != null) INK_SOFT else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Surface(
            onClick = onClick,
            shape = METRIC_SHAPE,
            color = scene?.bottom ?: MaterialTheme.colorScheme.surface.copy(alpha = .9f),
            shadowElevation = if (scene == null) 1.dp else 2.dp,
            modifier = Modifier.semantics { contentDescription = "$label: $value${if (sub == null) "" else " · $sub"}" },
        ) {
            if (scene == null) content()
            else Box(Modifier.background(Brush.linearGradient(listOf(scene.top, scene.bottom)))) { content() }
        }
    }
}

/** Açılan rota paneli (`ModePager`): isteğe bağlı mod seçici, varış · toplam · kalan · enerji · tırmanış, yürüme ve ters yön satırları. */
@Composable
private fun RoutePanel(state: MapMetricsState, lang: Lang, plan: Plan, now: LocalDateTime, remainingKm: String?, onMode: ((RideMode) -> Unit)?) {
    val remainingLabel = if (remainingKm == null) "" else " · ${RideText.REMAINING_SHORT.get(lang)} $remainingKm"
    val text = "${RideText.ROUTE_ARRIVAL.get(lang)} ${SpeedOptions.arrivalClock(now, plan.timeS)} · ${RideFormat.distance(plan.rideM)}$remainingLabel · ≈${
        java.lang.String.format(java.util.Locale.US, "%.1f", plan.energyWh)
    } Wh · ↑${Math.round(plan.climbM)} m · ↓${Math.round(plan.descentM)} m\n" +
        "${RideText.ROUTE_WALK.get(lang)}: ${Math.round(plan.walkM)} m · ${plan.legs.size} ${RideText.ROUTE_STOPS.get(lang)}\n" +
        "${RideText.ROUTE_WRONG_WAY.get(lang)}: ${Math.round(plan.wrongWayM)} m · ${RideText.ROUTE_RISK.get(lang)} ${RideFormat.percent(plan.wrongWayRisk)}"
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp,
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth().testTag("route-panel"),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onMode != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (mode in RideMode.entries) {
                        val label = "${mode.icon} ${mode.label.get(lang)}"
                        if (mode == state.mode) Button(onClick = { onMode(mode) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, fontSize = 11.sp, maxLines = 1) }
                        else FilledTonalButton(onClick = { onMode(mode) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, fontSize = 11.sp, maxLines = 1) }
                    }
                }
            }
            Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Batarya kutusuna dokununca canlı scooter değerleri (`scooter-sheet`; MAP.md 10.4); değer yoksa tek metin: bağlı değil. */
@Composable
fun ScooterValuesSheet(live: ScooterLive?, lang: Lang, modifier: Modifier = Modifier) {
    val s = live?.scooter
    val trip = live?.trip
    val fields = if (live == null) emptyList() else listOf(
        RideText.SCOOTER_INTERNAL to RideFormat.value(s?.internalPct, "%"),
        RideText.SCOOTER_EXTERNAL to RideFormat.value(s?.externalPct, "%"),
        RideText.SCOOTER_SPEED to RideFormat.value(s?.speedKmh, " ${RideText.SPEED_UNIT.get(lang)}", 1),
        RideText.SCOOTER_POWER to RideFormat.value(s?.powerW?.let { -it }, " W"),
        RideText.SCOOTER_RANGE to RideFormat.value(s?.rangeKm, " km"),
        RideText.SCOOTER_VOLTAGE to RideFormat.value(s?.voltageV, " V", 1),
        RideText.SCOOTER_TEMP to RideFormat.value(s?.tempC, "°"),
        RideText.SCOOTER_DISTANCE to RideFormat.value(s?.odometerKm, " km", 1),
        RideText.SCOOTER_TRIP to RideFormat.value(trip?.wh, " Wh", 1),
        RideText.SCOOTER_AVG to RideFormat.value(trip?.avgMovingKmh, " ${RideText.SPEED_UNIT.get(lang)}", 1),
    )
    Column(modifier.testTag("scooter-sheet").padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(RideText.SCOOTER_TITLE.get(lang), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        if (live == null) {
            Text(RideText.SCOOTER_NOT_CONNECTED.get(lang), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, value) in fields) {
                    Column(Modifier.width(140.dp)) {
                        Text(label.get(lang), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * "Farklı hızlarda rota" penceresi: her hız için satır (`speed-minus-i`, `speed-plus-i`, `"<kmh> km/s"`); ±1, 5–45 aralığı; sonuç [onChange] ile (`speedChoices`).
 * [options] her hız için süre ve Wh (hıza göre eşlenir); rota yoksa satırda "Rota yok". Varış saati [nowMs] üstüne eklenir.
 */
@Composable
fun SpeedSheet(
    choices: List<Double>,
    options: List<SpeedOption>,
    capacityWh: Double,
    lang: Lang,
    onChange: (List<Double>) -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
) {
    val now = localTime(nowMs)
    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp).testTag("speed-sheet")) {
        Text(RideText.SPEED_TITLE.get(lang), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(RideText.SPEED_SUB.get(lang), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        choices.forEachIndexed { i, kmh ->
            val o = options.firstOrNull { it.kmh == kmh }
            fun set(v: Double) = onChange(choices.toMutableList().also { it[i] = v.coerceIn(5.0, 45.0) })
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { set(kmh - 1) }, modifier = Modifier.testTag("speed-minus-$i").widthIn(min = 40.dp), contentPadding = PaddingValues(0.dp)) { Text("−", fontSize = 22.sp) }
                Text("${Math.round(kmh)} ${RideText.SPEED_UNIT.get(lang)}", Modifier.width(62.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { set(kmh + 1) }, modifier = Modifier.testTag("speed-plus-$i").widthIn(min = 40.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 22.sp) }
                Spacer(Modifier.width(6.dp))
                if (o == null) {
                    Text(RideText.SPEED_NONE_ROUTE.get(lang), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column {
                        Text(
                            "${RideFormat.minutes(o.seconds)} ${MapText.MIN.get(lang)} · ${RideText.SPEED_ARRIVAL.get(lang)} ${SpeedOptions.arrivalClock(now, o.seconds)}",
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val whText = RideFormat.value(o.wh, " Wh") + if (capacityWh > 0) " (%${RideFormat.value(o.wh / capacityWh * 100, "", 1)})" else ""
                        Text(whText, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
