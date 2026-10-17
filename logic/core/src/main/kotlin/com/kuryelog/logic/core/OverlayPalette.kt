package com.kuryelog.logic.core

/**
 * Kurye uygulamalarının üstündeki pencerelerin renkleri (rozet R7, balonlar ve kart R6, E7 kırmızı rozeti): Sürüş Paneli
 * `RawPalette` (ninebot-logger @ 6388c04) değerleri aynen. WEEM (ORDERS.md 16.3): kabuğun paleti (`logic/app` `Palette`) Compose
 * ekranlarınındır; pencereler Rawstats'taki renkleriyle kalır (gölge modda iki uygulamanın rozeti aynı görünür). Renkler 0xAARRGGBB.
 */
class OverlayPalette private constructor(val dark: Boolean, c: IntArray) {
    val bg = c[0]; val surface = c[1]; val raised = c[2]; val line = c[3]; val text = c[4]; val muted = c[5]
    val accent = c[6]; val accentInk = c[7]; val accentText = c[8]; val positive = c[9]; val positiveBg = c[10]
    val warn = c[11]; val warnBg = c[12]; val danger = c[13]; val wolt = c[14]; val bolt = c[15]; val woltBg = c[16]; val boltBg = c[17]
    val hero = c[18]; val heroLine = c[19]

    companion object {
        private val LIGHT = OverlayPalette(false, intArrayOf(
            0xfff5f5f5.toInt(), 0xffffffff.toInt(), 0xffeeeeee.toInt(), 0xffdddddd.toInt(), 0xff1c1c1c.toInt(), 0xff5c5c5c.toInt(),
            0xff216b57.toInt(), 0xfff5fff9.toInt(), 0xff216b57.toInt(), 0xff226d50.toInt(), 0xffe5f2e9.toInt(),
            0xff8a5b20.toInt(), 0xfff7eedb.toInt(), 0xffae4444.toInt(), 0xff21677c.toInt(), 0xff286743.toInt(), 0xffe5f0f3.toInt(), 0xffe8f2e8.toInt(),
            0xffeeeeee.toInt(), 0xffd6d6d6.toInt()))
        private val DARK = OverlayPalette(true, intArrayOf(
            0xff121212.toInt(), 0xff1e1e1e.toInt(), 0xff2a2a2a.toInt(), 0xff3d3d3d.toInt(), 0xffececec.toInt(), 0xffa6a6a6.toInt(),
            0xffa4d8b9.toInt(), 0xff173d2b.toInt(), 0xffa4d8b9.toInt(), 0xff9bd3af.toInt(), 0xff20392a.toInt(),
            0xffe8c58e.toInt(), 0xff382f21.toInt(), 0xfff2a5a1.toInt(), 0xff94cddd.toInt(), 0xffa5d6ad.toInt(), 0xff20373c.toInt(), 0xff243c2c.toInt(),
            0xff262626.toInt(), 0xff3d3d3d.toInt()))

        fun forMode(dark: Boolean): OverlayPalette = if (dark) DARK else LIGHT
    }
}
