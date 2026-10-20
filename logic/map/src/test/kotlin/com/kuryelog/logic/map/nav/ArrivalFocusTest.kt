package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.nav.ArrivalFocus.FOCUS_MAX_ZOOM
import com.kuryelog.logic.map.nav.ArrivalFocus.FOCUS_MIN_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * YolGo `arrival_focus_test.dart` (4; A6.2b, MAP.md 7.10, 7.15): hedefe ≤100 m kalınca varış odağı açılır; >150 m'de (histerezis), varınca ya da rota kalkınca
 * kapanır; kalan bilinmiyorsa durum değişmez. Odakta kamera ben + hedefi aynı kareye alır, zoom [18, 19]. Sağlayıcı testi saf [ArrivalFocusTracker] ile.
 */
class ArrivalFocusTest {
    private fun f(cur: Boolean, m: Double?, route: Boolean = true, arrived: Boolean = false) = ArrivalFocus.next(cur, route, arrived, m)

    @Test
    fun `histerezis 100 m de girer, 150 m ye kadar kalır`() {
        assertFalse(f(false, 101.0))
        assertTrue(f(false, 100.0))
        assertTrue(f(true, 140.0))
        assertTrue(f(true, 150.0))
        assertFalse(f(true, 151.0))
        assertFalse("dışarıdan 140 a gelmek açmaz", f(false, 140.0))
        assertTrue(f(true, null))
        assertFalse(f(false, null))
        assertFalse(f(true, 20.0, arrived = true))
        assertFalse(f(true, 20.0, route = false))
    }

    @Test
    fun `sağlayıcı kalan yolu izler, rota kalkınca kapanır`() {
        val t = ArrivalFocusTracker()
        assertFalse(t.on)
        assertFalse(t.update(hasRoute = true, arrived = false, remainingM = 400.0))
        assertTrue(t.update(true, false, 95.0))
        assertTrue(t.update(true, false, 130.0))
        assertFalse(t.update(true, false, 160.0))
        assertTrue(t.update(true, false, 80.0))
        assertFalse("varış: plan temizlenir", t.update(hasRoute = false, arrived = false, remainingM = null))
    }

    private val meLat = 40.4093
    private val meLon = 49.8671
    private fun northLat(m: Double) = meLat + m / 111195.08
    private fun mPerPx(lat: Double, z: Double) = 156543.03392 * cos(lat * PI / 180) / 2.0.pow(z)

    @Test
    fun `orta nokta merkez, zoom 18-19 ve iki nokta ekranda`() {
        for (m in listOf(10.0, 40.0, 70.0, 100.0)) {
            val destLat = northLat(m)
            val cam = ArrivalFocus.focusCamera(meLat, meLon, destLat, meLon, viewportPx = 360)
            assertTrue(cam.zoom in FOCUS_MIN_ZOOM..FOCUS_MAX_ZOOM)
            assertEquals((meLat + destLat) / 2, cam.lat, 1e-9)
            assertEquals(meLon, cam.lon, 1e-9)
            if (cam.zoom > FOCUS_MIN_ZOOM) {
                // sığdırma payı içinde: yarım mesafe kısa kenarın yarısından küçük
                assertTrue(m / 2 / mPerPx(cam.lat, cam.zoom) < 180)
            }
        }
    }

    @Test
    fun `çok yakın hedefte 19, uzakta 18 sınırı`() {
        assertEquals(FOCUS_MAX_ZOOM, ArrivalFocus.focusCamera(meLat, meLon, northLat(3.0), meLon, 360).zoom, 0.0)
        assertEquals(FOCUS_MIN_ZOOM, ArrivalFocus.focusCamera(meLat, meLon, northLat(400.0), meLon, 360).zoom, 0.0)
    }
}
