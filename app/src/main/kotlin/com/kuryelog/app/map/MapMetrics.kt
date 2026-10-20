package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.window.DialogProperties
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
import kotlin.math.max
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

private fun fmtKm(m: Double) = if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.US, "%.1f km", m / 1000)

private fun clockOf(nowMs: Long, seconds: Double): String {
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(if (nowMs > 0) nowMs else System.currentTimeMillis()), ZoneId.systemDefault()).plusSeconds(Math.round(seconds))
    return String.format(Locale.US, "%02d:%02d", at.hour, at.minute)
}

private val ink = Color(0xFF17283A)
private val inkLo = Color(0xFF3D5266)

/** Batarya kutusu renk sahnesi (`widgets.dart:169–176`): canlı değil gri, canlı ve ≤ eşik turuncu, üstü yeşil. Üst/alt degrade ve simge rengi. */
private class Scene(val top: Color, val bottom: Color, val icon: Color)

private val greyScene = Scene(Color(0xFFE3EAF2), Color(0xFFB9C7D6), Color(0xFF3D5266))
private val orangeScene = Scene(Color(0xFFFFE1C2), Color(0xFFFFB27A), Color(0xFFB4570F))
private val greenScene = Scene(Color(0xFFDDF3E6), Color(0xFFA6DDBD), Color(0xFF1C7A47))

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
        Column(Modifier.width(if (open) 250.dp else width).testTag("vertical-metrics")) {
            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Batarya kutusu yalnız rota varken (YolGo SORU-3); tahmin hâli renkte %50 eşiğini göstermez.
                if (plan != null) {
                    val scene = when {
                        !battery.live -> greyScene
                        pct != null && pct <= state.lowBatteryPct -> orangeScene
                        else -> greenScene
                    }
                    Metric(
                        "metric-battery", RideText.BATTERY.get(lang), pct?.let { "${it.roundToInt()}%" } ?: "—", if (battery.live) "🔋" else "🪫", big,
                        sub = battery.liveExternalPercent?.let { "+${it.roundToInt()}%" } ?: if (battery.live) null else RideText.BATTERY_ESTIMATE.get(lang),
                        scene = scene,
                    ) { sheet = Sheet.SCOOTER }
                    Metric("metric-time", RideText.ROUTE_TIME.get(lang), "${fmtMin(plan.timeS)} ${MapText.MIN.get(lang)}", "⏱", big, sub = clockOf(state.nowMs, plan.timeS)) { open = !open }
                    if (remainingKm != null) {
                        Metric("metric-remaining", RideText.REMAINING.get(lang), remainingKm, "🛣", big, sub = RideText.REMAINING_SUB.get(lang), showTitle = true) { open = !open }
                    }
                    state.avgKmh?.let { avg ->
                        Metric("metric-avg", RideText.AVG_TITLE.get(lang), "${avg.roundToInt()}", "⚡", big, sub = RideText.AVG_SUB.get(lang)) { open = !open }
                    }
                }
                if (state.speeds.isNotEmpty()) SpeedBox(state.speeds, lang, big) { sheet = Sheet.SPEED }
            }
            if (open && plan != null) {
                Column(
                    Modifier.padding(top = 4.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .95f), RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        RideMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = m == state.mode, onClick = { onMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, RideMode.entries.size),
                                modifier = Modifier.testTag("mode-${m.name.lowercase()}"),
                            ) { Text(m.label.get(lang), fontSize = 11.sp, maxLines = 1) }
                        }
                    }
                    val remainingLabel = remainingKm?.let { " · ${RideText.REMAINING_SHORT.get(lang)} $it" } ?: ""
                    Text(
                        "${RideText.ARRIVAL.get(lang)} ${clockOf(state.nowMs, plan.timeS)} · ${fmtKm(plan.rideM)}$remainingLabel · ≈${String.format(Locale.US, "%.1f", plan.energyWh)} Wh · ↑${plan.climbM.roundToInt()} m · ↓${plan.descentM.roundToInt()} m\n" +
                            "${RideText.WALK.get(lang)}: ${plan.walkM.roundToInt()} m · ${plan.legs.size} ${RideText.STOP.get(lang)}\n" +
                            "${RideText.WRONG_WAY.get(lang)}: ${plan.wrongWayM.roundToInt()} m · ${RideText.RISK.get(lang)} %${(plan.wrongWayRisk * 100).roundToInt()}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(RideText.SURFACE_NONE.get(lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    onRoadDetails?.let { TextButton(onClick = it) { Text(RideText.ROAD_DETAILS.get(lang)) } }
                }
            }
        }
    }
    when (sheet) {
        Sheet.SCOOTER -> BottomSheetHost(onDismiss = { sheet = null }) { ScooterValuesSheet(state.scooter, lang) }
        Sheet.SPEED -> BottomSheetHost(onDismiss = { sheet = null }) { SpeedSheet(state.speedChoices, state.speeds, battery.capacityWh, lang, onSpeedChoices) }
        null -> {}
    }
}

