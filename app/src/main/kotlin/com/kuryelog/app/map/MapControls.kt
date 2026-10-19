package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.map.ui.CameraMode

/** Konum düğmesi (`smart-locate`, 64×64 dp): kipe göre simge (`locate-free`, `locate-followNorth`, `locate-followHeading`); dokunuş [onTap]. */
@Composable
fun SmartLocateButton(mode: CameraMode, onTap: () -> Unit, modifier: Modifier = Modifier) {
    TODO("A6.5")
}

/** Alt yer düğmesi (`route-fab`): rota panelini açar; boyu konum düğmesiyle aynı. */
@Composable
fun RouteFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TODO("A6.5")
}

/**
 * HUD yerleşimi (`MapHudLayout`; MAP.md 6.3): [map] tam ekran; ortadaki boş alan ve sağa hizalı düğmenin solundaki saydam alan haritaya gider (kaydırma, iki parmak ölçek);
 * [header] (üstte, uzunsa kendi içinde kaydırılır, harita girdi almaz) ve [footer] (altta; [keyboard] açıkken yok) denetimleri haritaya geçmez; [alerts] engel katmanı
 * (görev uyarısı) varken harita girdi almaz.
 */
@Composable
fun MapHudLayout(
    keyboard: Boolean,
    map: @Composable () -> Unit,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    alerts: @Composable () -> Unit = {},
) {
    TODO("A6.5")
}
