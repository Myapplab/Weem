package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

/**
 * D9 (13 Ekim planı): Wolt'ta restorana varış GPS ile — restorana ≤60 m, hız <3 km/s, ≥20 sn; ayrılış Collect (ilk müşteri
 * karesi). Restoran konumu adres geocode'u, yoksa ya da yanlışsa kuryenin toplama anındaki konumu. Saha karelerinde konum yok:
 * GPS izleri uydurma (restoranın kuzeyinde metre cinsinden), ekran metinleri Wolt 2.202.6 saha karelerinden.
 */
class VenueArrivalD9Test {
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

    @Test fun stayAtTheGeocodedRestaurantIsTheArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val start = ms("18:05")
        assertTrue(ride(v, c, approach(start, 400, 40)).isEmpty())
        val parked = start + 76_000
        // Parked 40 m from the door, then walked in at 5 km/h: the walk does not end the stay.
        val ev = ride(v, c, stay(parked, 10, 40.0) + stay(parked + 10_000, 8, 20.0, kmh = 5.0) + stay(parked + 18_000, 10, 5.0))
        val e = ev.single()
        // Ekim planı madde 18: at the door (5 m, slow) the arrival is that moment, before the 20 s stay completes.
        assertEquals("restaurant_arrived", e.type); assertEquals(VenueArrival.SOURCE_NEAR_GEOCODE, e.reason); assertEquals(parked + 18_000, e.at)
        val p = t.pickups.single()
        assertEquals(parked + 18_000, p.arrivedAt); assertEquals(VenueArrival.SOURCE_NEAR_GEOCODE, p.arrivalSource); assertEquals(TripPhase.WAITING, t.phase)
        // Collect 6 min after parking: the departure; the stay is not found again.
        assertTrue(collect(c, v, parked + 360_000).none { it.type.startsWith("restaurant_arrival") })
        assertEquals(parked + 360_000, p.pickedAt); assertEquals(5.7, t.waitMinutes, 1e-9); assertEquals(5.7, OrderHistory.restaurantWaitMinutes(t)!!, 1e-9)
    }

    @Test fun redLightOrRidingPastIsNoArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val start = ms("18:05")
        // A red light 50 m before the door for 16 s, then past the door at 18 km/h and out of the circle.
        val light = stay(start, 16, 50.0) + approach(start + 16_000, 40, -80)
        assertTrue(ride(v, c, light).isEmpty())
        // Too few seconds parked (12 s), a fix 90 m away (walking around the block), then parked for good.
        assertTrue(ride(v, c, stay(start + 60_000, 12, 30.0) + listOf(fix(start + 72_000, 90.0, kmh = 4.0))).isEmpty())
        assertNull(t.pickups.single().arrivedAt)
        val ev = ride(v, c, stay(start + 80_000, 24, 30.0))
        assertEquals(start + 80_000, ev.single().let { t.pickups.single().arrivedAt })
    }

    @Test fun gpsLostInsideCountsUntilTheCollect() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val parked = ms("18:06")
        // 8 s of fixes at the door, then nothing (inside) and an inaccurate fix that says nothing.
        assertTrue(ride(v, c, stay(parked, 8, 25.0) + listOf(fix(parked + 60_000, 150.0, acc = 90.0))).isEmpty())
        assertNull(t.pickups.single().arrivedAt); assertEquals(TripPhase.TO_RESTAURANT, t.phase)
        val ev = collect(c, v, parked + 180_000)
        assertEquals(VenueArrival.SOURCE_GEOCODE, ev.single { it.type == "restaurant_arrived" }.reason)
        assertEquals(parked, t.pickups.single().arrivedAt); assertEquals(3.0, t.waitMinutes, 1e-9)
        assertEquals(TripPhase.TO_CUSTOMER, t.phase)
    }

    @Test fun withoutAGeocodeThePickupPointIsTheRestaurant() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val start = ms("18:05"); val parked = start + 76_000
        assertTrue(ride(v, c, approach(start, 400, 40) + stay(parked, 120, 40.0), venue = null).isEmpty())
        val ev = collect(c, v, parked + 125_000, venue = null)
        val e = ev.single { it.type == "restaurant_arrived" }
        assertEquals(VenueArrival.SOURCE_PICKUP_POINT, e.reason); assertEquals(parked + 125_000, e.at)
        assertEquals(parked, t.pickups.single().arrivedAt); assertEquals(VenueArrival.SOURCE_PICKUP_POINT, t.pickups.single().arrivalSource)
    }

    @Test fun aGeocodedStreetFarFromThePickupIsCorrected() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        // The street-only address was geocoded 400 m north of the door: a 30 s red light next to it looked like the arrival.
        val street = VenueArrival.Venue(lat0 + 400 / 111_195.0, lon0, VenueArrival.SOURCE_GEOCODE)
        val start = ms("18:05")
        val wrong = ride(v, c, stay(start, 30, 420.0), venue = street)
        assertEquals(start, t.pickups.single().arrivedAt); assertEquals("restaurant_arrived", wrong.single().type)
        val parked = start + 130_000
        assertTrue(ride(v, c, approach(start + 30_000, 400, 40) + stay(parked, 240, 35.0), venue = street).isEmpty())
        val e = collect(c, v, parked + 245_000, venue = street).single { it.type == "restaurant_arrival_corrected" }
        assertEquals(VenueArrival.SOURCE_PICKUP_POINT, e.reason)
        assertEquals(parked, t.pickups.single().arrivedAt); assertEquals(VenueArrival.SOURCE_PICKUP_POINT, t.pickups.single().arrivalSource)
    }

    @Test fun aWrongGeocodeWithoutAStayAtThePickupDropsTheArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val street = VenueArrival.Venue(lat0 + 400 / 111_195.0, lon0, VenueArrival.SOURCE_GEOCODE)
        val start = ms("18:05")
        ride(v, c, stay(start, 30, 420.0), venue = street)
        // Collected straight from the scooter seat (no 20 s stay at the door): no arrival is known, the wrong one is dropped.
        ride(v, c, approach(start + 30_000, 400, 0), venue = street)
        val e = collect(c, v, start + 112_000, venue = street).single { it.type == "restaurant_arrival_corrected" }
        assertEquals("gps_venue_mismatch", e.reason)
        assertNull(t.pickups.single().arrivedAt); assertNull(t.pickups.single().arrivalSource); assertEquals(0.0, t.waitMinutes, 1e-9)
        assertNull(OrderHistory.restaurantWaitMinutes(t))
    }

    @Test fun boltOrdersAndScreenArrivalsAreLeftAlone() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val at = ms("18:06")
        // The courier's own "Arrived" tap stays the arrival.
        c.click(wolt, "Arrived", at)
        assertEquals("user_arrived_action", t.pickups.single().arrivalSource)
        assertTrue(ride(v, c, stay(at + 30_000, 30, 10.0)).isEmpty())
        assertTrue(collect(c, v, at + 120_000).none { it.type.startsWith("restaurant_arrival") })
        assertEquals(at, t.pickups.single().arrivedAt)
        // Bolt: its own arrival status only (D9 is Wolt's).
        val bolt = "com.bolt.food.courier"
        val b = DeliveryCore().also { it.start() }
        b.observe(CourierParser.parse(bolt, listOf("Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99"), emptyList(), at, "accessibility"))
        val bt = b.active(bolt)!!
        val bv = VenueArrival()
        assertTrue(stay(at + 2_000, 40, 10.0).flatMap { f -> bv.add(f); bv.live(b, { _, _ -> geocoded }, f.at) }.isEmpty())
        assertNull(bt.pickups.single().arrivedAt)
    }

    @Test fun badOrStaleFixesGiveNoArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val at = ms("18:06")
        // Fixes worse than 60 m at the door say nothing.
        assertTrue(ride(v, c, stay(at, 40, 10.0).map { it.copy(accuracyM = 80.0) }).isEmpty())
        // Without a geocode the last fix before the pickup is 6 min old: maybe another place, no arrival.
        assertTrue(ride(v, c, stay(at + 60_000, 30, 10.0), venue = null).isEmpty())
        assertTrue(collect(c, v, at + 60_000 + 30_000 + 360_000, venue = null).none { it.type.startsWith("restaurant_arrival") })
        assertNull(t.pickups.single().arrivedAt)
    }

    @Test fun aStackedOrderStartsAtItsOwnFirstFrame() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival()
        // The courier stood 30 s next to the next restaurant before that order's first task frame (another order's customer there).
        val early = ms("18:00")
        val before = stay(early, 30, 20.0)
        before.forEach { v.add(it) }
        val t = toRestaurant(c)
        assertTrue(v.live(c, { _, _ -> geocoded }, ms("18:01") + 5_000).isEmpty())
        assertNull(t.pickups.single().arrivedAt)
        assertEquals(t.acceptedAt, VenueArrival.from(t, 0))
    }

    @Test fun distanceAndWindow() {
        assertEquals(60.0, VenueArrival.metres(lat0, lon0, lat0 + 60 / 111_195.0, lon0), 0.05)
        val v = VenueArrival()
        v.add(fix(1_000, 0.0)); v.add(fix(1_000, 5.0)); v.add(fix(500, 5.0))
        assertNull(v.lastFix(0, 2_000)?.takeIf { it.lat != lat0 })
        v.add(fix(1_000 + VenueArrival.KEEP_MS + 1, 0.0))
        assertNull(v.lastFix(0, 1_000))
    }
}
