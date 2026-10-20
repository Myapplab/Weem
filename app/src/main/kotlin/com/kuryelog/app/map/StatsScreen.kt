package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.day.StopRec
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.stats.DayReasons
import com.kuryelog.logic.map.stats.DayStats
import com.kuryelog.logic.map.stats.DurationText
import com.kuryelog.logic.map.stats.LightStats
import com.kuryelog.logic.map.stats.StatsTabs
import com.kuryelog.logic.map.stats.StreetLabel
import com.kuryelog.logic.map.ui.BaseMap
import com.kuryelog.logic.map.ui.StatsText
import com.kuryelog.logic.core.LogTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** İstatistik ekranının dış bağları (testte sahte): sokak adı, gün sonu ışık cevabı, silme, paylaşma. */
class StatsActions(
    val streetName: (lat: Double, lon: Double, maxM: Double) -> String? = { _, _, _ -> null },
    val onReview: (stopStartMs: Long, isLight: Boolean) -> Unit = { _, _ -> },
    val onDelete: (date: String, active: Boolean) -> Unit = { _, _ -> },
    val onShare: (DayState) -> Unit = {},
    val onRefresh: () -> Unit = {},
    /** Işık noktaları mini haritası (testte boş; uygulamada MapLibre). */
    val lightsMap: @Composable (List<StopRec>, Lang, Modifier) -> Unit = { lights, lang, m -> LightsMiniMap(lights, lang, m) },
)

/**
 * İstatistik (Y17; MAP.md 12.7): her gün bir sekme (kaydırılabilir), yenile; gün yoksa açıklama. [days] en yenisi önce; [focusStartMs] açılışta odaklanan günün başlangıcı.
 * Sekmeler: `stats-tab-<sıra>`, sayfa: `stats-day-page`.
 */
@Composable
fun StatsScreen(
    days: List<DayState>,
    lang: Lang,
    nowMs: Long,
    zone: ZoneId,
    focusStartMs: Long?,
    actions: StatsActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var selected by remember(days.size, focusStartMs) { mutableIntStateOf(StatsTabs.focusIndex(days, focusStartMs)) }
    val index = selected.coerceIn(0, max(days.lastIndex, 0))
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack, Modifier.testTag("stats-back")) { Text("←") }
            Text(StatsText.TITLE.get(lang), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = actions.onRefresh, Modifier.testTag("stats-refresh").semantics { contentDescription = StatsText.REFRESH.get(lang) }) { Text("↻") }
        }
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(StatsTabs.emptyText(lang), style = MaterialTheme.typography.bodyMedium) }
            return
        }
        ScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
            days.forEachIndexed { i, d ->
                Tab(selected = i == index, onClick = { selected = i }, Modifier.testTag("stats-tab-$i")) {
                    Text(StatsTabs.label(d, i, nowMs, zone, lang), Modifier.padding(horizontal = 12.dp, vertical = 12.dp), maxLines = 1)
                }
            }
        }
        DayStatsPage(days[index], lang, nowMs, zone, actions, Modifier.weight(1f))
    }
}

