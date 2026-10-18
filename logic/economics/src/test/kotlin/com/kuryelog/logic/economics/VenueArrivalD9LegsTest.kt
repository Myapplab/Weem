package com.kuryelog.logic.economics

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats VenueArrivalD9Test.kt @ 6388c04 — ekonomi payı (1 test); öbürleri logic/orders'ta (ORDERS.md 15.1).

/**
 * D9 (13 Ekim planı): Wolt'ta restorana varış GPS ile — restorana ≤60 m, hız <3 km/s, ≥20 sn; ayrılış Collect (ilk müşteri
 * karesi). Restoran konumu adres geocode'u, yoksa ya da yanlışsa kuryenin toplama anındaki konumu. Saha karelerinde konum yok:
 * GPS izleri uydurma (restoranın kuzeyinde metre cinsinden), ekran metinleri Wolt 2.202.6 saha karelerinden.
 */
class VenueArrivalD9LegsTest {
    private val wolt = "com.wolt.courierapp"
    private val baku = TimeZone.getTimeZone("Asia/Baku")
    private fun ms(clock: String) = PlatformHistory.toMs("2026-10-11", clock, baku)
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun offer(at: Long) = obs(at, "Decline", "Pickup 5 min", "AZN 2.61", "Expected earnings for the full delivery",
        "Delivery from", "Borani Sahil", "Route distance", "2 km", "Estimated", "9 - 17 min", "Accept", "Uzeyir Hajibayli str. 17, AZ1000 Baku")
    private fun pickup(at: Long, restaurant: String = "Borani Sahil") = obs(at, "Pickup 6 min", "Pickup from", restaurant, "Uzeyir Hajibayli str. 17, AZ1000 Baku")
    private fun card(at: Long) = obs(at, "Dropoff 3 min", "Dropoff to", "Test A.", "Test küçəsi 1, AZ1000 Baku", "Entrance", "Floor",
        "Order details", "From", "#330", "Order delivered!")

    private val lat0 = 40.3777
    private val lon0 = 49.8920
    /** A fix [north] metres north of the restaurant door. */
    private fun fix(at: Long, north: Double, kmh: Double? = 0.0, acc: Double? = 8.0) =
        VenueArrival.Fix(at, lat0 + north / 111_195.0, lon0, acc, kmh)
    private val geocoded = VenueArrival.Venue(lat0, lon0, VenueArrival.SOURCE_GEOCODE)

    /** Borani Sahil accepted at 18:01 (first pickup frame 18:01:04): the order on its way to the restaurant. */
    private fun toRestaurant(c: DeliveryCore): TripRecord {
        c.observe(offer(ms("18:01"))); c.observe(offer(ms("18:01") + 1_500)); c.observe(pickup(ms("18:01") + 4_000))
        return c.active(wolt)!!.also { assertEquals(TripPhase.TO_RESTAURANT, it.phase); assertEquals(ms("18:01") + 4_000, it.acceptedAt) }
    }
    /** Each fix in turn, the live check after it (as GpsService does). */
    private fun ride(v: VenueArrival, c: DeliveryCore, fixes: List<VenueArrival.Fix>, venue: VenueArrival.Venue? = geocoded): List<CoreEvent> =
        fixes.flatMap { f -> v.add(f); v.live(c, { _, _ -> venue }, f.at) }
    /** Every 2 s from [at] for [seconds] at [north] metres. */
    private fun stay(at: Long, seconds: Int, north: Double, kmh: Double? = 0.0) = (0 until seconds step 2).map { fix(at + it * 1_000L, north, kmh) }
    /** Riding at 18 km/h (10 m per 2 s fix) from [fromM] to [toM] metres north of the door. */
    private fun approach(at: Long, fromM: Int, toM: Int) = (fromM downTo toM step 10).mapIndexed { i, m -> fix(at + i * 2_000L, m.toDouble(), 18.0) }
    /** Wolt's "Collect" tap: the customer card follows (the pickup), then the order is at its customer. */
    private fun collect(c: DeliveryCore, v: VenueArrival, at: Long, venue: VenueArrival.Venue? = geocoded): List<CoreEvent> {
        val ev = c.observe(card(at))
        val t = c.trips.values.single { it.pkg == wolt }
        return ev + v.collected(c, t.id, t.pickups.indexOfLast { it.pickedAt == at }, { _, _ -> venue }, at)
    }

    @Test fun legsAveragesAndRecordsKeepTheGpsArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val parked = ms("18:06")
        ride(v, c, stay(parked, 30, 15.0))
        collect(c, v, parked + 240_000)
        c.observe(obs(parked + 600_000, "Quieter than usual", "Delivery demand", "l Low", "EXPECTED DEMAND", "Go offline"))
        c.observe(obs(parked + 604_000, "Quieter than usual", "Delivery demand", "l Low", "EXPECTED DEMAND", "Go offline"))
        assertTrue(t.countsAsDelivered)
        val legs = EconomicsLogs.tripLegs(t, emptyMap(), c.trips.values).getJSONArray("legs")
        val wait = (0 until legs.length()).map { legs.getJSONObject(it) }.single { it.getString("kind") == "wait_at_venue" }
        assertEquals("gps", wait.getString("source")); assertEquals(parked, wait.getLong("startAtMs")); assertEquals(parked + 240_000, wait.getLong("endAtMs"))
        assertEquals(4.0, OrderHistory.averages(listOf(t)) { "Wolt" }.restaurantMin!!, 1e-9)
        val back = RecordJson.trip(RecordJson.trip(t)).pickups.single()
        assertEquals(parked, back.arrivedAt); assertEquals(VenueArrival.SOURCE_GEOCODE, back.arrivalSource)
    }
}
