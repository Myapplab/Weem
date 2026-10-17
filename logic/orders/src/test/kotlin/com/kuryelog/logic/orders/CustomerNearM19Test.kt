package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 19 (16 Ekim, dördüncü tur): rotada müşteriye ≤10 m ve hız <10 km/s → müşteriye varış (doğruluk payı madde 18
 * gibi); mesafe YolGo/Google Maps'ten gelirse o (madde 20). Platformun "Arrived"ı önce geldiyse o kalır.
 */
class CustomerNearM19Test {
    private val wolt = "com.wolt.courierapp"
    private fun obs(at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(wolt, text.toList(), actions, at, "ocr")
    private fun offer(at: Long) = obs(at, "Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran",
        "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept", actions = listOf("Decline", "Accept"))
    private fun pickup(at: Long) = obs(at, "Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Collect order", "#014", "Test A.",
        "Collect", "Cancel this delivery", actions = listOf("Collect", "Cancel this delivery"))
    private fun card(at: Long, vararg extra: String) = obs(at, "Dropoff to", "Test A.", "Test küçəsi 39k, Bakı", "Address details", "Order details",
        "From", "Test Restoran", "#408", "Difficulties with the delivery?", "Send a message", *extra)
    private val lat0 = 40.3777; private val lon0 = 49.8920
    private fun fix(at: Long, north: Double, kmh: Double? = 0.0, acc: Double? = 5.0) = VenueArrival.Fix(at, lat0 + north / 111_195.0, lon0, acc, kmh)
    private val home = VenueArrival.Venue(lat0, lon0, "android_geocoder_address_estimate")
    private fun toCustomer(c: DeliveryCore): TripRecord {
        c.observe(offer(10_000)); c.click(wolt, "Accept", 14_000); c.observe(pickup(15_000)); c.observe(card(600_000))
        return c.active(wolt)!!.also { assertEquals(TripPhase.TO_CUSTOMER, it.phase) }
    }
    private fun ride(v: VenueArrival, c: DeliveryCore, fixes: List<VenueArrival.Fix>, nav: ArrivalDistance.Source? = null): List<CoreEvent> =
        fixes.flatMap { f -> v.add(f); v.customers(c, { _, _ -> home }, f.at) { _ -> nav } }

    @Test fun withinTenMetresAndSlowIsTheArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toCustomer(c)
        val ev = ride(v, c, listOf(fix(700_000, 80.0, 18.0), fix(702_000, 40.0, 18.0), fix(704_000, 13.0, 4.0)))
        assertEquals("customer_arrived", ev.single().type); assertEquals(VenueArrival.SOURCE_CUSTOMER_NEAR, ev.single().reason)
        val cu = t.customers.values.single()
        assertEquals(704_000L, cu.arrivedAt); assertEquals(VenueArrival.SOURCE_CUSTOMER_NEAR, cu.arrivalSource)
        assertEquals(TripPhase.AT_CUSTOMER, t.phase)
        assertTrue(ride(v, c, listOf(fix(706_000, 2.0, 0.0))).isEmpty())
    }

    @Test fun ridingPastOrAFixBeforeTheCollectIsNoArrival() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival()
        v.add(fix(500_000, 0.0, 0.0)) // restoranda beklerken (müşteri aşamasından önce) evin yanından geçmiş olsa bile
        val t = toCustomer(c)
        assertTrue(ride(v, c, listOf(fix(700_000, 3.0, 15.0), fix(702_000, -30.0, 18.0))).isEmpty())
        assertNull(t.customers.values.single().arrivedAt)
    }

    @Test fun thePlatformsArrivedStays() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toCustomer(c)
        c.click(wolt, "Arrived", 650_000)
        val before = t.customers.values.single().arrivedAt
        assertTrue(ride(v, c, listOf(fix(700_000, 1.0, 0.0))).isEmpty())
        assertEquals(before, t.customers.values.single().arrivedAt)
    }

    @Test fun aFreshNavigationDistanceDecides() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toCustomer(c)
        val ev = ride(v, c, listOf(fix(702_000, 60.0, 3.0)), ArrivalDistance.Source(7.0, 701_000, ArrivalDistance.MAPS))
        assertEquals(ArrivalDistance.MAPS, ev.single().reason)
        assertEquals(701_000L, t.customers.values.single().arrivedAt)
    }

    @Test fun arrivalSourceIsWrittenAndReadBack() {
        val c = DeliveryCore().also { it.start() }; val v = VenueArrival(); val t = toCustomer(c)
        ride(v, c, listOf(fix(704_000, 4.0, 2.0)))
        val back = RecordJson.trip(RecordJson.trip(t))
        assertEquals(VenueArrival.SOURCE_CUSTOMER_NEAR, back.customers.values.single().arrivalSource)
    }
}
