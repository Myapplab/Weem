package com.kuryelog.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ride.RideFormat
import com.kuryelog.logic.map.ui.HudLayout
import com.kuryelog.logic.map.ui.RideText
import com.kuryelog.logic.map.weather.WeatherHour
import com.kuryelog.logic.map.weather.WeatherIcons
import com.kuryelog.logic.map.weather.WeatherKind
import com.kuryelog.logic.map.weather.WeatherReport
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Hava görünümü: [report] yoksa "Hava yükleniyor…"/"Hava verisi yok"; [error] "Hava güncellenemedi"; [busy] yenileme sürüyor. */
data class WeatherView(val report: WeatherReport? = null, val error: String? = null, val busy: Boolean = false)

private val INK = Color(0xFF17283A)
private val INK_SOFT = Color(0xFF506275)
private fun argb(v: Long) = Color(v)

private fun bakuClock(ms: Long): String = String.format(java.util.Locale.ROOT, "%tH:%<tM", Instant.ofEpochMilli(ms).atZone(ZoneOffset.ofHours(4)))
private fun clock(h: WeatherHour): String = String.format(java.util.Locale.ROOT, "%02d:%02d", h.at.hour, h.at.minute)

/** Tek paylaşılan 80 ms hareket saati (12,5 Hz; MAP.md 10.9 `WeatherMotion`): [animate] kapalıysa 0'da durur (testler ve "animasyonları kaldır"). */
@Composable
private fun rememberWeatherPhase(animate: Boolean): Float {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(80)
            phase = (phase + .025f) % 1f
        }
    }
    return phase
}

/**
 * Harita üstündeki hava şeridi (YolGo `LiveWeatherChip`; MAP.md 10.9): kapalıyken `live-weather` 62 dp yuvarlak (`weather-compact`: şimdiki saat simgesi, sıcaklık "23°",
 * yağış ihtimali ≥%10 ise "40%"); dokununca 4 saatlik şerit ("Şimdi" + saatler, `weather-strip-i`); `weather-toggle` 8 saatlik listeyi açar (`weather-hour-0…7`, `weather-refresh`);
 * `weather-collapse` küçültür; bir saate dokununca ayrıntı ([WeatherDetailSheet]). [animate] simge hareketini açar (uygulamada açık, testlerde kapalı).
 */
@Composable
fun WeatherChip(view: WeatherView, lang: Lang, nowMs: Long, modifier: Modifier = Modifier, onRefresh: () -> Unit = {}, animate: Boolean = false) {
    val report = view.report
    val hours = report?.hours ?: emptyList()
    val strip = hours.take(WeatherReport.STRIP_HOURS)
    var expanded by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableIntStateOf(-1) }
    val phase = rememberWeatherPhase(animate)
    val imeDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding().value
    val stale = report != null && report.stale(nowMs)
    val label = RideText.WEATHER_LABEL.get(lang)

    // Eski önbellek 8 saatten az saat taşıyabilir: liste açılınca tazelenir.
    fun toggleList() {
        listOpen = !listOpen
        if (listOpen && hours.size < WeatherReport.DETAIL_HOURS && !view.busy) onRefresh()
    }

    if (!expanded) {
        val now = hours.firstOrNull()
        Box(
            modifier.testTag("live-weather").semantics {
                contentDescription = label + (now?.let { ": ${WeatherIcons.description(it).get(lang)} ${RideFormat.value(it.temperature, "°")}" } ?: "")
            },
        ) {
            WeatherBubble(now, HudLayout.WEATHER_COMPACT_DP.dp, phase, extra = true, busy = view.busy, tag = "weather-compact", onClick = { expanded = true })
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth().testTag("live-weather"),
            shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .95f), shadowElevation = 3.dp,
        ) {
            Column {
                Row(Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { expanded = false; listOpen = false }, modifier = Modifier.testTag("weather-collapse").width(30.dp).height(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("‹", fontSize = 24.sp) }
                    if (strip.isEmpty()) {
                        Text(
                            if (view.busy) RideText.WEATHER_LOADING.get(lang) else RideText.WEATHER_NO_DATA.get(lang),
                            Modifier.weight(1f).padding(start = 6.dp), fontSize = 14.sp,
                        )
                    }
                    strip.forEachIndexed { i, h ->
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val cell = maxWidth
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (i == 0) RideText.WEATHER_NOW.get(lang) else clock(h), maxLines = 1, fontSize = 12.sp,
                                    fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(3.dp))
                                // Her saat kendi yuvarlağında; dokununca o saatin ayrıntısı açılır.
                                WeatherBubble(h, (cell.value - 4).coerceIn(36f, 48f).dp, phase, tag = "weather-strip-$i", onClick = { detail = i })
                                Spacer(Modifier.height(2.dp))
                                Text(RideFormat.value(h.rainChance, "%"), maxLines = 1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TextButton(
                        onClick = ::toggleList, modifier = Modifier.testTag("weather-toggle").width(30.dp).height(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text(if (listOpen) "⌃" else "⌄", fontSize = 18.sp) }
                }
                if (listOpen) {
                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                        hours.forEachIndexed { i, h ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("weather-hour-$i").clickable { detail = i },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.padding(vertical = 4.dp)) { WeatherBubble(h, 40.dp, phase, tag = null, onClick = null) }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${if (i == 0) RideText.WEATHER_NOW.get(lang) else clock(h)} · ${WeatherIcons.description(h).get(lang)}", Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                )
                                Text("${RideFormat.value(h.temperature, "°")} · ${RideFormat.value(h.rainChance, "%")}", fontSize = 14.sp)
                                Text("›", fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        Text(
                            (if (stale || view.error != null) RideText.WEATHER_OLD_FORECAST.get(lang) else "") +
                                (view.error ?: "Open-Meteo · ${report?.let { bakuClock(it.fetchedAtMs) } ?: "—"}"),
                            Modifier.fillMaxWidth(), fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRefresh, enabled = !view.busy, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("weather-refresh")) {
                            Text("⟳  " + RideText.WEATHER_REFRESH.get(lang))
                        }
                    }
                }
            }
        }
    }
    if (detail >= 0 && report != null && detail < hours.size) {
        val screenDp = LocalConfiguration.current.screenHeightDp.toDouble()
        Dialog(onDismissRequest = { detail = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                // Boşluğa dokununca kapanır; anlam ağacına dokunulmaz (yoksa ayrıntı düğümü birleşir).
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { detail = -1 } },
                contentAlignment = Alignment.BottomCenter,
            ) {
                WeatherDetailSheet(report, detail, availableHeightDp = max(0.0, screenDp - imeDp), lang = lang)
            }
        }
    }
}

