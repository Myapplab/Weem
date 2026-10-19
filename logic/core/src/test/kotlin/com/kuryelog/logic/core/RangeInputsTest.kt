package com.kuryelog.logic.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N5 menzil girdileri (MAP.md 10.6, 10.14; A6.7): ev, bırakış noktası ya da batarya yokken alan yazılmaz; kaynak etiketine göre BLE ya da tahmin alanı dolar. */
class RangeInputsTest {
    private val home = 40.40 to 49.85
    private val drop = 40.43 to 49.88
    private val here = 40.41 to 49.86

    private class Fake(val battery: RangeBattery? = null, val legs: (Double) -> RangeLeg? = { null }) : RangePort {
        val calls = ArrayList<Pair<Double, Double>>()
        override fun battery(): RangeBattery? = battery
        override suspend fun rideTo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RangeLeg? {
            calls += fromLat to fromLon
            return legs(fromLat)
        }
    }

    @Test
    fun `hiçbir kaynak yokken hiçbir alan dolmaz`() = runBlocking {
        val r = RangeInputs.of(RangePort.NONE, home, drop, here)
        assertEquals(RangeInputs(), r)
        assertFalse(r.hasScooter)
    }

    @Test
    fun `canlı BLE yüzdesi blePct ve bleAtMs olur, tahmin estimatorWh olur`() = runBlocking {
        val ble = RangeInputs.of(Fake(RangeBattery(78.0, 466.0, RangeBattery.BLE_FRESH, 1_000L)), null, null, null)
        assertEquals(78.0, ble.blePct!!, 0.0)
        assertEquals(1_000L, ble.bleAtMs)
        assertNull(ble.estimatorWh)
        val est = RangeInputs.of(Fake(RangeBattery(50.0, 299.0, RangeBattery.ESTIMATOR, 2_000L)), null, null, null)
        assertNull(est.blePct)
        assertNull(est.bleAtMs)
        assertEquals(299.0, est.estimatorWh!!, 0.0)
        assertTrue(est.hasScooter)
    }

    @Test
    fun `bırakıştan eve km ve dakika, buradan eve dakika`() = runBlocking {
        val fake = Fake { fromLat -> if (fromLat == drop.first) RangeLeg(6.2, 21.0, 90.0) else RangeLeg(1.5, 6.0, 20.0) }
        val r = RangeInputs.of(fake, home, drop, here)
        assertEquals(6.2, r.destToChargerKm!!, 0.0)
        assertEquals(21.0, r.destToHomeMin!!, 0.0)
        assertEquals(6.0, r.hereToHomeMin!!, 0.0)
        assertEquals(2, fake.calls.size)
    }

    @Test
    fun `ev yoksa rota hesaplanmaz, bırakış yoksa yalnız buradan eve`() = runBlocking {
        val fake = Fake { RangeLeg(2.0, 8.0, 30.0) }
        assertEquals(RangeInputs(), RangeInputs.of(fake, null, drop, here))
        assertTrue(fake.calls.isEmpty())
        val r = RangeInputs.of(fake, home, null, here)
        assertNull(r.destToChargerKm)
        assertNull(r.destToHomeMin)
        assertEquals(8.0, r.hereToHomeMin!!, 0.0)
        assertEquals(1, fake.calls.size)
    }

    @Test
    fun `bir rota hesaplanamazsa yalnız onun alanı boş kalır`() = runBlocking {
        val r = RangeInputs.of(Fake { fromLat -> if (fromLat == here.first) RangeLeg(1.0, 4.0, 10.0) else null }, home, drop, here)
        assertNull(r.destToChargerKm)
        assertEquals(4.0, r.hereToHomeMin!!, 0.0)
    }
}
