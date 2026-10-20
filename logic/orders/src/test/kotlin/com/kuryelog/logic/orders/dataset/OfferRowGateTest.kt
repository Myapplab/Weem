package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Rawstats'tan YolGo `DatasetReadsTest.kt` 3., 4., 5. testler (3; A6.2b, MAP.md 13.3, 13.18): okuma ölçüsü teklif değişikliği değildir (çekirdek iz); `lastSeen` ya da kurye
 * değişimi teklif başına en çok 5 sn'de bir; bekletilen son görülme teklif gidince yazılır. WEEM'de satır üretimi iç teklif durumu değişince (okuma yok), seyreltme aynı.
 */
class OfferRowGateTest {
    private val day = "2026-10-14"

    private fun offer(lastSeen: Long = 2_000, fee: Double = 4.49, lat: Double? = null): JSONObject {
        val input = JSONObject("""{"displayedFeeAzn":$fee,"platformDistanceKm":2.3,"pickupAddress":"40.40912,49.86721","v2":{"offerKey":"offer-1000-1","platform":"wolt","firstSeenAtMs":1000,"lastSeenAtMs":$lastSeen,"shown":{"km":2.3}}}""")
        return DatasetContract.offer(input, 3_000, 4_000, lat?.let { DatasetContract.point(it, 49.867) }, "baku")!!
    }

    private fun read(lastSeen: Long = 2_000, fee: Double = 4.49, lat: Double? = 40.409, trigger: String = "observer") =
        DatasetContract.measure(offer(lastSeen, fee, lat), 10_000 + lastSeen, 9_900 + lastSeen, trigger)

    @Test
    fun theReadMeasurementIsNotAnOfferChange() {
        assertEquals(5_000L, DatasetContract.ROW_GAP_MS)
        val a = DatasetContract.measure(offer(), 10_500, 10_000, "observer")
        val b = DatasetContract.measure(offer(), 99_500, 90_000, "fallback")
        assertEquals(DatasetContract.changedFingerprint(a), DatasetContract.changedFingerprint(b))
        // lastSeenAtMs and the courier are changes, but not "core" ones.
        val c = DatasetContract.measure(offer(lastSeen = 2_500, lat = 40.41), 10_500, 10_000, "observer")
        assertNotEquals(DatasetContract.changedFingerprint(a), DatasetContract.changedFingerprint(c))
        assertEquals(DatasetContract.coreFingerprint(a), DatasetContract.coreFingerprint(c))
        assertNotEquals(DatasetContract.coreFingerprint(a), DatasetContract.coreFingerprint(DatasetContract.measure(offer(fee = 5.1), 10_500, 10_000, "observer")))
    }

    @Test
    fun onlyLastSeenOrCourierChangesAreWrittenAtMostEveryFiveSecondsPerOffer() {
        val gate = OfferRowGate()
        assertEquals(1, gate.select(day, listOf(read(2_000)), 0).size)               // a new offer: written
        assertEquals(0, gate.select(day, listOf(read(2_000)), 300).size)             // unchanged
        assertEquals(0, gate.select(day, listOf(read(2_300)), 600).size)             // only lastSeen, inside 5 s: held
        assertEquals(0, gate.select(day, listOf(read(2_600, lat = 40.41)), 900).size) // only the courier: held
        assertEquals(1, gate.select(day, listOf(read(2_900, fee = 5.1)), 1_200).size) // a real change: at once
        assertEquals(0, gate.select(day, listOf(read(3_200, fee = 5.1)), 1_500).size)
        val late = gate.select(day, listOf(read(7_000, fee = 5.1)), 6_300)           // 5 s since the last row
        assertEquals(7_000L, late.single().getLong("lastSeenAtMs"))
    }

    @Test
    fun theHeldLastSightingIsWrittenWhenTheOfferLeaves() {
        val gate = OfferRowGate()
        gate.select(day, listOf(read(2_000)), 0)
        assertEquals(0, gate.select(day, listOf(read(4_100)), 2_100).size)
        val gone = gate.select(day, emptyList(), 2_400)   // accepted or declined: the held row is its last sighting
        assertEquals(4_100L, gone.single().getLong("lastSeenAtMs"))
        assertEquals(0, gate.select(day, emptyList(), 2_700).size)
    }
}
