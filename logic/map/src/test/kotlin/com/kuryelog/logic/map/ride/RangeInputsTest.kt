package com.kuryelog.logic.map.ride

import com.kuryelog.logic.core.RangeBattery
import com.kuryelog.logic.core.RangeLeg
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.map.geo.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N5 menzil girdileri (MAP.md 10.6, T1-2 (a)): ev, bırakış ya da batarya yokken alan yazılmaz; taze BLE % ile tahmin ayrı alanlara gider. */
class RangeInputsTest {
    private val home = GeoPoint(40.40, 49.85)
    private val here = GeoPoint(40.41, 49.86)
    private val drop = GeoPoint(40.43, 49.90)

    private class Port(val battery: RangeBattery?, val legs: Map<Pair<Double, Double>, RangeLeg> = emptyMap()) : RangePort {
        val asked = mutableListOf<Pair<Double, Double>>()
        override fun battery() = battery
        override suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg? {
            asked += fromLat to fromLon
            return legs[fromLat to fromLon]
        }
    }

    @Test
    fun taze_BLE_yüzdesi_ve_iki_bacak() = runBlocking {
        val port = Port(
            RangeBattery(62.0, 370.0, RangeBattery.BLE_FRESH, 1_000),
            mapOf((drop.lat to drop.lon) to RangeLeg(7.5, 21.0, 120.0), (here.lat to here.lon) to RangeLeg(2.0, 6.0, 30.0)),
        )
        val r = RangeInputs.build(port, here, home, drop)
        assertEquals(62.0, r.blePct!!, 0.0)
        assertEquals(1_000L, r.bleAtMs)
        assertNull("tahmin değil", r.estimatorWh)
        assertEquals(7.5, r.destToChargerKm!!, 0.0)
        assertEquals(21.0, r.destToHomeMin!!, 0.0)
        assertEquals(6.0, r.hereToHomeMin!!, 0.0)
        assertTrue(r.hasScooter)
    }

    @Test
    fun tahmin_ayrı_alana_gider() = runBlocking {
        val r = RangeInputs.build(Port(RangeBattery(50.0, 299.0, RangeBattery.ESTIMATOR, 5)), null, null, null)
        assertNull(r.blePct)
        assertNull(r.bleAtMs)
        assertEquals(299.0, r.estimatorWh!!, 0.0)
        assertTrue(r.hasScooter)
    }

    @Test
    fun ev_bırakış_ya_da_batarya_yokken_alan_yazılmaz() = runBlocking {
        val port = Port(null, mapOf((drop.lat to drop.lon) to RangeLeg(7.5, 21.0, 120.0)))
        val noHome = RangeInputs.build(port, here, null, drop)
        assertEquals(RangeInputs(), noHome)
        assertTrue("ev yokken rota istenmez", port.asked.isEmpty())
        val noDrop = RangeInputs.build(port, null, home, null)
        assertNull(noDrop.destToChargerKm)
        assertFalse(noDrop.hasScooter)
    }

    @Test
    fun harita_hazır_değilse_ya_da_değer_bozuksa_null() = runBlocking {
        assertEquals(RangeInputs(), RangeInputs.build(RangePort.NONE, here, home, drop))
        val bad = RangeInputs.of(null, Double.NaN, -1.0, Double.POSITIVE_INFINITY)
        assertEquals(RangeInputs(), bad)
    }
}
