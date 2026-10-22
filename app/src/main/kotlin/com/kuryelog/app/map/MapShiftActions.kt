package com.kuryelog.app.map

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Dot
import com.kuryelog.app.ui.StatusPill
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemMotion
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSize
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.floating
import com.kuryelog.app.ui.mark
import com.kuryelog.app.ui.onSurface
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.ui.MapText
import java.util.Locale
import kotlinx.coroutines.delay

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
    val p = palette()
    if (collapsed) {
        // A10.4d (K4): katlı hap — aşama noktası, üç eylem (56 dp), tutamak; kaydırınca ya da dokununca açılır.
        Surface(
            onClick = { expand() },
            shape = CircleShape,
            color = p.surface.color(),
            contentColor = p.text.color(),
            modifier = modifier.testTag("shift-collapsed").floating(CircleShape).swipe(MapShiftActionsDefaults.SWIPE_DP) { if (it > 0) expand() },
        ) {
            Row(Modifier.height(WeemSize.ride).padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PhaseDot(if (s.active) (if (s.current != null) s.phase.tone() else WTone.POSITIVE) else WTone.NEUTRAL, Modifier.padding(horizontal = 10.dp))
                DayButton("shift-home", WeemIcons.Home, MapText.HOME.get(lang), s.active, onHome)
                DayButton("shift-stats", WeemIcons.BarChart, MapText.STATS.get(lang), true, onStats)
                DayButton("shift-end", WeemIcons.SportsScore, MapText.END.get(lang), s.active, onEnd)
                // Tutamak: açılma yönünü gösterir (dokunma hedefi değil).
                Icon(WeemIcons.ExpandLess, contentDescription = null, tint = p.muted.color(), modifier = Modifier.padding(start = 2.dp, end = 6.dp))
            }
        }
    } else {
        ShiftCardBody(
            day, hasRoute, lang, onHome, onStats, onEnd,
            modifier.testTag("shift-card").swipe(MapShiftActionsDefaults.SWIPE_DP) { if (it < 0) setCollapsed(true) },
        )
    }
}

/** Sipariş aşamasının tonu (`docs/TASARIM.md` 4): yolda nötr hap + rota mavisi, restoranda turuncu, alındı yeşil, müşteride pembe. */
internal fun OrderPhase.tone(): WTone = when (this) {
    OrderPhase.TO_RESTAURANT, OrderPhase.TO_CUSTOMER -> WTone.MOVE
    OrderPhase.AT_RESTAURANT -> WTone.RESTAURANT
    OrderPhase.READY_FOR_CUSTOMER -> WTone.PICKED
    OrderPhase.AT_CUSTOMER -> WTone.CUSTOMER
    OrderPhase.NONE -> WTone.NEUTRAL
}

