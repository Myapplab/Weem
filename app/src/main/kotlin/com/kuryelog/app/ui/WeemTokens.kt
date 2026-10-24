package com.kuryelog.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kuryelog.app.LocalWeemPalette
import com.kuryelog.logic.app.Palette

/** Boşluk (`docs/TASARIM.md` 6): 4'ün katları; ekran kenarı 16, kart içi 16, kartlar arası 12, bölümler arası 24. */
object WeemSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val x3 = 32.dp
    val x4 = 40.dp
}

/** Köşe: rozet 8 · karo 12 · kart 16 · panel 24 · alt sayfa 32; düğme, çip ve yuvarlak düğme tam. */
object WeemRadius {
    val badge = 8.dp
    val tile = 12.dp
    val card = 16.dp
    val panel = 24.dp
    val sheet = 32.dp
}

/** Dokunma: en az 48 dp; sürüş denetimleri 56 dp (`docs/TASARIM.md` 9). */
object WeemSize {
    val touch = 48.dp
    val ride = 56.dp
    val navBar = 72.dp
    val icon = 24.dp
    val iconRide = 28.dp
}

/** Hareket (`docs/TASARIM.md` 8): süreler ms; telefonda animasyon kapalıysa oynatılmaz. */
object WeemMotion {
    const val PRESS = 100
    const val STATE = 200
    const val SHEET_IN = 280
    const val SHEET_OUT = 200
    const val NUMBER = 350
    val enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** Material şekilleri: giriş alanı ve menü 12, karo 12, kart 16, panel 24, pencere 24 (alt sayfa kendi 32'sini verir). */
val WeemShapes = Shapes(
    extraSmall = RoundedCornerShape(WeemRadius.tile),
    small = RoundedCornerShape(WeemRadius.tile),
    medium = RoundedCornerShape(WeemRadius.card),
    large = RoundedCornerShape(WeemRadius.panel),
    extraLarge = RoundedCornerShape(WeemRadius.panel),
)

/**
 * `e1` (haritada yüzen öğeler): gündüz yumuşak gölge, gece ince çizgi + koyu gölge. Haritanın üstünde paneller tam opak
 * (`docs/TASARIM.md` 6).
 */
@Composable
fun Modifier.floating(shape: Shape): Modifier {
    val p = LocalWeemPalette.current
    val dark = p === Palette.dark
    val shadowed = shadow(if (dark) 6.dp else 4.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) .5f else .12f),
        spotColor = Color.Black.copy(alpha = if (dark) .6f else .16f))
    return if (dark) shadowed.border(1.dp, Color(p.outline), shape) else shadowed
}

