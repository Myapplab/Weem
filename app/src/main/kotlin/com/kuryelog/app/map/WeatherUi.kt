package com.kuryelog.app.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.isDark
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.map.ui.MapPalette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.HudLayout
import com.kuryelog.logic.map.ui.WeatherUiText
import com.kuryelog.logic.map.weather.WeatherHour
import com.kuryelog.logic.map.weather.WeatherIcons
import com.kuryelog.logic.map.weather.WeatherKind
import com.kuryelog.logic.map.weather.WeatherReport
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Hava görünümü: [report] yoksa "Hava yükleniyor…"/"Hava verisi yok"; [failed] "Hava güncellenemedi" (önceki rapor kalır); [busy] istek sürüyor. */
data class WeatherView(val report: WeatherReport? = null, val failed: Boolean = false, val busy: Boolean = false)

private fun clock(at: java.time.LocalDateTime) = String.format(Locale.US, "%02d:%02d", at.hour, at.minute)

/** Bakü saati (UTC+4 sabit; `bakuTime`): raporun alt satırı. */
private fun bakuClock(ms: Long): String = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.ofHours(4)).let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }

private fun value(n: Double?, unit: String, digits: Int = 0) = if (n == null) "—" else String.format(Locale.US, "%.${digits}f", n) + unit

private fun Long.color() = Color(this)

/**
 * Harita üstündeki hava şeridi (YolGo `LiveWeatherChip`; MAP.md 10.9): kapalıyken `live-weather` 62 dp kare (`weather-compact`: şimdiki saat simgesi, sıcaklık "23°",
 * yağış ihtimali ≥%10 ise "40%"); dokununca 4 saatlik şerit ("Şimdi" + saatler, `weather-strip-i`); `weather-toggle` 8 saatlik listeyi alttan açılan sayfada açar (`weather-list`, `weather-hour-0…7`, `weather-refresh`; S37);
 * `weather-collapse` küçültür; bir saate dokununca ayrıntı ([WeatherDetailSheet]). [onRefresh] "Yenile" (zorla); liste açılınca rapor 8 saatten kısaysa [onRefresh] çağrılır.
 */
