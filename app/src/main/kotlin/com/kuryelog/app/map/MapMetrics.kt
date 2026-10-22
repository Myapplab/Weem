package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.FitText
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.Segmented
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.mark
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.nav.RouteMeasure
import com.kuryelog.logic.map.ride.BatteryState
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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sol gösterge sütununun girdisi (YolGo `ModePager`; MAP.md 7.11, 10.5): [plan] varsa rota süresi (`metric-time`), canlı ölçüm [measure] rotadaysa "Durağa kalan"
 * (`metric-remaining`, `x.x km`, alt yazı "yürüme dahil"), rota kaydı ortalaması [avgKmh] (`metric-avg`), batarya kutusu (`metric-battery`, yalnız rota varken), hız kutusu
 * (`speed-popup`, yalnız seçenek varken). [speedChoices] hız penceresinin üç seyir hızı (`MapSettings.speedChoices`); [lowBatteryPct] canlı batarya rengi eşiği.
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

private fun fmtMin(seconds: Double) = if (seconds < 60) "<1" else (seconds / 60).roundToInt().toString()

/** 1.000 m altı "N m", değilse "X.X km". */
internal fun fmtKm(m: Double) = if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.US, "%.1f km", m / 1000)

private fun clockOf(nowMs: Long, seconds: Double): String {
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(if (nowMs > 0) nowMs else System.currentTimeMillis()), ZoneId.systemDefault()).plusSeconds(Math.round(seconds))
    return String.format(Locale.US, "%02d:%02d", at.hour, at.minute)
}


/**
 * Sol gösterge sütunu (`vertical-metrics`, genişlik `HudLayout.metricsWidthDp`): rota süresine dokununca açılan panel `"Varış HH:mm · <toplam sürüş km>[ · Kalan x.x km] · ≈W Wh · ↑m · ↓m"`
 * (rota dışında toplam kalır, kalan gizlenir); batarya kutusuna dokununca `scooter-sheet` ("Scooter · canlı"; canlıysa `310 W` gibi değerler); hız kutusuna `SpeedSheet`.
 * Dil değişince açık panel kapanır. [onRoadDetails] verilirse panelde "Yol ayrıntıları" (Yollarım, A6.11).
 */
