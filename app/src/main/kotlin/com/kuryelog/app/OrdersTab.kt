package com.kuryelog.app

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.logic.orders.CashTips
import com.kuryelog.logic.orders.DayDetail
import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.OrdersScreen
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
 * çizim ve dokunuşlar. Açıkken vardiya varsa 5 sn'de bir yenilenir.
 */
@Composable
fun OrdersTab(lang: Lang, source: (Context) -> OrdersSource = OrdersAccess::source, offerScores: (Context) -> OfferScoreBook = OrdersAccess::offerScores) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var platform by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var scoreTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(REFRESH_MS); if (Store.shiftOn(ctx)) version++ } }
    GuardedPage("orders", lang, key = version, build = { source(ctx).let { Triple(it, OrdersScreen(it, lang).warm(), offerScores(ctx)) } }) { (src, screen, book) ->
        val day = detail
        if (day != null) {
            DayDetailScreen(screen, src, day, platform.ifEmpty { null }, onBack = { detail = null }) { version++ }
        } else {
            val scores = ScoreTab(lang, book, scoreTab) { scoreTab = it }
            OrdersHome(screen, src, platform.ifEmpty { null }, { platform = it ?: "" }, selected, { selected = it }, { detail = it }, scores) { version++ }
        }
    }
}

private const val REFRESH_MS = 5_000L

/** E4 seçicisinin durumu: [on] "Teklif puanı" seçili. */
private class ScoreTab(val lang: Lang, val book: OfferScoreBook, val on: Boolean, val pick: (Boolean) -> Unit)

@Composable
private fun OrdersHome(
    screen: OrdersScreen, src: OrdersSource, platform: String?, onPlatform: (String?) -> Unit, selected: String?, onSelected: (String) -> Unit,
    onDetail: (String) -> Unit, scores: ScoreTab, onChanged: () -> Unit,
) {
    val today = remember(screen) { screen.today() }
    var metric by remember { mutableStateOf<OrderHistory.Metric?>(null) }
    var trip by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        NowCard(today.nowTitle, today.now, today.active.map { it.label to it.tripId }) { src.showCustomerCard(it); onChanged() }
        CardView(today.hero, onMetric = { metric = it })
        today.platforms.forEach { CardView(it) }
        // RS `renderOrders`: seçici gün sayfalarının üstünde; iki sekme aynı gün sayfalarını paylaşır.
        OfferScoreSelector(scores.lang, scores.on, scores.pick)
        val scoreDay: (@Composable ColumnScope.(String) -> Unit)? = if (!scores.on) null else { date ->
            OfferScoresDay(screen, scores.book, src.zone(), date, platform, scores.lang, screen.labels.cancel)
        }
        DayPages(screen, records = false, archive = false, platform = platform, onPlatform = onPlatform, selected = selected, onSelected = onSelected,
            onDetail = onDetail, onTrip = { trip = it }, scoreDay = scoreDay)
    }
    metric?.let { m -> MetricDialog(screen, m) { metric = null } }
    trip?.let { id -> TripDialog(screen, id) { trip = null } }
}

/** Şu an kartı: açık teklif ve siparişler; açık siparişlerin müşteri kartı düğmesi (canlı akış bağlıyken). */
@Composable
private fun NowCard(title: String, text: String?, buttons: List<Pair<String, String>>, onCustomerCard: (String) -> Unit) {
    if (text == null) return
    val p = LocalWeemPalette.current
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(p.surface)),
        border = BorderStroke(2.dp, Color(p.accent)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(p.accentText))
            Text(text, style = MaterialTheme.typography.bodyLarge)
            for ((label, id) in buttons) TextButton(onClick = { onCustomerCard(id) }) { Text(label) }
        }
    }
}