@Composable
fun WeatherChip(
    view: WeatherView,
    lang: Lang,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    expanded: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var stripLocal by remember { mutableStateOf(false) }
    val strip = expanded ?: stripLocal
    fun setStrip(v: Boolean) { stripLocal = v; onExpandedChange(v) }
    var open by remember { mutableStateOf(false) }
    var detail by remember { mutableIntStateOf(-1) }
    val report = view.report
    val hours = report?.hours.orEmpty()
    val first = hours.firstOrNull()
    val stale = report != null && nowMs - report.fetchedAtMs >= 40 * 60_000
    val p = palette()
    if (!strip) {
        // A10.4d (K4): arama hapının sağında hava hapı — hareketli simge, sıcaklık, yağış ihtimali ya da rüzgâr.
        Box(
            modifier.testTag("live-weather").size(HudLayout.WEATHER_COMPACT_DP.dp, HudLayout.LOCATE_BUTTON_DP.dp)
                .semantics { contentDescription = WeatherUiText.SECTION_TITLE.get(lang) + (first?.let { ": ${WeatherIcons.kind(it).get(lang)} ${value(it.temperature, "°")}" } ?: "") },
        ) {
            val more = first?.let { WeatherIcons.compactExtra(it) }
            Surface(
                onClick = { setStrip(true) }, shape = CircleShape, color = p.surface.color(), contentColor = p.text.color(),
                modifier = Modifier.fillMaxSize().floating(CircleShape).testTag("weather-compact"),
            ) {
                Row(Modifier.padding(start = WeemSpace.m, end = WeemSpace.s), verticalAlignment = Alignment.CenterVertically) {
                    val kind = first?.let { WeatherIcons.kind(it) }
                    if (kind == null) Icon(WeemIcons.PartlyCloudyDay, contentDescription = null, tint = p.muted.color(), modifier = Modifier.size(26.dp))
                    // S38 (pil): haritada sürekli duran hapta simge durağan; sonsuz animasyon harita açıkken ekranı saniyede 60–120 kez yeniden çizdiriyordu.
                    else WeatherMotion(kind, 28.dp, animate = false)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(if (first == null) (if (view.busy) "…" else "—") else value(first.temperature, "°"), style = WeemType.titleS.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
                        if (more != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (more.rain) WeemIcons.WaterDrop else WeemIcons.Air, contentDescription = null, tint = p.muted.color(), modifier = Modifier.size(11.dp))
                            Text(more.text, style = WeemType.labelS.copy(fontSize = 11.sp, lineHeight = 13.sp), color = p.muted.color(), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(WeemRadius.panel), color = p.surface.color(), contentColor = p.text.color(),
            modifier = modifier.fillMaxWidth().floating(RoundedCornerShape(WeemRadius.panel)).testTag("live-weather"),
        ) {
            Column {
                Row(Modifier.padding(start = WeemSpace.xs, end = WeemSpace.xs, top = WeemSpace.s, bottom = WeemSpace.s), verticalAlignment = Alignment.CenterVertically) {
                    WIconButton(WeemIcons.ChevronLeft, WeatherUiText.CLOSE.get(lang), { setStrip(false); open = false }, Modifier.testTag("weather-collapse"))
                    val shown = hours.take(WeatherReport.STRIP_HOURS)
                    if (shown.isEmpty()) {
                        Icon(WeemIcons.PartlyCloudyDay, contentDescription = null, tint = p.muted.color())
                        Text((if (view.busy) WeatherUiText.LOADING else WeatherUiText.NONE).get(lang), Modifier.weight(1f).padding(start = WeemSpace.s), style = WeemType.bodyM)
                    }
                    shown.forEachIndexed { i, h ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (i == 0) WeatherUiText.NOW.get(lang) else clock(h.at), style = WeemType.labelS, maxLines = 1,
                                color = (if (i == 0) p.accentText else p.muted).color(),
                            )
                            Spacer(Modifier.height(4.dp))
                            HourTile(h, 48.dp, "weather-strip-$i") { detail = i }
                            Spacer(Modifier.height(2.dp))
                            Text(value(h.rainChance, "%"), style = WeemType.labelS, maxLines = 1, color = p.muted.color())
                        }
                    }
                    WIconButton(
                        if (open) WeemIcons.ExpandLess else WeemIcons.ExpandMore, WeatherUiText.SHOW_EIGHT.get(lang),
                        {
                            open = !open
                            // Eski önbellek 8 saatten az saat taşıyabilir: liste açılınca tazelenir.
                            if (open && hours.size < WeatherReport.DETAIL_HOURS && !view.busy) onRefresh()
                        },
                        Modifier.testTag("weather-toggle"),
                    )
                }
            }
        }
        // S37 (23 Ekim): 8 saatlik liste haritanın üst panelinde kesiliyordu; alttan açılan sayfada, kendi içinde kaydırılır.
        if (open) WSheet(onDismiss = { open = false }, title = WeatherUiText.SHOW_EIGHT.get(lang), padded = false) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = WeemSpace.l).testTag("weather-list")) {
                hours.forEachIndexed { i, h ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { detail = i }.testTag("weather-hour-$i"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HourTile(h, 40.dp, null, modifier = Modifier.padding(vertical = 4.dp)) { detail = i }
                        Spacer(Modifier.width(WeemSpace.m))
                        Text(
                            "${if (i == 0) WeatherUiText.NOW.get(lang) else clock(h.at)} · ${WeatherIcons.kind(h).get(lang)}", Modifier.weight(1f), maxLines = 1,
                            style = if (i == 0) WeemType.titleS else WeemType.bodyM, overflow = TextOverflow.Ellipsis,
                        )
                        Text("${value(h.temperature, "°")} · ${value(h.rainChance, "%")}", style = WeemType.numS)
                        Icon(WeemIcons.ChevronRight, contentDescription = null, tint = p.muted.color(), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Text(
                    (if (stale || view.failed) WeatherUiText.STALE.get(lang) else "") +
                        (if (view.failed) WeatherUiText.FAILED.get(lang) else "Open-Meteo · ${report?.let { bakuClock(it.fetchedAtMs) } ?: "—"}"),
                    style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = WeemSpace.s),
                )
                WButton(
                    WeatherUiText.REFRESH.get(lang), onRefresh, Modifier.testTag("weather-refresh").align(Alignment.CenterHorizontally),
                    kind = BtnKind.TEXT, icon = WeemIcons.Refresh, enabled = !view.busy,
                )
            }
        }
    }
    if (detail >= 0 && report != null && detail < report.hours.size) {
        val screen = LocalConfiguration.current.screenHeightDp
        Dialog(onDismissRequest = { detail = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                // Örtü ayrı katman: tıklanabilir üst düğüm ayrıntıyı anlamsal olarak birleştirmesin.
                Box(Modifier.fillMaxSize().clickable { detail = -1 })
                // Klavye açıkken görünen yüksekliğin %40'ı (Dialog penceresi ekranı kaplar; IME payı ayrıca düşülür).
                val ime = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
                Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.ime)) {
                    WeatherDetailSheet(report, detail, availableHeightDp = max(0.0, screen - ime.value.toDouble()), lang = lang)
                }
            }
        }
    }
}

