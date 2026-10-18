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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
fun OfferScoreSelector(lang: Lang, scoreTab: Boolean, onPick: (Boolean) -> Unit) {
    val labels = remember(lang) { OfferScorePage.tabs(tl(lang)) }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(selected = (i == 1) == scoreTab, onClick = { onPick(i == 1) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) {
                Text(label)
            }
        }
    }
}

/** Puan sekmesinin bir gün sayfası: açıklama, ortalama fark, en büyük farklar, siparişler teklif anına göre; dokununca 1–10 sorusu. */
@Composable
fun ColumnScope.OfferScoresDay(screen: OrdersScreen, book: OfferScoreBook, zone: ZoneId, date: String, platform: String?, lang: Lang, cancel: String) {
    val ctx = LocalContext.current
    val p = LocalWeemPalette.current
    val tl = remember(lang) { tl(lang) }
    var version by remember { mutableIntStateOf(0) }
    val trips = remember(screen, date, platform) {
        screen.days().firstOrNull { it.date == date }?.trips.orEmpty().filter { platform == null || OfferScorePage.platform(it.pkg) == platform }
    }
    val page = remember(trips, version) { OfferScorePage.page(trips, book::of, zone, tl) }
    var asking by remember { mutableStateOf<TripRecord?>(null) }
    var infoOpen by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(Modifier.fillMaxWidth().padding(top = 6.dp), colors = CardDefaults.cardColors(containerColor = Color(p.surface))) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(page.intro, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
                val toggle = OfferScorePage.infoToggle(tl)
                TextButton(onClick = { infoOpen = !infoOpen }, modifier = Modifier.size(48.dp).semantics { contentDescription = toggle }) {
                    Text("ⓘ", color = muted)
                }
            }
            if (infoOpen) Text(page.info, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
        }
    }
    Text(page.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp),
        color = if (page.summaryAccent) Color(p.accentText) else muted)
    page.biggestTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = muted, modifier = Modifier.padding(top = 8.dp)) }
    for (line in page.biggest) Text(line, style = MaterialTheme.typography.bodySmall, color = muted)
    for (row in page.rows) {
        val trip = trips.firstOrNull { it.id == row.tripId }
        val tap = if (row.rateable && trip != null) Modifier.clickable(role = Role.Button) { asking = trip } else Modifier
        Card(Modifier.fillMaxWidth().padding(top = 6.dp).then(tap), colors = CardDefaults.cardColors(containerColor = Color(p.surface))) {
            Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium)
                Text(row.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (row.both) Color(p.accentText) else muted)
            }
        }
    }

    asking?.let { trip ->
        val current = OfferScore.ratingOf(book.of(trip).second)
        val items = OfferScorePage.ratingItems(tl)
        AlertDialog(
            onDismissRequest = { asking = null }, title = { Text(OfferScorePage.ratingTitle(tl)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { i, label ->
                        val pick: () -> Unit = {
                            asking = null
                            if (!book.rate(trip, i + 1, OfferScore.VIA_DAY)) Toast.makeText(ctx, OfferScorePage.ratingFailed(tl), Toast.LENGTH_SHORT).show()
                            version++
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = current == i + 1, role = Role.RadioButton, onClick = pick),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = current == i + 1, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { asking = null }) { Text(cancel) } },
        )
    }
}

private fun tl(lang: Lang): (String, String) -> String = { tr, az -> if (lang == Lang.AZ) az else tr }
