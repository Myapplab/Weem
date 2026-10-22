package com.kuryelog.app

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Store
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.ConfirmDialog
import com.kuryelog.app.ui.Dot
import com.kuryelog.app.ui.EmptyState
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.ScreenTitle
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.SubScreen
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WChip
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.mark
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.logic.orders.CashTips
import com.kuryelog.logic.orders.DayDetail
import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.OrderRow
import com.kuryelog.logic.orders.OrdersScreen
import com.kuryelog.logic.orders.OrdersSessions
import com.kuryelog.logic.orders.OrdersTools
import com.kuryelog.logic.orders.OrdersSource
import com.kuryelog.logic.orders.TripAction
import com.kuryelog.logic.orders.TripCard
import com.kuryelog.logic.orders.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Siparişler sekmesi (A3.8, ORDERS.md 10.3): "Bugün" kartları, gün sayfaları (sola kaydırma eski güne), gün ayrıntısı; "Siparişler |
 * Teklif puanı" seçicisi (E4, A4.1). İçerik ve metinler `logic/orders` [OrdersScreen]'den ve `logic/economics`'ten (testli); burada yalnız
 * çizim ve dokunuşlar. Açıkken vardiya varsa 5 sn'de bir yenilenir. A10.4d (`docs/TASARIM.md` 12): başlık, canlı ŞU AN kartı, net kazanç
 * kartı, platform kartları, oturumlar satırı, Araçlar, segment seçici, gün başlığı, süzgeç çipleri, siparişler tek kartta.
 */
@Composable
fun OrdersTab(lang: Lang, source: (Context) -> OrdersSource = OrdersAccess::source, offerScores: (Context) -> OfferScoreBook = OrdersAccess::offerScores) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var platform by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var scoreTab by rememberSaveable { mutableStateOf(false) }
    var sessionsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(REFRESH_MS); if (Store.shiftOn(ctx)) version++ } }
    GuardedPage("orders", lang, key = version, build = { source(ctx).let { Triple(it, OrdersScreen(it, lang).warm(), offerScores(ctx)) } }) { (src, screen, book) ->
        val day = detail
        if (sessionsOpen) {
            SessionsPageView(OrdersSessions(src, lang), screen.labels.back) { sessionsOpen = false }
        } else if (day != null) {
            DayDetailScreen(screen, src, day, platform.ifEmpty { null }, onBack = { detail = null }) { version++ }
        } else {
            val scores = ScoreTab(lang, book, scoreTab) { scoreTab = it }
            OrdersHome(screen, src, platform.ifEmpty { null }, { platform = it ?: "" }, selected, { selected = it }, { detail = it }, scores, { sessionsOpen = true }) { version++ }
        }
    }
}

private const val REFRESH_MS = 5_000L

/** E4 seçicisinin durumu: [on] "Teklif puanı" seçili. */
private class ScoreTab(val lang: Lang, val book: OfferScoreBook, val on: Boolean, val pick: (Boolean) -> Unit)

@Composable
private fun OrdersHome(
    screen: OrdersScreen, src: OrdersSource, platform: String?, onPlatform: (String?) -> Unit, selected: String?, onSelected: (String) -> Unit,
    onDetail: (String) -> Unit, scores: ScoreTab, onSessions: () -> Unit, onChanged: () -> Unit,
) {
    val today = remember(screen) { screen.today() }
    var metric by remember { mutableStateOf<OrderHistory.Metric?>(null) }
    var trip by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.s, bottom = WeemSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(WeemSpace.m),
    ) {
        ScreenTitle(screen.labels.ordersTab)
        NowCard(today.nowTitle, today.now, today.active.map { it.label to it.tripId }) { src.showCustomerCard(it); onChanged() }
        CardView(today.hero, onMetric = { metric = it })
        today.report?.let { CardView(it) }
        today.platforms.forEach { CardView(it) }
        // A10.4a (S20): bugünün çevrimiçi oturumları ve Araçlar (bölünmüş ekran, iptal gözlemi; yalnız vardiya açıkken).
        val sessions = remember(src, scores.lang) { OrdersSessions(src, scores.lang) }
        SettingsGroup(null) { ListRow(sessions.title, icon = WeemIcons.History, chevron = true, onClick = onSessions) }
        val tools = remember(src, scores.lang) { OrdersTools(src, scores.lang) }
        if (tools.visible) ToolsCard(tools)
        // RS `renderOrders`: seçici gün sayfalarının üstünde; iki sekme aynı gün sayfalarını paylaşır.
        OfferScoreSelector(scores.lang, scores.on, scores.pick, Modifier.padding(top = WeemSpace.s))
        val scoreDay: (@Composable ColumnScope.(String) -> Unit)? = if (!scores.on) null else { date ->
            OfferScoresDay(screen, scores.book, src.zone(), date, platform, scores.lang, screen.labels.cancel)
        }
        DayPages(screen, records = false, archive = false, platform = platform, onPlatform = onPlatform, selected = selected, onSelected = onSelected,
            onDetail = onDetail, onTrip = { trip = it }, scoreDay = scoreDay)
    }
    metric?.let { m -> MetricDialog(screen, m) { metric = null } }
    trip?.let { id -> TripDialog(screen, id) { trip = null } }
}

