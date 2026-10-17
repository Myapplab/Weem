package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.ScooterLive
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * RT `ScooterFeedTest.kt`'in 3 testi [ScooterLiveMapper]'a uyarlandı (SCOOTER.md 13.2; `pathMatchesYolGoReader` ve
 * `scooterReaderStillNeedsAllGates` çevrilmez: sağlayıcı kalkar, S7). Yük: yalnız scooter değerleri ve yolculuk sayacı, ölçülmeyen null.
 * Fark: bağlantı yokken `trip` null (eski yükte hep nesne).
 */
class ScooterLiveMapperTest {
    private fun frame(mono: Long, pct: Double? = 78.0) = NinebotDashboard.Snapshot(18.5, -310.0, null, null, pct, null, null, null, null, 18.5, 64.0, null, mono)
    private fun stats(wh: Double? = 12.5) = TripMeter.Stats("t1", 1_000, wh, null, 0, 0, null, 17.2, 3.1, 600_000, -310.0)

    @Test fun payloadCarriesLiveValuesAndTrip() {
        val live = ScooterLiveMapper.map(true, frame(mono = 4_580), stats(), nowMono = 5_000, nowWall = 5_000)
        val j = live.toJson()
        assertEquals(1, j.getInt("schemaVersion"))
        assertEquals(5_000L, j.getLong("generatedAtMs"))
        assertTrue(j.getBoolean("linkActive"))
        assertEquals(420L, j.getLong("ageMs"))
        val s = j.getJSONObject("scooter")
        assertEquals(78.0, s.getDouble("internalPct"), 0.0)
        assertEquals(64.0, s.getDouble("externalPct"), 0.0)
        assertEquals(-310.0, s.getDouble("powerW"), 0.0)
        assertEquals(18.5, s.getDouble("speedKmh"), 0.0)
        assertTrue(s.isNull("rangeKm"))
        val t = j.getJSONObject("trip")
        assertEquals(17.2, t.getDouble("avgMovingKmh"), 0.0)
        assertEquals(12.5, t.getDouble("wh"), 0.0)
        assertEquals(1_000L, t.getLong("startedAtMs"))
        assertTrue(live.fresh)
        assertEquals(ScooterLive.SOURCE, j.getString("source"))
    }

    @Test fun noLinkMeansNullsNotZeros() {
        val live = ScooterLiveMapper.map(false, frame(mono = 1, pct = Double.NaN), stats(), nowMono = 1, nowWall = 1)
        val j = live.toJson()
        assertFalse(j.getBoolean("linkActive"))
        assertTrue(j.isNull("ageMs"))
        assertTrue(j.getJSONObject("scooter").isNull("internalPct"))
        assertTrue(j.isNull("trip")) // WEEM: bağlantı yokken sayaç yok
        assertFalse(live.fresh)
        // Kare hiç yokken de aynı: sıfır uydurulmaz.
        val none = ScooterLiveMapper.map(true, null, stats(wh = null), nowMono = 9_000, nowWall = 9_000)
        assertNull(none.ageMs); assertNull(none.scooter.speedKmh); assertNull(none.trip!!.wh)
    }

    @Test fun payloadHasNoOrderOrLocationData() {
        val keys = JSONObject(ScooterLiveMapper.map(true, null, null, 1, 1).toJson().toString()).keys().asSequence().toSet()
        assertEquals(setOf("schemaVersion", "generatedAtMs", "source", "linkActive", "ageMs", "scooter", "trip"), keys)
        val inner = ScooterLiveMapper.map(true, frame(1), stats(), 1, 1).toJson().getJSONObject("scooter").keys().asSequence().toSet()
        assertTrue(inner.none { it in setOf("lat", "lon", "address", "customer", "orderId") })
    }

    @Test fun clockGoingBackwardsOrStaleFrameIsNotFresh() {
        assertNull(ScooterLiveMapper.map(true, frame(mono = 9_000), stats(), nowMono = 5_000, nowWall = 1).ageMs) // negatif yaş uydurulmaz
        val stale = ScooterLiveMapper.map(true, frame(mono = 0), stats(), nowMono = 16_000, nowWall = 1)
        assertEquals(16_000L, stale.ageMs); assertFalse(stale.fresh)
    }
}
