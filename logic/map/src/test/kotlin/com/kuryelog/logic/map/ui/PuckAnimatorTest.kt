package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.CompassSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S38: GPS yönü görünür ve gerçek zamanlı (pusula), dururken titreme işareti oynatmaz, değişiklik yokken kare yok. */
class PuckAnimatorTest {
    private val p0 = GeoPoint(40.4, 49.85)

    private fun step(to: GeoPoint, course: Double?, jump: Boolean = false, slide: Long = 850, turning: Boolean = false) =
        PuckStep(to, to, course, slide, jump, turning, null)

    @Test
    fun `açı farkı en kısa yoldan ve işaretli`() {
        assertEquals(2.0, Geo.angleDelta(1.0, 359.0), 1e-9)
        assertEquals(-2.0, Geo.angleDelta(359.0, 1.0), 1e-9)
        assertEquals(-90.0, Geo.angleDelta(0.0, 90.0), 1e-9)
        assertEquals(350.0, Geo.norm360(-10.0), 1e-9)
    }

    @Test
    fun `pusula yumuşatması ilk değeri alır, sonra yüzde 35 yaklaşır ve 360'ta sarar`() {
        val c = CompassSmoother()
        assertEquals(350.0, c.update(350.0)!!, 1e-9)
        // 350 → 10: kısa yol +20°, %35'i = 7° → 357.
        assertEquals(357.0, c.update(10.0)!!, 1e-9)
        assertEquals(357.0, c.update(Double.NaN)!!, 1e-9)
        c.reset()
        assertNull(c.value)
    }

    @Test
    fun `konum gelmeden pusula dönse de kare istenmez`() {
        val a = PuckAnimator()
        a.onCompass(10.0)
        a.onCompass(50.0)
        assertFalse(a.busy(0))
        assertNull(a.frame(0))
    }

    @Test
    fun `dururken yön pusuladan gelir (önceden yön yoktu)`() {
        val a = PuckAnimator()
        a.onStep(step(p0, course = null, jump = true), 5.0, null, p0, 0)
        assertNull(a.frame(0)!!.heading) // pusula yok, GPS yönü yok: yalnız nokta
        a.onCompass(120.0)
        assertTrue(a.busy(10))
        val f = a.frame(10)!!
        assertEquals(120.0, f.heading!!, 1e-9)
        assertFalse(f.moving)
    }

    @Test
    fun `hareketteyken çapa GPS yönü, pusulanın dönüşü anında eklenir`() {
        val a = PuckAnimator()
        a.onCompass(200.0) // scooter pusulayı saptırıyor: mutlak değer yanlış
        a.onStep(step(p0, course = 90.0, jump = true), 5.0, 90.0, p0, 0)
        assertEquals(90.0, a.frame(0)!!.heading!!, 1e-9)
        // Gidon 20° sağa döndü (pusula 200 → 220): ok GPS'i beklemeden döner.
        repeat(40) { a.onCompass(220.0) }
        var h = 0.0
        for (t in 1..20) h = a.frame(t * 33L)?.heading ?: h
        assertEquals(110.0, h, 0.5)
        // En çok ±45°: pusula saçmalasa da ok GPS yönünden fazla sapmaz.
        repeat(60) { a.onCompass(300.0) }
        for (t in 21..60) h = a.frame(t * 33L)?.heading ?: h
        assertEquals(135.0, h, 0.5)
    }

    @Test
    fun `durunca çapa bir dakika kalır, sonra yalnız pusula`() {
        val a = PuckAnimator()
        a.onCompass(200.0)
        a.onStep(step(p0, course = 90.0, jump = true), 5.0, 90.0, p0, 0)
        a.frame(0)
        a.onStep(step(p0, course = null), 5.0, null, p0, 1_000)
        // Işıkta: pusula kıpırdamadı, yön 90'da kalır (sıçramaz).
        assertEquals(90.0, a.frame(1_033)!!.heading ?: 90.0, 1e-6)
        assertFalse(a.busy(30_000))
        // 60 sn sonra çapa düşer: pusulanın mutlak değeri (200).
        assertTrue(a.busy(62_000))
        var h = 90.0
        for (t in 0..60) h = a.frame(62_000 + t * 33L)?.heading ?: h
        assertEquals(200.0, h, 0.5)
    }

    @Test
    fun `işaret kayar, bitince kare istemez, değişiklik yoksa null`() {
        val a = PuckAnimator()
        a.onStep(step(p0, course = null, jump = true), 5.0, null, p0, 0)
        a.frame(0)
        val b = GeoPoint(p0.lat + 0.0002, p0.lon) // ~22 m
        a.onStep(step(b, course = 0.0, slide = 850), 5.0, 0.0, b, 1_000)
        assertTrue(a.busy(1_000))
        val mid = a.frame(1_425)!!
        assertEquals(p0.lat + 0.0001, mid.point.lat, 1e-7)
        assertEquals(b, a.frame(1_900)!!.point)
        assertFalse(a.busy(1_950))
        assertNull(a.frame(1_983))
    }

    @Test
    fun `dururken iki metreden küçük titreme işareti oynatmaz`() {
        val a = PuckAnimator()
        a.onStep(step(p0, course = null, jump = true), 5.0, null, p0, 0)
        a.frame(0)
        val jitter = GeoPoint(p0.lat + 0.00001, p0.lon) // ~1,1 m
        a.onStep(step(jitter, course = null), 5.0, null, jitter, 1_000)
        assertFalse(a.busy(1_000))
        assertEquals(p0, a.frame(1_033)!!.point) // ham konum değişti, işaret yerinde
        assertEquals(p0, a.displayed)
        // 2 m'yi geçince kayar.
        val walk = GeoPoint(p0.lat + 0.00003, p0.lon) // ~3,3 m
        a.onStep(step(walk, course = null), 5.0, null, walk, 2_000)
        assertTrue(a.busy(2_000))
        assertNotNull(a.frame(2_900))
        assertEquals(walk, a.displayed)
    }

    @Test
    fun `kamera yaklaşması zamana bağlı`() {
        assertEquals(0.15, CameraEase.factor(0.15, 80, 80), 1e-9)
        assertEquals(1 - 0.85 * 0.85, CameraEase.factor(0.15, 80, 160), 1e-9)
        assertEquals(0.0, CameraEase.factor(0.15, 80, 0), 1e-9)
    }
}
