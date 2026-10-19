package com.kuryelog.logic.map.day

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `light_leaks_f52_test.dart` (3; A6.2b, MAP.md 11.9, 11.16) — Ekim planı madde 8 (F5.2): olası kaçaklar (a)–(d) için bugünkü davranış. Karakterizasyon testi:
 * kural değişirse (kullanıcı kararı/saha verisi) burası bilerek güncellenir.
 */
class LightLeaksTest {
    private val t0 = DayFixtures.ms("2026-10-16T12:00:00Z")
    private val lat0 = 40.4093
    private val lon0 = 49.8671
    private fun north(m: Double) = lat0 + m / 111195.08

    /** [waitS] sn durup sonra uzaklaşınca kaydedilen duruş. */
    private fun wait(info: RoadInfo, waitS: Int, accuracy: ((Int) -> Double)? = null, orderStop: Boolean = false): StopRec? {
        val t = LightTracker()
        var rec: StopRec? = null
        for (s in 0..waitS) {
            t.feed(DayFix(t0 + s * 1000L, north(0.0), lon0, speedMps = 0.0, accuracy = accuracy?.invoke(s) ?: 5.0), { _, _ -> info }, orderStop)
        }
        var s = waitS + 1
        while (s <= waitS + 10 && rec == null) {
            rec = t.feed(DayFix(t0 + s * 1000L, north((s - waitS) * 6.0), lon0, speedMps = 6.0), { _, _ -> info }, orderStop)
            s++
        }
        return rec
    }

    private val known = RoadInfo(onRoad = true, known = true, lightM = 4.0, surface = StopSurface.ROAD)
    private val unknownJunction = RoadInfo(onRoad = true, surface = StopSurface.ROAD, junctionM = 20.0)

    @Test
    fun `a bilinen ışıkta 3 sn yeter, bilinmeyen kavşakta 10 sn altı aday olmaz`() {
        assertEquals("light", wait(known, 4)?.kind)
        assertNull(wait(unknownJunction, 8))
        assertEquals("light?", wait(unknownJunction, 12)?.kind)
    }

    @Test
    fun `b tek tük kötü fix (35 m üstü) beklemeyi bozmaz, 12 sn den uzun kötü fix dizisi sıfırlar`() {
        assertEquals("light", wait(known, 30, accuracy = { s -> if (s % 5 == 0) 60.0 else 5.0 })?.kind)
        // 14 sn boyunca kötü fix: ölçüm sıfırlanır, kalan bekleme 3 sn'den uzunsa yine kaydedilir.
        assertEquals("light", wait(known, 30, accuracy = { s -> if (s in 5..18) 60.0 else 5.0 })?.kind)
    }

    @Test
    fun `d sipariş duruşunda (restoran müşteri) ışık ölçülmez`() {
        assertNull(wait(known, 30, orderStop = true))
    }
}