/**
 * Şu an kartı (canlı: 2 dp vurgu kenarı + canlı nokta): açık teklif ve siparişler, platform satırları noktalı; açık siparişlerin müşteri
 * kartı tonal düğmesi (canlı akış bağlıyken).
 */
@Composable
private fun NowCard(title: String, text: String?, buttons: List<Pair<String, String>>, onCustomerCard: (String) -> Unit) {
    if (text == null) return
    val p = palette()
    WCard(live = true, spacing = WeemSpace.m) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(p.accentContainer.color(), CircleShape), contentAlignment = Alignment.Center) { Dot(p.accent.color(), size = 10.dp) }
            Text(title, style = WeemType.labelM, color = p.accentText.color())
        }
        Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            for (line in text.lines().filter { it.isNotBlank() }) {
                val name = OrderHistory.PLATFORMS.firstOrNull { line.startsWith(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (name != null) Dot(p.mark(Tone.of(name).wtone()).color())
                    Text(line, style = WeemType.bodyM)
                }
            }
        }
        for ((label, id) in buttons) WButton(label, { onCustomerCard(id) }, kind = BtnKind.TONAL, icon = WeemIcons.Person, fill = true)
    }
}

/**
 * Gün sayfaları: tarihler yeniden eskiye (sola kaydırma eski güne); başlık kartında önceki/sonraki gün düğmeleri, dokununca tarih seçici,
 * toplamlar, km ve aktif süre; platform süzgeci; sipariş satırları tek kartta. [records] true: Kayıtlar sayfası ([archive]: arşivlenmiş
 * günler), [extra] her sayfanın sonuna o günün kayıt kartını koyar. [scoreDay] verilince (E4 "Teklif puanı") sayfa gövdesi odur; platform
 * seçicisi ve "Günün tüm ayrıntıları" gizlenir, seçili platform süzgeci geçerli kalır (RS aynı).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPages(
    screen: OrdersScreen, records: Boolean, archive: Boolean, platform: String?, onPlatform: (String?) -> Unit, selected: String?,
    onSelected: (String) -> Unit, onDetail: (String) -> Unit, onTrip: (String) -> Unit, modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.(String) -> Unit = {}, scoreDay: (@Composable ColumnScope.(String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val p = palette()
    val labels = screen.labels
    val dates = remember(screen, records, archive) { screen.dates(records, archive) }
    if (dates.isEmpty()) {
        EmptyState(labels.noRecords, modifier, icon = WeemIcons.ReceiptLong)
        return
    }
    val pager = rememberPagerState(initialPage = screen.index(dates, selected).coerceAtLeast(0)) { dates.size }
    val scope = rememberCoroutineScope()
    // Yeni gün eklendi ya da silindi: seçili gün yerinde kalır.
    LaunchedEffect(dates) { screen.index(dates, selected).takeIf { it >= 0 && it != pager.currentPage }?.let { pager.scrollToPage(it) } }
    LaunchedEffect(pager.settledPage) { dates.getOrNull(pager.settledPage)?.let(onSelected) }
    val current = pager.currentPage.coerceIn(dates.indices)
    val header = remember(screen, dates, current) { screen.header(dates[current], current, dates.size) }
    fun goTo(i: Int) {
        val target = i.coerceIn(dates.indices)
        scope.launch { if (ValueAnimator.areAnimatorsEnabled()) pager.animateScrollToPage(target) else pager.scrollToPage(target) }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        WCard(padding = WeemSpace.s, spacing = WeemSpace.xs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tarihler azalan sırada: sol ok eski güne, sağ ok yeni güne gider.
                WIconButton(WeemIcons.ChevronLeft, labels.older, { goTo(current + 1) }, enabled = current < dates.lastIndex)
                Text(
                    header.title, style = WeemType.titleS, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) {
                        val d = LocalDate.parse(dates[pager.currentPage])
                        DatePickerDialog(ctx, { _, y, m, day ->
                            val i = dates.indexOf(LocalDate.of(y, m + 1, day).toString())
                            if (i < 0) Toast.makeText(ctx, labels.noRecords, Toast.LENGTH_SHORT).show() else goTo(i)
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }.padding(vertical = 12.dp),
                )
                WIconButton(WeemIcons.ChevronRight, labels.newer, { goTo(current - 1) }, enabled = current > 0)
            }
            Text(header.totals, style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(header.activity, style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        }
        if (scoreDay == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(WeemSpace.s), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            WChip(labels.allOrders, platform == null, { onPlatform(null) })
            for (name in OrderHistory.PLATFORMS) WChip(name, platform == name, { onPlatform(name) }, dot = p.mark(Tone.of(name).wtone()).color())
        }
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, pageSpacing = WeemSpace.l, key = { dates[it] }) { i ->
            val date = dates[i]
            if (scoreDay != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) { scoreDay(date); extra(date) }
                return@HorizontalPager
            }
            val page = remember(screen, date, platform) { screen.page(date, platform) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
                page.empty?.let { EmptyState(it, icon = WeemIcons.ReceiptLong) }
                if (page.rows.isNotEmpty()) SettingsGroup(null, dividerInset = if (page.rows.any { it.showDot }) 38.dp else WeemSpace.l) {
                    for (row in page.rows) OrderRowView(row, onTrip)
                }
                if (!records) WButton(labels.dayDetails, { onDetail(date) }, kind = BtnKind.OUTLINE, fill = true)
                extra(date)
            }
        }
        Text(header.indicator, Modifier.fillMaxWidth(), style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.Center)
    }
}

/** Sipariş satırı: platform noktası, saat · restoran, alt satır, açık siparişte aşama hapı; sağda Brüt üstte ikincil, Net altta kalın. */
@Composable
private fun OrderRowView(row: OrderRow, onTrip: (String) -> Unit) {
    val p = palette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onTrip(row.tripId) }.padding(horizontal = WeemSpace.l, vertical = WeemSpace.m),
        horizontalArrangement = Arrangement.spacedBy(WeemSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.showDot) Dot(p.mark(row.tone.wtone()).color(), size = 10.dp)
        Column(Modifier.weight(1f)) {
            Text(row.title, style = WeemType.titleS, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.line, style = WeemType.bodyS, color = p.muted.color(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.status?.let { s ->
                val pill = row.phase?.pill()
                StatusPill(s, pill?.first ?: WTone.NEUTRAL, Modifier.padding(top = 6.dp), icon = pill?.second)
            }
        }
        val amounts = row.amounts.split('\n')
        Column(horizontalAlignment = Alignment.End) {
            if (amounts.size == 2) {
                Text(amounts[0], style = WeemType.bodyS, color = p.muted.color())
                Text(amounts[1], style = WeemType.numS)
            } else {
                Text(row.amounts, style = WeemType.bodyS, color = p.muted.color(), textAlign = TextAlign.End)
            }
        }
    }
}