/** Saat karosu: `raised` zeminde hareketli hava simgesi + sıcaklık; [tag] dokunma düğmesinin etiketi. */
@Composable
private fun HourTile(hour: WeatherHour, size: Dp, tag: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = palette()
    Surface(
        onClick = onClick, shape = RoundedCornerShape(WeemRadius.tile), color = p.raised.color(), contentColor = p.text.color(),
        modifier = modifier.size(size).let { if (tag != null) it.testTag(tag) else it },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            WeatherMotion(WeatherIcons.kind(hour), size * .42f)
            Text(value(hour.temperature, "°"), maxLines = 1, style = WeemType.labelM.copy(fontWeight = FontWeight.ExtraBold))
        }
    }
}

/**
 * Saat ayrıntısı (`weather-detail`, `weather-detail-scroll`): [availableHeightDp] (klavye düşülmüş) yüksekliğin %40'ı; başlık "Şimdi|HH:mm · <açıklama>" + sıcaklık;
 * alanlar (Hissedilen, Yağış ihtimali, …) kaydırılır. İçerik tek anlamsal blok (ekran okuyucu bir kez okur). Fotoğraf arka planı sonra (kredi: `assets/baku/credits.json`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherDetailSheet(report: WeatherReport, index: Int, availableHeightDp: Double, lang: Lang, modifier: Modifier = Modifier) {
    val hour = report.hours[index.coerceIn(0, report.hours.lastIndex)]
    val kind = WeatherIcons.kind(hour)
    val fields = listOf(
        WeatherUiText.DETAIL_FEELS to value(hour.feelsLike, "°"),
        WeatherUiText.DETAIL_RAIN_CHANCE to value(hour.rainChance, "%"),
        WeatherUiText.DETAIL_RAIN to value(hour.rainMm, " mm", 1),
        WeatherUiText.DETAIL_WIND to value(hour.wind, " km/h"),
        WeatherUiText.DETAIL_GUST to value(hour.gust, " km/h"),
        WeatherUiText.DETAIL_WIND_DIR to value(hour.windDirection, "°"),
        WeatherUiText.DETAIL_HUMIDITY to value(hour.humidity, "%"),
        WeatherUiText.DETAIL_CLOUD to value(hour.cloud, "%"),
        WeatherUiText.DETAIL_VISIBILITY to value(hour.visibility?.let { it / 1000 }, " km", 1),
        WeatherUiText.DETAIL_PRESSURE to value(hour.pressure, " hPa"),
        null to value(hour.uv, "", 1),
        WeatherUiText.DETAIL_DEW to value(hour.dewpoint, "°"),
    )
    val p = palette()
    val ink = p.text.color()
    val inkLo = p.muted.color()
    Box(
        modifier.fillMaxWidth().height(availableHeightDp.times(.4).dp).testTag("weather-detail")
            // Üst köşeler yalnız arka planda yuvarlanır; asimetrik `clip` Robolectric'te dokunma isabetini bozar (içerik köşelere değmez).
            .background(p.surface.color(), RoundedCornerShape(topStart = WeemRadius.sheet, topEnd = WeemRadius.sheet)),
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = WeemSpace.m).size(32.dp, 4.dp).background(p.high.color(), CircleShape))
        Column(Modifier.fillMaxSize().testTag("weather-detail-scroll").verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherMotion(kind, 50.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${if (index == 0) WeatherUiText.NOW.get(lang) else clock(hour.at)} · ${kind.get(lang)}", Modifier.weight(1f),
                        color = ink, style = WeemType.titleM,
                    )
                    Text(value(hour.temperature, "°"), color = ink, style = WeemType.headline.copy(fontWeight = FontWeight.ExtraBold))
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((label, v) in fields) {
                        Column(Modifier.width(132.dp).background(p.raised.color(), RoundedCornerShape(WeemRadius.tile)).padding(horizontal = WeemSpace.m, vertical = WeemSpace.s)) {
                            Text(label?.get(lang) ?: "UV", style = WeemType.labelS, color = inkLo)
                            Text(v, color = ink, style = WeemType.numS)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Open-Meteo · ${WeatherUiText.FORECAST.get(lang)} · ${bakuClock(report.fetchedAtMs)}", color = inkLo, style = WeemType.bodyS)
            }
        }
    }
}

/**
 * Hareketli hava simgesi (`WeatherMotion`): güneş/ay ışınları döner, rüzgâr çizgileri sallanır, bulut kayar, yağmur/kar damlaları düşer. Testlerde animasyon durur.
 * [animate] false: ilk karede durur (S38: haritadaki hap; animasyon yalnız açılan listede ve ayrıntıda).
 */
