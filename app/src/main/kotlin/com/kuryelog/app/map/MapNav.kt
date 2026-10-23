package com.kuryelog.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.FitText
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.StatusPill
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
import com.kuryelog.app.ui.withoutGlyph
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.input.SearchItem
import com.kuryelog.logic.map.input.SearchView
import com.kuryelog.logic.map.nav.NavPanelText
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.nav.NavText
import com.kuryelog.logic.map.nav.NavToast
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.nav.StopConfidence
import com.kuryelog.logic.map.nav.ToastTone
import com.kuryelog.logic.map.ui.LongPressAction
import com.kuryelog.logic.map.ui.LongPressRow
import com.kuryelog.logic.map.ui.RoutePin
import com.kuryelog.logic.map.ui.RouteStroke
import java.time.LocalDateTime

/** Rota paneli eylemleri (`BottomPanel`; MAP.md 7.11). */
class RoutePanelActions(
    val acceptPending: (addStop: Boolean) -> Unit = {},
    val dismissPending: () -> Unit = {},
    val retry: () -> Unit = {},
    val clear: () -> Unit = {},
    val dismissArrived: () -> Unit = {},
    val goHome: () -> Unit = {},
    val dismissHomeSuggest: () -> Unit = {},
)

/** Rota panelinde gösterilecek bir şey var mı (YolGo `RouteFab.hasInfo`): durak, varış, hata, bekleyen kart ya da eve dönüş önerisi. */
fun NavState.hasPanelInfo(): Boolean = stops.isNotEmpty() || arrived != null || error != null || pending != null || homeSuggest

/**
 * Rota paneli (YolGo `BottomPanel`, `widgets.dart:315-523`; MAP.md 7.11): üstte eve dönüş önerisi; çifte siparişte varış kartı sonraki rotada da; gövde önceliği bekleyen kart →
 * hesaplanıyor → etkin durak → varış kartı → hata + "Tekrar"; altta "Bitir". "Yol yok" seçicisi (Bölüm 9) A6.11'de, ortalama hız satırı (rota kaydı) A6.9'da.
 */
@Composable
fun RoutePanel(nav: NavState, lang: Lang, dark: Boolean, actions: RoutePanelActions, now: LocalDateTime = LocalDateTime.now()) {
    val p = palette()
    val shape = RoundedCornerShape(WeemRadius.panel)
    Surface(
        shape = shape,
        color = p.surface.color(),
        contentColor = p.text.color(),
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).floating(shape).testTag("route-panel"),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
            if (nav.homeSuggest) {
                // Eve dön önerisi bandı: dolgu uyarı rengi "Eve git" + kapat.
                Row(
                    Modifier.fillMaxWidth().background(p.warnBg.color(), RoundedCornerShape(WeemRadius.card)).padding(start = WeemSpace.m, top = WeemSpace.xs, bottom = WeemSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(WeemIcons.BatteryAlertFilled, contentDescription = null, tint = p.warn.color(), modifier = Modifier.size(20.dp))
                    Text(NavText.HOME_SUGGEST.get(lang), color = p.warn.color(), style = WeemType.bodyM, modifier = Modifier.weight(1f).padding(horizontal = WeemSpace.s))
                    WButton(NavText.HOME_GO.get(lang), actions.goHome, kind = BtnKind.WARN_FILLED, icon = WeemIcons.Home)
                    WIconButton(WeemIcons.Close, Str.CLOSE.get(lang), actions.dismissHomeSuggest)
                }
            }
            val arrived = nav.arrived
            if (arrived != null && (nav.hasRoute || nav.busy) && nav.pending == null) ArrivedCard(arrived, lang, actions.dismissArrived)
            val stop = nav.activeStop
            val pending = nav.pending
            var finishShown = false
            when {
                pending != null -> PendingCard(pending, lang, actions)
                nav.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.accent.color())
                    Spacer(Modifier.width(WeemSpace.m))
                    Text(nav.busyText ?: NavText.WORKING.get(lang), style = WeemType.bodyM)
                }
                stop != null && nav.hasRoute -> {
                    ActiveStop(stop, nav, lang, now, actions.clear)
                    finishShown = true
                }
                arrived != null -> ArrivedCard(arrived, lang, actions.dismissArrived)
                else -> nav.error?.let { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(WeemIcons.WarningFilled, contentDescription = null, tint = p.danger.color(), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(WeemSpace.s))
                        Text(e, color = p.danger.color(), style = WeemType.bodyM, modifier = Modifier.weight(1f).testTag("route-error"))
                        if (nav.stops.isNotEmpty()) WButton(NavText.RETRY.get(lang), actions.retry, kind = BtnKind.TEXT, icon = WeemIcons.Refresh)
                    }
                }
            }
            if (!finishShown && (nav.hasRoute || nav.stops.isNotEmpty())) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { FinishButton(lang, actions.clear) }
            }
        }
    }
}

