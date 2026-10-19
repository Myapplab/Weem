package com.kuryelog.app.map

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.LocalWeemPalette
import com.kuryelog.logic.app.Palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.MapText
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.ui.MapPalette
import kotlinx.coroutines.delay
import java.util.Locale

object MapShiftActionsDefaults {
    /** Kart açıldıktan sonra kendiliğinden simgelere döner (1.7.0: 20 sn). */
    const val COLLAPSE_AFTER_MS = 20_000L

    /** Soldan sağa (açar) ya da sağdan sola (katlar) en az bu kadar kaydırma (dp). */
    const val SWIPE_DP = 24f
}

/**
 * Harita sekmesinin gün eylemleri (YolGo `ShiftBar`; MAP.md 6.12, SORU-N7): katlı hâlde (`shift-collapsed`) tek sıra, sol kenarda üç simge — Eve dön (`shift-home`),
 * İstatistik (`shift-stats`), Günü bitir (`shift-end`); soldan sağa kaydırınca açık kart (`shift-card`: aşama metni, rota varken "#N"); yeni sipariş ya da dışarı dokunuş
 * kartı katlar ([collapseSignal] değişince — ana ekran dışarı dokunuşta artırır). Gün kapalıyken Eve dön ve Günü bitir pasif. Sipariş düğmeleri yok (aşamayı yalnız ekran
 * okuması ilerletir). Onay pencereleri (Eve dön, Günü bitir) çağıranın ([onHome], [onEnd]). [onCollapsedChange] katlı mı (konum ve rota düğmeleri kartın yanına iner).
 */
@Composable
fun MapShiftActions(
    day: DayView,
    hasRoute: Boolean,
    lang: Lang,
    onHome: () -> Unit,
    onStats: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    collapseSignal: Int = 0,
    onCollapsedChange: (Boolean) -> Unit = {},
) {
    var collapsed by remember { mutableStateOf(true) }
    var opened by remember { mutableIntStateOf(0) }
    fun setCollapsed(v: Boolean) {
        collapsed = v
        onCollapsedChange(v)
    }
    fun expand() {
        setCollapsed(false)
        opened++
    }
    // Kartı açan değişiklikler (saniye sayaçları değil): gün, sipariş no, aşama, sıradakiler, teslim sayısı, ışık; rota.
    val s = day.state
    val signature = "${s.active}|${s.current?.n}|${s.phase}|${s.queuedOrders.size}|${s.delivered}|${day.lightSinceMs != null}|$hasRoute"
    var lastSignature by remember { mutableStateOf(signature) }
    if (signature != lastSignature) {
        lastSignature = signature
        expand()
    }
    LaunchedEffect(opened) {
        if (opened == 0) return@LaunchedEffect
        delay(MapShiftActionsDefaults.COLLAPSE_AFTER_MS)
        setCollapsed(true)
    }
    var lastSignal by remember { mutableIntStateOf(collapseSignal) }
    if (collapseSignal != lastSignal) {
        lastSignal = collapseSignal
        if (!collapsed) setCollapsed(true)
    }
    val buttons = @Composable { cell: Boolean -> DayButtons(s.active, lang, cell, onHome, onStats, onEnd) }
    if (collapsed) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
            shadowElevation = 3.dp,
            modifier = modifier.testTag("shift-collapsed").swipe(MapShiftActionsDefaults.SWIPE_DP) { if (it > 0) expand() },
        ) {
            Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                buttons(true)
                // Tutamak: kaydırma yönünü gösterir (dokunma hedefi değil; kart kaydırınca açılır).
                Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp).padding(start = 6.dp))
            }
        }
    } else {
        ShiftCardBody(day, hasRoute, lang, buttons, modifier.testTag("shift-card").swipe(MapShiftActionsDefaults.SWIPE_DP) { if (it < 0) setCollapsed(true) })
    }
}

/** Yatay kaydırma: toplam [minDp] aşılınca işaretiyle [onSwipe]; dokunuşlar düğmelere gider. */
private fun Modifier.swipe(minDp: Float, onSwipe: (Float) -> Unit): Modifier = pointerInput(Unit) {
    var drag = 0f
    detectHorizontalDragGestures(
        onDragStart = { drag = 0f },
        onDragEnd = { if (kotlin.math.abs(drag) > minDp.dp.toPx()) onSwipe(drag) },
        onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
    )
}

@Composable
private fun DayButtons(active: Boolean, lang: Lang, cell: Boolean, onHome: () -> Unit, onStats: () -> Unit, onEnd: () -> Unit) {
    Row {
        DayButton("shift-home", "⌂", MapText.HOME.get(lang), active, cell, onHome)
        DayButton("shift-stats", "▥", MapText.STATS.get(lang), true, cell, onStats)
        DayButton("shift-end", "⏹", MapText.END.get(lang), active, cell, onEnd)
    }
}

@Composable
private fun DayButton(tag: String, glyph: String, label: String, enabled: Boolean, cell: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = (if (cell) Modifier.size(50.dp) else Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)).testTag(tag).semantics { contentDescription = label },
    ) {
        Text(glyph, fontSize = 24.sp)
    }
}

