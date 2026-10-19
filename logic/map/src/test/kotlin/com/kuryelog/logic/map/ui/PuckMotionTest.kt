package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.EdgeHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y1/Y2 karakterizasyonu (MAP.md 6.4 `_onFix`): süzgeç, kayma süresi, yola oturtma eşikleri, ileri tahmin, sıçrama. */
class PuckMotionTest {
    private val lat = 40.4
    private val lon = 49.85

    @Test
    fun `kötü okuma yok sayılır, ilk okuma sıçrar ve kamera yönünü verir`() {
        val m = PuckMotion()
        assertNull(m.onFix(PuckFix(lat, lon, 66.0, 5.0, 90.0), 0, null))
        assertNull(m.onFix(PuckFix(Double.NaN, lon, 5.0, 5.0, 90.0), 0, null))
        val first = m.onFix(PuckFix(lat, lon, 5.0, 5.0, 90.0), 1_000, null)!!
        assertTrue(first.jump)
        assertEquals(90.0, first.cameraHeading!!, 0.0)
        assertEquals(850L, first.slideMs) // ilk okumada aralık 1 sn
        // >120 m sıçrama kaymadan geçer, kamera yönü değişmez.
        val far = m.onFix(PuckFix(lat + 0.002, lon, 5.0, 5.0, 90.0), 2_000, first.to)!!
        assertTrue(far.jump)
        assertNull(far.cameraHeading)
    }

    @Test
    fun `kayma süresi aralığın yüzde 85i, 250-1000 ms`() {
        assertEquals(250L, PuckMotion.slideMs(100))
        assertEquals(425L, PuckMotion.slideMs(500))
        assertEquals(1000L, PuckMotion.slideMs(3000))
        val m = PuckMotion()
        val a = m.onFix(PuckFix(lat, lon, 5.0, 0.0, -1.0), 0, null)!!
        val b = m.onFix(PuckFix(lat, lon, 5.0, 0.0, -1.0), 600, a.to)!!
        assertEquals(510L, b.slideMs)
        assertEquals(false, b.jump)
        assertNull(b.course) // dururken yön yok
        assertEquals(a.to, b.from)
    }

    @Test
    fun `ileri tahmin hız çarpı 0,7 sn (dönüşte 0,3), en çok 15 m`() {
        assertEquals(7.0, PuckMotion.leadM(10.0, turning = false), 1e-9)
        assertEquals(3.0, PuckMotion.leadM(10.0, turning = true), 1e-9)
        assertEquals(15.0, PuckMotion.leadM(30.0, turning = false), 1e-9)
        val m = PuckMotion()
        val s = m.onFix(PuckFix(lat, lon, 5.0, 10.0, 0.0), 0, null)!!
        assertEquals(7.0, Geo.haversine(lat, lon, s.to.lat, s.to.lon), 0.05)
        assertTrue(s.to.lat > lat)
        // 1,4 m/s hareket sayılmaz: tahmin yok, yön yok.
        val slow = PuckMotion().onFix(PuckFix(lat, lon, 5.0, 1.4, 0.0), 0, null)!!
        assertEquals(GeoPoint(lat, lon), slow.to)
        assertNull(slow.course)
    }

    @Test
    fun `yola oturtma eşikleri ve yol yönü`() {
        val calls = mutableListOf<List<Double>>()
        val hit = EdgeHit(edge = 1, dist = 4.0, fraction = .5, lat = lat, lon = lon + 0.00005, segBearing = 190.0)
        val m = PuckMotion { _, _, d, h, tol -> calls += listOf(d, h, tol); hit }
        val a = m.onFix(PuckFix(lat, lon, 5.0, 5.0, 2.0), 0, null)!!
        assertEquals(listOf(15.0, 2.0, 40.0), calls.last())
        // Kenarın ters yönü (10°) GPS yönüne (2°) <30°: yön yolunki olur.
        assertEquals(10.0, a.course!!, 1e-9)
        // Dönüş (≥25°): 10 m / 25°.
        val b = m.onFix(PuckFix(lat, lon, 5.0, 5.0, 40.0), 1_000, a.to)!!
        assertTrue(b.turning)
        assertEquals(listOf(10.0, 40.0, 25.0), calls.last())
        // Doğruluk >25 m oturtmaz.
        val n = calls.size
        m.onFix(PuckFix(lat, lon, 30.0, 5.0, 40.0), 2_000, b.to)
        assertEquals(n, calls.size)
        assertNotNull(m.accuracyM)
    }

    @Test
    fun `yön filtresi yalnız iyi okumada beslenir, aksi sıfırlanır`() {
        val m = PuckMotion()
        m.onFix(PuckFix(lat, lon, 5.0, 5.0, 100.0, headingAccuracyDeg = 10.0), 0, null)
        assertEquals(100.0, m.heading.value!!, 1e-9)
        m.onFix(PuckFix(lat, lon, 5.0, 5.0, 110.0, headingAccuracyDeg = 10.0), 1_000, GeoPoint(lat, lon))
        assertEquals(104.0, m.heading.value!!, 1e-9) // ağırlık 0,4
        m.onFix(PuckFix(lat, lon, 5.0, 5.0, 110.0, headingAccuracyDeg = 60.0), 2_000, GeoPoint(lat, lon))
        assertNull(m.heading.value)
    }
}
