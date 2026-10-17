package com.kuryelog.logic.app

import org.junit.Assert.assertTrue
import org.junit.Test

/** `RawPaletteContrastTest` karşılığı: sakin yüzeylerde de metin iki temada okunur kalmalı. */
class PaletteContrastTest {
    private fun readable(label: String, ink: Long, background: Long) {
        val ratio = Contrast.ratio(ink, background)
        assertTrue("$label: karşıtlık $ratio, 4,5:1 altında", ratio >= 4.5)
    }

    @Test fun bodyAndSecondaryTextRemainReadableOnEveryCardSurface() {
        for (dark in listOf(false, true)) {
            val p = Palette.of(dark)
            val surfaces = listOf(p.bg, p.surface, p.raised, p.woltBg, p.boltBg, p.positiveBg, p.warnBg)
            for ((i, background) in surfaces.withIndex()) {
                readable("dark=$dark yüzey=$i metin", p.text, background)
                readable("dark=$dark yüzey=$i ikincil", p.muted, background)
            }
        }
    }

    @Test fun buttonAndStateColoursKeepReadableLabels() {
        for (dark in listOf(false, true)) {
            val p = Palette.of(dark)
            readable("dark=$dark düğme", p.accentInk, p.accent)
            readable("dark=$dark olumlu", p.positive, p.positiveBg)
            readable("dark=$dark uyarı", p.warn, p.warnBg)
            readable("dark=$dark Wolt", p.wolt, p.woltBg)
            readable("dark=$dark Bolt", p.bolt, p.boltBg)
            for (background in listOf(p.bg, p.surface, p.raised)) {
                readable("dark=$dark vurgu metni", p.accentText, background)
                readable("dark=$dark tehlike", p.danger, background)
            }
        }
    }

    @Test fun lightAndDarkPalettesAreActuallyOpposite() {
        assertTrue(Contrast.luminance(Palette.light.bg) > Contrast.luminance(Palette.light.text))
        assertTrue(Contrast.luminance(Palette.dark.bg) < Contrast.luminance(Palette.dark.text))
    }
}
