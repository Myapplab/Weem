package com.kuryelog.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.kuryelog.app.LocalWeemPalette
import com.kuryelog.logic.app.Palette

/**
 * Renk yalnız anlam için (`docs/TASARIM.md` 3–4): hap, banner, kart zemini, simge kutusu aynı ton tablosundan. `MOVE` ("yolda") hapı
 * nötrdür, rengi yalnız simgede (S36).
 */
enum class WTone { NEUTRAL, ACCENT, POSITIVE, WARN, DANGER, WOLT, BOLT, POWER, MOVE, RESTAURANT, PICKED, CUSTOMER }

/** Ton zemini üstündeki yazı (karşıtlık `PaletteContrastTest`'te). */
fun Palette.ink(t: WTone): Long = when (t) {
    WTone.NEUTRAL, WTone.MOVE -> text
    WTone.ACCENT -> onAccentContainer
    WTone.POSITIVE -> positive
    WTone.WARN -> warn
    WTone.DANGER -> danger
    WTone.WOLT -> wolt
    WTone.BOLT -> bolt
    WTone.POWER -> power
    WTone.RESTAURANT -> restaurant
    WTone.PICKED -> picked
    WTone.CUSTOMER -> customer
}

/** Ton zemini (hap, banner, ton kartı, simge kutusu). */
fun Palette.container(t: WTone): Long = when (t) {
    WTone.NEUTRAL, WTone.MOVE -> raised
    WTone.ACCENT -> accentContainer
    WTone.POSITIVE -> positiveBg
    WTone.WARN -> warnBg
    WTone.DANGER -> dangerBg
    WTone.WOLT -> woltBg
    WTone.BOLT -> boltBg
    WTone.POWER -> powerBg
    WTone.RESTAURANT -> restaurantBg
    WTone.PICKED -> pickedBg
    WTone.CUSTOMER -> customerBg
}

/** Tonun simge ve nokta rengi: "yolda" rota mavisi, vurgu dolgu rengi; ötekiler yazı rengiyle aynı. */
fun Palette.mark(t: WTone): Long = when (t) {
    WTone.MOVE -> move
    WTone.ACCENT -> accent
    WTone.NEUTRAL -> muted
    else -> ink(t)
}

/** Kart ya da zemin üstünde (ton zemini olmadan) yazılan tonlu metin. */
fun Palette.onSurface(t: WTone): Long = when (t) {
    WTone.ACCENT -> accentText
    WTone.NEUTRAL, WTone.MOVE -> text
    else -> ink(t)
}

val Palette.isDark: Boolean get() = this === Palette.dark

/** Etkin palet (`WeemTheme`). */
@Composable
@ReadOnlyComposable
fun palette(): Palette = LocalWeemPalette.current

fun Long.color(): Color = Color(this)
