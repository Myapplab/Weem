package com.kuryelog.logic.app

import org.junit.Assert.assertTrue
import org.junit.Test

/** `RawPaletteContrastTest` karşılığı: sakin yüzeylerde de metin iki temada okunur kalmalı (A10.4d: `docs/TASARIM.md` 4). */
class PaletteContrastTest {
    private fun atLeast(label: String, ink: Long, background: Long, min: Double) {
        val ratio = Contrast.ratio(ink, background)
        assertTrue("$label: karşıtlık $ratio, $min:1 altında", ratio >= min)
    }

    private fun readable(label: String, ink: Long, background: Long) = atLeast(label, ink, background, 4.5)

    @Test fun bodyAndSecondaryTextRemainReadableOnEveryCardSurface() {
        for (dark in listOf(false, true)) {
            val p = Palette.of(dark)
            val surfaces = listOf(
                p.bg, p.surface, p.raised, p.high, p.woltBg, p.boltBg, p.positiveBg, p.warnBg, p.dangerBg, p.powerBg, p.accentContainer,
                p.restaurantBg, p.pickedBg, p.customerBg,
            )
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
            readable("dark=$dark tonal düğme", p.onAccentContainer, p.accentContainer)
            readable("dark=$dark olumlu", p.positive, p.positiveBg)
            readable("dark=$dark uyarı", p.warn, p.warnBg)
            readable("dark=$dark tehlike zemini", p.danger, p.dangerBg)
            readable("dark=$dark güç", p.power, p.powerBg)
            readable("dark=$dark Wolt", p.wolt, p.woltBg)
            readable("dark=$dark Bolt", p.bolt, p.boltBg)
            readable("dark=$dark restoranda", p.restaurant, p.restaurantBg)
            readable("dark=$dark alındı", p.picked, p.pickedBg)
            readable("dark=$dark müşteride", p.customer, p.customerBg)
            for (background in listOf(p.bg, p.surface, p.raised)) {
                readable("dark=$dark vurgu metni", p.accentText, background)
                readable("dark=$dark tehlike", p.danger, background)
            }
        }
    }

    /** Ana yazı güneşte de okunsun: zemin ve kart üstünde 7:1. */
    @Test fun mainTextHasSevenToOneOnScreenAndCards() {
        for (dark in listOf(false, true)) {
            val p = Palette.of(dark)
            for (background in listOf(p.bg, p.surface)) atLeast("dark=$dark ana yazı", p.text, background, 7.0)
        }
    }

    /** "Yolda" hapının simgesi (yazı değil): kart ve hap zemininde en az 3:1. */
    @Test fun moveIconStaysVisible() {
        for (dark in listOf(false, true)) {
            val p = Palette.of(dark)
            for (background in listOf(p.surface, p.raised)) atLeast("dark=$dark yolda simgesi", p.move, background, 3.0)
        }
    }

    @Test fun lightAndDarkPalettesAreActuallyOpposite() {
        assertTrue(Contrast.luminance(Palette.light.bg) > Contrast.luminance(Palette.light.text))
        assertTrue(Contrast.luminance(Palette.dark.bg) < Contrast.luminance(Palette.dark.text))
    }
}
