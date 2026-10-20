package com.kuryelog.app.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
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
import com.kuryelog.logic.map.ui.MapPalette
import com.kuryelog.logic.map.ui.RouteLayer
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
    val pal = MapPalette.of(dark)
    val orange = Color(pal.orange)
    val red = Color(pal.red)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("route-panel"),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            if (nav.homeSuggest) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Text("🔋", color = orange)
                    Spacer(Modifier.width(8.dp))
                    Text(NavText.HOME_SUGGEST.get(lang), color = orange, modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.goHome) { Text(NavText.HOME_GO.get(lang)) }
                    TextButton(onClick = actions.dismissHomeSuggest) { Text("✕") }
                }
            }
            val arrived = nav.arrived
            if (arrived != null && (nav.hasRoute || nav.busy) && nav.pending == null) {
                Box(Modifier.padding(bottom = 10.dp)) { ArrivedCard(arrived, lang, orange, actions.dismissArrived) }
            }
            val stop = nav.activeStop
            val pending = nav.pending
            when {
                pending != null -> PendingCard(pending, lang, Color(pal.pink), actions)
                nav.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(nav.busyText ?: NavText.WORKING.get(lang), fontSize = 14.sp)
                }
                stop != null && nav.hasRoute -> ActiveStop(stop, nav, lang, now, Color(pal.blue), orange, Color(pal.pink))
                arrived != null -> ArrivedCard(arrived, lang, orange, actions.dismissArrived)
                else -> nav.error?.let { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠", color = red)
                        Spacer(Modifier.width(8.dp))
                        Text(e, color = red, fontSize = 15.sp, modifier = Modifier.weight(1f).testTag("route-error"))
                        if (nav.stops.isNotEmpty()) TextButton(onClick = actions.retry) { Text(NavText.RETRY.get(lang)) }
                    }
                }
            }
            if (nav.hasRoute || nav.stops.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = actions.clear, modifier = Modifier.testTag("route-finish")) { Text("✕  " + NavText.FINISH.get(lang), color = red) }
                }
            }
        }
    }
}

@Composable
private fun ActiveStop(stop: Stop, nav: NavState, lang: Lang, now: LocalDateTime, blue: Color, orange: Color, pink: Color) {
    val leg = nav.plan?.legs?.firstOrNull()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📍", color = pink)
            Spacer(Modifier.width(6.dp))
            Text(stop.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (leg != null) {
                Text(NavPanelText.eta(leg.timeS, now, lang), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = blue, modifier = Modifier.testTag("route-eta"))
            }
        }
        stop.unitNote?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = orange, modifier = Modifier.padding(top = 6.dp)) }
        leg?.let { l -> NavPanelText.streets(l.streets, lang) }?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        if (stop.confidence == StopConfidence.LOW) Text(NavText.LOW_CONFIDENCE.get(lang), fontSize = 13.sp, color = orange, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ArrivedCard(stop: Stop, lang: Lang, orange: Color, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag("route-arrived")) {
        Text("✔", fontSize = 22.sp, color = Color(0xFF2E9E6B))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(NavPanelText.arrived(stop.label, lang), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            stop.unitNote?.let { Text(it, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = orange) }
        }
        TextButton(onClick = onClose) { Text("✕") }
    }
}

@Composable
private fun PendingCard(stop: Stop, lang: Lang, pink: Color, actions: RoutePanelActions) {
    Column(Modifier.testTag("route-pending")) {
        Text(NavText.NEW_ADDRESS.get(lang), color = pink, fontWeight = FontWeight.ExtraBold)
        Text(stop.label, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Button(onClick = { actions.acceptPending(true) }, modifier = Modifier.weight(1f)) { Text(NavText.ADD_STOP.get(lang)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { actions.acceptPending(false) }, modifier = Modifier.weight(1f)) { Text(NavText.NEW_ROUTE.get(lang)) }
            TextButton(onClick = actions.dismissPending) { Text("✕") }
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
    val pal = MapPalette.of(dark)
    val color = Color(
        when (t.tone) {
            ToastTone.INFO -> pal.blue
            ToastTone.WARN -> pal.orange
            ToastTone.DANGER -> pal.red
            ToastTone.WALK -> pal.walk
        },
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
        border = BorderStroke(1.5.dp, color),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { shown = null },
    ) {
        Text(t.text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
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
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = .98f), modifier = Modifier.fillMaxSize().testTag("search-overlay")) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("←", fontSize = 20.sp) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; onQuery(it) },
                    placeholder = { Text(NavText.SEARCH_FIELD.get(lang), maxLines = 1) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onSubmit(text) }),
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("search-field"),
                )
                TextButton(onClick = { if (text.isNotBlank()) onSubmit(text) }, enabled = text.isNotBlank()) { Text("→") }
            }
            Text(NavText.SEARCH_HINT.get(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            if (view.busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            view.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp, top = 6.dp)) }
            val showList = text.trim().length >= 2 || view.items.isNotEmpty()
            if (showList) {
                if (view.items.isEmpty() && !view.busy) {
                    Text(NavText.NO_RESULTS.get(lang), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(view.items, key = { it.source + it.id }) { item ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (item.offline) "⊙" else "📍", modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                if (item.address.isNotEmpty()) Text(item.address, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (view.items.any { it.source == "google" }) {
                    Text(NavText.GOOGLE_ATTRIBUTION.get(lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

/** Uzun basış sayfası (MAP.md 6.7): satıra dokununca sayfa kapanır ve eylem çalışır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressSheet(rows: List<LongPressRow>, onPick: (LongPressAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag("long-press-sheet")) {
            for (r in rows) {
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = r.enabled) { onPick(r.action) }.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(r.title, fontWeight = FontWeight.SemiBold, color = if (r.enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                    r.subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** [RouteLayer] çizgileri ve rozetleri yüzey modeline. */
internal fun RouteStroke.toMapLine() = MapLine(id, points, color, widthDp.toFloat(), if (dashed) LinePattern.DASH else LinePattern.SOLID, z)

internal fun RoutePin.toMarker() = MapMarker(id, point, if (reached) MarkerKind.REACHED else if (first) MarkerKind.STOP_FIRST else MarkerKind.STOP, if (reached) "" else number.toString())
