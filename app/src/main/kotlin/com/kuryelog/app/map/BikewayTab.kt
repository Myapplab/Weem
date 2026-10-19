package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.BikewayStrings

/**
 * Yollarım → Bisiklet sekmesinin girdisi (MAP.md 5.1): OSM'de [osmWays] sokak ve toplam [osmMeters], rota ağırlığı [weight] (×0.85), katman anahtarı, elle işaretler
 * (way id → ad). [oldMap] harita dosyası bisiklet bilgisi içermiyorsa (eski harita) turuncu uyarı gösterilir.
 */
data class BikewayTabState(
    val osmWays: Int,
    val weight: Double = 0.85,
    val layerOn: Boolean = true,
    val marks: List<Pair<Long, String>> = emptyList(),
    val osmMeters: Double = 0.0,
    val oldMap: Boolean = false,
)

/**
 * Bisiklet sekmesi: not ("Bakü'de OSM bisiklet yolu kaydı az; eksikleri elle işaretleyebilirsin."), "Haritada bisiklet yolları" anahtarı, ağırlık "×0.85" ve kaydırıcı,
 * "OSM: N sokak · km", elle işaretler listesi ve "İşareti kaldır" (içerik açıklaması) simgesi.
 */
@Composable
fun BikewayTab(
    state: BikewayTabState,
    lang: Lang,
    onLayerChange: (Boolean) -> Unit,
    onRemoveMark: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onWeightChange: (Double) -> Unit = {},
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        run {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(BikewayStrings.TITLE.get(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(BikewayStrings.NOTE.get(lang), fontWeight = FontWeight.SemiBold)
                    Text(BikewayStrings.HELP.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!state.oldMap) {
                        Text(
                            "OSM: ${state.osmWays} ${BikewayStrings.OSM_STREETS.get(lang)} · ${BikewayStrings.fmtKm(state.osmMeters)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(com.kuryelog.logic.map.graph.BikewayText.oldMapWarning(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        run {
            Row(Modifier.fillMaxWidth().clickable { onLayerChange(!state.layerOn) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(BikewayStrings.LAYER_TITLE.get(lang))
                    Text(BikewayStrings.LAYER_SUB.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.layerOn, onCheckedChange = onLayerChange)
            }
        }
        run {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(BikewayStrings.PREF_TITLE.get(lang))
                    Text(BikewayStrings.PREF_SUB.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(BikewayStrings.factor(state.weight * 100), fontWeight = FontWeight.Bold)
            }
            Slider(value = (state.weight * 100).coerceIn(50.0, 100.0).toFloat(), onValueChange = { onWeightChange(it / 100.0) }, valueRange = 50f..100f, steps = 9)
        }
        Text(BikewayStrings.MARKS_TITLE.get(lang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        if (state.marks.isEmpty()) Text(BikewayStrings.NONE_YET.get(lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((way, name) in state.marks) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name.ifEmpty { BikewayStrings.UNNAMED.get(lang) })
                    Text("OSM #$way", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val remove = BikewayStrings.REMOVE.get(lang)
                IconButton(onClick = { onRemoveMark(way) }, modifier = Modifier.semantics { contentDescription = remove }) { Text("✕") }
            }
        }
    }
}
