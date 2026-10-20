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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlin.math.roundToInt
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
 * yağış ihtimali ≥%10 ise "40%"); dokununca 4 saatlik şerit ("Şimdi" + saatler, `weather-strip-i`); `weather-toggle` 8 saatlik listeyi açar (`weather-hour-0…7`, `weather-refresh`);
 * `weather-collapse` küçültür; bir saate dokununca ayrıntı ([WeatherDetailSheet]). [onRefresh] "Yenile" (zorla); liste açılınca rapor 8 saatten kısaysa [onRefresh] çağrılır.
 */
@Composable
fun WeatherChip(view: WeatherView, lang: Lang, nowMs: Long, modifier: Modifier = Modifier, onRefresh: () -> Unit = {}) {
    var strip by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var detail by remember { mutableIntStateOf(-1) }
    val report = view.report
    val hours = report?.hours.orEmpty()
    val first = hours.firstOrNull()
    val stale = report != null && nowMs - report.fetchedAtMs >= 40 * 60_000
    if (!strip) {
        Box(
            modifier.testTag("live-weather").size(HudLayout.WEATHER_COMPACT_DP.dp)
                .semantics { contentDescription = WeatherUiText.SECTION_TITLE.get(lang) + (first?.let { ": ${WeatherIcons.kind(it).get(lang)} ${value(it.temperature, "°")}" } ?: "") },
        ) {
            WeatherBubble(first, HudLayout.WEATHER_COMPACT_DP.dp, "weather-compact", busy = view.busy, extra = true) { strip = true }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .95f), shadowElevation = 3.dp,
            modifier = modifier.fillMaxWidth().testTag("live-weather"),
        ) {
            Column {
                Row(Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { strip = false; open = false }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.width(30.dp).height(44.dp).testTag("weather-collapse"),
                    ) { Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val shown = hours.take(WeatherReport.STRIP_HOURS)
                    if (shown.isEmpty()) {
                        Text("☁", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Text((if (view.busy) WeatherUiText.LOADING else WeatherUiText.NONE).get(lang), Modifier.weight(1f).padding(start = 6.dp), fontSize = 14.sp)
                    }
                    shown.forEachIndexed { i, h ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (i == 0) WeatherUiText.NOW.get(lang) else clock(h.at), fontSize = 12.sp, maxLines = 1,
                                fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            WeatherBubble(h, 44.dp, "weather-strip-$i") { detail = i }
                            Spacer(Modifier.height(2.dp))
                            Text(value(h.rainChance, "%"), fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(
                        onClick = {
                            open = !open
                            // Eski önbellek 8 saatten az saat taşıyabilir: liste açılınca tazelenir.
                            if (open && hours.size < WeatherReport.DETAIL_HOURS && !view.busy) onRefresh()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.width(30.dp).height(44.dp).testTag("weather-toggle"),
                    ) { Text(if (open) "⌃" else "⌄", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (open) {
                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                        hours.forEachIndexed { i, h ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { detail = i }.testTag("weather-hour-$i"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WeatherBubble(h, 40.dp, null, modifier = Modifier.padding(vertical = 4.dp)) { detail = i }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${if (i == 0) WeatherUiText.NOW.get(lang) else clock(h.at)} · ${WeatherIcons.kind(h).get(lang)}", Modifier.weight(1f), maxLines = 1,
                                    fontSize = 14.sp, fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                )
                                Text("${value(h.temperature, "°")} · ${value(h.rainChance, "%")}", fontSize = 14.sp)
                                Text("›", fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        Text(
                            (if (stale || view.failed) WeatherUiText.STALE.get(lang) else "") +
                                (if (view.failed) WeatherUiText.FAILED.get(lang) else "Open-Meteo · ${report?.let { bakuClock(it.fetchedAtMs) } ?: "—"}"),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = onRefresh, enabled = !view.busy, modifier = Modifier.testTag("weather-refresh").align(Alignment.CenterHorizontally)) {
                            Text("↻  " + WeatherUiText.REFRESH.get(lang))
                        }
                    }
                }
            }
        }
    }
    if (detail >= 0 && report != null && detail < report.hours.size) {
        val screen = LocalConfiguration.current.screenHeightDp
        Dialog(onDismissRequest = { detail = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable { detail = -1 }.windowInsetsPadding(WindowInsets.ime), contentAlignment = Alignment.BottomCenter) {
                // Klavye açıkken görünen yüksekliğin %40'ı (Dialog penceresi ekranı kaplar; IME payı ayrıca düşülür).
                val ime = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
                WeatherDetailSheet(report, detail, availableHeightDp = max(0.0, screen - ime.value.toDouble()), lang = lang)
            }
        }
    }
}

/** Havaya göre degrade kare (köşeleri yuvarlak): hareketli simge + sıcaklık, [extra] ise yağış ihtimali/rüzgâr satırı. [tag] dokunma düğmesinin etiketi. */
@Composable
private fun WeatherBubble(hour: WeatherHour?, size: Dp, tag: String?, modifier: Modifier = Modifier, busy: Boolean = false, extra: Boolean = false, onClick: () -> Unit) {
    val kind = hour?.let { WeatherIcons.kind(it) }
    val top = kind?.top?.color() ?: MaterialTheme.colorScheme.surface
    val bottom = kind?.bottom?.color() ?: MaterialTheme.colorScheme.surface
    val on = kind?.onColor?.color() ?: MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(size * .3f)
    val more = hour?.takeIf { extra }?.let { WeatherIcons.compactExtra(it) }
    Surface(
        onClick = onClick, shape = shape, color = bottom, shadowElevation = 2.dp,
        modifier = modifier.size(size).let { if (tag != null) it.testTag(tag) else it },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(top, bottom))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (kind == null) Text("☁", fontSize = (size.value * .32f).sp, color = on) else WeatherMotion(kind, size * .36f)
                Text(
                    if (hour == null) (if (busy) "…" else "—") else value(hour.temperature, "°"), maxLines = 1, color = on,
                    fontSize = (size.value * .27f).sp, lineHeight = (size.value * .3f).sp, fontWeight = FontWeight.ExtraBold,
                )
                if (more != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (more.rain) "💧" else "≈", color = on.copy(alpha = .85f), fontSize = 9.sp, lineHeight = 11.sp)
                        Text(more.text, maxLines = 1, softWrap = false, color = on.copy(alpha = .85f), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
    val ink = Color(0xFF17283A)
    val inkLo = Color(0xFF506275)
    Box(
        modifier.fillMaxWidth().height(availableHeightDp.times(.4).dp).testTag("weather-detail").clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE5EFF5), Color.White))),
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(36.dp, 4.dp).background(Color(0x8017283A), RoundedCornerShape(2.dp)))
        Column(Modifier.fillMaxSize().testTag("weather-detail-scroll").verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherMotion(kind, 50.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${if (index == 0) WeatherUiText.NOW.get(lang) else clock(hour.at)} · ${kind.get(lang)}", Modifier.weight(1f),
                        color = ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    )
                    Text(value(hour.temperature, "°"), color = ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((label, v) in fields) {
                        Column(Modifier.width(132.dp)) {
                            Text(label?.get(lang) ?: "UV", fontSize = 11.sp, color = inkLo)
                            Text(v, color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Open-Meteo · ${WeatherUiText.FORECAST.get(lang)} · ${bakuClock(report.fetchedAtMs)}", color = ink, fontSize = 13.sp)
            }
        }
    }
}

/** Hareketli hava simgesi (`WeatherMotion`): güneş/ay ışınları döner, rüzgâr çizgileri sallanır, bulut kayar, yağmur/kar damlaları düşer. Testlerde animasyon durur. */
@Composable
private fun WeatherMotion(kind: WeatherKind, size: Dp) {
    val phase by rememberInfiniteTransition(label = "weather").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val t = phase
        val stroke = max(1.5f, s / 18)
        fun line(c: Color, x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(x1, y1), Offset(x2, y2), stroke, StrokeCap.Round)
        when (kind) {
            WeatherKind.SUN, WeatherKind.NIGHT -> {
                val c = if (kind == WeatherKind.SUN) Color(0xFFE8A927) else Color(0xFF7185B2)
                drawCircle(c, s * .2f, Offset(s / 2, s / 2), style = Stroke(stroke))
                for (i in 0 until 8) {
                    val a = i * PI / 4 + t * PI / 4
                    line(c, s / 2 + (cos(a) * s * .31).toFloat(), s / 2 + (sin(a) * s * .31).toFloat(), s / 2 + (cos(a) * s * .41).toFloat(), s / 2 + (sin(a) * s * .41).toFloat())
                }
            }
            WeatherKind.WIND -> {
                val c = Color(0xFF5588C5)
                for (i in 0 until 3) {
                    val dx = (sin(t * PI * 2 + i) * s * .08).toFloat()
                    line(c, s * .13f + dx, s * (.3f + i * .2f), s * .83f + dx, s * (.3f + i * .2f))
                }
            }
            else -> {
                val c = Color(0xFF5588C5)
                val dx = (sin(t * PI * 2) * s * .04).toFloat()
                drawCircle(c, s * .16f, Offset(s * .35f + dx, s * .4f), style = Stroke(stroke))
                drawCircle(c, s * .2f, Offset(s * .55f + dx, s * .34f), style = Stroke(stroke))
                drawRoundRect(c, Offset(s * .17f + dx, s * .4f), Size(s * .67f, s * .2f), androidx.compose.ui.geometry.CornerRadius(s * .1f), Stroke(stroke))
                if (kind == WeatherKind.RAIN || kind == WeatherKind.THUNDER || kind == WeatherKind.SNOW) {
                    val d = Color(0xFF2D77CA)
                    for (i in 0 until 3) {
                        val y = s * (.67f + ((t + i * .2f) % 1f) * .2f)
                        line(d, s * (.3f + i * .2f), y, s * (.26f + i * .2f), y + s * .1f)
                    }
                }
            }
        }
    }
}