@Composable
private fun WeatherMotion(kind: WeatherKind, size: Dp, animate: Boolean = true) {
    val phase = if (!animate) 0f
    else rememberInfiniteTransition(label = "weather").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    val ink = MapPalette.of(palette().isDark)
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val t = phase
        val stroke = max(1.5f, s / 18)
        fun line(c: Color, x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(x1, y1), Offset(x2, y2), stroke, StrokeCap.Round)
        when (kind) {
            WeatherKind.SUN, WeatherKind.NIGHT -> {
                val c = Color(if (kind == WeatherKind.SUN) ink.weatherSun else ink.weatherMoon)
                drawCircle(c, s * .2f, Offset(s / 2, s / 2), style = Stroke(stroke))
                for (i in 0 until 8) {
                    val a = i * PI / 4 + t * PI / 4
                    line(c, s / 2 + (cos(a) * s * .31).toFloat(), s / 2 + (sin(a) * s * .31).toFloat(), s / 2 + (cos(a) * s * .41).toFloat(), s / 2 + (sin(a) * s * .41).toFloat())
                }
            }
            WeatherKind.WIND -> {
                val c = Color(ink.weatherCloud)
                for (i in 0 until 3) {
                    val dx = (sin(t * PI * 2 + i) * s * .08).toFloat()
                    line(c, s * .13f + dx, s * (.3f + i * .2f), s * .83f + dx, s * (.3f + i * .2f))
                }
            }
            else -> {
                val c = Color(ink.weatherCloud)
                val dx = (sin(t * PI * 2) * s * .04).toFloat()
                drawCircle(c, s * .16f, Offset(s * .35f + dx, s * .4f), style = Stroke(stroke))
                drawCircle(c, s * .2f, Offset(s * .55f + dx, s * .34f), style = Stroke(stroke))
                drawRoundRect(c, Offset(s * .17f + dx, s * .4f), Size(s * .67f, s * .2f), androidx.compose.ui.geometry.CornerRadius(s * .1f), Stroke(stroke))
                if (kind == WeatherKind.RAIN || kind == WeatherKind.THUNDER || kind == WeatherKind.SNOW) {
                    val d = Color(ink.weatherRain)
                    for (i in 0 until 3) {
                        val y = s * (.67f + ((t + i * .2f) % 1f) * .2f)
                        line(d, s * (.3f + i * .2f), y, s * (.26f + i * .2f), y + s * .1f)
                    }
                }
            }
        }
    }
}
