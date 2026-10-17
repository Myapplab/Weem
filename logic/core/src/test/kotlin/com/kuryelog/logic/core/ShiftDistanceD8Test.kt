package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** D8 (saha 12 Ekim: 4 sa 54 dk vardiyanın sonunda "km: 0"): vardiya mesafesi süreç ölünce kaybolmaz, scooter odometresi yedektir. */
class ShiftDistanceD8Test {
    private val min = 60_000L

    @Test fun aNewProcessKeepsTheSavedGpsDistance() {
        // The process that counted 23.4 km died; the shift is ended from a new process whose accumulator is 0.
        val r = ShiftDistance.result(0.0, 23_400.0, null)
        assertEquals(23.4, r.km, 1e-9); assertEquals(23.4, r.gpsKm, 1e-9); assertNull(r.scooterKm); assertFalse(r.fromScooter)
        // The live accumulator is newer than the value saved every 15 s.
        assertEquals(23.5, ShiftDistance.result(23_500.0, 23_400.0, null).km, 1e-9)
        assertEquals(0.0, ShiftDistance.result(Double.NaN, -5.0, null).km, 0.0)
    }

    @Test fun scooterOdometerCoversADeadGpsService() {
        var o: ShiftDistance.Odo? = null
        for ((i, km) in listOf(1250.0, 1250.4, 1251.2, 1258.9).withIndex()) o = ShiftDistance.observe(o, km, i * 10 * min)
        assertEquals(8.9, o!!.km, 1e-9)
        val r = ShiftDistance.result(3_100.0, 0.0, o)
        assertEquals(8.9, r.km, 1e-9); assertEquals(3.1, r.gpsKm, 1e-9); assertEquals(8.9, r.scooterKm!!, 1e-9); assertTrue(r.fromScooter)
        // GPS longer (the scooter link was off for a while): GPS is written, the odometer kept beside it.
        assertFalse(ShiftDistance.result(20_000.0, 0.0, o).fromScooter)
    }

    @Test fun odometerMisreadsAreIgnored() {
        var o = ShiftDistance.observe(null, 1250.0, 0)
        o = ShiftDistance.observe(o, 1250.5, min)
        assertEquals(o, ShiftDistance.observe(o, null, 2 * min)); assertEquals(o, ShiftDistance.observe(o, Double.NaN, 2 * min))
        assertEquals(o, ShiftDistance.observe(o, 1249.0, 2 * min))          // backwards
        assertEquals(o, ShiftDistance.observe(o, 1300.0, 2 * min))          // 49.5 km in a minute
        assertEquals(1270.5, ShiftDistance.observe(o, 1270.5, 31 * min)!!.lastKm, 1e-9) // 20 km in 30 min (link was off)
        // An odd first reading is replaced while nothing was ridden yet.
        val odd = ShiftDistance.observe(null, 0.0, 0)
        assertEquals(1250.0, ShiftDistance.observe(odd, 1250.0, 1000)!!.firstKm, 1e-9)
    }

    @Test fun savedAsMetres() {
        val o = ShiftDistance.Odo(1250.1234, 1262.98, 5 * min)
        val back = ShiftDistance.load(ShiftDistance.meters(o.firstKm), ShiftDistance.meters(o.lastKm), o.lastAt)!!
        assertEquals(12.857, back.km, 1e-9); assertEquals(5 * min, back.lastAt)
        assertNull(ShiftDistance.load(-1, -1, 0)); assertNull(ShiftDistance.load(5000, 4000, 0))
    }
}
