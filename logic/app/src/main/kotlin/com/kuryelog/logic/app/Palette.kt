package com.kuryelog.logic.app

import kotlin.math.pow

/**
 * Renkler 0xAARRGGBB. Gece/gündüz iki palet (A10.4d tasarım dili, `docs/TASARIM.md` 4; vurgu lacivert, S36); okunurluk testi
 * `PaletteContrastTest` (yazı 4,5:1, ana yazı 7:1).
 */
data class Palette(
    /** Ekran zemini. */
    val bg: Long,
    /** Kart ve haritada yüzen paneller (tam opak). */
    val surface: Long,
    /** Karo, çip, iç kutu, segment zemini. */
    val raised: Long,
    /** Basılı hâl, sayfa tutamağı, iskelet. */
    val high: Long,
    /** 1 dp ince çizgi (gece kart kenarı, ayırıcı). */
    val outline: Long,
    /** Çerçeveli düğme ve çipin kenarı. */
    val outlineStrong: Long,
    val text: Long,
    val muted: Long,
    /** Birincil düğme dolgusu. */
    val accent: Long,
    val accentInk: Long,
    /** Bağlantı ve seçili yazı. */
    val accentText: Long,
    /** Seçili gösterge, tonal düğme zemini. */
    val accentContainer: Long,
    val onAccentContainer: Long,
    val positive: Long,
    val positiveBg: Long,
    val warn: Long,
    val warnBg: Long,
    val danger: Long,
    val dangerBg: Long,
    val wolt: Long,
    val woltBg: Long,
    val bolt: Long,
    val boltBg: Long,
    /** Scooter gücü (W) ve akım. */
    val power: Long,
    val powerBg: Long,
    /** "Yolda" aşaması: hap nötr (yazı + raised), yalnız simge bu renkte (rota mavisi). */
    val move: Long,
    val restaurant: Long,
    val restaurantBg: Long,
    val picked: Long,
    val pickedBg: Long,
    val customer: Long,
    val customerBg: Long,
) {
    companion object {
        val light = Palette(
            bg = 0xFFF4F5F8, surface = 0xFFFFFFFF, raised = 0xFFECEEF2, high = 0xFFE3E6EB, outline = 0xFFDCDFE5, outlineStrong = 0xFFC5CAD3,
            text = 0xFF0E1015, muted = 0xFF565C6A,
            accent = 0xFF2C3EA8, accentInk = 0xFFFFFFFF, accentText = 0xFF2838A1, accentContainer = 0xFFE5ECFF, onAccentContainer = 0xFF1C297C,
            positive = 0xFF0B7A43, positiveBg = 0xFFDDF4E7,
            warn = 0xFF8A4B00, warnBg = 0xFFFFEDD5,
            danger = 0xFFC4291C, dangerBg = 0xFFFDE8E6,
            wolt = 0xFF00668F, woltBg = 0xFFE0F3FB,
            bolt = 0xFF0B6E3B, boltBg = 0xFFDFF5E8,
            power = 0xFFA84C08, powerBg = 0xFFFFEFD9,
            move = 0xFF1765D1,
            restaurant = 0xFF9A3E00, restaurantBg = 0xFFFFE9D9,
            picked = 0xFF0B6B47, pickedBg = 0xFFDDF4EA,
            customer = 0xFFA0156E, customerBg = 0xFFFDE4F3,
        )
        val dark = Palette(
            bg = 0xFF0E0F13, surface = 0xFF17191F, raised = 0xFF21242C, high = 0xFF2B2F38, outline = 0xFF2E323C, outlineStrong = 0xFF3D424E,
            text = 0xFFF3F4F7, muted = 0xFFA5ABB8,
            accent = 0xFF9BB1F5, accentInk = 0xFF0E1533, accentText = 0xFFAABFFC, accentContainer = 0xFF1F2A56, onAccentContainer = 0xFFDDE4F9,
            positive = 0xFF62D69B, positiveBg = 0xFF102E1F,
            warn = 0xFFFFC27A, warnBg = 0xFF382607,
            danger = 0xFFFF8A80, dangerBg = 0xFF3A1614,
            wolt = 0xFF66CCF2, woltBg = 0xFF0A2633,
            bolt = 0xFF66D99A, boltBg = 0xFF0E2A1B,
            power = 0xFFFFB547, powerBg = 0xFF3A2A10,
            move = 0xFF8BB5FF,
            restaurant = 0xFFFFB37A, restaurantBg = 0xFF3A220E,
            picked = 0xFF7FE0B3, pickedBg = 0xFF0F2E22,
            customer = 0xFFFF9AD5, customerBg = 0xFF3A1430,
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