/**
 * Gün sayfaları: tarihler yeniden eskiye (sola kaydırma eski güne); başlıkta ‹ › okları, dokununca tarih seçici, toplamlar, km ve aktif
 * süre; platform süzgeci; sipariş satırları. [records] true: Kayıtlar sayfası ([archive]: arşivlenmiş günler), [extra] her sayfanın sonuna
 * o günün kayıt kartını koyar. [scoreDay] verilince (E4 "Teklif puanı") sayfa gövdesi odur; platform seçicisi ve "Günün tüm ayrıntıları"
 * gizlenir, seçili platform süzgeci geçerli kalır (RS aynı).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPages(
    screen: OrdersScreen, records: Boolean, archive: Boolean, platform: String?, onPlatform: (String?) -> Unit, selected: String?,
    onSelected: (String) -> Unit, onDetail: (String) -> Unit, onTrip: (String) -> Unit, extra: @Composable ColumnScope.(String) -> Unit = {},
    scoreDay: (@Composable ColumnScope.(String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val labels = screen.labels
    val dates = remember(screen, records, archive) { screen.dates(records, archive) }
    if (dates.isEmpty()) {
        Text(labels.noRecords, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
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
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tarihler azalan sırada: ‹ eski güne, › yeni güne gider.
                ArrowButton("‹", labels.older, enabled = current < dates.lastIndex) { goTo(current + 1) }
                Text(
                    header.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) {
                        val d = LocalDate.parse(dates[pager.currentPage])
                        DatePickerDialog(ctx, { _, y, m, day ->
                            val i = dates.indexOf(LocalDate.of(y, m + 1, day).toString())
                            if (i < 0) Toast.makeText(ctx, labels.noRecords, Toast.LENGTH_SHORT).show() else goTo(i)
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }.padding(vertical = 12.dp),
                )
                ArrowButton("›", labels.newer, enabled = current > 0) { goTo(current - 1) }
            }
            Text(header.totals, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(header.activity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (scoreDay == null) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = platform == null, onClick = { onPlatform(null) }, label = { Text(labels.allOrders) })
        for (name in OrderHistory.PLATFORMS) FilterChip(selected = platform == name, onClick = { onPlatform(name) }, label = { Text(name) })
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, key = { dates[it] }) { i ->
        val date = dates[i]
        if (scoreDay != null) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { scoreDay(date); extra(date) }
            return@HorizontalPager
        }
        val page = remember(screen, date, platform) { screen.page(date, platform) }
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            page.empty?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            for (row in page.rows) OrderRowView(row.tripId, row, onTrip)
            if (!records) OutlinedButton(onClick = { onDetail(date) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(labels.dayDetails) }
            extra(date)
        }
    }
    Text(header.indicator, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun ArrowButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun OrderRowView(id: String, row: com.kuryelog.logic.orders.OrderRow, onTrip: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 6.dp).clickable(role = Role.Button) { onTrip(id) }) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.showDot) Text("●", color = toneColor(row.tone), modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyMedium)
                Text(row.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(row.amounts, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ---------- Gün ayrıntısı ----------

@Composable
private fun DayDetailScreen(screen: OrdersScreen, src: OrdersSource, date: String, platform: String?, onBack: () -> Unit, onChanged: () -> Unit) {
    val detail = remember(screen, date, platform) { screen.dayDetail(date, platform) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text(screen.labels.back) }
        DayDetailBody(screen, src, detail, onChanged)
    }
}

@Composable
private fun DayDetailBody(screen: OrdersScreen, src: OrdersSource, detail: DayDetail, onChanged: () -> Unit) {
    CardView(detail.header)
    detail.cards.forEach { CardView(it) }
    detail.empty?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
    for (t in detail.trips) TripCardView(screen, src, t, onChanged)
}

/** Sipariş kartı ve (canlı akış bağlıysa) düzeltme düğmeleri: bahşişi düzenle, nakit cevabı, müşteri kartı, "Teslim ettim". */
@Composable
private fun TripCardView(screen: OrdersScreen, src: OrdersSource, tc: TripCard, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var action by remember { mutableStateOf<TripAction?>(null) }
    CardView(tc.card, footer = {
        for (a in tc.actions) OutlinedButton(
            onClick = { if (a is TripAction.CustomerCard) { src.showCustomerCard(tc.tripId); onChanged() } else action = a },
            Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(a.label) }
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
        is TripAction.ConfirmDelivered -> AlertDialog(
            onDismissRequest = { action = null }, title = { Text(a.title) }, text = { Text(a.message) },
            confirmButton = { TextButton(onClick = { src.confirmDelivered(tc.tripId); action = null; onChanged() }) { Text(a.confirm) } },
            dismissButton = { TextButton(onClick = { action = null }) { Text(labels.cancel) } },
        )
        is TripAction.CustomerCard -> action = null
    }
}

@Composable
private fun InputDialog(title: String, initial: String, labels: com.kuryelog.logic.orders.OrdersLabels, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(labels.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } },
    )
}

// ---------- Diyaloglar ----------

/** E1: bir ortalamanın dayandığı bugünkü siparişlerin listesi (saat, platform, restoran, değer ya da neden). */
@Composable
private fun MetricDialog(screen: OrdersScreen, metric: OrderHistory.Metric, onClose: () -> Unit) {
    val list = remember(screen, metric) { screen.metricList(metric) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(list.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(list.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.empty?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
                for ((text, muted) in list.rows) Text(
                    text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(screen.labels.close) } },
    )
}

/** Listeden bir siparişe dokununca: tam kart (düzeltme düğmesiz). */
@Composable
internal fun TripDialog(screen: OrdersScreen, id: String, onClose: () -> Unit) {
    val tc = remember(screen, id) { screen.tripDetail(id) }
    if (tc == null) { onClose(); return }
    AlertDialog(
        onDismissRequest = onClose, title = {},
        text = { Box(Modifier.verticalScroll(rememberScrollState())) { CardView(tc.card) } },
        confirmButton = { TextButton(onClick = onClose) { Text(screen.labels.close) } },
    )
}
