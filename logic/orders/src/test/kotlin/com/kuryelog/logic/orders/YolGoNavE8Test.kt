package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * E8 (Faz 3) ters yön: YolGo aktif hedefin koordinatını (bir kurye uygulamasının "navigasyon" isteğiyle açılan hedef), kalan
 * yol mesafesini ve kalan dönüş sayısını `content://az.duzyol.app.nav/nav/v1/current` ile verir (iki yönde FeedSigner kapısı).
 * Rawstats yükü doğrular, saklar ve D9'un restoran konumunu (OrderTracker.venueOf) önce bu kaynaktan alır: Wolt'un alış
 * ekranındayken yapılan navigasyon isteği o siparişin restoranıdır; müşteri ekranındaki istek restoran değildir.
 */
class YolGoNavE8Test {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.deliverycourier"
    private val baku = TimeZone.getTimeZone("Asia/Baku")
    private fun ms(clock: String) = PlatformHistory.toMs("2026-10-11", clock, baku)
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun offer(at: Long, restaurant: String) = obs(at, "Decline", "Pickup 5 min", "AZN 2.61", "Expected earnings for the full delivery",
        "Delivery from", restaurant, "Route distance", "2 km", "Estimated", "9 - 17 min", "Accept", "Uzeyir Hajibayli str. 17, AZ1000 Baku")
    private fun pickup(at: Long, restaurant: String) = obs(at, "Pickup 6 min", "Pickup from", restaurant, "Uzeyir Hajibayli str. 17, AZ1000 Baku")
    private fun card(at: Long) = obs(at, "Dropoff 3 min", "Dropoff to", "Test A.", "Test küçəsi 1, AZ1000 Baku", "Entrance", "Floor",
        "Order details", "From", "#330")

    private val lat0 = 40.3777
    private val lon0 = 49.8920
    private fun target(at: Long, kind: String = "unknown", origin: String = "intent", pkg: String? = wolt, north: Double = 0.0) =
        RouteStatus.Target(lat0 + north / 111_195.0, lon0, NavKind.of(kind)!!, NavOrigin.of(origin)!!, at, pkg)

    /** Borani Sahil accepted at 18:01:04; the trail as OrderTracker records it from each frame. */
    private fun accepted(c: DeliveryCore, trail: ScreenTrail, restaurant: String = "Borani Sahil", at: Long = ms("18:01")): TripRecord {
        for (o in listOf(offer(at, restaurant), offer(at + 1_500, restaurant), pickup(at + 4_000, restaurant))) see(c, trail, o)
        return c.trips.values.last { it.pkg == wolt }
    }
    private fun see(c: DeliveryCore, trail: ScreenTrail, o: CourierObservation) {
        c.observe(o)
        trail.record(o.at, o.pkg, o.stage, if (o.stage == ScreenStage.PICKUP || o.stage == ScreenStage.DROPOFF) c.shown(o.pkg)?.id else null)
    }

    // ---------------------------------------------------------------- screen trail

    @Test fun trailKeepsChangesAndAnswersForAMoment() {
        val trail = ScreenTrail(keepMs = 60_000)
        trail.record(1_000, wolt, ScreenStage.IDLE, null)
        trail.record(2_000, wolt, ScreenStage.IDLE, null)          // no change: not a new entry
        trail.record(3_000, wolt, ScreenStage.PICKUP, "trip-a")
        trail.record(3_500, bolt, ScreenStage.IDLE, null)
        trail.record(9_000, wolt, ScreenStage.DROPOFF, "trip-a")
        assertNull(trail.at(wolt, 500))
        assertEquals(ScreenStage.IDLE, trail.at(wolt, 2_500)!!.stage); assertEquals(1_000, trail.at(wolt, 2_500)!!.at)
        assertEquals("trip-a", trail.at(wolt, 8_999)!!.tripId); assertEquals(ScreenStage.PICKUP, trail.at(wolt, 8_999)!!.stage)
        assertEquals(ScreenStage.DROPOFF, trail.at(wolt, 9_000)!!.stage)
        assertEquals(ScreenStage.IDLE, trail.at(bolt, 9_000)!!.stage)
        trail.record(70_000, wolt, ScreenStage.IDLE, null)
        assertNull("older than keepMs", trail.at(wolt, 2_500))
    }

