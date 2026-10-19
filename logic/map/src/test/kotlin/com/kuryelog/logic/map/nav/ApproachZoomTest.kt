package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.nav.ApproachZoom.APPROACH_MAX_ZOOM
import com.kuryelog.logic.map.nav.ApproachZoom.speedZoom
import com.kuryelog.logic.map.nav.ApproachZoom.zoomFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `approach_zoom_test.dart` (4; A6.2b, MAP.md 7.10, 7.15): hedefe ~1 km kala zoom hız tabanından ~17,5'e yumuşak (ease-in-out) artar;
 * elle dokunulunca rota açıkken 10 sn sonra takip ve otomatik zoom geri gelir. Uygulama: A6.6.
 */
class ApproachZoomTest {
    @Test
    fun `hız tabanı eskisi gibi`() {
        assertEquals(16.6, speedZoom(0.0), 0.0)
        assertEquals(16.2, speedZoom(10.0), 0.0)
        assertEquals(15.9, speedZoom(20.0), 0.0)
        assertEquals(15.6, speedZoom(30.0), 0.0)
    }

    @Test
    fun `1 km den uzakta ya da kalan bilinmiyorsa yalnız hız tabanı`() {
        for (kmh in listOf(0.0, 10.0, 20.0, 30.0)) {
            assertEquals(speedZoom(kmh), zoomFor(kmh, null), 0.0)
            assertEquals(speedZoom(kmh), zoomFor(kmh, 1000.0), 1e-9)
            assertEquals(speedZoom(kmh), zoomFor(kmh, 3000.0), 0.0)
        }
    }

    @Test
    fun `100 m de ve altında 17,5 aradaki geçiş tek yönlü ve uçlarda yumuşak`() {
        for (kmh in listOf(5.0, 20.0, 30.0)) {
            assertEquals(APPROACH_MAX_ZOOM, zoomFor(kmh, 100.0), 1e-9)
            assertEquals(APPROACH_MAX_ZOOM, zoomFor(kmh, 20.0), 1e-9)
            var last = zoomFor(kmh, 1000.0)
            var d = 990.0
            while (d >= 100) {
                val z = zoomFor(kmh, d)
                assertTrue(z >= last - 1e-12)
                assertTrue("10 m'de 0,05 kademeden az: sıçrama yok", z - last < .05)
                last = z
                d -= 10
            }
            // ease-in-out: ilk ve son 100 m'deki artış ortadakinden küçük
            val start = zoomFor(kmh, 900.0) - zoomFor(kmh, 1000.0)
            val middle = zoomFor(kmh, 500.0) - zoomFor(kmh, 600.0)
            val end = zoomFor(kmh, 100.0) - zoomFor(kmh, 200.0)
            assertTrue(start < middle)
            assertTrue(end < middle)
        }
    }

    @Test
    fun `elle dokunma rota açıkken 10 sn sonra takip ve otomatik zoom, rota yokken eski 8 sn yalnız hareket halinde`() {
        assertEquals(10, ApproachZoom.AUTO_ZOOM_PAUSE_S)
        assertEquals(Refollow.NONE, ApproachZoom.refollowAfterGesture(route = true, moving = false, sinceMs = 9_000))
        assertEquals(Refollow.FOLLOW_AUTO_ZOOM, ApproachZoom.refollowAfterGesture(route = true, moving = false, sinceMs = 10_000))
        assertEquals(Refollow.NONE, ApproachZoom.refollowAfterGesture(route = false, moving = false, sinceMs = 30_000))
        assertEquals(Refollow.NONE, ApproachZoom.refollowAfterGesture(route = false, moving = true, sinceMs = 7_000))
        assertEquals(Refollow.FOLLOW_KEEP_ZOOM, ApproachZoom.refollowAfterGesture(route = false, moving = true, sinceMs = 8_000))
    }
}
