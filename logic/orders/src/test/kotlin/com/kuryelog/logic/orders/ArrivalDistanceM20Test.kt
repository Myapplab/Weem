package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 20 (16 Ekim, dördüncü tur): restorana/müşteriye varış mesafesi YolGo'nun nav yükünden (kalan metre) ya da Google
 * Maps navigasyon bildiriminden gelir, ikisi varsa yenisi; kuş uçuşu GPS her zaman yedek. GPS'te doğruluk payı (madde 18 kararı):
 * `uzaklık − min(doğruluk, 20) ≤ eşik`, doğruluğu 30 m'den kötü fix sayılmaz.
 */
class ArrivalDistanceM20Test {
    private fun src(m: Double, at: Long, kind: String, acc: Double? = null) = ArrivalDistance.Source(m, at, kind, acc)

    @Test fun googleMapsSummaryGivesMetres() {
        assertEquals(5.0, NavDistance.metres("1 min · 5 m")!!, 1e-9)
        assertEquals(2100.0, NavDistance.metres("8 min · 2,1 km")!!, 1e-9)
        assertEquals(450.0, NavDistance.metres("3 мин · 450 м")!!, 1e-9)
        assertNull(NavDistance.metres("Turn left"))
        assertNull(NavDistance.metres(null))
    }

    @Test fun theNewerNavigationSourceWinsAndGpsIsTheFallback() {
        val yolgo = src(12.0, 100_000, ArrivalDistance.YOLGO); val maps = src(4.0, 105_000, ArrivalDistance.MAPS)
        val gps = src(30.0, 106_000, ArrivalDistance.GPS, 8.0)
        assertEquals(maps, ArrivalDistance.pick(yolgo, maps, gps, 110_000))
        assertEquals(yolgo, ArrivalDistance.pick(yolgo, src(4.0, 90_000, ArrivalDistance.MAPS), gps, 110_000))
        assertEquals("30 sn'den eski nav değeri sayılmaz", gps, ArrivalDistance.pick(src(3.0, 70_000, ArrivalDistance.YOLGO), null, gps, 110_000))
        assertEquals(gps, ArrivalDistance.pick(null, null, gps, 110_000))
        assertNull(ArrivalDistance.pick(null, null, null, 110_000))
    }

    @Test fun nearWithTheAccuracyAllowance() {
        assertTrue(ArrivalDistance.near(src(5.0, 0, ArrivalDistance.YOLGO), 5.0))
        assertFalse(ArrivalDistance.near(src(6.0, 0, ArrivalDistance.MAPS), 5.0))
        assertTrue(ArrivalDistance.near(src(17.0, 0, ArrivalDistance.GPS, 12.0), 5.0))
        assertFalse(ArrivalDistance.near(src(26.0, 0, ArrivalDistance.GPS, 25.0), 5.0)) // pay en çok 20 m
        assertFalse(ArrivalDistance.near(src(20.0, 0, ArrivalDistance.GPS, 35.0), 5.0)) // kötü fix
        assertTrue(ArrivalDistance.near(src(4.0, 0, ArrivalDistance.GPS, null), 5.0))
        assertTrue(ArrivalDistance.near(src(28.0, 0, ArrivalDistance.GPS, 20.0), 10.0))
    }

    @Test fun slowMeansBelowTenKmh() {
        assertTrue(ArrivalDistance.slow(null)); assertTrue(ArrivalDistance.slow(9.9)); assertFalse(ArrivalDistance.slow(10.0))
    }
}