/** Aşamanın metni ve rengi (YolGo `_phaseInfo`; metinler `:orders` ile aynı). */
private fun phaseText(phase: OrderPhase, lang: Lang): String = when (phase) {
    OrderPhase.TO_RESTAURANT -> MapText.PHASE_TO_RESTAURANT.get(lang)
    OrderPhase.AT_RESTAURANT -> MapText.PHASE_AT_RESTAURANT.get(lang)
    OrderPhase.READY_FOR_CUSTOMER -> MapText.PHASE_READY.get(lang)
    OrderPhase.TO_CUSTOMER -> MapText.PHASE_TO_CUSTOMER.get(lang)
    OrderPhase.AT_CUSTOMER -> MapText.PHASE_AT_CUSTOMER.get(lang)
    OrderPhase.NONE -> MapText.PHASE_NONE.get(lang)
}

private fun phaseColor(phase: OrderPhase, p: MapPalette): Color = Color(
    when (phase) {
        OrderPhase.AT_RESTAURANT -> p.food
        OrderPhase.READY_FOR_CUSTOMER -> p.green
        OrderPhase.AT_CUSTOMER -> p.pink
        OrderPhase.NONE -> p.textLo
        else -> p.blue
    },
)

/** Süre metni (YolGo `fmtDur`): "45 sn", "12 dk", "1 sa 5 dk". */
internal fun fmtDur(seconds: Long, lang: Lang): String {
    val s = seconds.coerceAtLeast(0)
    if (s < 60) return "$s ${MapText.SEC.get(lang)}"
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h ${MapText.HOUR.get(lang)} $m ${MapText.MIN.get(lang)}" else "$m ${MapText.MIN.get(lang)}"
}

@Composable
private fun ShiftCardBody(day: DayView, compact: Boolean, lang: Lang, buttons: @Composable (Boolean) -> Unit, modifier: Modifier) {
    val s: DayState = day.state
    val o = s.current
    val palette = MapPalette.of(LocalWeemPalette.current == Palette.dark)
    val now = System.currentTimeMillis()
    val total = s.startedAtMs?.let { (now - it) / 1000 } ?: 0
    val line = when {
        !s.active -> MapText.DAY_OFF.get(lang)
        compact && o != null -> "#${o.n} ${phaseText(s.phase, lang)}" + if (s.queuedOrders.isEmpty()) "" else " · +${s.queuedOrders.size}"
        else -> "${fmtDur(total, lang)} · ${String.format(Locale.US, "%.1f", s.distM / 1000)} km" + if (compact && s.delivered > 0) " · 📦${s.delivered}" else ""
    }
    Surface(
        shape = RoundedCornerShape(if (compact) 16.dp else 22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, top = if (compact) 6.dp else 12.dp, end = 4.dp, bottom = if (compact) 6.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", fontSize = 8.sp, color = if (s.active) Color(palette.green) else Color(palette.textLo))
                Spacer(Modifier.width(6.dp))
                Text(
                    line, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    fontSize = if (compact) 15.sp else 13.sp, fontWeight = if (compact) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (compact && o != null) phaseColor(s.phase, palette) else MaterialTheme.colorScheme.onSurface,
                )
                buttons(false)
            }
            day.lightSinceMs?.let { LightWaitRow(it, lang, palette) }
            if (!compact) {
                val lo = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 8.dp)) {
                    if (s.active) {
                        val done = s.orders.filter { it.deliveredAtMs != null && !it.cancelled }.map { it.n }.toSet().size
                        Text("${MapText.COMPLETED_ORDERS.get(lang)}: $done", style = lo, modifier = Modifier.testTag("completed-orders"))
                    }
                    if (s.active && o == null) {
                        Text(MapText.NO_ORDER.get(lang), style = lo)
                    }
                    if (o != null) {
                        OrderRow(o, s.phase, lang, palette, now)
                        for (other in s.queuedOrders) {
                            Text("#${other.n} · " + if (other.restLeftAtMs != null) MapText.QUEUED_PICKED.get(lang) else MapText.QUEUED_WAITING.get(lang), style = lo)
                        }
                    }
                }
            }
        }
    }
}

/** Işıkta bekleme sayacı (YolGo `LightWaitChip`, MAP.md 11.9): turuncu, `m:ss`, 1 sn'de bir; katlı kartta da görünür. */
@Composable
private fun LightWaitRow(sinceMs: Long, lang: Lang, palette: MapPalette) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val s = ((now - sinceMs) / 1000).coerceAtLeast(0)
    Row(Modifier.padding(top = 4.dp).testTag("light-wait"), verticalAlignment = Alignment.CenterVertically) {
        Text("🚦 ${MapText.LIGHT_WAITING.get(lang)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(palette.orange), modifier = Modifier.weight(1f))
        Text("${s / 60}:${(s % 60).toString().padStart(2, '0')}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(palette.orange))
    }
}

@Composable
private fun OrderRow(order: OrderRec, phase: OrderPhase, lang: Lang, palette: MapPalette, now: Long) {
    val clockStart = when (phase) {
        OrderPhase.AT_RESTAURANT -> order.restArrivedAtMs
        OrderPhase.READY_FOR_CUSTOMER, OrderPhase.TO_CUSTOMER -> order.restLeftAtMs
        OrderPhase.AT_CUSTOMER -> order.custArrivedAtMs
        else -> order.startedAtMs
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "#${order.n} ${phaseText(phase, lang)}", color = phaseColor(phase, palette), fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (clockStart != null) Text(fmtDur((now - clockStart) / 1000, lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
