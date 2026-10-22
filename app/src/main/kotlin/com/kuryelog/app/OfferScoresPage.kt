package com.kuryelog.app

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.BtnKind
import com.kuryelog.app.ui.RadioRow
import com.kuryelog.app.ui.Segmented
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.WButton
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WSheet
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.economics.OfferScore
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.logic.economics.OfferScorePage
import com.kuryelog.logic.orders.OrdersScreen
import com.kuryelog.logic.orders.TripRecord
import java.time.ZoneId

/**
 * E4 "Teklif puanı" (A4.1, ECONOMICS.md 6; RS `MainActivity.renderOrders/renderOfferScores/scoreDayBody/pickRating`): Siparişler
 * sekmesinde "Siparişler | Teklif puanı" seçicisi ve puan sekmesinin gün sayfası. İçerik ve metinler `logic/economics`
 * [OfferScorePage]'den; burada yalnız çizim ve dokunuşlar. WEEM yalnız okur; yazdığı tek şey kuryenin kendi puanı.
 */
@Composable
fun OfferScoreSelector(lang: Lang, scoreTab: Boolean, onPick: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val labels = remember(lang) { OfferScorePage.tabs(tl(lang)) }
    Segmented(labels, if (scoreTab) 1 else 0, { onPick(it == 1) }, modifier, tags = listOf("orders-tab-orders", "orders-tab-scores"))
}

/**
 * Puan sekmesinin bir gün sayfası: açıklama kartı (bilgi düğmesiyle), ortalama fark, en büyük farklar, siparişler teklif anına göre tek
 * kartta; dokununca 1–10 sorusu alttan açılan sayfada.
 */
@Composable
fun ColumnScope.OfferScoresDay(screen: OrdersScreen, book: OfferScoreBook, zone: ZoneId, date: String, platform: String?, lang: Lang, cancel: String) {
    val ctx = LocalContext.current
    val p = palette()
    val tl = remember(lang) { tl(lang) }
    var version by remember { mutableIntStateOf(0) }
    val trips = remember(screen, date, platform) {
        screen.days().firstOrNull { it.date == date }?.trips.orEmpty().filter { platform == null || OfferScorePage.platform(it.pkg) == platform }
    }
    val page = remember(trips, version) { OfferScorePage.page(trips, book::of, zone, tl) }
    var asking by remember { mutableStateOf<TripRecord?>(null) }
    var infoOpen by remember { mutableStateOf(false) }
    val muted = p.muted.color()

    WCard(padding = WeemSpace.l, spacing = WeemSpace.s) {
        Row(verticalAlignment = Alignment.Top) {
            Text(page.intro, style = WeemType.bodyS, color = muted, modifier = Modifier.weight(1f).padding(top = WeemSpace.xs))
            WIconButton(if (infoOpen) WeemIcons.InfoFilled else WeemIcons.Info, OfferScorePage.infoToggle(tl), { infoOpen = !infoOpen }, selected = infoOpen)
        }
        if (infoOpen) Text(page.info, style = WeemType.bodyS, color = muted)
    }
    Text(
        page.summary, style = WeemType.bodyM.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = WeemSpace.xs),
        color = if (page.summaryAccent) p.accentText.color() else muted,
    )
    page.biggestTitle?.let { Text(it, style = WeemType.labelM, color = muted) }
    for (line in page.biggest) Text(line, style = WeemType.bodyS, color = muted)
    if (page.rows.isNotEmpty()) SettingsGroup(null) {
        for (row in page.rows) {
            val trip = trips.firstOrNull { it.id == row.tripId }
            val tap = if (row.rateable && trip != null) Modifier.clickable(role = Role.Button) { asking = trip } else Modifier
            Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).then(tap).padding(horizontal = WeemSpace.l, vertical = WeemSpace.m), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.label, style = WeemType.titleS)
                Text(row.text, style = WeemType.bodyS.copy(fontWeight = FontWeight.SemiBold), color = if (row.both) p.accentText.color() else muted)
            }
        }
    }

    asking?.let { trip ->
        val current = OfferScore.ratingOf(book.of(trip).second)
        val items = OfferScorePage.ratingItems(tl)
        WSheet(onDismiss = { asking = null }, title = OfferScorePage.ratingTitle(tl)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SettingsGroup(null, dividerInset = 52.dp, selectableGroup = true) {
                    items.forEachIndexed { i, label ->
                        RadioRow(label, current == i + 1, {
                            asking = null
                            if (!book.rate(trip, i + 1, OfferScore.VIA_DAY)) Toast.makeText(ctx, OfferScorePage.ratingFailed(tl), Toast.LENGTH_SHORT).show()
                            version++
                        })
                    }
                }
            }
            WButton(cancel, { asking = null }, kind = BtnKind.TEXT, fill = true)
        }
    }
}

private fun tl(lang: Lang): (String, String) -> String = { tr, az -> if (lang == Lang.AZ) az else tr }