// ---------- Gün ayrıntısı ----------

@Composable
private fun DayDetailScreen(screen: OrdersScreen, src: OrdersSource, date: String, platform: String?, onBack: () -> Unit, onChanged: () -> Unit) {
    val detail = remember(screen, date, platform) { screen.dayDetail(date, platform) }
    SubScreen(screen.labels.dayDetails, screen.labels.back, onBack) {
        DayDetailBody(screen, src, detail, onChanged)
    }
}

@Composable
private fun DayDetailBody(screen: OrdersScreen, src: OrdersSource, detail: DayDetail, onChanged: () -> Unit) {
    CardView(detail.header)
    detail.cards.forEach { CardView(it) }
    detail.empty?.let { EmptyState(it, icon = WeemIcons.ReceiptLong) }
    for (t in detail.trips) TripCardView(screen, src, t, onChanged)
}

/** Sipariş kartı ve (canlı akış bağlıysa) düzeltme düğmeleri: bahşişi düzenle, nakit cevabı, müşteri kartı, "Teslim ettim". */
@Composable
private fun TripCardView(screen: OrdersScreen, src: OrdersSource, tc: TripCard, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var action by remember { mutableStateOf<TripAction?>(null) }
    CardView(tc.card, footer = {
        for (a in tc.actions) WButton(
            a.label,
            { if (a is TripAction.CustomerCard) { src.showCustomerCard(tc.tripId); onChanged() } else action = a },
            kind = if (a is TripAction.CustomerCard) BtnKind.TONAL else BtnKind.OUTLINE,
            icon = when (a) {
                is TripAction.EditTip -> WeemIcons.Edit
                is TripAction.CashAnswer -> WeemIcons.Payments
                is TripAction.CustomerCard -> WeemIcons.Person
                is TripAction.ConfirmDelivered -> WeemIcons.CheckCircle
            },
            fill = true,
        )
    })
    val labels = screen.labels
    when (val a = action) {
        null -> {}
        is TripAction.EditTip -> InputDialog(a.title, a.initial, labels, onDismiss = { action = null }) { text ->
            text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { src.setTip(tc.tripId, it) }
            action = null; onChanged()
        }
        is TripAction.CashAnswer -> InputDialog(a.title, a.initial.orEmpty(), labels, onDismiss = { action = null }) { text ->
            val got = CashTips.parseInput(text)
            if (got == null || src.recordCashTip(tc.tripId, got) == null) Toast.makeText(ctx, a.badInput, Toast.LENGTH_SHORT).show()
            action = null; onChanged()
        }
        is TripAction.ConfirmDelivered -> ConfirmDialog(
            title = a.title, text = a.message, confirm = a.confirm, dismiss = labels.cancel,
            onConfirm = { src.confirmDelivered(tc.tripId); action = null; onChanged() }, onDismiss = { action = null },
        )
        is TripAction.CustomerCard -> action = null
    }
}