/**
 * Havaya göre renkli yuvarlak köşeli kare (YolGo `WeatherBubble`): hareketli simge + sıcaklık (+ [extra]: yağış ya da rüzgâr). [onClick] null ise dokunulmaz (liste satırında
 * satırın kendisi tıklanır). [tag] dokunulan düğümün etiketi.
 */
@Composable
private fun WeatherBubble(
    hour: WeatherHour?,
    size: Dp,
    phase: Float,
    extra: Boolean = false,
    busy: Boolean = false,
    tag: String?,
    onClick: (() -> Unit)?,
) {
    val kind = hour?.let { WeatherIcons.kind(it) }
    val palette = kind?.let { WeatherIcons.palette(it) }
    val top = palette?.let { argb(it.first) } ?: MaterialTheme.colorScheme.surface
    val bottom = palette?.let { argb(it.second) } ?: MaterialTheme.colorScheme.surface
    val on = palette?.let { argb(WeatherIcons.onColor(it)) } ?: MaterialTheme.colorScheme.onSurface
    val more = if (hour != null && extra) WeatherIcons.compactExtra(hour) else null
    val shape = RoundedCornerShape(size * .3f)
    val base = Modifier.size(size).let { if (tag != null) it.testTag(tag) else it }
    Surface(
        shape = shape, color = bottom, shadowElevation = 2.dp,
        modifier = if (onClick != null) base.clip(shape).clickable(onClick = onClick) else base,
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(top, bottom)))
                .background(Brush.verticalGradient(colorStops = arrayOf(.55f to Color(0x00000000), 1f to Color(0x22000000)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                if (kind == null) Text("☁", fontSize = (size.value * .32f).sp, color = on)
                else WeatherGlyph(kind, phase, size * .36f)
                Text(
                    if (hour == null) (if (busy) "…" else "—") else RideFormat.value(hour.temperature, "°"), maxLines = 1,
                    fontSize = (size.value * .27f).sp, fontWeight = FontWeight.ExtraBold, lineHeight = (size.value * .29f).sp, color = on,
                )
                if (more != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (more.rain) "💧" else "≋", fontSize = 9.sp, color = on.copy(alpha = .85f))
                        Text(more.text, maxLines = 1, softWrap = false, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp, color = on.copy(alpha = .85f))
                    }
                }
            }
        }
    }
}

