package com.kuryelog.app.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.roads.GhostMark
import com.kuryelog.logic.map.roads.QualityAsk
import com.kuryelog.logic.map.roads.RoadState
import com.kuryelog.logic.map.roads.RoadsText
import com.kuryelog.logic.map.roads.WwRowView
import com.kuryelog.logic.map.roads.WwTone
import com.kuryelog.logic.map.route.QualityRating
import com.kuryelog.logic.map.route.StreetPrefs
import com.kuryelog.logic.map.settings.MapSettings
import java.time.ZoneId
import kotlin.math.roundToInt

/** Kalite sekmesi satırı (elle puan) ve kalite sayfasının verisi. */
data class QualityRowView(
    val way: Long,
    val name: String,
    val pctText: String,
    val color: Color,
    val subtitle: String,
    val rating: Int,
    val sheetSubtitle: String,
    val hasStats: Boolean,
)

data class SpotRowView(val id: String, val title: String, val subtitle: String, val removed: Boolean)

/** Yollarım görünümü (MAP.md 9.8): ekran yalnız çizer, çalışma zamanı ([RoadsHost]) kurar. */
data class RoadsScreenState(
    val learningOn: Boolean = false,
    val quality: List<QualityRowView> = emptyList(),
    val tolerancePct: Double = 10.0,
    val spots: List<SpotRowView> = emptyList(),
    val traffic: List<Pair<String, String>> = emptyList(),
    val ww: List<WwRowView> = emptyList(),
    val wwThresholdPct: Double = 50.0,
    val wwAskAlways: Boolean = false,
    val ghosts: List<Pair<String, String>> = emptyList(),
)

class RoadsActions(
    val rate: (way: Long, pct: Int, name: String) -> Unit = { _, _, _ -> },
    val openPercent: (way: Long, name: String) -> Unit = { _, _ -> },
    val resetQuality: (Long) -> Unit = {},
    val tolerance: (Double) -> Unit = {},
    val spotRemoved: (id: String, removed: Boolean) -> Unit = { _, _ -> },
    val wwAdd: (way: Long, problem: Boolean, name: String) -> Unit = { _, _, _ -> },
    val wwReset: (Long) -> Unit = {},
    val unapprove: (Long) -> Unit = {},
    val threshold: (Double) -> Unit = {},
    val askAlways: (Boolean) -> Unit = {},
    val removeGhost: (Int) -> Unit = {},
    val clearGhosts: () -> Unit = {},
)

/** [RoadState] + ayarlar + sokak kararları → ekran verisi (hayaletler yeniden eskiye). */
fun roadsScreenState(s: RoadState, settings: MapSettings, streets: StreetPrefs, lang: Lang, zone: ZoneId, graph: RoadGraph?): RoadsScreenState {
    val k = s.k
    val quality = k.ratings.keys.map { way ->
        val st = k.ways[way]
        val r = k.ratings[way] ?: QualityRating.NONE
        QualityRowView(
            way, k.nameOf(way, lang), RoadsText.pct(r / 100.0), RoadsText.tone(r / 100.0).color(), RoadsText.manualRow(lang, st), r,
            RoadsText.sheetSubtitle(lang, k.qualityOf(way, graph), st), st != null,
        )
    }
    val spots = s.marks.map { m -> SpotRowView(m.id, RoadsText.spotTitle(lang, k.names[m.way].orEmpty()), RoadsText.spotSubtitle(lang, m.removed), m.removed) }
    val traffic = k.ways.filter { it.value.m >= 50 }.entries.sortedByDescending { it.value.m }.map { (way, st) -> k.nameOf(way, lang) to RoadsText.trafficRow(st, lang) }
    val ghosts = k.ghosts.reversed().map { g: GhostMark -> g.name.ifEmpty { RoadsText.unnamed(lang) } to RoadsText.ghostRow(lang, g, zone) }
    return RoadsScreenState(
        learningOn = settings.roadLearning, quality = quality, tolerancePct = settings.qualityTolerancePct, spots = spots, traffic = traffic,
        ww = RoadsText.wwRows(lang, k, streets.approved, streets.names, settings.wwMaxProblemPct), wwThresholdPct = settings.wwMaxProblemPct,
        wwAskAlways = settings.wwAskAlways, ghosts = ghosts,
    )
}

