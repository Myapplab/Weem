package com.kuryelog.app.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.Segmented
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemTopBar
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.ink
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.app.Palette
import com.kuryelog.logic.app.Text as Str
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
fun roadsScreenState(s: RoadState, settings: MapSettings, streets: StreetPrefs, lang: Lang, zone: ZoneId, graph: RoadGraph?, palette: Palette = Palette.light): RoadsScreenState {
    val k = s.k
    val quality = k.ratings.keys.map { way ->
        val st = k.ways[way]
        val r = k.ratings[way] ?: QualityRating.NONE
        QualityRowView(
            way, k.nameOf(way, lang), RoadsText.pct(r / 100.0), Color(RoadsText.tone(r / 100.0).ink(palette)), RoadsText.manualRow(lang, st), r,
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
    surface: @Composable () -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WeemTopBar(RoadsText.title(), onBack, Str.BACK.get(lang))
        // Sekmeler: Kalite · Ters yön · Yol yok · Bisiklet (segment; uzun Azerice ad iki satıra iner).
        Segmented(
            RoadsText.tabs(lang), tab, { tab = it }, Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.xs),
            tags = RoadsText.tabs(lang).indices.map { "roads-tab-$it" },
        )
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> QualityTab(state, lang, actions, surface)
                1 -> WrongWayTab(state, lang, actions)
                2 -> GhostTab(state, lang, actions)
                else -> bikeway()
            }
        }
        prompt()
    }
}

