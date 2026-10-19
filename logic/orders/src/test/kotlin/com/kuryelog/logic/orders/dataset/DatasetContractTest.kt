package com.kuryelog.logic.orders.dataset

import com.kuryelog.logic.core.Clock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Rawstats'tan YolGo `DatasetContractTest.kt` (6; A6.2b, MAP.md 13.3, 13.18; aynı girdi → aynı çıktı). 3. test sahte [Clock] (`Asia/Baku`) ile: gün sınırı telefon saat diliminde
 * (MAP.md 13.5), Bakü'de sonuç aynı.
 */
class DatasetContractTest {
    private val baku = object : Clock {
        override fun wallMs() = 0L
        override fun monoMs() = 0L
        override fun zone(): ZoneId = ZoneId.of("Asia/Baku")
    }

    private fun input() = JSONObject("""{"displayedFeeAzn":4.49,"platformDistanceKm":2.3,"customerName":"SECRET PERSON","phone":"+994551234567","pickupAddress":"40.40912,49.86721","dropoffAddress":"Secret street, door code 9876","v2":{"offerKey":"offer-1000-1","platform":"bolt","firstSeenAtMs":1000,"lastSeenAtMs":2000,"shown":{"km":2.3,"demandLabel":"PRIVATE NOTE"},"pickupZone":"Z1","dropoffZone":"unknown"}}""")

    @Test
    fun projectsOnlySafeFieldsAndPreservesIdentity() {
        val row = DatasetContract.offer(input(), 3000, 4000, null, "baku")!!
        assertEquals("offer-1000-1", row.getString("offerKey"))
        assertEquals(4.49, row.getDouble("feeAzn"), 0.0)
        assertEquals(40.409, row.getJSONObject("pickup").getDouble("lat"), 0.0)
        for (secret in listOf("SECRET", "+994", "Secret street", "9876", "PRIVATE NOTE", "customerName", "phone", "dropoffAddress")) assertFalse(row.toString().contains(secret))
        assertTrue(row.isNull("dropoff")); assertTrue(row.isNull("routeKm"))
        assertEquals("unavailable", row.getJSONObject("field_sources").getString("routeKm"))
        assertFalse(row.getBoolean("power_verified"))
    }

    @Test
    fun rejectsUnsafeIdentityAndInvalidTimestamp() {
        val o = input(); o.getJSONObject("v2").put("offerKey", "Customer name 55123")
        assertNull(DatasetContract.offer(o, 3000, 4000, null, null))
        o.getJSONObject("v2").put("offerKey", "offer-1000-1").put("lastSeenAtMs", 999)
        assertNull(DatasetContract.offer(o, 3000, 4000, null, null))
    }

    @Test
    fun civilDateSplitsAtBakuMidnight() {
        val zone = baku.zone()
        assertEquals("2026-10-09", DatasetContract.day(java.time.Instant.parse("2026-10-09T19:59:59Z").toEpochMilli(), zone))
        assertEquals("2026-10-10_Cumartesi", DatasetContract.folder(DatasetContract.day(java.time.Instant.parse("2026-10-09T20:00:00Z").toEpochMilli(), zone)))
    }

    @Test
    fun plusCodeNeedsObservedReferenceAndNeverUsesDefault() {
        assertNull(DatasetContract.coordinate("9RQW+344 Baku", null))
        assertNotNull(DatasetContract.coordinate("9RQW+344 Baku", DatasetContract.point(40.409, 49.867)))
        assertNull(DatasetContract.point(91.0, 49.0))
    }

    @Test
    fun pollingClockDoesNotInventOfferChanges() {
        val a = DatasetContract.offer(input(), 3000, 4000, null, null)!!
        val b = DatasetContract.offer(input(), 5000, 6000, null, null)!!
        assertEquals(DatasetContract.changedFingerprint(a), DatasetContract.changedFingerprint(b))
        b.put("lastSeenAtMs", 2500)
        assertNotEquals(DatasetContract.changedFingerprint(a), DatasetContract.changedFingerprint(b))
    }

    @Test
    fun unknownNestedValuesCarrySourceFlags() {
        val row = DatasetContract.flagUnknowns(DatasetContract.offer(input(), 3000, 4000, null, null)!!)
        assertEquals("unavailable", row.getJSONObject("field_sources").getString("shown.minLo"))
        assertEquals("unavailable", row.getJSONObject("field_sources").getString("scooter_id"))
    }
}
