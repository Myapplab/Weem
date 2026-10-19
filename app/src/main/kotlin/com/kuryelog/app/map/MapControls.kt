package com.kuryelog.app.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.CameraMode
import com.kuryelog.logic.map.ui.HudLayout

/** Kipin anahtar adı (`locate-<kip>`, YolGo `CameraMode.name`). */
internal val CameraMode.key: String
    get() = when (this) {
        CameraMode.FREE -> "free"
        CameraMode.FOLLOW_NORTH -> "followNorth"
        CameraMode.FOLLOW_HEADING -> "followHeading"
    }

/** Konum düğmesi (`smart-locate`, 64×64 dp): kipe göre simge (`locate-free`, `locate-followNorth`, `locate-followHeading`) ve ipucu; dokunuş [onTap]. */
@Composable
fun SmartLocateButton(mode: CameraMode, onTap: () -> Unit, modifier: Modifier = Modifier, lang: Lang = Lang.TR) {
    val (glyph, hint) = when (mode) {
        CameraMode.FREE -> "◎" to tl(lang, "Konumuma dön", "Mövqeyimə qayıt")
        CameraMode.FOLLOW_NORTH -> "⌖" to tl(lang, "Gidiş yönüne çevir", "Hərəkət istiqamətinə çevir")
        CameraMode.FOLLOW_HEADING -> "➤" to tl(lang, "Kuzey yukarı", "Şimal yuxarı")
    }
    RoundHudButton(glyph, hint, "smart-locate", onTap, modifier, glyphTag = "locate-${mode.key}")
}

/** Alt yer düğmesi (`route-fab`): rota panelini açar; boyu konum düğmesiyle aynı. */
@Composable
fun RouteFab(onClick: () -> Unit, modifier: Modifier = Modifier, lang: Lang = Lang.TR) {
    RoundHudButton("⇅", tl(lang, "Rota", "Marşrut"), "route-fab", onClick, modifier)
}

@Composable
private fun RoundHudButton(glyph: String, hint: String, tag: String, onClick: () -> Unit, modifier: Modifier, glyphTag: String? = null) {
    // Kip anahtarı dış kutuda: tıklanabilir yüzey alt düğümlerin anlamını birleştirir.
    Box(if (glyphTag != null) modifier.testTag(glyphTag) else modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        modifier = Modifier.size(HudLayout.LOCATE_BUTTON_DP.dp).testTag(tag).semantics { contentDescription = hint },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
    }
}

/**
 * HUD yerleşimi (`MapHudLayout`; MAP.md 6.3): [map] tam ekran; ortadaki boş alan ve sağa hizalı düğmenin solundaki saydam alan haritaya gider (kaydırma, iki parmak ölçek);
 * [header] (üstte, uzunsa kendi içinde kaydırılır, harita girdi almaz) ve [footer] (altta; [keyboard] açıkken yok) denetimleri haritaya geçmez; [alerts] engel katmanı
 * (görev uyarısı) varken harita girdi almaz. Paneller içerikleri kadar yer kaplar; kaydırıcı yalnız içerik sınırı aşınca takılır (boş alanda sürüklemeyi yutmasın).
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight.value.toDouble()
        Box(Modifier.fillMaxSize()) { map() }
        val p = HudLayout.TOP_PADDING_DP
        HudPanel(
            maxHeightDp = HudLayout.topMaxHeightDp(h, keyboard),
            tag = "hud-header-scroll",
            reverse = false,
            modifier = Modifier.align(Alignment.TopStart).padding(start = p[0].dp, top = p[1].dp, end = p[2].dp, bottom = p[3].dp),
            content = header,
        )
        if (!keyboard) {
            HudPanel(
                maxHeightDp = HudLayout.bottomMaxHeightDp(h),
                tag = "hud-footer-scroll",
                reverse = true,
                modifier = Modifier.align(Alignment.BottomStart).padding(HudLayout.BOTTOM_PADDING_DP.dp),
                content = footer,
            )
        }
        Box(Modifier.matchParentSize()) { alerts() }
    }
}

@Composable
private fun HudPanel(maxHeightDp: Double, tag: String, reverse: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    var contentPx by remember { mutableIntStateOf(0) }
    val maxPx = with(LocalDensity.current) { maxHeightDp.dp.toPx() }
    val overflow = contentPx > maxPx + 0.5f
    val scroll = rememberScrollState()
    Box(
        modifier.fillMaxWidth().heightIn(max = maxHeightDp.dp).testTag(tag)
            .then(if (overflow) Modifier.verticalScroll(scroll, reverseScrolling = reverse) else Modifier),
    ) {
        Box(Modifier.fillMaxWidth().wrapContentHeight(unbounded = true, align = if (reverse) Alignment.Bottom else Alignment.Top).onSizeChanged { contentPx = it.height }) {
            content()
        }
    }
}

internal fun tl(lang: Lang, tr: String, az: String) = if (lang == Lang.AZ) az else tr
