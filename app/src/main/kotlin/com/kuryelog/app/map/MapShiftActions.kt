package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayView

object MapShiftActionsDefaults {
    /** Kart açıldıktan sonra kendiliğinden simgelere döner (1.7.0: 20 sn). */
    const val COLLAPSE_AFTER_MS = 20_000L
}

/**
 * Harita sekmesinin gün eylemleri (YolGo `ShiftBar`; MAP.md 6.12, SORU-N7): katlı hâlde (`shift-collapsed`) tek sıra, sol kenarda üç simge — Eve dön (`shift-home`),
 * İstatistik (`shift-stats`), Günü bitir (`shift-end`); soldan sağa kaydırınca açık kart (`shift-card`: aşama metni, rota varken "#N"); yeni sipariş ya da dışarı dokunuş
 * kartı katlar. Gün kapalıyken Eve dön ve Günü bitir pasif. Sipariş düğmeleri yok (aşamayı yalnız ekran okuması ilerletir). İskelet (A6.2b): gövde A6.5'te.
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
) {
    TODO("A6.5")
}
