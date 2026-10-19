package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rawstats'tan YolGo `DatasetReadsTest.kt` 2. test (1; A6.2b, MAP.md 13.3, 13.18): her satır okuma ölçüsünü taşır; eski Rawstats'ta gecikme null + bayrak; `changedAt` okuma.
 * Çevrilmez: 1. test (yoklama sabitleri; yalnız `ROW_GAP_MS` kalır, [OfferRowGateTest]'te denetlenir) ve 6. test (`PollRowGate`; WEEM'de okuma ve yoklama yok, MAP.md 13.11).
 */
class DatasetMeasureTest {
    private fun offer(lastSeen: Long = 2_000, fee: Double = 4.49, lat: Double? = null): JSONObject {
        val input = JSONObject("""{"displayedFeeAzn":$fee,"platformDistanceKm":2.3,"pickupAddress":"40.40912,49.86721","v2":{"offerKey":"offer-1000-1","platform":"wolt","firstSeenAtMs":1000,"lastSeenAtMs":$lastSeen,"shown":{"km":2.3}}}""")
        return DatasetContract.offer(input, 3_000, 4_000, lat?.let { DatasetContract.point(it, 49.867) }, "baku")!!
    }

    @Test
    fun everyRowCarriesTheReadMeasurement() {
        val row = DatasetContract.measure(offer(), 10_500, 10_000, "observer")
        assertEquals(10_500L, row.getLong("receivedAtMs"))
        assertEquals(10_000L, row.getLong("rawstatsChangedAtMs"))
        assertEquals(500L, row.getLong("feedDelayMs"))
        assertEquals("observer", row.getString("readTrigger"))
        assertEquals("rawstats_feed_v2", row.getJSONObject("field_sources").getString("rawstatsChangedAtMs"))
        // Old Rawstats sends no changedAtMs: the delay stays unknown and flagged, it is never invented.
        val old = DatasetContract.measure(offer(), 10_500, null, "fallback")
        assertTrue(old.isNull("rawstatsChangedAtMs")); assertTrue(old.isNull("feedDelayMs"))
        assertEquals("fallback", old.getString("readTrigger"))
        assertEquals("unavailable_old_rawstats", old.getJSONObject("field_sources").getString("feedDelayMs"))
        assertEquals(9_000L, DatasetContract.changedAt(JSONObject("""{"changedAtMs":9000}""")))
        assertNull(DatasetContract.changedAt(JSONObject("{}")))
        assertNull(DatasetContract.changedAt(JSONObject("""{"changedAtMs":"x"}""")))
    }
}
