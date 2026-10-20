package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.CourierPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WEEM `OrderEvents.day` (LINK.md 4.2): platform başına teslim sayısı, brüt, net; ölçülmeyen null. */
class OrderDaySummaryTest {
    private val wolt = CourierPlatform.WOLT.pkg
    private val bolt = CourierPlatform.BOLT.pkg

    private fun trip(id: String, pkg: String, gross: Double?, done: Boolean = true) =
        TripRecord(id, pkg, OfferState("o-$id", pkg, 0, CourierObservation(pkg, 0, ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(), gross = gross)), 1_000L)
            .also { if (done) it.completedAt = 9_000L }

    @Test
    fun countsDeliveredOrdersPerPlatform() {
        val d = OrderDaySummary.of("2026-10-20", listOf(trip("a", wolt, 4.0), trip("b", wolt, 3.5), trip("c", wolt, 9.0, done = false), trip("d", bolt, null)))
        assertEquals("2026-10-20", d.day)
        val w = d.platforms.getValue(CourierPlatform.WOLT)
        assertEquals(2, w.orders)
        assertEquals(7.5, w.grossAzn!!, 1e-9)
        assertEquals(7.5, w.netAzn!!, 1e-9)
        val b = d.platforms.getValue(CourierPlatform.BOLT)
        assertEquals(1, b.orders)
        assertNull(b.grossAzn)
        assertNull(b.netAzn)
        assertNull(w.onlineMin)
        assertNull(d.cashAzn)
        assertEquals(emptyMap<CourierPlatform, Any>(), OrderDaySummary.of("2026-10-20", listOf(trip("x", wolt, 2.0, done = false))).platforms)
    }
}
