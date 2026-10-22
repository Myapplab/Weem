package com.kuryelog.logic.app

import com.kuryelog.logic.core.OverlayPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A10.4d (`docs/TASARIM.md` 4): Sürüş Paneli çizimleri ve kurye uygulamalarının üstündeki pencereler uygulama paletiyle aynı tablodan
 * beslenir; `OverlayPalette` (`logic/core`, `:scooter`/`:orders`/`:capture`/`:map` okur) ortak rollerde `Palette` ile aynı kalmalı.
 */
class OverlayPaletteTest {
    @Test fun overlayColoursFollowTheAppPalette() {
        for (dark in listOf(false, true)) {
            val p = Palette.of(dark)
            val o = OverlayPalette.forMode(dark)
            val pairs = listOf(
                "bg" to (p.bg to o.bg), "surface" to (p.surface to o.surface), "raised" to (p.raised to o.raised), "line" to (p.outline to o.line),
                "lineStrong" to (p.outlineStrong to o.lineStrong), "text" to (p.text to o.text), "muted" to (p.muted to o.muted),
                "accent" to (p.accent to o.accent), "accentInk" to (p.accentInk to o.accentInk), "accentText" to (p.accentText to o.accentText),
                "positive" to (p.positive to o.positive), "positiveBg" to (p.positiveBg to o.positiveBg), "warn" to (p.warn to o.warn),
                "warnBg" to (p.warnBg to o.warnBg), "danger" to (p.danger to o.danger), "dangerBg" to (p.dangerBg to o.dangerBg),
                "wolt" to (p.wolt to o.wolt), "woltBg" to (p.woltBg to o.woltBg), "bolt" to (p.bolt to o.bolt), "boltBg" to (p.boltBg to o.boltBg),
                "power" to (p.power to o.power), "powerBg" to (p.powerBg to o.powerBg), "hero" to (p.raised to o.hero), "heroLine" to (p.outline to o.heroLine),
            )
            for ((name, pair) in pairs) assertEquals("dark=$dark $name", pair.first.toInt(), pair.second)
            assertEquals(dark, o.dark)
        }
    }
}
