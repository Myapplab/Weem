package com.kuryelog.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kuryelog.app.ui.WeemShapes
import com.kuryelog.app.ui.WeemType
import com.kuryelog.logic.app.Palette

private fun c(argb: Long) = Color(argb)

/** Platform, aşama ve durum renkleri için etkin palet (A3.8; A10.4d Palette v2). */
val LocalWeemPalette = staticCompositionLocalOf { Palette.light }

/**
 * `logic/app` paletini Material renklerine bağlar (karşıtlık testi o palette); yazı ölçeği Inter (`WeemType`), köşeler `WeemShapes`
 * (A10.4d, `docs/TASARIM.md` 4–6). Material bileşenleri de aynı dili konuşsun diye bütün kap renkleri tek `surface`; seçili gösterge ve
 * tonal düğme `accentContainer`.
 */
@Composable
fun WeemTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = Palette.of(dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c(p.accent), onPrimary = c(p.accentInk),
        primaryContainer = c(p.accentContainer), onPrimaryContainer = c(p.onAccentContainer),
        inversePrimary = c(Palette.of(!dark).accent),
        secondary = c(p.accent), onSecondary = c(p.accentInk),
        secondaryContainer = c(p.accentContainer), onSecondaryContainer = c(p.onAccentContainer),
        tertiary = c(p.accent), onTertiary = c(p.accentInk),
        tertiaryContainer = c(p.accentContainer), onTertiaryContainer = c(p.onAccentContainer),
        background = c(p.bg), onBackground = c(p.text),
        surface = c(p.surface), onSurface = c(p.text),
        surfaceVariant = c(p.raised), onSurfaceVariant = c(p.muted),
        surfaceTint = c(p.surface),
        inverseSurface = c(p.text), inverseOnSurface = c(p.bg),
        error = c(p.danger), onError = c(if (dark) p.bg else p.surface),
        errorContainer = c(p.dangerBg), onErrorContainer = c(p.danger),
        outline = c(p.outlineStrong), outlineVariant = c(p.outline),
        scrim = Color.Black,
        surfaceBright = c(p.surface), surfaceDim = c(p.bg),
        surfaceContainerLowest = c(p.surface), surfaceContainerLow = c(p.surface), surfaceContainer = c(p.surface),
        surfaceContainerHigh = c(p.surface), surfaceContainerHighest = c(p.surface),
    )
    MaterialTheme(colorScheme = scheme, typography = WeemType.typography, shapes = WeemShapes) {
        CompositionLocalProvider(LocalWeemPalette provides p, content = content)
    }
}