/** 12 dp aşama noktası, çevresinde %22 hâle (renk yumuşak geçer). */
@Composable
private fun PhaseDot(tone: WTone, modifier: Modifier = Modifier) {
    val c by animateColorAsState(palette().mark(tone).color(), tween(WeemMotion.STATE), label = "phase-dot")
    Box(modifier.size(20.dp).background(c.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).background(c, CircleShape))
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
private fun DayButton(tag: String, icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    WIconButton(icon, label, onClick, Modifier.testTag(tag), size = WeemSize.ride, enabled = enabled, iconSize = WeemSize.icon)
}

/** Açık kartın eylem karosu: simge + etiket, en az 72 dp; dokunma tüm karoda. */
@Composable
private fun ActionTile(tag: String, icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette()
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(WeemRadius.card), color = p.raised.color(),
        contentColor = (if (enabled) p.text else p.muted).color(),
        modifier = modifier.heightIn(min = 72.dp).testTag(tag),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = WeemSpace.s), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(WeemSize.icon))
            Spacer(Modifier.height(4.dp))
            Text(label, style = WeemType.labelS, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
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

/** Süre metni (YolGo `fmtDur`): "45 sn", "12 dk", "1 sa 5 dk". */
internal fun fmtDur(seconds: Long, lang: Lang): String {
    val s = seconds.coerceAtLeast(0)
    if (s < 60) return "$s ${MapText.SEC.get(lang)}"
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h ${MapText.HOUR.get(lang)} $m ${MapText.MIN.get(lang)}" else "$m ${MapText.MIN.get(lang)}"
}

@Composable
private fun ShiftCardBody(day: DayView, compact: Boolean, lang: Lang, onHome: () -> Unit, onStats: () -> Unit, onEnd: () -> Unit, modifier: Modifier) {
    val s: DayState = day.state
    val o = s.current
    val p = palette()
    val now = System.currentTimeMillis()
    val total = s.startedAtMs?.let { (now - it) / 1000 } ?: 0
    val line = when {
        !s.active -> MapText.DAY_OFF.get(lang)
        compact && o != null -> "#${o.n} ${phaseText(s.phase, lang)}" + if (s.queuedOrders.isEmpty()) "" else " · +${s.queuedOrders.size}"
        else -> "${fmtDur(total, lang)} · ${String.format(Locale.US, "%.1f", s.distM / 1000)} km"
    }
    val shape = RoundedCornerShape(if (compact) WeemRadius.panel else WeemRadius.panel)
    Surface(shape = shape, color = p.surface.color(), contentColor = p.text.color(), modifier = modifier.fillMaxWidth().floating(shape)) {
        if (compact) {
            // Rota varken tek satır: aşama (ya da gün) + teslim sayısı + üç eylem.
            Row(Modifier.padding(start = 6.dp, end = 4.dp).heightIn(min = WeemSize.ride), verticalAlignment = Alignment.CenterVertically) {
                PhaseDot(if (s.active) (if (o != null) s.phase.tone() else WTone.POSITIVE) else WTone.NEUTRAL, Modifier.padding(horizontal = 10.dp))
                Text(
                    line, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = WeemType.titleS,
                    color = if (o != null) p.onSurface(s.phase.tone()).color() else p.text.color(),
                )
                if (s.delivered > 0) {
                    Icon(WeemIcons.Inventory2, contentDescription = null, tint = p.muted.color(), modifier = Modifier.size(16.dp))
                    Text("${s.delivered}", style = WeemType.numS, modifier = Modifier.padding(start = 2.dp, end = 4.dp))
                }
                DayButton("shift-home", WeemIcons.Home, MapText.HOME.get(lang), s.active, onHome)
                DayButton("shift-stats", WeemIcons.BarChart, MapText.STATS.get(lang), true, onStats)
                DayButton("shift-end", WeemIcons.SportsScore, MapText.END.get(lang), s.active, onEnd)
            }
            return@Surface
        }
        Column(Modifier.padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.s, bottom = WeemSpace.l), verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
            // Tutamak: kaydırma yönünü gösterir (dokunma hedefi değil; sola kaydırınca ya da dışarı dokununca katlanır).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 32.dp, height = 4.dp).background(p.high.color(), CircleShape))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot((if (s.active) p.positive else p.muted).color())
                Spacer(Modifier.width(WeemSpace.s))
                Text(line, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = WeemType.titleS)
            }
            if (s.active) {
                val done = s.orders.filter { it.deliveredAtMs != null && !it.cancelled }.map { it.n }.toSet().size
                Text("${MapText.COMPLETED_ORDERS.get(lang)}: $done", style = WeemType.bodyS, color = p.muted.color(), modifier = Modifier.testTag("completed-orders"))
            }
            if (s.active && o == null) Text(MapText.NO_ORDER.get(lang), style = WeemType.bodyS, color = p.muted.color())
            if (o != null) {
                OrderRow(o, s.phase, lang, now)
                for (other in s.queuedOrders) {
                    Text(
                        "#${other.n} · " + if (other.restLeftAtMs != null) MapText.QUEUED_PICKED.get(lang) else MapText.QUEUED_WAITING.get(lang),
                        style = WeemType.bodyS, color = p.muted.color(),
                    )
                }
            }
            Row(Modifier.padding(top = WeemSpace.xs), horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
                ActionTile("shift-home", WeemIcons.Home, MapText.HOME.get(lang), s.active, onHome, Modifier.weight(1f))
                ActionTile("shift-stats", WeemIcons.BarChart, MapText.STATS.get(lang), true, onStats, Modifier.weight(1f))
                ActionTile("shift-end", WeemIcons.SportsScore, MapText.END.get(lang), s.active, onEnd, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrderRow(order: OrderRec, phase: OrderPhase, lang: Lang, now: Long) {
    val clockStart = when (phase) {
        OrderPhase.AT_RESTAURANT -> order.restArrivedAtMs
        OrderPhase.READY_FOR_CUSTOMER, OrderPhase.TO_CUSTOMER -> order.restLeftAtMs
        OrderPhase.AT_CUSTOMER -> order.custArrivedAtMs
        else -> order.startedAtMs
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WeemSpace.s)) {
        StatusPill("#${order.n} ${phaseText(phase, lang)}", phase.tone(), Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (clockStart != null) Text(fmtDur((now - clockStart) / 1000, lang), style = WeemType.numS, color = palette().muted.color())
    }
}
