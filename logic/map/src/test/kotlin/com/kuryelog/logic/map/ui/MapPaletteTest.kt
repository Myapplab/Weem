package com.kuryelog.logic.map.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Y1 karakterizasyonu (MAP.md 6.8): eğim eşikleri %3 / %8 ve gece/gündüz harita renkleri. */
class MapPaletteTest {
    @Test
    fun `eğim rengi yüzde 3 ve 8 eşikleri, iniş mavi`() {
        for (p in listOf(MapPalette.NIGHT, MapPalette.DAY)) {
            assertEquals(p.blue, p.forGrade(-0.12))
            assertEquals(p.blue, p.forGrade(0.03))
            assertEquals(p.orange, p.forGrade(0.031))
            assertEquals(p.orange, p.forGrade(0.08))
            assertEquals(p.red, p.forGrade(0.081))
        }
    }

    @Test
    fun `paletler YolGo değerleri`() {
        assertEquals(0xFF8BB5FF.toInt(), MapPalette.of(dark = true).blue)
        assertEquals(0xFF1765D1.toInt(), MapPalette.of(dark = false).blue)
        assertEquals(0xFFB68CFF.toInt(), MapPalette.NIGHT.pink)
        assertEquals(0xFFC0008A.toInt(), MapPalette.DAY.pink)
        assertEquals(0xFF3DDCE8.toInt(), MapPalette.NIGHT.bikeway)
        assertEquals(0xFF00838F.toInt(), MapPalette.DAY.bikeway)
        assertEquals(0x66A0A8B8, MapPalette.OTHER_PLAN)
    }
}
