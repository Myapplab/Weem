package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang

/** Yollarım → Bisiklet sekmesinin girdisi (MAP.md 5.2): OSM'de [osmWays] kayıt, rota ağırlığı [weight] (×0.85), katman anahtarı, elle işaretler (way id → ad). */
data class BikewayTabState(val osmWays: Int, val weight: Double = 0.85, val layerOn: Boolean = true, val marks: List<Pair<Long, String>> = emptyList())

/**
 * Bisiklet sekmesi: not ("Bakü'de OSM bisiklet yolu kaydı az; eksikleri elle işaretleyebilirsin."), "Haritada bisiklet yolları" anahtarı, ağırlık "×0.85", "OSM: N yol",
 * elle işaretler listesi ve "İşareti kaldır" (içerik açıklaması) simgesi. İskelet (A6.2b): gövde A6.8'de.
 */
@Composable
fun BikewayTab(state: BikewayTabState, lang: Lang, onLayerChange: (Boolean) -> Unit, onRemoveMark: (Long) -> Unit, modifier: Modifier = Modifier) {
    TODO("A6.8")
}
