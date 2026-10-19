package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Konum imleci (MAP.md 6.4, 6.15; `logic/map` `ui.PuckStyle` ölçüleri): rota açık sürerken ve hareket yönü [course] belliyse küçük beyaz kenarlı mavi ok (`puck-arrow`,
 * gidiş yönüne döner); dururken ya da rota yokken nokta (+ koni) (`puck-dot`). [arrow] rota açık mı. İskelet (A6.2b): gövde A6.5'te.
 */
@Composable
fun LocationPuck(course: Double?, arrow: Boolean, modifier: Modifier = Modifier) {
    TODO("A6.5")
}
