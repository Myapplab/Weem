package com.kuryelog.logic.core

/**
 * Kurye uygulamalarının üstündeki pencerelerin (rozet R7, balonlar ve kart R6, E7 kırmızı rozeti, kalibrasyon paneli) ve Sürüş Paneli
 * çizimlerinin (gösterge, yüzen top, PiP) renkleri. A10.4d (`docs/TASARIM.md` 4): uygulama paletiyle (`logic/app` `Palette` v2) aynı tablo;
 * `logic/app` `OverlayPaletteTest` ortak rolleri eşit tutar. Eski Sürüş Paneli `RawPalette` renkleri kalktı (S28: gölge mod
 * karşılaştırması yok). Pencerelerin **metni** değişmez (ekran okuma kendi satırını onunla tanır, `OwnLines`). Renkler 0xAARRGGBB.
 */
class OverlayPalette private constructor(val dark: Boolean, c: IntArray) {
    val bg = c[0]; val surface = c[1]; val raised = c[2]; val line = c[3]; val text = c[4]; val muted = c[5]
    val accent = c[6]; val accentInk = c[7]; val accentText = c[8]; val positive = c[9]; val positiveBg = c[10]
    val warn = c[11]; val warnBg = c[12]; val danger = c[13]; val wolt = c[14]; val bolt = c[15]; val woltBg = c[16]; val boltBg = c[17]
    /** Güç hapı zemini ve çizgisi (eski adlar; `raised` ve `line` ile aynı). */
    val hero = c[18]; val heroLine = c[19]
    val dangerBg = c[20]; val power = c[21]; val powerBg = c[22]; val lineStrong = c[23]

    companion object {
        private val LIGHT = OverlayPalette(false, intArrayOf(
            0xfff4f5f8.toInt(), 0xffffffff.toInt(), 0xffeceef2.toInt(), 0xffdcdfe5.toInt(), 0xff0e1015.toInt(), 0xff565c6a.toInt(),
            0xff2c3ea8.toInt(), 0xffffffff.toInt(), 0xff2838a1.toInt(), 0xff0b7a43.toInt(), 0xffddf4e7.toInt(),
            0xff8a4b00.toInt(), 0xffffedd5.toInt(), 0xffc4291c.toInt(), 0xff00668f.toInt(), 0xff0b6e3b.toInt(), 0xffe0f3fb.toInt(), 0xffdff5e8.toInt(),
            0xffeceef2.toInt(), 0xffdcdfe5.toInt(),
            0xfffde8e6.toInt(), 0xffa84c08.toInt(), 0xffffefd9.toInt(), 0xffc5cad3.toInt()))
        private val DARK = OverlayPalette(true, intArrayOf(
            0xff0e0f13.toInt(), 0xff17191f.toInt(), 0xff21242c.toInt(), 0xff2e323c.toInt(), 0xfff3f4f7.toInt(), 0xffa5abb8.toInt(),
            0xff9bb1f5.toInt(), 0xff0e1533.toInt(), 0xffaabffc.toInt(), 0xff62d69b.toInt(), 0xff102e1f.toInt(),
            0xffffc27a.toInt(), 0xff382607.toInt(), 0xffff8a80.toInt(), 0xff66ccf2.toInt(), 0xff66d99a.toInt(), 0xff0a2633.toInt(), 0xff0e2a1b.toInt(),
            0xff21242c.toInt(), 0xff2e323c.toInt(),
            0xff3a1614.toInt(), 0xffffb547.toInt(), 0xff3a2a10.toInt(), 0xff3d424e.toInt()))

        fun forMode(dark: Boolean): OverlayPalette = if (dark) DARK else LIGHT
    }
}
