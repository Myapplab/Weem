package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 18 (16 Ekim, dördüncü tur): restorana ≤5 m ve hız <10 km/s → restorana varış; bekleme buradan sayılır. GPS
 * doğruluğu payı: `uzaklık − min(doğruluk, 20) ≤ 5`. Mesafe YolGo/Google Maps'ten gelirse o (madde 20). İki kural da tutarsa yeni
 * kuralın anı geçer; yeni kural hiç tutmazsa bugünkü 60 m / 20 sn kuralı (D9) yedek.
 */
class VenueNearM18Test {
    private val wolt = "com.wolt.courierapp"
    private val baku = TimeZone.getTimeZone("Asia/Baku")
    private fun ms(clock: String) = PlatformHistory.toMs("2026-10-11", clock, baku)
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun offer(at: Long) = obs(at, "Decline", "Pickup 5 min", "AZN 2.61", "Expected earnings for the full delivery",
        "Delivery from", "Test Restoran", "Route distance", "2 km", "Estimated", "9 - 17 min", "Accept", "Test küç. 1, AZ1000 Baku")
    private fun pickup(at: Long) = obs(at, "Pickup 6 min", "Pickup from", "Test Restoran", "Test küç. 1, AZ1000 Baku")
    private val lat0 = 40.3777; private val lon0 = 49.8920
    private fun fix(at: Long, north: Double, kmh: Double? = 0.0, acc: Double? = 8.0) = VenueArrival.Fix(at, lat0 + north / 111_195.0, lon0, acc, kmh)
    private val geocoded = VenueArrival.Venue(lat0, lon0, VenueArrival.SOURCE_GEOCODE)
    private fun toRestaurant(c: DeliveryCore): TripRecord {
        c.observe(offer(ms("18:01"))); c.observe(offer(ms("18:01") + 1_500)); c.observe(pickup(ms("18:01") + 4_000))
        return c.active(wolt)!!
    }
    private fun ride(v: VenueArrival, c: DeliveryCore, fixes: List<VenueArrival.Fix>, nav: ArrivalDistance.Source? = null): List<CoreEvent> =
        fixes.flatMap { f -> v.add(f); v.live(c, { _, _ -> geocoded }, f.at) { _, _ -> nav } }

    @Test fun withinFiveMetresAndSlowIsTheArrivalAtOnce() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val s = ms("18:05")
        // 18 km/h yaklaşma, sonra 12 m'de (doğruluk 8 m) 6 km/s ile park ediyor.
        val ev = ride(v, c, listOf(fix(s, 60.0, 18.0), fix(s + 2_000, 40.0, 18.0), fix(s + 4_000, 12.0, 6.0)))
        assertEquals("restaurant_arrived", ev.single().type)
        assertEquals(s + 4_000, t.pickups.single().arrivedAt); assertEquals(VenueArrival.SOURCE_NEAR_GEOCODE, t.pickups.single().arrivalSource)
        assertEquals(TripPhase.WAITING, t.phase)
    }

    @Test fun ridingPastTheDoorOrABadFixIsNoArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val s = ms("18:05")
        assertTrue(ride(v, c, listOf(fix(s, 2.0, 12.0), fix(s + 2_000, -20.0, 14.0), fix(s + 4_000, -90.0, 20.0))).isEmpty())
        assertTrue(ride(v, c, listOf(fix(s + 6_000, 2.0, 12.0, acc = 40.0))).isEmpty())
        assertNull(t.pickups.single().arrivedAt)
    }

    @Test fun theNewRuleReplacesAnEarlierDwellArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val s = ms("18:05")
        // 40 m'de 24 sn durdu (bugünkü kural varış der), sonra kapıya yürüdü: varış kapıya geldiği an.
        val dwell = (0 until 24 step 2).map { fix(s + it * 1_000L, 40.0) }
        ride(v, c, dwell)
        assertEquals(s, t.pickups.single().arrivedAt); assertEquals(VenueArrival.SOURCE_GEOCODE, t.pickups.single().arrivalSource)
        val ev = ride(v, c, listOf(fix(s + 30_000, 20.0, 4.0), fix(s + 36_000, 3.0, 2.0)))
        assertEquals("restaurant_arrival_corrected", ev.single().type)
        assertEquals(s + 36_000, t.pickups.single().arrivedAt); assertEquals(VenueArrival.SOURCE_NEAR_GEOCODE, t.pickups.single().arrivalSource)
    }

    @Test fun aFreshNavigationDistanceDecides() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val s = ms("18:05")
        // GPS 30 m gösteriyor (doğruluk 5), YolGo kalan 4 m diyor ve kurye yavaş: YolGo'nun anı.
        val nav = ArrivalDistance.Source(4.0, s + 1_000, ArrivalDistance.YOLGO)
        val ev = ride(v, c, listOf(fix(s + 2_000, 30.0, 3.0, acc = 5.0)), nav)
        assertEquals(1, ev.size)
        assertEquals(s + 1_000, t.pickups.single().arrivedAt); assertEquals(ArrivalDistance.YOLGO, t.pickups.single().arrivalSource)
    }

    @Test fun aNavigationDistanceWhileRidingIsNoArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toRestaurant(c)
        val s = ms("18:05")
        val nav = ArrivalDistance.Source(3.0, s + 1_000, ArrivalDistance.MAPS)
        assertTrue(ride(v, c, listOf(fix(s + 2_000, 30.0, 15.0, acc = 5.0)), nav).isEmpty())
        assertNull(t.pickups.single().arrivedAt)
    }
}
