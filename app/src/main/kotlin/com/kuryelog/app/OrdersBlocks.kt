package com.kuryelog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.app.ui.Dot
import com.kuryelog.app.ui.FitText
import com.kuryelog.app.ui.HeroNumber
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemRadius
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
import com.kuryelog.app.ui.color
import com.kuryelog.app.ui.container
import com.kuryelog.app.ui.ink
import com.kuryelog.app.ui.mark
import com.kuryelog.app.ui.onSurface
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.orders.Block
import com.kuryelog.logic.orders.Card as ModelCard
import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.Style
import com.kuryelog.logic.orders.Tile
import com.kuryelog.logic.orders.Tone
import com.kuryelog.logic.orders.TripPhase

/**
 * `logic/orders` kart modelini çizer (Rawstats `card`/`text`/`stat` yardımcılarının Compose karşılığı). Metin ve sıra modelden gelir.
 * A10.4d (`docs/TASARIM.md` 11–12): kart `WCard` (öne çıkan kart iç 20), platform başlığı ton hapı, karolar sütunlu ızgara (ilk sıra
 * `numM`, sonrakiler `numS`), ortalama karoları dokunulur (`raised`, ›), büyük sayı `displayM`.
 */
fun Tone.wtone(): WTone = when (this) { Tone.WOLT -> WTone.WOLT; Tone.BOLT -> WTone.BOLT; Tone.NEUTRAL -> WTone.NEUTRAL }

/** Açık siparişin aşama hapı: ton ve simge (`docs/TASARIM.md` 4; "yolda" nötr, rengi simgede). */
fun TripPhase.pill(): Pair<WTone, ImageVector> = when (this) {
    TripPhase.TO_RESTAURANT, TripPhase.TO_CUSTOMER -> WTone.MOVE to WeemIcons.TwoWheelerFilled
    TripPhase.WAITING -> WTone.RESTAURANT to WeemIcons.RestaurantFilled
    TripPhase.AT_CUSTOMER -> WTone.CUSTOMER to WeemIcons.LocationOnFilled
    TripPhase.FINISHED -> WTone.NEUTRAL to WeemIcons.CheckCircle
    TripPhase.END_UNCONFIRMED -> WTone.WARN to WeemIcons.Warning
}

@Composable
fun CardView(card: ModelCard, modifier: Modifier = Modifier, onMetric: (OrderHistory.Metric) -> Unit = {}, footer: @Composable ColumnScope.() -> Unit = {}) {
    val cols = card.blocks.filterIsInstance<Block.Tiles>().maxOfOrNull { it.items.size } ?: 0
    val primary = card.blocks.firstOrNull { it is Block.Tiles }
    WCard(modifier, outlined = card.outlined, padding = if (card.hero) WeemSpace.xl else WeemSpace.l, spacing = WeemSpace.m) {
        val blocks = card.blocks
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            val next = blocks.getOrNull(i + 1)
            when {
                // "Net kazanç" etiketi ve büyük sayı tek blok (etiket sayının hemen üstünde).
                b is Block.Line && b.style == Style.MUTED && next is Block.Big -> { HeroNumber(next.text, label = b.text); i++ }
                // Ortalama karoları (dokunulur) tek ızgarada, aralar 8.
                b.isMetricRow() -> {
                    val rows = mutableListOf(b as Block.Tiles)
                    while (blocks.getOrNull(i + 1)?.isMetricRow() == true) { i++; rows += blocks[i] as Block.Tiles }
                    Column(verticalArrangement = Arrangement.spacedBy(WeemSpace.s)) { for (r in rows) TilesRow(r, cols, primary = false, onMetric) }
                }
                else -> BlockView(b, cols, b === primary, onMetric)
            }
            i++
        }
        footer()
    }
}

private fun Block.isMetricRow() = this is Block.Tiles && items.any { it.metric != null }

