package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.SectionLabel
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.StatTile
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WChip
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemTopBar
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogTime
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
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WeemTopBar(StatsText.TITLE.get(lang), onBack, Str.BACK.get(lang), backTag = "stats-back") {
            WIconButton(WeemIcons.Refresh, StatsText.REFRESH.get(lang), actions.onRefresh, Modifier.testTag("stats-refresh"))
        }
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(WeemSpace.xxl), contentAlignment = Alignment.Center) { EmptyState(StatsTabs.emptyText(lang), icon = WeemIcons.BarChart) }
            return
        }
        // Günler: kaydırılan çip sırası (seçili çip vurgu kabında); odaklanan/seçilen gün görünür alana kaydırılır.
        val chips = rememberLazyListState(initialFirstVisibleItemIndex = index)
        LaunchedEffect(index) { chips.animateScrollToItem(index) }
        LazyRow(
            Modifier.fillMaxWidth(),
            state = chips,
            contentPadding = PaddingValues(horizontal = WeemSpace.l, vertical = WeemSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(WeemSpace.s),
        ) {
            itemsIndexed(days) { i, d -> WChip(StatsTabs.label(d, i, nowMs, zone, lang), i == index, { selected = i }, Modifier.testTag("stats-tab-$i")) }
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
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.s, bottom = WeemSpace.xxl).testTag("stats-day-page"),
        verticalArrangement = Arrangement.spacedBy(WeemSpace.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date, style = WeemType.titleL)
                DayReasons.of(d, lang)?.let { Text(it, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            WButton(StatsText.DELETE_DAY.get(lang), { confirm = true }, Modifier.testTag("stats-delete"), kind = BtnKind.DANGER, icon = WeemIcons.Delete)
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
        SectionLabel(StatsText.LIGHTS_HEADER.get(lang))
        StatRow(
            listOf(
                StatsText.LIGHT_STOPS.get(lang) to "${lights.count}",
                StatsText.LIGHT_TOTAL.get(lang) to DurationText.fmt(lights.totalS.toLong(), lang),
                StatsText.LIGHT_AVG_MAX.get(lang) to "${DurationText.fmt(lights.avgS.toLong(), lang)} / ${DurationText.fmt(lights.maxS.toLong(), lang)}",
            ),
        )
        if (lights.count == 0) {
            Text(StatsText.NO_LIGHTS.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(Modifier.fillMaxWidth().height(260.dp).testTag("stats-lights-map")) { actions.lightsMap(lights.lights, lang, Modifier.fillMaxSize()) }
            SettingsGroup(null, dividerInset = 70.dp) {
                for (s in lights.lights) {
                    ListRow(
                        StreetLabel.of(s, actions.streetName), subtitle = DurationText.clock(s.startMs, zone), icon = WeemIcons.Traffic,
                        trailing = { Text(DurationText.fmt(s.durS.toLong(), lang), style = WeemType.numS) },
                    )
                }
            }
        }
        val candidates = d.lightCandidates
        if (candidates.isNotEmpty()) {
            SectionLabel(StatsText.CAND_HEADER.get(lang))
            Text(StatsText.CAND_INFO.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (s in candidates) CandidateRow(s, lang, zone, actions)
        }
        if (d.orders.isNotEmpty()) {
            SectionLabel(StatsText.ORDERS_HEADER.get(lang))
            SettingsGroup(null, dividerInset = 70.dp) { for (o in d.orders) OrderRow(o, lang, zone) }
        }
        if (!d.active) WButton(StatsText.SHARE_DAY.get(lang), { actions.onShare(d) }, Modifier.padding(top = WeemSpace.s).testTag("stats-share"), kind = BtnKind.OUTLINE, icon = WeemIcons.Share, fill = true)
    }
    if (confirm) ConfirmDialog(
        title = StatsText.DELETE_TITLE.fmtDate(lang, date),
        text = StatsText.DELETE_BODY.get(lang) + if (d.active) "\n\n" + StatsText.DELETE_ACTIVE.get(lang) else "",
        confirm = StatsText.DELETE_DAY.get(lang), dismiss = StatsText.CANCEL.get(lang), danger = true, confirmTag = "stats-delete-confirm",
        onConfirm = { confirm = false; actions.onDelete(date, d.active) }, onDismiss = { confirm = false },
    )
}

private fun StatsText.fmtDate(lang: Lang, date: String) = String.format(get(lang), date)

@Composable
private fun StatRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
        for ((label, value) in items) StatTile(value, label, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun CandidateRow(s: StopRec, lang: Lang, zone: ZoneId, actions: StatsActions) {
    val long = s.longWait
    WCard(Modifier.testTag("stats-candidate-${s.startMs}"), padding = WeemSpace.m) {
        val street = StreetLabel.of(s, actions.streetName)
        Text((if (long) "? " else "") + street, style = WeemType.titleS, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            DurationText.clock(s.startMs, zone) + " · " + DurationText.fmt(s.durS.toLong(), lang) + if (long) StatsText.CAND_LONG.get(lang) else "",
            style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(
                if (long) StatsText.CONFIRM_LONG.get(lang) else StatsText.CONFIRM_LIGHT.get(lang), { actions.onReview(s.startMs, true) },
                Modifier.weight(1f).testTag("stats-confirm-${s.startMs}"), icon = WeemIcons.Check,
            )
            WButton(
                if (long) StatsText.REJECT_LONG.get(lang) else StatsText.REJECT_LIGHT.get(lang), { actions.onReview(s.startMs, false) },
                Modifier.weight(1f).testTag("stats-reject-${s.startMs}"), kind = BtnKind.OUTLINE,
            )
        }
    }
}

@Composable
private fun OrderRow(o: OrderRec, lang: Lang, zone: ZoneId) {
    val unknown = "–"
    val total = o.totalS?.let { DurationText.fmt(it.toLong(), lang) } ?: unknown
    ListRow(
        "#${o.n} · ${DurationText.clock(o.startedAtMs, zone)} · ${String.format(Locale.US, "%.1f", o.distM / 1000)} km · $total",
        subtitle = "${StatsText.ORDER_REST.get(lang)} ${o.restWaitS?.let { DurationText.fmt(it.toLong(), lang) } ?: unknown} · ${StatsText.ORDER_DOOR.get(lang)} ${o.custWaitS?.let { DurationText.fmt(it.toLong(), lang) } ?: unknown}",
        icon = if (o.cancelled) WeemIcons.Cancel else WeemIcons.CheckCircleFilled, iconTone = if (o.cancelled) WTone.NEUTRAL else WTone.POSITIVE,
        titleStyle = WeemType.titleS,
    )
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
