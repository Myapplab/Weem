package com.kuryelog.logic.map.ride

import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `v170_features_test.dart` "batarya" (1; A6.2b, MAP.md 10.3, 10.4, 10.12): canlı % tahmini geçer; bağlantı kopunca tahmin son canlı %'den sürer.
 * Yük ayrıştırma ve tazelik testleri `logic/core` `MessagesTest`'te (A2.5, `ScooterLive`) karşılandı; yoklama aralığı, zaman aşımı, voltaj/km tazeleme,
 * Rawstats ev cevabı çevrilmez (Rawstats yoklaması yok, S7); yüzey dizini sonra (Y13).
 */
class BatteryEstimatorTest {
    private fun live(pct: Double, ext: Double?) = ScooterLive(
        generatedAtMs = 1, linkActive = true, ageMs = 400, scooter = ScooterLive.Values(internalPct = pct, externalPct = ext),
    )

    @Test
    fun `canlı yüzde tahmini geçer, bağlantı kopunca tahmin son canlı yüzdeden sürer`() {
        val prefs = MemoryMapPrefs(mapOf("battery.setPct" to 50.0))
        val estimator = BatteryEstimator(prefs, capacityWh = 598.0) { 1_790_000_000_000L }
        assertEquals(50.0, estimator.state.value.percent!!, 0.0)
        estimator.onLive(live(78.0, 64.0))
        val b = estimator.state.value
        assertTrue(b.live)
        assertEquals(78.0, b.percent!!, 0.0)
        assertEquals(64.0, b.liveExternalPercent!!, 0.0)
        estimator.onLive(null)
        assertFalse(estimator.state.value.live)
        assertEquals("son canlı değer tahminin çapası", 78.0, estimator.state.value.percent!!, 0.0)
        assertEquals(78.0, prefs.getDouble("battery.setPct")!!, 0.0)
    }

    @Test
    fun `ikinci batarya sessizleşince son yüzdesi bağlantı sürdükçe 10 dk gösterilir`() {
        var now = 1_790_000_000_000L
        val estimator = BatteryEstimator(MemoryMapPrefs(emptyMap()), capacityWh = 598.0) { now }
        estimator.onLive(live(80.0, 96.0))
        assertEquals(96.0, estimator.state.value.liveExternalPercent!!, 0.0)
        now += 5 * 60_000L
        estimator.onLive(live(79.0, null)) // harici paket sessiz: yüzdesi bayat
        assertEquals(96.0, estimator.state.value.liveExternalPercent!!, 0.0)
        now += 6 * 60_000L
        estimator.onLive(live(79.0, null))
        assertNull(estimator.state.value.liveExternalPercent)
        // Bağlantı kopunca tutulmaz.
        estimator.onLive(live(79.0, 95.0))
        estimator.onLive(null)
        assertNull(estimator.state.value.liveExternalPercent)
    }
}