/** Bir günün sayfası (MAP.md 12.7): başlık ve neden, kutular, trafik ışıkları (mini harita + liste), eksik ışık kontrolü, siparişler, paylaş. */
@Composable
fun DayStatsPage(d: DayState, lang: Lang, nowMs: Long, zone: ZoneId, actions: StatsActions, modifier: Modifier = Modifier) {
    val sum = DayStats.summarize(d, nowMs)
    val lights = LightStats.of(d)
    val date = LogTime.day(d.startedAtMs ?: nowMs, zone)
    var confirm by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp).testTag("stats-day-page"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DayReasons.of(d, lang)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = { confirm = true }, Modifier.testTag("stats-delete")) { Text(StatsText.DELETE_DAY.get(lang), color = MaterialTheme.colorScheme.error) }
        }
        fun dur(key: String) = DurationText.fmt(sum.getLong(key), lang)
        val homeShown = sum.getDouble("homeS") > 0 || sum.getDouble("homeKm") > 0
        StatRow(
            listOf(
                StatsText.TOTAL_TIME.get(lang) to dur("totalS"),
                StatsText.KM.get(lang) to String.format(Locale.US, "%.2f", sum.getDouble("km")),
                StatsText.MOVING.get(lang) to dur("movingS"),
            ),
        )
        StatRow(
            listOf(
                StatsText.DELIVERED_OF.get(lang) to "${sum.getInt("delivered")}/${sum.getInt("orders")}",
                StatsText.REST_WAIT.get(lang) to dur("restWaitS"),
                StatsText.DOOR_WAIT.get(lang) to dur("custWaitS"),
            ),
        )
        if (homeShown) {
            StatRow(
                listOf(
                    StatsText.HOME_KM.get(lang) to String.format(Locale.US, "%.2f", sum.getDouble("homeKm")),
                    StatsText.HOME_TIME.get(lang) to DurationText.fmt(sum.getDouble("homeS").toLong(), lang),
                ),
            )
        }
        Text(StatsText.LIGHTS_HEADER.get(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        StatRow(
            listOf(
                StatsText.LIGHT_STOPS.get(lang) to "${lights.count}",
                StatsText.LIGHT_TOTAL.get(lang) to DurationText.fmt(lights.totalS.toLong(), lang),
                StatsText.LIGHT_AVG_MAX.get(lang) to "${DurationText.fmt(lights.avgS.toLong(), lang)} / ${DurationText.fmt(lights.maxS.toLong(), lang)}",
            ),
        )
        if (lights.count == 0) {
            Text(StatsText.NO_LIGHTS.get(lang), style = MaterialTheme.typography.bodySmall)
        } else {
            Box(Modifier.fillMaxWidth().height(260.dp).testTag("stats-lights-map")) { actions.lightsMap(lights.lights, lang, Modifier.fillMaxSize()) }
            for (s in lights.lights) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(StreetLabel.of(s, actions.streetName), maxLines = 1)
                        Text(DurationText.clock(s.startMs, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(DurationText.fmt(s.durS.toLong(), lang), fontWeight = FontWeight.Bold)
                }
            }
        }
        val candidates = d.lightCandidates
        if (candidates.isNotEmpty()) {
            Text(StatsText.CAND_HEADER.get(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(StatsText.CAND_INFO.get(lang), style = MaterialTheme.typography.bodySmall)
            for (s in candidates) CandidateRow(s, lang, zone, actions)
        }
        if (d.orders.isNotEmpty()) {
            Text(StatsText.ORDERS_HEADER.get(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            for (o in d.orders) OrderRow(o, lang, zone)
        }
        if (!d.active) OutlinedButton(onClick = { actions.onShare(d) }, Modifier.testTag("stats-share")) { Text(StatsText.SHARE_DAY.get(lang)) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(StatsText.DELETE_TITLE.fmtDate(lang, date)) },
            text = { Text(StatsText.DELETE_BODY.get(lang) + if (d.active) "\n\n" + StatsText.DELETE_ACTIVE.get(lang) else "") },
            confirmButton = { TextButton(onClick = { confirm = false; actions.onDelete(date, d.active) }, Modifier.testTag("stats-delete-confirm")) { Text(StatsText.DELETE_DAY.get(lang), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(StatsText.CANCEL.get(lang)) } },
        )
    }
}

private fun StatsText.fmtDate(lang: Lang, date: String) = String.format(get(lang), date)

@Composable
private fun StatRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, value) in items) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(s: StopRec, lang: Lang, zone: ZoneId, actions: StatsActions) {
    val long = s.longWait
    Row(Modifier.fillMaxWidth().testTag("stats-candidate-${s.startMs}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val street = StreetLabel.of(s, actions.streetName)
            Text((if (long) "? " else "") + street, maxLines = 1)
            Text(
                DurationText.clock(s.startMs, zone) + " · " + DurationText.fmt(s.durS.toLong(), lang) + if (long) StatsText.CAND_LONG.get(lang) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { actions.onReview(s.startMs, true) }, Modifier.padding(end = 4.dp).testTag("stats-confirm-${s.startMs}")) {
            Text(if (long) StatsText.CONFIRM_LONG.get(lang) else StatsText.CONFIRM_LIGHT.get(lang))
        }
        OutlinedButton(onClick = { actions.onReview(s.startMs, false) }, Modifier.testTag("stats-reject-${s.startMs}")) {
            Text(if (long) StatsText.REJECT_LONG.get(lang) else StatsText.REJECT_LIGHT.get(lang))
        }
    }
}

@Composable
private fun OrderRow(o: OrderRec, lang: Lang, zone: ZoneId) {
    val unknown = "–"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (o.cancelled) "✕" else "✓", Modifier.padding(end = 8.dp), color = if (o.cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column {
            val total = o.totalS?.let { DurationText.fmt(it.toLong(), lang) } ?: unknown
            Text("#${o.n} · ${DurationText.clock(o.startedAtMs, zone)} · ${String.format(Locale.US, "%.1f", o.distM / 1000)} km · $total")
            Text(
                "${StatsText.ORDER_REST.get(lang)} ${o.restWaitS?.let { DurationText.fmt(it.toLong(), lang) } ?: unknown} · ${StatsText.ORDER_DOOR.get(lang)} ${o.custWaitS?.let { DurationText.fmt(it.toLong(), lang) } ?: unknown}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Işık noktaları mini haritası (`_LightsMap`, MAP.md 12.7): MapLibre raster (S4); tek ışıkta nokta ± 0,002°, çoğunda bütün noktalar; her ışıkta süre etiketi. */
@Composable
fun LightsMiniMap(lights: List<StopRec>, lang: Lang, modifier: Modifier = Modifier) {
    val pts = lights.map { GeoPoint(it.lightLat ?: it.lat, it.lightLon ?: it.lon) }
    val minLat = pts.minOf { it.lat }
    val maxLat = pts.maxOf { it.lat }
    val minLon = pts.minOf { it.lon }
    val maxLon = pts.maxOf { it.lon }
    val spanLat = if (pts.size == 1) 0.004 else max(maxLat - minLat, 0.0005)
    val spanLon = if (pts.size == 1) 0.004 else max(maxLon - minLon, 0.0005)
    // 260 dp yükseklik, ~360 dp genişlik: karo 256 px → zoom = log2(360° · 360 / (span · 256)); kenar boşluğu için bir basamak düşülür; en çok 17.
    val zoom = min(17.0, max(11.0, Math.log(360.0 * 360.0 / (max(spanLat * 1.4, spanLon) * 256.0)) / Math.log(2.0) - 1.0))
    val model = MapSurfaceModel(
        base = BaseMap.OSM, dark = false,
        markers = lights.mapIndexed { i, s -> MapMarker("light-$i", pts[i], MarkerKind.POI_LIGHT, label = DurationText.fmt(s.durS.toLong(), lang)) },
        camera = MapCameraCommand(1, GeoPoint((minLat + maxLat) / 2, (minLon + maxLon) / 2), zoom),
    )
    MapSurface(model, MapSurfaceEvents(), modifier)
}