@Composable
private fun InputDialog(title: String, initial: String, labels: com.kuryelog.logic.orders.OrdersLabels, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    ConfirmDialog(
        title = title, confirm = labels.save, dismiss = labels.cancel, onConfirm = { onSave(text) }, onDismiss = onDismiss,
        body = {
            OutlinedTextField(
                text, { text = it }, singleLine = true, textStyle = WeemType.numM, shape = RoundedCornerShape(WeemRadius.tile),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ---------- Pencereler ----------

/** E1: bir ortalamanın dayandığı bugünkü siparişlerin listesi (saat, platform, restoran, değer ya da neden); alttan açılan sayfa. */
@Composable
private fun MetricDialog(screen: OrdersScreen, metric: OrderHistory.Metric, onClose: () -> Unit) {
    val p = palette()
    val list = remember(screen, metric) { screen.metricList(metric) }
    WSheet(onDismiss = onClose, title = list.title) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(list.summary, style = WeemType.bodyS, color = p.muted.color())
            list.empty?.let { Text(it, style = WeemType.bodyM, color = p.muted.color()) }
            for ((text, muted) in list.rows) Text(text, style = WeemType.bodyM, color = (if (muted) p.muted else p.text).color())
        }
        WButton(screen.labels.close, onClose, kind = BtnKind.TEXT, fill = true)
    }
}

/** Listeden bir siparişe dokununca: tam kart (düzeltme düğmesiz), alttan açılan sayfada. */
@Composable
internal fun TripDialog(screen: OrdersScreen, id: String, onClose: () -> Unit) {
    val tc = remember(screen, id) { screen.tripDetail(id) }
    if (tc == null) { onClose(); return }
    WSheet(onDismiss = onClose) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { CardView(tc.card) }
        WButton(screen.labels.close, onClose, kind = BtnKind.TEXT, fill = true)
    }
}
