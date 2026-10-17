package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * E1 (Faz 4): each "Sipariş başına ortalama" value (batarya, restoranda, müşteride, harcanan süre, sipariş öncesi) opens that
 * day's list: time · platform · restaurant · the order's value, "—" and why when the order has none. The list holds exactly the
 * orders the average is taken over, so the average of its values is the number on the card. Synthetic records.
 */
class AverageListE1Test {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.courier"
    private val min = 60_000L
    private val tr = { a: String, _: String -> a }
    private val az = { _: String, b: String -> b }
    private val platformOf = { pkg: String -> if (pkg.contains("wolt")) "Wolt" else "Bolt Food" }

    private fun trip(id: String, pkg: String, at: Long, done: Long?, restaurant: String?) = TripRecord(id, pkg, null, at).also {
        it.completedAt = done; it.phase = if (done != null) TripPhase.FINISHED else TripPhase.TO_RESTAURANT; it.sessionId = "s-0"
        if (restaurant != null) it.pickups.add(PickupRecord(restaurant, null))
    }

    /** a: everything measured; b: no energy, GPS arrival only; c: unverified delivery (closed by the next order); open: not delivered. */
    private fun day(): List<TripRecord> {
        val a = trip("a", wolt, 10 * min, 30 * min, "Test Döner").also {
            it.usedWh = 40.0; it.energyCoveredMs = 20 * min
            it.pickups[0].apply { arrivedAt = 12 * min; pickedAt = 16 * min; arrivalSource = "gps_dwell" }
            it.customers["c"] = CustomerRecord("c", arrivedAt = 27 * min, deliveredAt = 30 * min)
        }
        val b = trip("b", bolt, 40 * min, 60 * min, "Test Kafe").also { it.pickups[0].apply { arrivedAt = 45 * min; pickedAt = 47 * min; arrivalSource = "platform_status" } }
        val c = trip("c", wolt, 62 * min, null, "Test Pizza").also {
            it.phase = TripPhase.END_UNCONFIRMED; it.deliveredInferredAt = 80 * min
            it.usedWh = 30.0; it.energyCoveredMs = 10 * min; it.energyPartialMs = 5 * min
        }
        val open = trip("open", wolt, 85 * min, null, "Test Burger")
        return listOf(open, c, b, a) // OrderTracker.today is newest first
    }

    @Test fun everyMetricListsTheDeliveredOrdersInTimeOrder() {
        for (m in OrderHistory.Metric.values()) {
            val rows = OrderHistory.metricRows(day(), m, platformOf, tr)
            assertEquals(m.name, listOf("a", "b", "c"), rows.map { it.tripId })
            assertEquals(listOf("Wolt", "Bolt Food", "Wolt"), rows.map { it.platform })
            assertEquals(listOf("Test Döner", "Test Kafe", "Test Pizza"), rows.map { it.restaurant })
        }
    }

    @Test fun listAverageIsTheCardAverage() {
        val trips = day()
        val avg = OrderHistory.averages(trips, platformOf)
        fun mean(m: OrderHistory.Metric) = OrderHistory.metricRows(trips, m, platformOf, tr).mapNotNull { it.value }.let { if (it.isEmpty()) null else it.average() }
        assertEquals(avg.energyWh, mean(OrderHistory.Metric.ENERGY))
        assertEquals(avg.restaurantMin, mean(OrderHistory.Metric.RESTAURANT))
        assertEquals(avg.customerMin, mean(OrderHistory.Metric.CUSTOMER))
        assertEquals(avg.orderMin, mean(OrderHistory.Metric.ORDER))
        assertEquals(avg.beforeOfferMin, mean(OrderHistory.Metric.BEFORE_OFFER))
    }

    @Test fun missingValuesSayWhy() {
        val trips = day()
        fun why(m: OrderHistory.Metric) = OrderHistory.metricRows(trips, m, platformOf, tr).associate { it.tripId to it.why }
        assertEquals(mapOf("a" to null, "b" to "scooter ölçümü yok", "c" to "harici batarya okunmayan süre %50 (sınır %25)"), why(OrderHistory.Metric.ENERGY))
        assertEquals(mapOf("a" to null, "b" to null, "c" to "restorana varış görülmedi"), why(OrderHistory.Metric.RESTAURANT))
        assertEquals(mapOf("a" to null, "b" to "müşteriye varış görülmedi", "c" to "teslim anı görülmedi (doğrulanmamış teslim)"), why(OrderHistory.Metric.CUSTOMER))
        assertEquals(mapOf("a" to null, "b" to null, "c" to "teslim anı görülmedi (doğrulanmamış teslim)"), why(OrderHistory.Metric.ORDER))
        // Measured within each platform: c's offer came 32 min after a (the previous Wolt order) ended, b (Bolt) does not count.
        assertEquals(32.0, OrderHistory.metricRows(trips, OrderHistory.Metric.BEFORE_OFFER, platformOf, tr).first { it.tripId == "c" }.value!!, 1e-9)
        val noStart = listOf(trip("x", wolt, 10 * min, 30 * min, "Test Döner").also { it.sessionId = null })
        assertEquals("çevrimiçi başlangıcı ve önceki sipariş bilinmiyor",
            OrderHistory.metricRows(noStart, OrderHistory.Metric.BEFORE_OFFER, platformOf, tr).single().why)
        // After an unverified delivery the previous end is not known.
        val afterInferred = listOf(day().first { it.id == "c" }, trip("y", wolt, 90 * min, 100 * min, "Test Burger"))
        assertEquals("önceki sipariş doğrulanmamış teslimle kapandı, bitişi bilinmiyor",
            OrderHistory.metricRows(afterInferred, OrderHistory.Metric.BEFORE_OFFER, platformOf, tr).first { it.tripId == "y" }.why)
    }

    @Test fun rowTextIsTimePlatformRestaurantValue() {
        val rows = OrderHistory.metricRows(day(), OrderHistory.Metric.RESTAURANT, platformOf, tr)
        assertEquals("Wolt · Test Döner · 4.0 dk", OrderHistory.metricRowText(rows[0], OrderHistory.Metric.RESTAURANT, tr))
        assertEquals("Wolt · Test Pizza · — (restorana varış görülmedi)", OrderHistory.metricRowText(rows[2], OrderHistory.Metric.RESTAURANT, tr))
        val energy = OrderHistory.metricRows(day(), OrderHistory.Metric.ENERGY, platformOf, az)
        assertEquals("Wolt · Test Döner · 40.0 Wh", OrderHistory.metricRowText(energy[0], OrderHistory.Metric.ENERGY, az))
        assertEquals("Bolt Food · Test Kafe · — (skuter ölçməsi yoxdur)", OrderHistory.metricRowText(energy[1], OrderHistory.Metric.ENERGY, az))
    }

    @Test fun emptyDayHasNoRows() {
        assertTrue(OrderHistory.metricRows(emptyList(), OrderHistory.Metric.ORDER, platformOf, tr).isEmpty())
    }

    // ------------------------------------------------------------------ wiring (source checks; the screens are telefonda denenmedi)
    private fun main(): File = listOf("src/main", "app/src/main", "kuryelog/app/src/main").map(::File).firstOrNull { it.isDirectory }
        ?: error("KuryeLog kaynak klasörü bulunamadı: ${File(".").absolutePath}")

}
