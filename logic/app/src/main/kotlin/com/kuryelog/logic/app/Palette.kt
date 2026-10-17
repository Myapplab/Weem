package com.kuryelog.logic.app

import kotlin.math.pow

/** Renkler 0xAARRGGBB. Gece/gündüz iki palet; okunurluk testi `PaletteContrastTest` (4,5:1). */
data class Palette(
    val bg: Long,
    val surface: Long,
    val raised: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val accentInk: Long,
    val accentText: Long,
    val positive: Long,
    val positiveBg: Long,
    val warn: Long,
    val warnBg: Long,
    val danger: Long,
    val wolt: Long,
    val woltBg: Long,
    val bolt: Long,
    val boltBg: Long,
) {
    companion object {
        val light = Palette(
            bg = 0xFFF4F6F8, surface = 0xFFFFFFFF, raised = 0xFFE8ECF0,
            text = 0xFF14181C, muted = 0xFF4A5560,
            accent = 0xFF0B5FA5, accentInk = 0xFFFFFFFF, accentText = 0xFF0B5FA5,
            positive = 0xFF0B6B3A, positiveBg = 0xFFDDF3E6,
            warn = 0xFF8A4B00, warnBg = 0xFFFFEBD0,
            danger = 0xFFB3261E,
            wolt = 0xFF005F88, woltBg = 0xFFD9F0FA,
            bolt = 0xFF0A6B33, boltBg = 0xFFDCF5E4,
        )
        val dark = Palette(
            bg = 0xFF0E1114, surface = 0xFF171B20, raised = 0xFF222830,
            text = 0xFFF1F4F7, muted = 0xFFA9B4BF,
            accent = 0xFF4DA3F0, accentInk = 0xFF06121D, accentText = 0xFF7FBDF5,
            positive = 0xFF6FDC9B, positiveBg = 0xFF12301F,
            warn = 0xFFFFC27A, warnBg = 0xFF3A2606,
            danger = 0xFFFF8A80,
            wolt = 0xFF6CCBF0, woltBg = 0xFF0A2A38,
            bolt = 0xFF6FDC9B, boltBg = 0xFF0F2E1B,
        )

        fun of(dark: Boolean): Palette = if (dark) Companion.dark else light
    }
}

/** WCAG göreli parlaklık ve karşıtlık oranı. */
object Contrast {
    private fun linear(channel: Long): Double {
        val v = channel / 255.0
        return if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Long): Double =
        .2126 * linear((argb shr 16) and 255) + .7152 * linear((argb shr 8) and 255) + .0722 * linear(argb and 255)

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + .05) / (minOf(la, lb) + .05)
    }
}
