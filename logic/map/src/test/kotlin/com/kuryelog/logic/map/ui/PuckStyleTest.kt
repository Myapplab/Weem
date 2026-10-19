package com.kuryelog.logic.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `puck_arrow_test.dart` saf 2 test + `v160_y1_y2_test.dart` imleç boyutu (1; 3. `puck_arrow` tanımı `:map` Robolectric `LocationPuckTest`; A6.2b,
 * MAP.md 6.14, 10.12): rota açık sürerken nokta yerine küçük beyaz kenarlı mavi ok; dururken ya da rota yokken nokta (+ koni); imleç Google Maps gibi ~20 dp.
 */
class PuckStyleTest {
    @Test
    fun `ok yalnız rota açık ve hareket yönü belliyken`() {
        assertTrue(PuckStyle.showsArrow(route = true, course = 90.0))
        assertFalse(PuckStyle.showsArrow(route = true, course = null)) // dururken (yön yok)
        assertFalse(PuckStyle.showsArrow(route = false, course = 90.0)) // rota yok
        assertTrue(PuckStyle.ARROW_SIZE in 20.0..24.0)
        assertTrue(PuckStyle.ARROW_SIZE <= PuckStyle.CANVAS)
    }

    @Test
    fun `ok yolu kutunun içinde ve ucu kuzeyde (yukarı)`() {
        val (cx, cy) = 30.0 to 30.0
        val pts = PuckStyle.arrowPoints(cx, cy, PuckStyle.ARROW_SIZE)
        val top = pts.minOf { it.second }
        val bottom = pts.maxOf { it.second }
        assertEquals(PuckStyle.ARROW_SIZE, bottom - top, .01)
        assertEquals(cy - PuckStyle.ARROW_SIZE / 2, top, .01)
        assertTrue(pts.maxOf { it.first } - pts.minOf { it.first } < PuckStyle.ARROW_SIZE)
    }

    @Test
    fun `Ekim planı madde 5 konum imleci Google Maps gibi 20 dp (Y2 büyütmesi geri alındı)`() {
        // Beyaz kenar çapı ~20 dp, mavi nokta kenarın 3/4'ü, gölge kenardan biraz geniş.
        assertTrue(PuckStyle.RING_RADIUS * 2 in 18.0..22.0)
        assertEquals(.75, PuckStyle.DOT_RADIUS / PuckStyle.RING_RADIUS, .01)
        assertTrue(PuckStyle.SHADOW_RADIUS in PuckStyle.RING_RADIUS..PuckStyle.RING_RADIUS + 2)
        // Yön konisi ~28 dp; iki yüzeyin çizim kutusu koniyi kesmez ve eski 96/110 px'ten küçük.
        assertTrue(PuckStyle.BEAM_RADIUS in 26.0..30.0)
        assertTrue(PuckStyle.CANVAS >= PuckStyle.BEAM_RADIUS * 2)
        assertTrue(PuckStyle.CANVAS <= 64)
    }
}