@Composable
private fun FinishButton(lang: Lang, onClick: () -> Unit) {
    WButton(NavText.FINISH.get(lang), onClick, Modifier.testTag("route-finish"), kind = BtnKind.DANGER, icon = WeemIcons.Close)
}

/** Kapı notu bandı: uyarı zemini, kapı simgesi, büyük kalın yazı (sürüşte okunur). */
@Composable
private fun DoorNote(text: String, sizeSp: Int = 20) {
    val p = palette()
    Row(
        Modifier.fillMaxWidth().background(p.warnBg.color(), RoundedCornerShape(WeemRadius.card)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WeemIcons.DoorFront, contentDescription = null, tint = p.warn.color())
        Text(text, color = p.warn.color(), style = WeemType.titleL.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp + 6).sp, fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun ActiveStop(stop: Stop, nav: NavState, lang: Lang, now: LocalDateTime, onFinish: () -> Unit) {
    val p = palette()
    val leg = nav.plan?.legs?.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
            IconBox(WeemIcons.LocationOnFilled, tone = WTone.CUSTOMER)
            Column(Modifier.weight(1f)) {
                Text(stop.label, style = WeemType.titleM, maxLines = 2, overflow = TextOverflow.Ellipsis)
                leg?.let { l -> NavPanelText.streets(l.streets, lang) }?.let {
                    Text(it, style = WeemType.bodyS, color = p.muted.color(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        stop.unitNote?.let { DoorNote(it) }
        if (stop.confidence == StopConfidence.LOW) Banner(NavText.LOW_CONFIDENCE.get(lang).withoutGlyph(), tone = WTone.WARN)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leg != null) {
                FitText(NavPanelText.eta(leg.timeS, now, lang), style = WeemType.numM, color = p.accentText.color(), modifier = Modifier.weight(1f).testTag("route-eta"))
            } else Spacer(Modifier.weight(1f))
            FinishButton(lang, onFinish)
        }
    }
}

@Composable
private fun ArrivedCard(stop: Stop, lang: Lang, onClose: () -> Unit) {
    val p = palette()
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag("route-arrived")) {
        Icon(WeemIcons.CheckCircleFilled, contentDescription = null, tint = p.positive.color(), modifier = Modifier.padding(top = 12.dp).size(24.dp))
        Spacer(Modifier.width(WeemSpace.m))
        Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            Text(NavPanelText.arrived(stop.label, lang), style = WeemType.titleS)
            stop.unitNote?.let { DoorNote(it, sizeSp = 24) }
        }
        WIconButton(WeemIcons.Close, Str.CLOSE.get(lang), onClose)
    }
}

@Composable
private fun PendingCard(stop: Stop, lang: Lang, actions: RoutePanelActions) {
    val p = palette()
    Column(Modifier.testTag("route-pending"), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(NavText.NEW_ADDRESS.get(lang), WTone.CUSTOMER, icon = WeemIcons.AddLocationAlt, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            WIconButton(WeemIcons.Close, Str.CLOSE.get(lang), actions.dismissPending)
        }
        Text(stop.label, style = WeemType.titleM, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WButton(NavText.ADD_STOP.get(lang), { actions.acceptPending(true) }, Modifier.weight(1f), icon = WeemIcons.AddLocationAlt)
            WButton(NavText.NEW_ROUTE.get(lang), { actions.acceptPending(false) }, Modifier.weight(1f), kind = BtnKind.OUTLINE, icon = WeemIcons.AltRoute)
        }
    }
}

/** Bildirim şeridi (YolGo `ToastBanner`; MAP.md 7.11): tek bildirim, kenarlık bildirim rengi; [NavToast.seconds] sonra kalkar. */
@Composable
fun NavToastBanner(toast: NavToast?, dark: Boolean) {
    var shown by remember { mutableStateOf<NavToast?>(null) }
    LaunchedEffect(toast?.id) {
        shown = toast
        if (toast != null) {
            kotlinx.coroutines.delay(toast.seconds * 1000L)
            if (shown?.id == toast.id) shown = null
        }
    }
    val t = shown ?: return
    val p = palette()
    val (icon, tone) = when (t.tone) {
        ToastTone.INFO -> WeemIcons.InfoFilled to WTone.ACCENT
        ToastTone.WARN -> WeemIcons.WarningFilled to WTone.WARN
        ToastTone.DANGER -> WeemIcons.ErrorFilled to WTone.DANGER
        ToastTone.WALK -> WeemIcons.Route to WTone.MOVE
    }
    val shape = RoundedCornerShape(WeemRadius.card)
    Surface(
        onClick = { shown = null },
        shape = shape,
        color = p.surface.color(),
        contentColor = p.text.color(),
        modifier = Modifier.fillMaxWidth().padding(top = WeemSpace.xs).floating(shape),
    ) {
        Row(Modifier.padding(horizontal = WeemSpace.l, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = p.mark(tone).color(), modifier = Modifier.size(22.dp))
            Text(t.text.withoutGlyph(), style = WeemType.titleS, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Arama katmanı (YolGo `map_hud.dart:14-76`; MAP.md 8.8): alan odakta açılır; yazdıkça [onQuery]; Enter ya da "Rota çiz" → [onSubmit]; öneri listesi (≤ ekranın %27'si değil:
 * katman tam ekran, liste kaydırılır), satır simgesi çevrimdışı ⊙ / çevrimiçi 📍, hata satırı, öğe yoksa "Sonuç bulunamadı", Google öğesi varsa "Google Maps" atfı.
 */
@Composable
fun SearchOverlay(view: SearchView, lang: Lang, onQuery: (String) -> Unit, onSubmit: (String) -> Unit, onPick: (SearchItem) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        onQuery("")
        runCatching { focus.requestFocus() }
    }
    val p = palette()
    Surface(color = p.bg.color(), contentColor = p.text.color(), modifier = Modifier.fillMaxSize().testTag("search-overlay")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = WeemSpace.m, vertical = WeemSpace.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WIconButton(WeemIcons.ArrowBack, Str.BACK.get(lang), onClose)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; onQuery(it) },
                    placeholder = { Text(NavText.SEARCH_FIELD.get(lang), maxLines = 1) },
                    leadingIcon = { Icon(WeemIcons.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    textStyle = WeemType.bodyL,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onSubmit(text) }),
                    modifier = Modifier.weight(1f).padding(horizontal = WeemSpace.xs).focusRequester(focus).testTag("search-field"),
                )
                WIconButton(WeemIcons.AltRoute, NavText.DRAW_ROUTE.get(lang), { if (text.isNotBlank()) onSubmit(text) }, enabled = text.isNotBlank(), tinted = true)
            }
            Text(NavText.SEARCH_HINT.get(lang), style = WeemType.bodyS, color = p.muted.color(), modifier = Modifier.padding(start = 60.dp, top = WeemSpace.xs))
            if (view.busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = WeemSpace.s), color = p.accent.color())
            view.error?.let { Banner(it, Modifier.padding(top = WeemSpace.s), tone = WTone.DANGER) }
            val showList = text.trim().length >= 2 || view.items.isNotEmpty()
            if (showList) {
                if (view.items.isEmpty() && !view.busy) {
                    EmptyState(NavText.NO_RESULTS.get(lang), icon = WeemIcons.Search)
                }
                LazyColumn(Modifier.weight(1f, fill = false).padding(top = WeemSpace.s)) {
                    items(view.items, key = { it.source + it.id }) { item ->
                        ListRow(
                            item.title, subtitle = item.address.ifEmpty { null },
                            icon = if (item.offline) WeemIcons.Map else WeemIcons.LocationOn, iconTone = if (item.offline) WTone.NEUTRAL else WTone.CUSTOMER,
                            onClick = { onPick(item) },
                        )
                    }
                }
                if (view.items.any { it.source == "google" }) {
                    Text(NavText.GOOGLE_ATTRIBUTION.get(lang), style = WeemType.labelS, color = p.muted.color(), modifier = Modifier.padding(WeemSpace.s))
                }
            }
        }
    }
}