@Composable
fun MapMetrics(
    state: MapMetricsState,
    lang: Lang,
    modifier: Modifier = Modifier,
    onMode: (RideMode) -> Unit = {},
    onSpeedChoices: (List<Double>) -> Unit = {},
    onRoadDetails: (() -> Unit)? = null,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val width = HudLayout.metricsWidthDp(screenWidth.toDouble()).dp
    val big = width >= 84.dp
    var open by remember(lang) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val plan = state.plan
    val battery = state.battery
    val pct = battery.percent
    val remainingKm = state.measure?.takeIf { plan != null && it.onRoute }?.let { String.format(Locale.US, "%.1f km", it.remainingM / 1000) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        // En az 1 dp: boş sütunun sınırı sıfıra kırpılıp genişliği kaybolmasın (testler genişliği ölçer).
        Column(Modifier.width(if (open) 250.dp else width).heightIn(min = 1.dp).testTag("vertical-metrics")) {
            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Batarya kutusu yalnız rota varken (YolGo SORU-3); tahmin hâli renkte %50 eşiğini göstermez.
                if (plan != null) {
                    // Batarya tonu: canlı değilken nötr (tahmin), eşik altında uyarı, değilse tamam; altta doluluk çubuğu.
                    val tone = when {
                        !battery.live -> WTone.NEUTRAL
                        pct != null && pct <= state.lowBatteryPct -> WTone.WARN
                        else -> WTone.POSITIVE
                    }
                    Metric(
                        "metric-battery", RideText.BATTERY.get(lang), pct?.let { "${it.roundToInt()}%" } ?: "—",
                        if (tone == WTone.WARN) WeemIcons.BatteryAlertFilled else WeemIcons.Battery5BarFilled, tone, big,
                        sub = battery.liveExternalPercent?.let { "+${it.roundToInt()}%" } ?: if (battery.live) null else RideText.BATTERY_ESTIMATE.get(lang),
                        fill = pct?.let { (it / 100).toFloat().coerceIn(0f, 1f) },
                    ) { sheet = Sheet.SCOOTER }
                    Metric("metric-time", RideText.ROUTE_TIME.get(lang), "${fmtMin(plan.timeS)} ${MapText.MIN.get(lang)}", WeemIcons.Schedule, WTone.ACCENT, big, sub = clockOf(state.nowMs, plan.timeS)) { open = !open }
                    if (remainingKm != null) {
                        Metric("metric-remaining", RideText.REMAINING.get(lang), remainingKm, WeemIcons.Route, WTone.ACCENT, big, sub = RideText.REMAINING_SUB.get(lang), showTitle = true) { open = !open }
                    }
                    state.avgKmh?.let { avg ->
                        Metric("metric-avg", RideText.AVG_TITLE.get(lang), "${avg.roundToInt()}", WeemIcons.Speed, WTone.ACCENT, big, sub = RideText.AVG_SUB.get(lang)) { open = !open }
                    }
                }
                if (state.speeds.isNotEmpty()) SpeedBox(state.speeds, lang, big) { sheet = Sheet.SPEED }
            }
            if (open && plan != null) {
                val panelShape = RoundedCornerShape(WeemRadius.card)
                Column(
                    Modifier.padding(top = 6.dp).fillMaxWidth().floating(panelShape).background(MaterialTheme.colorScheme.surface, panelShape).padding(WeemSpace.m),
                    verticalArrangement = Arrangement.spacedBy(WeemSpace.s),
                ) {
                    Segmented(
                        RideMode.entries.map { it.label.get(lang) }, RideMode.entries.indexOf(state.mode), { onMode(RideMode.entries[it]) },
                        tags = RideMode.entries.map { "mode-${it.name.lowercase()}" },
                    )
                    val remainingLabel = remainingKm?.let { " · ${RideText.REMAINING_SHORT.get(lang)} $it" } ?: ""
                    Text(
                        "${RideText.ARRIVAL.get(lang)} ${clockOf(state.nowMs, plan.timeS)} · ${fmtKm(plan.rideM)}$remainingLabel · ≈${String.format(Locale.US, "%.1f", plan.energyWh)} Wh · ↑${plan.climbM.roundToInt()} m · ↓${plan.descentM.roundToInt()} m\n" +
                            "${RideText.WALK.get(lang)}: ${plan.walkM.roundToInt()} m · ${plan.legs.size} ${RideText.STOP.get(lang)}\n" +
                            "${RideText.WRONG_WAY.get(lang)}: ${plan.wrongWayM.roundToInt()} m · ${RideText.RISK.get(lang)} %${(plan.wrongWayRisk * 100).roundToInt()}",
                        style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(RideText.SURFACE_NONE.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    onRoadDetails?.let { WButton(RideText.ROAD_DETAILS.get(lang), it, kind = BtnKind.TEXT, icon = WeemIcons.Route) }
                }
            }
        }
    }
    when (sheet) {
        Sheet.SCOOTER -> WSheet(onDismiss = { sheet = null }) { ScooterValuesSheet(state.scooter, lang) }
        Sheet.SPEED -> WSheet(onDismiss = { sheet = null }) { SpeedSheet(state.speedChoices, state.speeds, battery.capacityWh, lang, onSpeedChoices) }
        null -> {}
    }
}

private enum class Sheet { SCOOTER, SPEED }

/**
 * Gösterge karosu (`docs/TASARIM.md` 11 `MetricTile`): `surface`, köşe 16, `e1`; ton simgesi + değer (`numM`, sığmazsa küçülür), altında alt yazı
 * (`labelS`); [showTitle] iken başlık üstte; [fill] verilirse altta 4 dp doluluk çubuğu (batarya). Dokunma tüm karoda.
 */
@Composable
private fun Metric(
    tag: String,
    label: String,
    value: String,
    icon: ImageVector,
    tone: WTone,
    big: Boolean,
    sub: String? = null,
    showTitle: Boolean = false,
    fill: Float? = null,
    onClick: () -> Unit,
) {
    val p = palette()
    val shape = RoundedCornerShape(WeemRadius.card)
    Box(Modifier.testTag(tag)) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = p.surface.color(),
            contentColor = p.text.color(),
            modifier = Modifier.fillMaxWidth().floating(shape).semantics { contentDescription = "$label: $value${sub?.let { " · $it" } ?: ""}" },
        ) {
            Box {
                Column(Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = if (fill != null) 11.dp else 8.dp)) {
                    if (showTitle) Text(label, style = WeemType.labelS, color = p.muted.color(), maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!showTitle) Icon(icon, contentDescription = null, tint = p.mark(tone).color(), modifier = Modifier.size(if (big) 16.dp else 14.dp))
                        if (!showTitle) Spacer(Modifier.width(3.dp))
                        FitText(value, style = if (big) WeemType.numM else WeemType.numM.copy(fontSize = 19.sp, lineHeight = 22.sp), color = p.text.color())
                    }
                    if (sub != null) Text(sub, style = WeemType.labelS, color = p.muted.color(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                if (fill != null) Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(fill).height(4.dp).background((if (tone == WTone.NEUTRAL) p.muted else p.mark(tone)).color()),
                )
            }
        }
    }
}

