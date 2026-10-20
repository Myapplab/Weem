package com.kuryelog.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kuryelog.logic.map.ui.BikewayText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Yollarım → Bisiklet sekmesinin girdisi (MAP.md 5.2): OSM'de [osmWays] kayıt ([osmMeters] biliniyorsa uzunluk), rota ağırlığı [weight] (×0.85), katman anahtarı,
 * elle işaretler (way id → ad), [hasBikewayData] false ise eski harita uyarısı.
 */
data class BikewayTabState(
    val osmWays: Int,
    val weight: Double = 0.85,
    val layerOn: Boolean = true,
    val marks: List<Pair<Long, String>> = emptyList(),
    val osmMeters: Double? = null,
    val hasBikewayData: Boolean = true,
)

/**
 * Bisiklet sekmesi: not, yardım, "OSM: N sokak · km" istatistiği (eski haritada uyarı), "Haritada bisiklet yolları" anahtarı, ağırlık "×0.85" ([onWeightChange] verilirse
 * 50–100 kaydırıcı, adım 5), elle işaretler listesi ve "İşareti kaldır" simgesi (geri al yok).
 */
@Composable
fun BikewayTab(
    state: BikewayTabState,
    lang: Lang,
    onLayerChange: (Boolean) -> Unit,
    onRemoveMark: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onWeightChange: ((Int) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(BikewayText.TAB_TITLE.get(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(BikewayText.TAB_NOTE.get(lang), style = MaterialTheme.typography.bodyMedium)
        Text(BikewayText.TAB_HELP.get(lang), style = MaterialTheme.typography.bodySmall)
        if (state.hasBikewayData) {
            val km = state.osmMeters?.let { fmtKm(it) }
            Text(
                if (km == null) BikewayText.TAB_STATS.format(lang, state.osmWays) else BikewayText.TAB_STATS_KM.format(lang, state.osmWays, km),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Text(BikewayText.TAB_OLD_MAP.get(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth().clickable { onLayerChange(!state.layerOn) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(BikewayText.LAYER.get(lang), style = MaterialTheme.typography.bodyLarge)
                Text(BikewayText.LAYER_SUB.get(lang), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.layerOn, onCheckedChange = null)
        }
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(BikewayText.WEIGHT.get(lang), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(String.format(Locale.US, "×%.2f", state.weight), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Text(BikewayText.WEIGHT_SUB.get(lang), style = MaterialTheme.typography.bodySmall)
            if (onWeightChange != null) {
                Slider(
                    value = (state.weight * 100).toFloat(),
                    onValueChange = { onWeightChange((it / 5).roundToInt() * 5) },
                    valueRange = 50f..100f,
                    steps = 9,
                )
            }
        }
        Text(BikewayText.MARKS_TITLE.get(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (state.marks.isEmpty()) {
            Text(BikewayText.MARKS_EMPTY.get(lang), style = MaterialTheme.typography.bodyMedium)
        }
        for ((id, name) in state.marks) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { BikewayText.NO_NAME.get(lang) }, style = MaterialTheme.typography.bodyLarge)
                    Text(BikewayText.MARK_ID.format(lang, id), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onRemoveMark(id) }, modifier = Modifier.semantics { contentDescription = BikewayText.REMOVE_MARK.get(lang) }) {
                    Text("✕")
                }
            }
        }
    }
}
