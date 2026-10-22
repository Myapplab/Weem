package com.kuryelog.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuryelog.app.ui.Banner
import com.kuryelog.app.ui.IconBox
import com.kuryelog.app.ui.ListRow
import com.kuryelog.app.ui.SettingsGroup
import com.kuryelog.app.ui.SwitchRow
import com.kuryelog.app.ui.WCard
import com.kuryelog.app.ui.WIconButton
import com.kuryelog.app.ui.WTone
import com.kuryelog.app.ui.WeemIcons
import com.kuryelog.app.ui.WeemSpace
import com.kuryelog.app.ui.WeemType
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
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = WeemSpace.l, end = WeemSpace.l, top = WeemSpace.m, bottom = WeemSpace.x4), verticalArrangement = Arrangement.spacedBy(WeemSpace.m)) {
        WCard {
            Row(horizontalArrangement = Arrangement.spacedBy(WeemSpace.m), verticalAlignment = Alignment.CenterVertically) {
                IconBox(WeemIcons.DirectionsBike, tone = WTone.ACCENT)
                Text(BikewayText.TAB_TITLE.get(lang), style = WeemType.titleM, modifier = Modifier.weight(1f))
            }
            Text(BikewayText.TAB_NOTE.get(lang), style = WeemType.bodyM)
            Text(BikewayText.TAB_HELP.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.hasBikewayData) {
                val km = state.osmMeters?.let { fmtKm(it) }
                Text(if (km == null) BikewayText.TAB_STATS.format(lang, state.osmWays) else BikewayText.TAB_STATS_KM.format(lang, state.osmWays, km), style = WeemType.titleS)
            } else {
                Banner(BikewayText.TAB_OLD_MAP.get(lang), tone = WTone.DANGER)
            }
        }
        SettingsGroup(null, dividerInset = 70.dp) {
            SwitchRow(BikewayText.LAYER.get(lang), state.layerOn, { onLayerChange(!state.layerOn) }, subtitle = BikewayText.LAYER_SUB.get(lang), icon = WeemIcons.Layers)
            Column(Modifier.padding(horizontal = WeemSpace.l, vertical = WeemSpace.m)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(BikewayText.WEIGHT.get(lang), Modifier.weight(1f), style = WeemType.titleS)
                    Text(String.format(Locale.US, "×%.2f", state.weight), style = WeemType.numS)
                }
                Text(BikewayText.WEIGHT_SUB.get(lang), style = WeemType.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onWeightChange != null) {
                    Slider(
                        value = (state.weight * 100).toFloat(),
                        onValueChange = { onWeightChange((it / 5).roundToInt() * 5) },
                        valueRange = 50f..100f,
                        steps = 9,
                    )
                }
            }
        }
        SettingsGroup(BikewayText.MARKS_TITLE.get(lang)) {
            if (state.marks.isEmpty()) ListRow(BikewayText.MARKS_EMPTY.get(lang), titleStyle = WeemType.bodyM)
            for ((id, name) in state.marks) {
                ListRow(
                    name.ifBlank { BikewayText.NO_NAME.get(lang) }, subtitle = BikewayText.MARK_ID.format(lang, id), icon = WeemIcons.DirectionsBikeFilled, iconTone = WTone.ACCENT,
                    trailing = { WIconButton(WeemIcons.Close, BikewayText.REMOVE_MARK.get(lang), { onRemoveMark(id) }) },
                )
            }
        }
    }
}