/** Uzun basış sayfası (MAP.md 6.7): satıra dokununca sayfa kapanır ve eylem çalışır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressSheet(
    rows: List<LongPressRow>,
    onPick: (LongPressAction) -> Unit,
    onDismiss: () -> Unit,
    onThumb: ((Boolean) -> Unit)? = null,
    thumbHints: Pair<String, String> = "" to "",
) {
    WSheet(onDismiss = onDismiss, padded = false) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("long-press-sheet")) {
            for (r in rows) {
                Row(Modifier.fillMaxWidth().padding(end = WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                    ListRow(r.title, Modifier.weight(1f).padding(start = WeemSpace.s), subtitle = r.subtitle, enabled = r.enabled, onClick = { onPick(r.action) })
                    // Elle puan satırı: başparmak yukarı (%90) / aşağı (%25) (MAP.md 9.4.2).
                    if (r.thumbs && onThumb != null) {
                        WIconButton(WeemIcons.ThumbUp, thumbHints.first, { onThumb(true) }, Modifier.testTag("thumb-up"), tone = WTone.POSITIVE)
                        Spacer(Modifier.width(WeemSpace.s))
                        WIconButton(WeemIcons.ThumbDown, thumbHints.second, { onThumb(false) }, Modifier.testTag("thumb-down"), tone = WTone.DANGER)
                    }
                }
            }
        }
    }
}

/** [RouteLayer] çizgileri ve rozetleri yüzey modeline. */
internal fun RouteStroke.toMapLine() = MapLine(id, points, color, widthDp.toFloat(), if (dashed) LinePattern.DASH else LinePattern.SOLID, z)

internal fun RoutePin.toMarker() = MapMarker(id, point, if (reached) MarkerKind.REACHED else if (first) MarkerKind.STOP_FIRST else MarkerKind.STOP, if (reached) "" else number.toString())

/** Yer noktası → işaret (MAP.md 5.3 türleri); kimlik sıra + konum. [onRoute] rotanın üstündeki ışık (S38, büyük). */
internal fun Poi.toMarker(i: Int, onRoute: Boolean = false) = MapMarker(
    "poi-$i-$lat-$lon", GeoPoint(lat, lon),
    when (kind) {
        'l' -> if (onRoute) MarkerKind.POI_LIGHT_ROUTE else MarkerKind.POI_LIGHT
        's' -> MarkerKind.POI_METRO
        'm' -> MarkerKind.POI_MALL
        'f' -> MarkerKind.POI_FASTFOOD
        'c' -> MarkerKind.POI_CAFE
        't' -> MarkerKind.POI_MARKET
        else -> MarkerKind.POI_RESTAURANT
    },
)