private enum class Sheet { SCOOTER, SPEED }

/** Tek gösterge kutusu: simge üstte, değer altta (alt yazı); [scene] verilirse degrade ve koyu yazı, yoksa yarı saydam panel. Dokunma tüm kutuda. */
@Composable
private fun Metric(
    tag: String,
    label: String,
    value: String,
    icon: String,
    big: Boolean,
    sub: String? = null,
    scene: Scene? = null,
    showTitle: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val textColor = if (scene == null) scheme.onSurface else ink
    val subColor = if (scene == null) scheme.onSurfaceVariant else inkLo
    Box(Modifier.testTag(tag)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = scene?.bottom ?: scheme.surface.copy(alpha = .9f),
            shadowElevation = if (scene == null) 1.dp else 2.dp,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: $value${sub?.let { " · $it" } ?: ""}" },
        ) {
            Column(
                Modifier.then(if (scene == null) Modifier else Modifier.background(Brush.linearGradient(listOf(scene.top, scene.bottom)))).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, fontSize = if (big) 16.sp else 14.sp, lineHeight = 18.sp, color = scene?.icon ?: scheme.primary)
                Spacer(Modifier.height(3.dp))
                if (showTitle) Text(label, fontSize = if (big) 12.sp else 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = subColor, textAlign = TextAlign.Center, maxLines = 2)
                Text(value, fontSize = if (big) 19.sp else 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                if (sub != null) Text(sub, fontSize = if (big) 12.sp else 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = subColor, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Hız kutusu (`speed-popup`): her hız için `"<kmh> · <dk>dk"`; dokununca hız penceresi. */
@Composable
private fun SpeedBox(speeds: List<SpeedOption>, lang: Lang, big: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.testTag("speed-popup")) {
        Surface(
            onClick = onClick, shape = RoundedCornerShape(20.dp), color = scheme.surface.copy(alpha = .9f), shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = RideText.SPEED_POPUP.get(lang) },
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("⏲", fontSize = 14.sp, color = scheme.primary)
                for (o in speeds) {
                    Text(
                        "${o.kmh.roundToInt()} · ${fmtMin(o.seconds)}${MapText.MIN.get(lang)}", fontSize = if (big) 13.sp else 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/**
 * Alt kenara oturan pencere (Dialog): dışına dokunmak ya da geri kapatır. Compose Material3 `ModalBottomSheet` yerine sade pencere: JVM denemelerinde kararlı, animasyonsuz.
 */
@Composable
internal fun BottomSheetHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
            ) { content() }
        }
    }
}

/** "Farklı hızlarda rota" penceresi: her hız için satır (`speed-minus-i`, `speed-plus-i`, `"<kmh> km/s"`); ±1, 5–45 aralığı; sonuç [onChange] ile (`speedChoices`). */
@Composable
fun SpeedSheet(choices: List<Double>, options: List<SpeedOption>, capacityWh: Double, lang: Lang, onChange: (List<Double>) -> Unit, modifier: Modifier = Modifier) {
    val low = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 12.dp)) {
        Text(RideText.SPEED_SHEET_TITLE.get(lang), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(RideText.SPEED_SHEET_HINT.get(lang), fontSize = 12.5.sp, color = low, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        choices.forEachIndexed { i, kmh ->
            val o = options.firstOrNull { it.kmh == kmh }
            fun set(v: Double) = onChange(choices.toMutableList().also { it[i] = v.coerceIn(5.0, 45.0) })
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { set(kmh - 1) }, modifier = Modifier.testTag("speed-minus-$i")) { Text("−") }
                Text("${kmh.roundToInt()} ${RideText.KMH.get(lang)}", Modifier.widthIn(min = 62.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { set(kmh + 1) }, modifier = Modifier.testTag("speed-plus-$i")) { Text("+") }
                Spacer(Modifier.width(6.dp))
                if (o == null) Text(RideText.NO_ROUTE.get(lang), fontSize = 12.5.sp, color = low)
                else Column {
                    Text("${fmtMin(o.seconds)} ${MapText.MIN.get(lang)} · ${RideText.ARRIVAL_LC.get(lang)} ${clockOf(0, o.seconds)}", fontWeight = FontWeight.Bold)
                    Text(
                        String.format(Locale.US, "%.0f Wh", o.wh) + if (capacityWh > 0) String.format(Locale.US, " (%%%.1f)", o.wh / capacityWh * 100) else "",
                        fontSize = 12.5.sp, color = low,
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
    Column(modifier.fillMaxWidth().padding(20.dp).testTag("scooter-sheet"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🛴  " + RideText.SCOOTER_TITLE.get(lang), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        if (live == null) Text(RideText.SCOOTER_OFF.get(lang), fontSize = 13.sp, color = low)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((label, value) in fields) {
                Column(Modifier.width(140.dp)) {
                    Text(label.get(lang), fontSize = 13.sp, color = low)
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
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