/** Hız kutusu (`speed-popup`): her hız için `"<kmh> · <dk>dk"`; dokununca hız penceresi. */
@Composable
private fun SpeedBox(speeds: List<SpeedOption>, lang: Lang, big: Boolean, onClick: () -> Unit) {
    val p = palette()
    val shape = RoundedCornerShape(WeemRadius.card)
    Box(Modifier.testTag("speed-popup")) {
        Surface(
            onClick = onClick, shape = shape, color = p.surface.color(), contentColor = p.text.color(),
            modifier = Modifier.fillMaxWidth().floating(shape).semantics { contentDescription = RideText.SPEED_POPUP.get(lang) },
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(WeemIcons.Timer, contentDescription = null, tint = p.mark(WTone.ACCENT).color(), modifier = Modifier.size(16.dp))
                for (o in speeds) {
                    Text(
                        "${o.kmh.roundToInt()} · ${fmtMin(o.seconds)}${MapText.MIN.get(lang)}", style = (if (big) WeemType.labelM else WeemType.labelS).copy(fontFeatureSettings = "tnum"),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** "Farklı hızlarda rota" penceresi: her hız için satır (`speed-minus-i`, `speed-plus-i`, `"<kmh> km/s"`); ±1, 5–45 aralığı; sonuç [onChange] ile (`speedChoices`). */
@Composable
fun SpeedSheet(choices: List<Double>, options: List<SpeedOption>, capacityWh: Double, lang: Lang, onChange: (List<Double>) -> Unit, modifier: Modifier = Modifier) {
    val low = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
        Text(RideText.SPEED_SHEET_TITLE.get(lang), style = WeemType.titleL)
        Text(RideText.SPEED_SHEET_HINT.get(lang), style = WeemType.bodyS, color = low)
        choices.forEachIndexed { i, kmh ->
            val o = options.firstOrNull { it.kmh == kmh }
            fun set(v: Double) = onChange(choices.toMutableList().also { it[i] = v.coerceIn(5.0, 45.0) })
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.card)).padding(horizontal = WeemSpace.xs, vertical = WeemSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WIconButton(WeemIcons.Remove, "−1", { set(kmh - 1) }, Modifier.testTag("speed-minus-$i"))
                Text("${kmh.roundToInt()} ${RideText.KMH.get(lang)}", Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center, style = WeemType.numS)
                WIconButton(WeemIcons.Add, "+1", { set(kmh + 1) }, Modifier.testTag("speed-plus-$i"))
                Spacer(Modifier.width(WeemSpace.s))
                if (o == null) Text(RideText.NO_ROUTE.get(lang), style = WeemType.bodyS, color = low)
                else Column(Modifier.weight(1f)) {
                    Text("${fmtMin(o.seconds)} ${MapText.MIN.get(lang)} · ${RideText.ARRIVAL_LC.get(lang)} ${clockOf(0, o.seconds)}", style = WeemType.titleS)
                    Text(
                        String.format(Locale.US, "%.0f Wh", o.wh) + if (capacityWh > 0) String.format(Locale.US, " (%%%.1f)", o.wh / capacityWh * 100) else "",
                        style = WeemType.bodyS, color = low,
                    )
                }
            }
        }
    }
}

/** Batarya kutusuna dokununca canlı scooter değerleri (`scooter-sheet`; MAP.md 10.4): canlı değilken tek metin; değer yoksa "—". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScooterValuesSheet(live: ScooterLive?, lang: Lang, modifier: Modifier = Modifier) {
    fun v(n: Double?, unit: String, digits: Int = 0) = if (n == null) "—" else String.format(Locale.US, "%.${digits}f", n) + unit
    val s = live?.scooter
    val trip = live?.trip
    val fields = if (live == null) emptyList() else listOf(
        RideText.SCOOTER_INTERNAL to v(s?.internalPct, "%"),
        RideText.SCOOTER_EXTERNAL to v(s?.externalPct, "%"),
        RideText.SCOOTER_SPEED to v(s?.speedKmh, " ${RideText.KMH.get(lang)}", 1),
        RideText.SCOOTER_POWER to v(s?.powerW?.let { -it }, " W"),
        RideText.SCOOTER_RANGE to v(s?.rangeKm, " km"),
        RideText.SCOOTER_VOLTAGE to v(s?.voltageV, " V", 1),
        RideText.SCOOTER_TEMP to v(s?.tempC, "°"),
        RideText.SCOOTER_ODOMETER to v(s?.odometerKm, " km", 1),
        RideText.SCOOTER_TRIP to v(trip?.wh, " Wh", 1),
        RideText.SCOOTER_AVG to v(trip?.avgMovingKmh, " ${RideText.KMH.get(lang)}", 1),
    )
    val low = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().testTag("scooter-sheet"), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
            IconBox(WeemIcons.ElectricScooterFilled, tone = WTone.ACCENT)
            Text(RideText.SCOOTER_TITLE.get(lang), style = WeemType.titleL)
        }
        if (live == null) Text(RideText.SCOOTER_OFF.get(lang), style = WeemType.bodyM, color = low)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            for ((label, value) in fields) {
                Column(Modifier.width(150.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(WeemRadius.tile)).padding(horizontal = WeemSpace.m, vertical = WeemSpace.s)) {
                    Text(label.get(lang), style = WeemType.labelS, color = low)
                    Text(value, style = WeemType.numM)
                }
            }
        }
    }
}

/** Hız seçenekleri listesi: hız listesi `speedChoices` ile verilir; plan yok ya da nokta < 2 ise boş. */
fun speedOptionsFor(plan: Plan?, params: com.kuryelog.logic.map.route.RideParams, choices: List<Double>): List<SpeedOption> {
    val ride = plan?.legs?.flatMap { it.ride }.orEmpty()
    return if (ride.size < 2) emptyList() else SpeedOptions.speedOptions(ride, params, choices)
}
