package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/** 0.18.0 Bugün card: per-order averages count only the orders where each value was measured. Synthetic records. */
class TodayAveragesTest {
    private val wolt = "com.wolt.courierapp"
    private val min = 60_000L

    private fun trip(id: String, at: Long, done: Long?, wh: Double = 0.0, covered: Long = 0L) =
        TripRecord(id, wolt, null, at).also { it.completedAt = done; it.usedWh = wh; it.energyCoveredMs = covered; it.sessionId = "s-0" }

    @Test fun averagesUseOnlyMeasuredOrders() {
        val a = trip("a", 10 * min, 30 * min, wh = 40.0, covered = 20 * min).also {
            it.pickups.add(PickupRecord("R", null, arrivedAt = 12 * min, pickedAt = 16 * min))
            it.customers["c"] = CustomerRecord("c", arrivedAt = 27 * min, deliveredAt = 30 * min)
        }
        val b = trip("b", 40 * min, 60 * min).also { it.pickups.add(PickupRecord("R", null, arrivedAt = 45 * min, pickedAt = 47 * min)) }
        val open = trip("c", 65 * min, null, wh = 99.0, covered = min)
        val x = OrderHistory.averages(listOf(a, b, open)) { "Wolt" }
        assertEquals(40.0, x.energyWh!!, 1e-9)          // b has no energy samples, the open order is not delivered
        assertEquals(3.0, x.restaurantMin!!, 1e-9)       // (4 + 2) / 2
        assertEquals(3.0, x.customerMin!!, 1e-9)         // only a has a customer arrival
        assertEquals(20.0, x.orderMin!!, 1e-9)           // 20 and 20 minutes
        assertEquals(10.0, x.beforeOfferMin!!, 1e-9)     // a: 10 since session start, b: 10 since a ended
    }

    @Test fun nothingMeasuredGivesNulls() {
        val x = OrderHistory.averages(emptyList()) { "Wolt" }
        assertNull(x.energyWh); assertNull(x.restaurantMin); assertNull(x.customerMin); assertNull(x.orderMin); assertNull(x.beforeOfferMin)
    }
}
