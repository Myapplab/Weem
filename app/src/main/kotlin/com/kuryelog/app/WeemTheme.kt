package com.kuryelog.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kuryelog.logic.app.Palette

private fun c(argb: Long) = Color(argb)

/** `logic/app` paletini Material renklerine bağlar (karşıtlık testi o palette). */
@Composable
fun WeemTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = Palette.of(dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = c(p.accent), onPrimary = c(p.accentInk),
            background = c(p.bg), onBackground = c(p.text),
            surface = c(p.surface), onSurface = c(p.text),
            surfaceVariant = c(p.raised), onSurfaceVariant = c(p.muted),
            secondaryContainer = c(p.raised), onSecondaryContainer = c(p.text),
            error = c(p.danger),
        )
    } else {
        lightColorScheme(
            primary = c(p.accent), onPrimary = c(p.accentInk),
            background = c(p.bg), onBackground = c(p.text),
            surface = c(p.surface), onSurface = c(p.text),
            surfaceVariant = c(p.raised), onSurfaceVariant = c(p.muted),
            secondaryContainer = c(p.raised), onSecondaryContainer = c(p.text),
            error = c(p.danger),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