@Composable
private fun BlockView(b: Block, cols: Int, primary: Boolean, onMetric: (OrderHistory.Metric) -> Unit) {
    val p = palette()
    when (b) {
        is Block.Line -> StyledText(b.text, b.style)
        is Block.Big -> FitText(b.text, style = WeemType.displayM)
        is Block.Heading -> PlatformHeading(b.text, b.tone)
        is Block.Sub -> Text(b.text, style = WeemType.titleS, modifier = Modifier.padding(top = WeemSpace.s).semantics { heading() })
        is Block.Tiles -> TilesRow(b, cols, primary, onMetric)
        is Block.Person -> Column(Modifier.fillMaxWidth().background(p.raised.color(), RoundedCornerShape(WeemRadius.tile)).padding(WeemSpace.m)) {
            Text(b.name, style = WeemType.titleS)
            Text(b.details, style = WeemType.bodyM, modifier = Modifier.padding(top = WeemSpace.xs))
        }
        Block.Divider -> HorizontalDivider(thickness = 1.dp, color = p.outline.color())
    }
}

/**
 * Platform başlığı: yalnız ad ("Wolt", "Tüm siparişler") ton hapında (nokta + kalın ad); uzun başlık ("Wolt · Müşteriye gidiliyor") nokta +
 * `titleS` platform renginde.
 */
@Composable
private fun PlatformHeading(text: String, tone: Tone) {
    val p = palette()
    val t = tone.wtone()
    if (" · " in text) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(p.mark(t).color(), size = 10.dp)
            Text(text, style = WeemType.titleS, color = p.onSurface(t).color(), modifier = Modifier.semantics { heading() })
        }
    } else {
        Row(
            Modifier.heightIn(min = 28.dp).background(p.container(t).color(), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(WeemSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(p.mark(t).color())
            Text(
                text, style = WeemType.labelL.copy(fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold), color = p.ink(t).color(),
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/** Karo sırası: [cols] sütunlu ızgaraya oturur (eksik sütun boş kalır); ortalama karoları arası 8, ötekiler 12. */
@Composable
private fun TilesRow(b: Block.Tiles, cols: Int, primary: Boolean, onMetric: (OrderHistory.Metric) -> Unit) {
    val metric = b.items.any { it.metric != null }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (metric) WeemSpace.s else WeemSpace.m)) {
        for (t in b.items) TileView(t, Modifier.weight(1f), primary, onMetric)
        repeat((cols - b.items.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier, primary: Boolean, onMetric: (OrderHistory.Metric) -> Unit) {
    val p = palette()
    val value = (if (t.tone == Tone.NEUTRAL) p.text else p.onSurface(t.tone.wtone())).color()
    val m = t.metric
    if (m == null) {
        Column(modifier) {
            FitText(t.value, style = if (primary) WeemType.numM else WeemType.numS, color = value)
            Text(t.label, style = WeemType.labelS, color = p.muted.color(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        return
    }
    // E1: dokununca o ortalamanın bugünkü siparişleri; sağ üstte ›.
    Surface(onClick = { onMetric(m) }, modifier = modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(WeemRadius.tile), color = p.raised.color(), contentColor = p.text.color()) {
        Box {
            Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 20.dp, bottom = 10.dp)) {
                FitText(t.value, style = WeemType.numS, color = value)
                Text(t.label, style = WeemType.labelS, color = p.muted.color(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(
                WeemIcons.ChevronRight, contentDescription = null, tint = p.muted.color(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 6.dp).size(16.dp),
            )
        }
    }
}

@Composable
fun StyledText(text: String, style: Style, modifier: Modifier = Modifier) {
    val p = palette()
    val m = modifier.fillMaxWidth()
    when (style) {
        Style.BODY -> Text(text, style = WeemType.bodyM, modifier = m)
        Style.MUTED -> Text(text, style = WeemType.bodyS, color = p.muted.color(), modifier = m)
        Style.BOLD -> Text(text, style = WeemType.titleS, modifier = m)
        Style.ACCENT_BOLD -> Text(text, style = WeemType.bodyM.copy(fontWeight = FontWeight.SemiBold), color = p.accentText.color(), modifier = m)
        Style.DANGER -> Text(text, style = WeemType.bodyM, color = p.danger.color(), modifier = m)
        Style.DANGER_BOLD -> Text(text, style = WeemType.bodyM.copy(fontWeight = FontWeight.SemiBold), color = p.danger.color(), modifier = m)
        Style.TITLE -> Text(text, style = WeemType.titleM, modifier = m)
    }
}
