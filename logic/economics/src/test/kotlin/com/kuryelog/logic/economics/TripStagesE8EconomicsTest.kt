package com.kuryelog.logic.economics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats TripStagesE8Test.kt @ 6388c04 — ekonomi payı (2 test: `courierState`); öbürleri logic/orders'ta (ORDERS.md 15.1).

/**
 * E8 (Faz 3) kabul: "sipariş aşamaları gerçek sırayla gelir". Feed v2'nin yeni üst düzey `stages` nesnesi her açık ve son
 * 10 dakikada biten siparişin aşamalarını kayıttaki gerçek saatleriyle verir; YolGo bir okumayı kaçırsa da (D17: restoran
 * varış ve çıkış aynı ms) aşamaları bu saatlerle uygular. `courierState` şeması kapalı olduğu için değişmez. Wolt 2.202.6
 * metinleri saha karelerinden; müşteri adı ve adresi uydurma.
 */
class TripStagesE8EconomicsTest {
    private val wolt = "com.wolt.courierapp"
    private val baku = TimeZone.getTimeZone("Asia/Baku")
    private fun ms(clock: String) = PlatformHistory.toMs("2026-10-11", clock, baku)
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun offer(at: Long, restaurant: String) = obs(at, "Decline", "Pickup 5 min", "AZN 2.61", "Expected earnings for the full delivery",
        "Delivery from", restaurant, "Route distance", "2 km", "Estimated", "9 - 17 min", "Accept", "Test küç. 1, AZ1000 Baku")
    private fun pickup(at: Long, restaurant: String) = obs(at, "Pickup 6 min", "Pickup from", restaurant, "Test küç. 1, AZ1000 Baku")
    private fun card(at: Long) = obs(at, "Dropoff 3 min", "Dropoff to", "Test A.", "Test küçəsi 1, AZ1000 Baku", "Entrance", "Floor",
        "Order details", "From", "#330", "Order delivered!")
    /** The customer card before the courier reaches the slider. */
    private fun cardNoSlider(at: Long) = obs(at, "Dropoff 9 min", "Dropoff to", "Test A.", "Test küçəsi 1, AZ1000 Baku", "Entrance", "Floor",
        "Order details", "From", "#330")
    private fun waiting(at: Long) = obs(at, "Quieter than usual", "Delivery demand", "l Low", "EXPECTED DEMAND", "Go offline")

    /** Borani Sahil accepted at 18:01:04 (first restaurant frame), GPS arrival 18:06 (D9), Collect 18:12. */
    private fun atCustomer(c: DeliveryCore): TripRecord {
        c.observe(waiting(ms("18:00"))); c.observe(waiting(ms("18:00") + 1_000))
        c.observe(offer(ms("18:01"), "Borani Sahil")); c.observe(offer(ms("18:01") + 1_500, "Borani Sahil"))
        c.observe(pickup(ms("18:01") + 4_000, "Borani Sahil"))
        val t = c.active(wolt)!!
        assertEquals(1, c.venueArrived(t.id, 0, ms("18:06"), VenueArrival.SOURCE_GEOCODE, ms("18:06") + 20_000).size)
        c.observe(card(ms("18:12")))
        return t.also { assertEquals(TripPhase.TO_CUSTOMER, it.phase) }
    }
    // WEEM (S7): feed v2'ye ekleme (`TripStages.feed/attach`, `changedAtMs`) yok; aşamalar `OrderStages.of` → `logic/core` `TripStages`
    // (ORDERS.md 13.2). Ekonomi payı aynı: aşama `courierState`'tekiyle aynı, `courierState` kapalı şemasını korur.

    @Test fun activeOrderCarriesEveryStageWithItsRealTime() {
        val c = DeliveryCore().also { it.start() }; val t = atCustomer(c)
        val j = OrderStages.of(c.trips.values, { null }, ms("18:13")).trips[0]
        assertEquals(t.id, j.tripId); assertEquals(wolt, j.platformPackage)
        assertTrue(j.active); assertEquals(TripStages.Stage.TO_DROPOFF, j.stage)
        assertNull(j.outcome); assertNull(j.endedAtMs)
        val e = j.events
        assertEquals(listOf("to_pickup", "at_pickup", "to_dropoff"), e.map { it.stage.key })
        assertEquals(listOf(ms("18:01") + 4_000, ms("18:06"), ms("18:12")), e.map { it.atMs })
        assertEquals("gps", e[1].source)
        // The stage agrees with the feed's own courierState.
        val state = EconomicsLogs.courierState(listOf(wolt), c.trips.values, 0, 60.0, null, null, emptyList(), null, OfferGeo.UNKNOWN, null, false)
        assertEquals(state.getJSONArray("activeTrips").getJSONObject(0).getString("stage"), j.stage!!.key)
    }

    @Test fun attachingKeepsTheClosedCourierStateSchema() {
        val c = DeliveryCore().also { it.start() }; atCustomer(c)
        val state = EconomicsLogs.courierState(listOf(wolt), c.trips.values, 0, 60.0, null, null, emptyList(), null, OfferGeo.UNKNOWN, null, false)
        // WEEM: reading the stages leaves courierState as it was; its keys stay the schema's closed set (no `stages`, no `changedAtMs`).
        OrderStages.of(c.trips.values, { null }, ms("18:13"))
        assertEquals(state.toString(), EconomicsLogs.courierState(listOf(wolt), c.trips.values, 0, 60.0, null, null, emptyList(), null, OfferGeo.UNKNOWN, null, false).toString())
        assertFalse(state.has("stages")); assertFalse(state.has("changedAtMs"))
        assertEquals(setOf("tripId", "platformPackage", "stage", "remainingMinEstimate", "dropZone"), state.getJSONArray("activeTrips").getJSONObject(0).keys().asSequence().toSet())
    }
}
