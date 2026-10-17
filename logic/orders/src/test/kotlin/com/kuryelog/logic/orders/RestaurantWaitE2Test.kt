package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * E2 (Faz 4): every order shows its restaurant wait (D9: arrival → Collect) on its card and in the order list; an order
 * without one shows "—" and why. Synthetic records (uydurma restoran adları).
 */
class RestaurantWaitE2Test {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.courier"
    private val min = 60_000L
    private val tr = { a: String, _: String -> a }
    private val az = { _: String, b: String -> b }

    private fun trip(pkg: String = wolt, done: Long? = 40 * min) = TripRecord("t", pkg, null, 10 * min).also { t ->
        t.completedAt = done
        if (done == null) t.phase = TripPhase.TO_RESTAURANT else t.phase = TripPhase.FINISHED
    }

    @Test fun gpsArrivalToCollectIsTheWait() {
        val t = trip().also { it.pickups.add(PickupRecord("Test Döner", null, arrivedAt = 20 * min, pickedAt = 24 * min + 20_000, arrivalSource = "gps_dwell")) }
        assertEquals(4.33, OrderHistory.restaurantWaitMinutes(t)!!, 0.01)
        assertNull(OrderHistory.restaurantWaitMissing(t, tr))
        assertEquals("Restoranda 4 dk", OrderHistory.restaurantWaitShort(t, tr))
        assertEquals("Restoranda bekleme: 4 dk (GPS)", OrderHistory.restaurantWaitLine(t, tr))
        assertEquals("Restoranda 4 dəq", OrderHistory.restaurantWaitShort(t, az))
        assertEquals("Restoranda gözləmə: 4 dəq (GPS)", OrderHistory.restaurantWaitLine(t, az))
    }

    @Test fun platformArrivalSaysSoAndLongWaitsHaveHours() {
        val t = trip(bolt).also { it.pickups.add(PickupRecord("Test Kafe", null, arrivedAt = 20 * min, pickedAt = 85 * min, arrivalSource = "platform_status")) }
        assertEquals("Restoranda bekleme: 1 sa 5 dk (platform)", OrderHistory.restaurantWaitLine(t, tr))
    }

    @Test fun twoPickupsAddUp() {
        val t = trip().also {
            it.pickups.add(PickupRecord("A", null, arrivedAt = 20 * min, pickedAt = 23 * min, arrivalSource = "gps_dwell"))
            it.pickups.add(PickupRecord("B", null, arrivedAt = 30 * min, pickedAt = 32 * min, arrivalSource = "gps_dwell"))
        }
        assertEquals("Restoranda 5 dk", OrderHistory.restaurantWaitShort(t, tr))
    }

    @Test fun missingWaitShowsDashAndWhy() {
        val noArrival = trip().also { it.pickups.add(PickupRecord("Test Döner", null, pickedAt = 24 * min)) }
        assertEquals("restorana varış görülmedi", OrderHistory.restaurantWaitMissing(noArrival, tr))
        assertEquals("Restoranda —", OrderHistory.restaurantWaitShort(noArrival, tr))
        assertEquals("Restoranda bekleme: — (restorana varış görülmedi)", OrderHistory.restaurantWaitLine(noArrival, tr))
        assertEquals("Restoranda gözləmə: — (restorana çatma görünmədi)", OrderHistory.restaurantWaitLine(noArrival, az))

        val onTheWay = trip(done = null).also { it.pickups.add(PickupRecord("Test Döner", null)) }
        assertEquals("restorana gidiliyor", OrderHistory.restaurantWaitMissing(onTheWay, tr))

        val waiting = trip(done = null).also { it.phase = TripPhase.WAITING; it.pickups.add(PickupRecord("Test Döner", null, arrivedAt = 20 * min, arrivalSource = "gps_dwell")) }
        assertEquals("restoranda, henüz alınmadı", OrderHistory.restaurantWaitMissing(waiting, tr))

        val ended = trip(done = null).also { it.phase = TripPhase.END_UNCONFIRMED; it.pickups.add(PickupRecord("Test Döner", null, arrivedAt = 20 * min)) }
        assertEquals("alış görülmedi", OrderHistory.restaurantWaitMissing(ended, tr))

        val noScreen = trip()
        assertEquals("restoran ekranı görülmedi", OrderHistory.restaurantWaitMissing(noScreen, tr))
    }

    // ------------------------------------------------------------------ wiring (source checks; the screens are telefonda denenmedi)
    private fun main(): File = listOf("src/main", "app/src/main", "kuryelog/app/src/main").map(::File).firstOrNull { it.isDirectory }
        ?: error("KuryeLog kaynak klasörü bulunamadı: ${File(".").absolutePath}")

    @Test fun listLineCarriesTheWait() {
        val t = trip().also { it.pickups.add(PickupRecord("Test Döner", null, arrivedAt = 20 * min, pickedAt = 23 * min, arrivalSource = "gps_dwell")) }
        assertTrue(OrderHistory.listLine(t, tr).startsWith("Restoranda 3 dk"))
    }
}