/** Hava simgesi (`_WeatherPainter`): güneş/ay dönen ışınlar, rüzgâr çizgileri, bulut ve yağış damlaları; [phase] 0–1. */
@Composable
private fun WeatherGlyph(kind: WeatherKind, phase: Float, size: Dp) {
    val stroke = max(1.5f, size.value / 18f)
    val density = LocalDensity.current
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val w = with(density) { stroke.dp.toPx() }
        val t = phase
        when (kind) {
            WeatherKind.SUN, WeatherKind.NIGHT -> {
                val c = if (kind == WeatherKind.SUN) Color(0xFFE8A927) else Color(0xFF7185B2)
                drawCircle(c, s * .2f, Offset(s / 2, s / 2), style = Stroke(w, cap = StrokeCap.Round))
                for (i in 0 until 8) {
                    val a = i * PI / 4 + t * PI / 4
                    drawLine(
                        c, Offset((s / 2 + cos(a) * s * .31).toFloat(), (s / 2 + sin(a) * s * .31).toFloat()),
                        Offset((s / 2 + cos(a) * s * .41).toFloat(), (s / 2 + sin(a) * s * .41).toFloat()), w, StrokeCap.Round,
                    )
                }
            }
            WeatherKind.WIND -> {
                val c = Color(0xFF5588C5)
                for (i in 0 until 3) {
                    val dx = (sin(t * PI * 2 + i) * s * .08).toFloat()
                    drawLine(c, Offset(s * .13f + dx, s * (.3f + i * .2f)), Offset(s * .83f + dx, s * (.3f + i * .2f)), w, StrokeCap.Round)
                }
            }
            else -> {
                val c = Color(0xFF5588C5)
                val dx = (sin(t * PI * 2) * s * .04).toFloat()
                drawCircle(c, s * .16f, Offset(s * .35f + dx, s * .4f), style = Stroke(w, cap = StrokeCap.Round))
                drawCircle(c, s * .2f, Offset(s * .55f + dx, s * .34f), style = Stroke(w, cap = StrokeCap.Round))
                drawRoundRect(c, Offset(s * .17f + dx, s * .4f), Size(s * .67f, s * .2f), CornerRadius(s * .1f), style = Stroke(w, cap = StrokeCap.Round))
                if (kind == WeatherKind.RAIN || kind == WeatherKind.THUNDER || kind == WeatherKind.SNOW) drops(s, t, w)
            }
        }
    }
}

private fun DrawScope.drops(s: Float, t: Float, w: Float) {
    val c = Color(0xFF2D77CA)
    for (i in 0 until 3) {
        val y = s * (.67f + ((t + i * .2f) % 1f) * .2f)
        drawLine(c, Offset(s * (.3f + i * .2f), y), Offset(s * (.26f + i * .2f), y + s * .1f), w, StrokeCap.Round)
    }
}

/**
 * Saat ayrıntısı (`weather-detail`, `weather-detail-scroll`): ekranın %40'ı ([availableHeightDp] klavye düşülmüş yükseklik); başlık "Şimdi|HH:mm · <açıklama>" + sıcaklık;
 * alanlar (Hissedilen, Yağış ihtimali, …) kaydırılır. Arka plan fotoğrafı yok (`assets/baku` WEEM'e girmedi, APK boyutu): YolGo'nun fotoğrafsız yedek rengi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherDetailSheet(report: WeatherReport, index: Int, availableHeightDp: Double, lang: Lang, modifier: Modifier = Modifier) {
    val hour = report.hours[index]
    val fields = listOf(
        RideText.WEATHER_FIELD_FEELS.get(lang) to RideFormat.value(hour.feelsLike, "°"),
        RideText.WEATHER_FIELD_RAIN_CHANCE.get(lang) to RideFormat.value(hour.rainChance, "%"),
        RideText.WEATHER_FIELD_RAIN.get(lang) to RideFormat.value(hour.rainMm, " mm", 1),
        RideText.WEATHER_FIELD_WIND.get(lang) to RideFormat.value(hour.wind, " km/h"),
        RideText.WEATHER_FIELD_GUST.get(lang) to RideFormat.value(hour.gust, " km/h"),
        RideText.WEATHER_FIELD_WIND_DIR.get(lang) to RideFormat.value(hour.windDirection, "°"),
        RideText.WEATHER_FIELD_HUMIDITY.get(lang) to RideFormat.value(hour.humidity, "%"),
        RideText.WEATHER_FIELD_CLOUD.get(lang) to RideFormat.value(hour.cloud, "%"),
        RideText.WEATHER_FIELD_VISIBILITY.get(lang) to RideFormat.value(hour.visibility?.let { it / 1000 }, " km", 1),
        RideText.WEATHER_FIELD_PRESSURE.get(lang) to RideFormat.value(hour.pressure, " hPa"),
        "UV" to RideFormat.value(hour.uv, "", 1),
        RideText.WEATHER_FIELD_DEW.get(lang) to RideFormat.value(hour.dewpoint, "°"),
    )
    Box(modifier.fillMaxWidth().height((availableHeightDp * .4).dp).testTag("weather-detail")) {
        // Köşe yuvarlama yalnız zeminde: kaydırılan sütunu kırpmak kenardan başlayan kaydırmayı keser.
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFFE5EFF5)).background(
                Brush.verticalGradient(colorStops = arrayOf(0f to Color(0x44FFFFFF), .35f to Color(0xEFFFFFFF), .65f to Color(0xFFFFFFFF))),
            ),
        )
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x8017283A)))
        Column(
            Modifier.fillMaxSize().testTag("weather-detail-scroll").verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(WeatherIcons.kind(hour), 0f, 50.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${if (index == 0) RideText.WEATHER_NOW.get(lang) else clock(hour)} · ${WeatherIcons.description(hour).get(lang)}", Modifier.weight(1f),
                    color = INK, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                )
                Text(RideFormat.value(hour.temperature, "°"), color = INK, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((name, value) in fields) {
                    Column(Modifier.width(132.dp)) {
                        Text(name, fontSize = 11.sp, color = INK_SOFT)
                        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = INK)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Open-Meteo · ${RideText.WEATHER_FORECAST.get(lang)} · ${bakuClock(report.fetchedAtMs)}", fontSize = 13.sp, color = INK)
        }
    }
}
