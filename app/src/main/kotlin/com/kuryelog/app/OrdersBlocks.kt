package com.kuryelog.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.orders.Block
import com.kuryelog.logic.orders.Card as ModelCard
import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.Style
import com.kuryelog.logic.orders.Tile
import com.kuryelog.logic.orders.Tone

/** `logic/orders` kart modelini çizer (Rawstats `card`/`text`/`stat` yardımcılarının Compose karşılığı). Metin ve sıra modelden gelir. */
@Composable
fun toneColor(tone: Tone): Color {
    val p = LocalWeemPalette.current
    return Color(when (tone) { Tone.WOLT -> p.wolt; Tone.BOLT -> p.bolt; Tone.NEUTRAL -> p.text })
}

@Composable
fun CardView(card: ModelCard, modifier: Modifier = Modifier, onMetric: (OrderHistory.Metric) -> Unit = {}, footer: @Composable ColumnScope.() -> Unit = {}) {
    val p = LocalWeemPalette.current
    val container = Color(when {
        card.tone == Tone.WOLT -> p.woltBg
        card.tone == Tone.BOLT -> p.boltBg
        card.hero -> p.raised
        else -> p.surface
    })
    Card(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (card.outlined) BorderStroke(2.dp, toneColor(if (card.tone == Tone.NEUTRAL) Tone.WOLT else card.tone)) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (b in card.blocks) BlockView(b, onMetric)
            footer()
        }
    }
}

@Composable
private fun BlockView(b: Block, onMetric: (OrderHistory.Metric) -> Unit) {
    val p = LocalWeemPalette.current
    when (b) {
        is Block.Line -> StyledText(b.text, b.style)
        is Block.Big -> Text(b.text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
        is Block.Heading -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = toneColor(b.tone), modifier = Modifier.padding(end = 8.dp))
            Text(b.text, color = toneColor(b.tone), fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }
        is Block.Sub -> Text(b.text, color = Color(p.accentText), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
        is Block.Tiles -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in b.items) TileView(t, Modifier.weight(1f), onMetric)
        }
        is Block.Person -> Surface(Modifier.fillMaxWidth().padding(top = 6.dp), color = Color(p.raised), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp)) {
                Text(b.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(b.details, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Block.Divider -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier, onMetric: (OrderHistory.Metric) -> Unit) {
    val m = t.metric
    Column(
        modifier.heightIn(min = 48.dp).let { if (m != null) it.clickable(role = Role.Button) { onMetric(m) } else it }.padding(vertical = 4.dp),
    ) {
        Text(t.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (t.tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSurface else toneColor(t.tone))
        Text(t.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StyledText(text: String, style: Style, modifier: Modifier = Modifier) {
    val p = LocalWeemPalette.current
    val scheme = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    when (style) {
        Style.BODY -> Text(text, style = typo.bodyMedium, modifier = modifier.fillMaxWidth())
        Style.MUTED -> Text(text, style = typo.bodySmall, color = scheme.onSurfaceVariant, modifier = modifier.fillMaxWidth())
        Style.BOLD -> Text(text, style = typo.titleSmall, fontWeight = FontWeight.Bold, modifier = modifier.fillMaxWidth())
        Style.ACCENT_BOLD -> Text(text, style = typo.bodyMedium, fontWeight = FontWeight.Bold, color = Color(p.accentText), modifier = modifier.fillMaxWidth())
        Style.DANGER -> Text(text, style = typo.bodyMedium, color = scheme.error, modifier = modifier.fillMaxWidth())
        Style.DANGER_BOLD -> Text(text, style = typo.bodyMedium, fontWeight = FontWeight.Bold, color = scheme.error, modifier = modifier.fillMaxWidth())
        Style.TITLE -> Text(text, style = typo.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
}