/** Sekmenin üstündeki açıklama kartı: başlık + ikincil metin (+ isteğe bağlı satırlar). */
@Composable
private fun InfoCard(title: String, text: String, content: @Composable () -> Unit = {}) {
    SettingsGroup(null, Modifier.padding(top = WeemSpace.m)) {
        Column(Modifier.padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.xs)) {
            Text(title, style = WeemType.titleM)
            Text(text, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** Yüzde rozeti: tonda 1 dp çerçeve, köşe 12, kalın yüzde. */
@Composable
private fun PctBadge(text: String, color: Color) {
    Box(
        Modifier.width(56.dp).border(1.dp, color, RoundedCornerShape(WeemRadius.tile)).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = WeemType.numS.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1) }
}

@Composable
private fun QualityTab(state: RoadsScreenState, lang: Lang, actions: RoadsActions, surface: @Composable () -> Unit = {}) {
    var sheet by remember { mutableStateOf<QualityRowView?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.x4)) {
        surface()
        SettingsGroup(RoadsText.manualHeader(lang)) {
            if (state.quality.isEmpty()) ListRow(RoadsText.emptyList(lang), titleStyle = WeemType.bodyM, titleTone = WTone.NEUTRAL)
            for (r in state.quality) {
                ListRow(
                    r.name, Modifier.testTag("quality-row-${r.way}"), subtitle = r.subtitle,
                    leading = { PctBadge(r.pctText, r.color) }, chevron = true, onClick = { sheet = r },
                )
            }
        }
        SettingsGroup(null, Modifier.padding(top = WeemSpace.l)) {
            Column(Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(RoadsText.tolerance(lang), Modifier.weight(1f), style = WeemType.titleS)
                    Text("%${state.tolerancePct.roundToInt()}", style = WeemType.numS)
                }
                Slider(
                    value = state.tolerancePct.toFloat().coerceIn(0f, 30f), onValueChange = { actions.tolerance(it.roundToInt().toDouble()) }, valueRange = 0f..30f, steps = 29,
                )
            }
        }
        SettingsGroup(RoadsText.spotsHeader(lang)) {
            if (state.spots.isEmpty()) ListRow(RoadsText.emptyList(lang), titleStyle = WeemType.bodyM)
            for (sp in state.spots) {
                ListRow(
                    sp.title, subtitle = sp.subtitle, enabled = !sp.removed, titleStyle = WeemType.bodyM,
                    trailing = { WButton(if (sp.removed) RoadsText.undo(lang) else RoadsText.delete(), { actions.spotRemoved(sp.id, !sp.removed) }, kind = BtnKind.TEXT) },
                )
            }
        }
        SettingsGroup(RoadsText.trafficHeader(lang), footer = RoadsText.trafficSubtitle(lang)) {
            for ((name, line) in state.traffic) ListRow(name, subtitle = line, titleStyle = WeemType.bodyM)
        }
    }
    sheet?.let { r ->
        fun done(f: () -> Unit) { f(); sheet = null }
        // K4: çok seçenekli menü alttan açılan sayfada.
        WSheet(onDismiss = { sheet = null }, title = r.name) {
            Text(r.sheetSubtitle, style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsGroup(null, dividerInset = 70.dp) {
                MenuRow(RoadsText.sheetGood(lang), WeemIcons.ThumbUp, r.rating == QualityRating.GOOD) { done { actions.rate(r.way, QualityRating.GOOD, r.name) } }
                MenuRow(RoadsText.sheetBad(lang), WeemIcons.ThumbDown, r.rating == QualityRating.BAD) { done { actions.rate(r.way, QualityRating.BAD, r.name) } }
                MenuRow(RoadsText.sheetPercent(lang), WeemIcons.Tune) { done { actions.openPercent(r.way, r.name) } }
                if (r.rating != QualityRating.NONE) MenuRow(RoadsText.sheetClear(lang), WeemIcons.Close) { done { actions.rate(r.way, QualityRating.NONE, "") } }
                if (r.hasStats) MenuRow(RoadsText.sheetReset(lang), WeemIcons.Refresh) { done { actions.resetQuality(r.way) } }
            }
            WButton(RoadsText.cancel(lang), { sheet = null }, kind = BtnKind.TEXT, fill = true)
        }
    }
}

/** Menü satırı: simge kutusu + metin; seçiliyse vurgu tonunda ve ✓. */
@Composable
private fun MenuRow(text: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    ListRow(
        text, icon = icon, iconTone = if (selected) WTone.ACCENT else WTone.NEUTRAL, titleTone = if (selected) WTone.ACCENT else null, onClick = onClick,
        trailing = if (selected) ({ Icon(WeemIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
    )
}

@Composable
private fun WrongWayTab(state: RoadsScreenState, lang: Lang, actions: RoadsActions) {
    var menu by remember { mutableStateOf<WwRowView?>(null) }
    val p = palette()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.x4)) {
        InfoCard(RoadsText.wwInfoTitle(lang), RoadsText.wwInfo(lang)) {
            Row(Modifier.padding(start = WeemSpace.l, end = WeemSpace.s).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(RoadsText.wwThreshold(lang), Modifier.weight(1f), style = WeemType.titleS)
                WIconButton(WeemIcons.Remove, "−5", { actions.threshold((state.wwThresholdPct - 5).coerceIn(20.0, 90.0)) }, tinted = true)
                Text("%${state.wwThresholdPct.roundToInt()}", style = WeemType.numS, modifier = Modifier.padding(horizontal = WeemSpace.s).testTag("ww-threshold"))
                WIconButton(WeemIcons.Add, "+5", { actions.threshold((state.wwThresholdPct + 5).coerceIn(20.0, 90.0)) }, tinted = true)
            }
            SwitchRow(RoadsText.wwAskAlways(lang), state.wwAskAlways, { actions.askAlways(!state.wwAskAlways) }, subtitle = RoadsText.wwAskAlwaysSub(lang))
        }
        if (state.ww.isEmpty()) EmptyState(RoadsText.wwEmpty(lang), icon = WeemIcons.AltRoute)
        else SettingsGroup(null, Modifier.padding(top = WeemSpace.l), dividerInset = 70.dp) {
            for (r in state.ww) {
                ListRow(
                    r.name, Modifier.testTag("ww-row-${r.way}"), subtitle = r.subtitle, subtitleTone = if (r.avoided) WTone.DANGER else null,
                    icon = WeemIcons.AltRoute, iconTone = if (r.approved) WTone.POSITIVE else WTone.NEUTRAL, onClick = { menu = r },
                    trailing = {
                        Text(
                            r.ratioText, style = WeemType.numS.copy(fontWeight = FontWeight.ExtraBold),
                            color = when (r.tone) {
                                WwTone.NONE -> p.muted
                                WwTone.RED -> p.danger
                                WwTone.ORANGE -> p.warn
                                WwTone.GREEN -> p.positive
                            }.color(),
                        )
                    },
                )
            }
        }
    }
    menu?.let { r ->
        fun done(f: () -> Unit) { f(); menu = null }
        WSheet(onDismiss = { menu = null }, title = r.name) {
            SettingsGroup(null, dividerInset = 70.dp) {
                MenuRow(RoadsText.wwAddProblem(lang), WeemIcons.Warning) { done { actions.wwAdd(r.way, true, r.name) } }
                MenuRow(RoadsText.wwAddClean(lang), WeemIcons.CheckCircle) { done { actions.wwAdd(r.way, false, r.name) } }
                MenuRow(RoadsText.wwReset(lang), WeemIcons.Refresh) { done { actions.wwReset(r.way) } }
                if (r.approved) MenuRow(RoadsText.wwUnapprove(lang), WeemIcons.Close) { done { actions.unapprove(r.way) } }
            }
            WButton(RoadsText.cancel(lang), { menu = null }, kind = BtnKind.TEXT, fill = true)
        }
    }
}

@Composable
private fun GhostTab(state: RoadsScreenState, lang: Lang, actions: RoadsActions) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, bottom = WeemSpace.x4)) {
        InfoCard(RoadsText.ghostInfoTitle(lang), RoadsText.ghostInfo(lang))
        if (state.ghosts.isEmpty()) EmptyState(RoadsText.emptyList(lang), icon = WeemIcons.GpsOff)
        else SettingsGroup(null, Modifier.padding(top = WeemSpace.l)) {
            state.ghosts.forEachIndexed { i, (name, sub) ->
                ListRow(
                    name, subtitle = sub, titleStyle = WeemType.bodyM,
                    trailing = { WIconButton(WeemIcons.Close, RoadsText.delete(), { actions.removeGhost(i) }, Modifier.testTag("ghost-del-$i")) },
                )
            }
        }
        if (state.ghosts.isNotEmpty()) {
            WButton(RoadsText.ghostClearAll(lang), { confirm = true }, Modifier.padding(top = WeemSpace.l), kind = BtnKind.DANGER, icon = WeemIcons.Delete, fill = true)
        }
    }
    if (confirm) ConfirmDialog(
        title = RoadsText.ghostClearAll(lang), text = RoadsText.ghostClearConfirm(lang), confirm = RoadsText.delete(), dismiss = RoadsText.cancel(lang), danger = true,
        onConfirm = { confirm = false; actions.clearGhosts() }, onDismiss = { confirm = false },
    )
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
    val pal = palette()
    val state = remember(road, settings, lang, graph, pal) { roadsScreenState(road, settings, nav.streets.current, lang, ZoneId.systemDefault(), graph, pal) }
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
    // Y13/Y14 alt ekranları (A10.4b): sokak parçaları, ölçüm ve kalibrasyon, yol kalitesi kalibrasyonu.
    var sub by remember { mutableStateOf<SurfaceSub?>(null) }
    sub?.let { target ->
        BackHandler { sub = if (target is SurfaceSub.Street || target is SurfaceSub.Calibration) (if (target is SurfaceSub.Calibration && target.from != null) target.from else SurfaceSub.Setup.takeIf { target is SurfaceSub.Calibration }) else null }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (target) {
                is SurfaceSub.Street -> StreetPiecesScreen(lang, target.key, target.title, onBack = { sub = null }, onCalibrate = { sub = SurfaceSub.Calibration(it, target) })
                SurfaceSub.Setup -> SurfaceSetupScreen(lang, onBack = { sub = null }, onCalibration = { sub = SurfaceSub.Calibration(20, SurfaceSub.Setup) })
                is SurfaceSub.Calibration -> RoadCalibrationScreen(lang, target.band, onBack = { sub = target.from })
            }
        }
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        RoadsScreen(
            state, lang, actions, onBack, initialTab = initialTab,
            prompt = { ask?.let { a -> RoadPromptCard(a, lang) } },
            surface = { SurfaceQualitySection(lang, onStreet = { k, t -> sub = SurfaceSub.Street(k, t) }, onSetup = { sub = SurfaceSub.Setup }) },
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

/** Yollarım'ın Y13/Y14 alt ekranları. */
internal sealed interface SurfaceSub {
    data class Street(val key: String, val title: String) : SurfaceSub
    data object Setup : SurfaceSub
    data class Calibration(val band: Int, val from: SurfaceSub?) : SurfaceSub
}
