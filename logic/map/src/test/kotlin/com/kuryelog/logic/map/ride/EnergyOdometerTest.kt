package com.kuryelog.logic.map.ride

import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * YolGo `battery_estimator_test.dart` (5; A6.2b, MAP.md 10.3, 10.12): batarya tahmini dururken GPS titremesini sürüş saymamalı ve barometre ↔ DEM geçişindeki
 * mutlak yükseklik farkını tırmanış sanmamalı. 1. test Dart `Random(7)` yerine Kotlin tohumlu dizi (sonuç diziden bağımsız: 0).
 */
class EnergyOdometerTest {
    private val flat = 0.0135
    private val climb = 0.38
    private val t0 = 1_790_000_000_000L
    private val start = GeoPoint(40.4093, 49.8671)
    private fun north(m: Double) = GeoPoint(start.lat + m / 111320, start.lon)

    @Test
    fun `dururken GPS titremesi batarya harcaması üretmez`() {
        val odo = EnergyOdometer()
        val random = Random(7)
        var wh = 0.0
        for (i in 0 until 600) {
            // ±6 m titreşen konum, hız 0 (10 dakika restoranda bekleme)
            val jitter = GeoPoint(start.lat + (random.nextDouble() - .5) * 12 / 111320, start.lon + (random.nextDouble() - .5) * 12 / 85000)
            wh += odo.feed(jitter, t0 + i * 1000L, accuracy = 12.0, speedMps = 0.0, flatWhPerM = flat, climbWhPerM = climb)
        }
        assertEquals(0.0, wh, 0.0)
    }

    @Test
    fun `hızı bilinmeyen titreşim de doğruluk eşiği altında kalınca sayılmaz`() {
        val odo = EnergyOdometer()
        var wh = 0.0
        for (i in 0 until 300) {
            val p = if (i % 2 == 0) start else north(2.5) // 2.5 m sıçrama, accuracy 15 m
            wh += odo.feed(p, t0 + i * 1000L, accuracy = 15.0, speedMps = -1.0, flatWhPerM = flat, climbWhPerM = climb)
        }
        assertEquals(0.0, wh, 0.0)
    }

    @Test
    fun `gerçek düz sürüş mesafe kadar harcama üretir`() {
        val odo = EnergyOdometer()
        var wh = 0.0
        for (i in 0..150) {
            wh += odo.feed(north(i * 7.0), t0 + i * 1000L, accuracy = 5.0, speedMps = 7.0, flatWhPerM = flat, climbWhPerM = climb) { AltitudeSample(10.0, AltitudeSource.DEM) }
        }
        // 1050 m · 0.0135 Wh/m ≈ 14.2 Wh (son yarım pencere henüz kapanmadı)
        assertEquals(1050 * flat, wh, 30 * flat + 0.01)
    }

    @Test
    fun `barometre DEM geçişi sahte tırmanış eklemez, aynı kaynak tırmanışı sayar`() {
        fun ride(altitude: (Int) -> AltitudeSample): Double {
            val odo = EnergyOdometer()
            var wh = 0.0
            for (i in 0..10) {
                wh += odo.feed(north(i * 7.0), t0 + i * 1000L, accuracy = 5.0, speedMps = 7.0, flatWhPerM = flat, climbWhPerM = climb) { altitude(i) }
            }
            return wh
        }
        // Barometre 85 m derken sinyal kesildi, DEM 20 m: 65 m "iniş/çıkış" sahte.
        val switched = ride { i -> if (i < 5) AltitudeSample(85.0, AltitudeSource.BARO) else AltitudeSample(20.0, AltitudeSource.DEM) }
        val demToBaro = ride { i -> if (i < 5) AltitudeSample(20.0, AltitudeSource.DEM) else AltitudeSample(85.0, AltitudeSource.BARO) }
        assertEquals(switched, demToBaro, 1e-9)
        assertTrue("65 m · 0.38 = 24.7 Wh eklenmemeli", demToBaro < 2)
        // Aynı kaynak (barometre) 3 m yükseldiyse tırmanış gerçek.
        val rising = ride { i -> AltitudeSample(50.0 + i * 0.3, AltitudeSource.BARO) }
        assertTrue(rising > switched)
    }

    @Test
    fun `sinyal sıçraması (16 m sn üstü) yol sayılmaz`() {
        val odo = EnergyOdometer()
        var wh = odo.feed(start, t0, accuracy = 5.0, speedMps = 7.0, flatWhPerM = flat, climbWhPerM = climb)
        wh += odo.feed(north(900.0), t0 + 2000, accuracy = 5.0, speedMps = 7.0, flatWhPerM = flat, climbWhPerM = climb)
        assertEquals(0.0, wh, 0.0)
    }
}