    // ---------------------------------------------------------------- which pickup a target belongs to

    @Test fun navigationRequestedOnWoltsPickupScreenIsThatOrdersRestaurant() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail(); val t = accepted(c, trail)
        val hit = NavBinding.pickupOf(target(ms("18:01") + 20_000), c.trips.values, trail, ms("18:01") + 21_000)!!
        assertSame(t, hit.first); assertEquals(0, hit.second)
    }

    @Test fun navigationRequestedOnTheCustomerScreenIsNotARestaurant() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail()
        val a = accepted(c, trail)                                            // Borani Sahil, 18:01
        see(c, trail, card(ms("18:08")))                                      // collected, on the way to its customer
        val b = accepted(c, trail, "Hatay Usulü Dönər", ms("18:09"))          // a second order accepted on the way (stacked)
        assertNotSame(a, b); assertNull(b.pickups.single().pickedAt)
        // Back on A's customer card the courier asks for navigation: the customer, not B's restaurant (whatever YolGo guessed).
        see(c, trail, card(ms("18:10")))
        assertNull(NavBinding.pickupOf(target(ms("18:10") + 5_000, kind = "restaurant"), c.trips.values, trail, ms("18:10") + 6_000))
        // On B's restaurant screen the same request is B's restaurant.
        see(c, trail, pickup(ms("18:11"), "Hatay Usulü Dönər"))
        val hit = NavBinding.pickupOf(target(ms("18:11") + 5_000), c.trips.values, trail, ms("18:11") + 6_000)!!
        assertSame(b, hit.first); assertEquals(0, hit.second)
    }

    @Test fun onlyAnAppNavigationRequestOfTheSamePlatformCounts() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail(); accepted(c, trail)
        val at = ms("18:01") + 20_000
        assertNull("typed or pasted address", NavBinding.pickupOf(target(at, origin = "clipboard"), c.trips.values, trail, at))
        assertNull("map long press", NavBinding.pickupOf(target(at, origin = "map"), c.trips.values, trail, at))
        assertNull("Bolt's navigation", NavBinding.pickupOf(target(at, pkg = bolt), c.trips.values, trail, at))
        assertNotNull("sender unknown: the Wolt screen decides", NavBinding.pickupOf(target(at, pkg = null), c.trips.values, trail, at))
    }

    @Test fun requestBeforeTheOrderOrAfterItsCollectIsNotItsRestaurant() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail(); val t = accepted(c, trail)
        val at = ms("18:01") + 20_000
        assertNotNull(NavBinding.pickupOf(target(at), c.trips.values, trail, at + 1_000))
        assertNull("a target kept for hours", NavBinding.pickupOf(target(at), c.trips.values, trail, at + RouteStatus.TARGET_MAX_AGE_MS + 1_000))
        assertNull("before the order", NavBinding.pickupOf(target(t.acceptedAt - 5_000, kind = "restaurant"), c.trips.values, trail, ms("18:02")))
        see(c, trail, card(ms("18:12")))
        assertNull("after the Collect", NavBinding.pickupOf(target(ms("18:13"), kind = "restaurant"), c.trips.values, trail, ms("18:13")))
    }

    @Test fun withoutScreenEvidenceOnlyYolGosOwnRestaurantGuessCounts() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail(); accepted(c, trail)
        val empty = ScreenTrail() // the process restarted: no trail
        val at = ms("18:01") + 20_000
        assertNotNull(NavBinding.pickupOf(target(at, kind = "restaurant"), c.trips.values, empty, at))
        assertNull(NavBinding.pickupOf(target(at, kind = "customer"), c.trips.values, empty, at))
        assertNull(NavBinding.pickupOf(target(at, kind = "unknown"), c.trips.values, empty, at))
        // A stale screen (more than 2 min before the request) is no evidence either.
        assertNull(NavBinding.pickupOf(target(ms("18:01") + 4_000 + NavBinding.TRAIL_MAX_GAP_MS + 1_000), c.trips.values, trail,
            ms("18:01") + 4_000 + NavBinding.TRAIL_MAX_GAP_MS + 1_000))
    }

    @Test fun twoOpenWoltPickupsTheOneOnScreenWins() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail()
        val first = accepted(c, trail)
        // A second Wolt order accepted while the first one is still before its pickup; its restaurant screen is on screen now.
        val second = accepted(c, trail, "Hatay Usulü Dönər", ms("18:03"))
        assertNotSame(first, second); assertEquals(2, c.trips.values.count { it.active && it.pickups.single().pickedAt == null })
        val hit = NavBinding.pickupOf(target(ms("18:03") + 10_000), c.trips.values, trail, ms("18:03") + 11_000)!!
        assertSame(second, hit.first)
        // Without the screen, two open pickups are ambiguous even for YolGo's own restaurant guess.
        assertNull(NavBinding.pickupOf(target(ms("18:03") + 10_000, kind = "restaurant"), c.trips.values, ScreenTrail(), ms("18:03") + 11_000))
    }

    // ---------------------------------------------------------------- D9 with YolGo's coordinate

    private fun fix(at: Long, north: Double, kmh: Double? = 0.0) = VenueArrival.Fix(at, lat0 + north / 111_195.0, lon0, 8.0, kmh)
    private fun stay(at: Long, seconds: Int, north: Double) = (0 until seconds step 2).map { fix(at + it * 1_000L, north) }

    @Test fun yolgoCoordinateComesBeforeTheGeocode() {
        val y = VenueArrival.Venue(lat0, lon0, VenueArrival.SOURCE_YOLGO)
        val g = VenueArrival.Venue(lat0 + 0.002, lon0, VenueArrival.SOURCE_GEOCODE)
        assertSame(y, NavBinding.preferred(y, g)); assertSame(g, NavBinding.preferred(null, g)); assertNull(NavBinding.preferred(null, null))
    }

    @Test fun arrivalFoundWithYolgosCoordinateIsTheD9Arrival() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail(); val t = accepted(c, trail)
        val v = VenueArrival(); val venue = VenueArrival.Venue(lat0, lon0, VenueArrival.SOURCE_YOLGO)
        val parked = ms("18:06")
        val ev = stay(parked, 24, 10.0).flatMap { f -> v.add(f); v.live(c, { _, _ -> venue }, f.at) }
        // Ekim planı madde 18: 10 m from YolGo's target with an 8 m fix is "at the door" at once (same moment, new source).
        assertEquals(VenueArrival.SOURCE_NEAR_YOLGO, ev.single().reason)
        assertEquals(parked, t.pickups.single().arrivedAt); assertEquals(VenueArrival.SOURCE_NEAR_YOLGO, t.pickups.single().arrivalSource)
        assertEquals(TripPhase.WAITING, t.phase)
    }

    @Test fun aWrongYolgoCoordinateIsCorrectedAtTheCollectLikeAGeocode() {
        val c = DeliveryCore().also { it.start() }; val trail = ScreenTrail(); val t = accepted(c, trail)
        val v = VenueArrival()
        // The YolGo target was 300 m north of the real door; a 30 s stop there looked like an arrival.
        val wrong = VenueArrival.Venue(lat0 + 300 / 111_195.0, lon0, VenueArrival.SOURCE_YOLGO)
        stay(ms("18:05"), 30, 300.0).forEach { f -> v.add(f); v.live(c, { _, _ -> wrong }, f.at) }
        assertEquals(ms("18:05"), t.pickups.single().arrivedAt)
        // The real stay at the door, then the Collect.
        stay(ms("18:07"), 120, 0.0).forEach { f -> v.add(f); v.live(c, { _, _ -> wrong }, f.at) }
        val collectAt = ms("18:09")
        c.observe(card(collectAt))
        val ev = v.collected(c, t.id, 0, { _, _ -> wrong }, collectAt)
        assertEquals("restaurant_arrival_corrected", ev.single().type)
        assertEquals(ms("18:07"), t.pickups.single().arrivedAt)
        assertEquals(VenueArrival.SOURCE_PICKUP_POINT, t.pickups.single().arrivalSource)
    }
}