/**
 * Yollarım (YolGo `RoadsScreen`; MAP.md 9.8): sekmeler Kalite · Ters yön · Yol yok · Bisiklet ([bikeway] Y15 sekmesi). [prompt] açıksa altta kalite kartı.
 */
@Composable
fun RoadsScreen(
    state: RoadsScreenState,
    lang: Lang,
    actions: RoadsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    prompt: @Composable () -> Unit = {},
    bikeway: @Composable () -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Text(RoadsText.title(), style = MaterialTheme.typography.titleLarge)
        }
        TabRow(selectedTabIndex = tab) {
            RoadsText.tabs(lang).forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1, fontSize = 12.sp) }, modifier = Modifier.testTag("roads-tab-$i")) }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> QualityTab(state, lang, actions)
                1 -> WrongWayTab(state, lang, actions)
                2 -> GhostTab(state, lang, actions)
                else -> bikeway()
            }
        }
        prompt()
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun Lo(text: String) = Text(text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun QualityTab(state: RoadsScreenState, lang: Lang, actions: RoadsActions) {
    var sheet by remember { mutableStateOf<QualityRowView?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Text(RoadsText.manualHeader(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        if (state.quality.isEmpty()) Lo(RoadsText.emptyList(lang).let { "   $it" })
        for (r in state.quality) {
            Row(Modifier.fillMaxWidth().clickable { sheet = r }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("quality-row-${r.way}"), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, r.color), modifier = Modifier.width(54.dp)) {
                    Text(r.pctText, color = r.color, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(RoadsText.tolerance(lang), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("%${state.tolerancePct.roundToInt()}", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = state.tolerancePct.toFloat().coerceIn(0f, 30f), onValueChange = { actions.tolerance(it.roundToInt().toDouble()) }, valueRange = 0f..30f, steps = 29,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(RoadsText.spotsHeader(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        if (state.spots.isEmpty()) Lo("   " + RoadsText.emptyList(lang))
        for (sp in state.spots) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sp.title, color = if (sp.removed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                    Text(sp.subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { actions.spotRemoved(sp.id, !sp.removed) }) { Text(if (sp.removed) RoadsText.undo(lang) else RoadsText.delete()) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(RoadsText.trafficHeader(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Lo("    " + RoadsText.trafficSubtitle(lang))
        for ((name, line) in state.traffic) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    sheet?.let { r ->
        fun done(f: () -> Unit) { f(); sheet = null }
        AlertDialog(
            onDismissRequest = { sheet = null },
            title = { Column { Text(r.name, fontWeight = FontWeight.ExtraBold); Lo(r.sheetSubtitle) } },
            text = {
                Column {
                    MenuRow("👍 " + RoadsText.sheetGood(lang), r.rating == QualityRating.GOOD) { done { actions.rate(r.way, QualityRating.GOOD, r.name) } }
                    MenuRow("👎 " + RoadsText.sheetBad(lang), r.rating == QualityRating.BAD) { done { actions.rate(r.way, QualityRating.BAD, r.name) } }
                    MenuRow("% " + RoadsText.sheetPercent(lang)) { done { actions.openPercent(r.way, r.name) } }
                    if (r.rating != QualityRating.NONE) MenuRow(RoadsText.sheetClear(lang)) { done { actions.rate(r.way, QualityRating.NONE, "") } }
                    if (r.hasStats) MenuRow(RoadsText.sheetReset(lang)) { done { actions.resetQuality(r.way) } }
                }
            },
            confirmButton = { TextButton(onClick = { sheet = null }) { Text(RoadsText.cancel(lang)) } },
        )
    }
}

@Composable
private fun MenuRow(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun WrongWayTab(state: RoadsScreenState, lang: Lang, actions: RoadsActions) {
    var menu by remember { mutableStateOf<WwRowView?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        InfoCard {
            Text(RoadsText.wwInfoTitle(lang), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Lo(RoadsText.wwInfo(lang))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(RoadsText.wwThreshold(lang), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { actions.threshold((state.wwThresholdPct - 5).coerceIn(20.0, 90.0)) }) { Text("−") }
                Text("%${state.wwThresholdPct.roundToInt()}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("ww-threshold"))
                TextButton(onClick = { actions.threshold((state.wwThresholdPct + 5).coerceIn(20.0, 90.0)) }) { Text("+") }
            }
            Row(Modifier.fillMaxWidth().clickable { actions.askAlways(!state.wwAskAlways) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(RoadsText.wwAskAlways(lang))
                    Lo(RoadsText.wwAskAlwaysSub(lang))
                }
                Switch(checked = state.wwAskAlways, onCheckedChange = null)
            }
        }
        if (state.ww.isEmpty()) Text(RoadsText.wwEmpty(lang), textAlign = TextAlign.Center, fontSize = 12.5.sp, modifier = Modifier.fillMaxWidth().padding(24.dp))
        for (r in state.ww) {
            Row(Modifier.fillMaxWidth().clickable { menu = r }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("ww-row-${r.way}"), verticalAlignment = Alignment.CenterVertically) {
                Text("⇄", color = if (r.approved) GOOD_GREEN else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.subtitle, fontSize = 11.5.sp, color = if (r.avoided) BAD_RED else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    r.ratioText, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    color = when (r.tone) {
                        WwTone.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                        WwTone.RED -> BAD_RED
                        WwTone.ORANGE -> FAIR_ORANGE
                        WwTone.GREEN -> GOOD_GREEN
                    },
                )
            }
        }
    }
    menu?.let { r ->
        fun done(f: () -> Unit) { f(); menu = null }
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(r.name, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    MenuRow(RoadsText.wwAddProblem(lang)) { done { actions.wwAdd(r.way, true, r.name) } }
                    MenuRow(RoadsText.wwAddClean(lang)) { done { actions.wwAdd(r.way, false, r.name) } }
                    MenuRow(RoadsText.wwReset(lang)) { done { actions.wwReset(r.way) } }
                    if (r.approved) MenuRow(RoadsText.wwUnapprove(lang)) { done { actions.unapprove(r.way) } }
                }
            },
            confirmButton = { TextButton(onClick = { menu = null }) { Text(RoadsText.cancel(lang)) } },
        )
    }
}

@Composable
private fun GhostTab(state: RoadsScreenState, lang: Lang, actions: RoadsActions) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        InfoCard {
            Text(RoadsText.ghostInfoTitle(lang), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Lo(RoadsText.ghostInfo(lang))
        }
        if (state.ghosts.isEmpty()) Text(RoadsText.emptyList(lang), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
        state.ghosts.forEachIndexed { i, (name, sub) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name)
                    Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.removeGhost(i) }, modifier = Modifier.testTag("ghost-del-$i")) { Text("✕") }
            }
        }
        if (state.ghosts.isNotEmpty()) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(RoadsText.ghostClearAll(lang), color = BAD_RED) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(RoadsText.ghostClearConfirm(lang)) },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(RoadsText.cancel(lang)) } },
            confirmButton = { TextButton(onClick = { confirm = false; actions.clearGhosts() }) { Text(RoadsText.delete()) } },
        )
    }
}

/** Yollarım'ın çalışma zamanına bağlı hâli (`NavRuntime.roads`, sokak kararları, Harita ayarları, bisiklet işaretleri). */
@Composable
fun RoadsHost(lang: Lang, onBack: () -> Unit, initialTab: Int = 0) {
    val ctx = LocalContext.current
    val nav = remember { NavRuntime.get(ctx) }
    val rt = nav.roads
    val road by rt.book.states.collectAsState()
    val settings by MapSettingsRepo.settings(ctx).collectAsState()
    val data by MapData.controller(ctx).state.collectAsState()
    val ask by rt.prompt.state.collectAsState()
    var bikeRev by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    val graph = data.graph
    val state = remember(road, settings, lang, graph) { roadsScreenState(road, settings, nav.streets.current, lang, ZoneId.systemDefault(), graph) }
    val actions = remember(rt) {
        RoadsActions(
            rate = { w, p, n -> rt.book.rate(w, p, n) },
            openPercent = { w, n -> rt.prompt.open(w, n) },
            resetQuality = { rt.book.resetQuality(it) },
            tolerance = { v -> MapSettingsRepo.update(ctx) { it.copy(qualityTolerancePct = v) } },
            spotRemoved = { id, removed -> rt.book.setSpotRemoved(id, removed) },
            wwAdd = { w, p, n -> rt.book.addWwPass(w, p, n) },
            wwReset = { rt.book.resetWw(it) },
            unapprove = { w -> nav.streets.remove(com.kuryelog.logic.map.route.StreetList.APPROVED, w); rt.book.streetsEdited() },
            threshold = { v -> MapSettingsRepo.update(ctx) { it.copy(wwMaxProblemPct = v) } },
            askAlways = { v -> MapSettingsRepo.update(ctx) { it.copy(wwAskAlways = v) } },
            removeGhost = { i -> rt.book.state.k.ghosts.reversed().getOrNull(i)?.let(rt.book::removeGhost) },
            clearGhosts = { rt.book.clearGhosts() },
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        RoadsScreen(
            state, lang, actions, onBack, initialTab = initialTab,
            prompt = { ask?.let { a -> RoadPromptCard(a, lang) } },
            bikeway = {
                val marks = remember(bikeRev) { nav.bikeway.marks }
                val stats = remember(graph) { graph?.let { bikewayStats(it) } }
                BikewayTab(
                    BikewayTabState(
                        osmWays = stats?.first ?: 0, weight = settings.bikewayCostPct / 100, layerOn = settings.showBikewayLayer,
                        marks = marks.ways.map { it to marks.names[it].orEmpty() }, osmMeters = stats?.second, hasBikewayData = graph?.hasBikewayData ?: true,
                    ),
                    lang,
                    onLayerChange = { on -> MapSettingsRepo.update(ctx) { it.copy(showBikewayLayer = on) } },
                    onRemoveMark = { id -> nav.bikeway.unmark(id); nav.reloadBikeway(); bikeRev++ },
                    onWeightChange = { pct -> MapSettingsRepo.update(ctx) { it.copy(bikewayCostPct = pct.toDouble()) } },
                )
            },
        )
    }
}

/** Kalite kartı çalışma zamanına bağlı (Harita sekmesi alt paneli ve Yollarım). */
@Composable
fun RoadPromptCard(ask: QualityAsk, lang: Lang, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = remember { NavRuntime.get(ctx).roads.prompt }
    QualityPromptCard(ask, lang, onSet = p::set, onSave = p::save, onCancel = p::cancel, modifier = modifier.padding(horizontal = 8.dp))
}

/** OSM bisiklet yolu istatistiği: sokak sayısı ve toplam uzunluk (m). */
internal fun bikewayStats(g: RoadGraph): Pair<Int, Double> {
    val ids = HashSet<Long>()
    var m = 0.0
    for (e in 0 until g.edgeCount) if (g.isBikeway(e)) {
        ids += g.osmIdOf(e)
        m += g.edgeLen[e]
    }
    return ids.size to m
}
