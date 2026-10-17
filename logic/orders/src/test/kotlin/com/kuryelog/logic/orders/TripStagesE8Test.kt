package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * E8 (Faz 3) kabul: "sipariş aşamaları gerçek sırayla gelir". WEEM: `OrderEvents.stages` (`logic/core` `TripStages` tipi; üretici `OrderStages`). Rawstats'ta feed v2'nin yeni üst düzey `stages` nesnesi her açık ve son
 * 10 dakikada biten siparişin aşamalarını kayıttaki gerçek saatleriyle verir; YolGo bir okumayı kaçırsa da (D17: restoran
 * varış ve çıkış aynı ms) aşamaları bu saatlerle uygular. `courierState` şeması kapalı olduğu için değişmez. Wolt 2.202.6
 * metinleri saha karelerinden; müşteri adı ve adresi uydurma.
 */
class TripStagesE8Test {
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

        @Test fun deliveredOrderStaysTenMinutesWithItsOutcomeAndTime() {
        val c = DeliveryCore().also { it.start() }; val t = atCustomer(c)
        c.observe(waiting(ms("18:20"))); c.observe(waiting(ms("18:20") + 1_500)); c.observe(waiting(ms("18:20") + 4_000))
        assertEquals(TripPhase.FINISHED, t.phase)
        val done = t.completedAt!!
        val j = OrderStages.of(c.trips.values, { null }, done + 9 * 60_000L).trips[0]
        assertFalse(j.active); assertNull(j.stage)
        assertEquals(TripStages.Outcome.DELIVERED, j.outcome); assertEquals(done, j.endedAtMs)
        assertEquals(listOf(TripStages.Stage.TO_PICKUP, TripStages.Stage.AT_PICKUP, TripStages.Stage.TO_DROPOFF), j.events.map { it.stage })
        assertEquals(0, OrderStages.of(c.trips.values, { null }, done + 11 * 60_000L).trips.size)
    }

        @Test fun cancelledBeforePickupAndUnverifiedDeliveryAreTold() {
        // D7: accepted, then Wolt's waiting map before the pickup: not delivered (cancelled).
        val c = DeliveryCore().also { it.start() }
        c.observe(offer(ms("12:27"), "Soloha")); c.observe(offer(ms("12:27") + 1_500, "Soloha")); c.observe(pickup(ms("12:27") + 4_000, "Soloha"))
        val cancelled = c.active(wolt)!!
        val closed = (c.observe(waiting(ms("12:36"))) + c.observe(waiting(ms("12:36") + 3_500))).single { it.type == "end_unconfirmed" }.at
        val j = OrderStages.of(c.trips.values, { id -> closed.takeIf { id == cancelled.id } }, ms("12:37")).trips[0]
        assertEquals(TripStages.Outcome.CANCELLED, j.outcome); assertEquals(closed, j.endedAtMs)
        // Without the close time (a restart lost it) the order is not listed: its age is unknown.
        assertEquals(0, OrderStages.of(c.trips.values, { null }, ms("12:37")).trips.size)

        // D3(b): the next order's restaurant screen after the slider: an unverified delivery at that moment.
        val d = DeliveryCore().also { it.start() }; val first = atCustomer(d)
        d.observe(offer(ms("18:20"), "Hatay Usulü Dönər")); d.observe(pickup(ms("18:20") + 3_000, "Hatay Usulü Dönər"))
        assertTrue(first.deliveredInferred)
        val trips = OrderStages.of(d.trips.values, { null }, ms("18:21")).trips
        assertEquals(2, trips.size)
        val ended = trips.single { it.tripId == first.id }
        assertEquals(TripStages.Outcome.DELIVERED_UNVERIFIED, ended.outcome); assertEquals(ms("18:20") + 3_000, ended.endedAtMs)
        val next = trips.single { it.active }
        assertEquals(TripStages.Stage.TO_PICKUP, next.stage)
    }

        @Test fun secondPickupOfOneOrderGoesBackToPickupInOrder() {
        val c = DeliveryCore().also { it.start() }
        c.observe(offer(ms("13:00"), "Mix Point")); c.observe(offer(ms("13:00") + 1_500, "Mix Point"))
        c.observe(pickup(ms("13:00") + 4_000, "Mix Point"))
        val t = c.active(wolt)!!
        c.observe(cardNoSlider(ms("13:08")))               // first restaurant collected
        c.observe(pickup(ms("13:10"), "Bravo Market"))     // a second restaurant of the same order
        val e = OrderStages.trip(t, null).events
        assertEquals(listOf(TripStages.Stage.TO_PICKUP, TripStages.Stage.TO_PICKUP), e.map { it.stage })
        assertEquals(listOf(0, 1), e.map { it.pickup })
        assertEquals(listOf(ms("13:00") + 4_000, ms("13:08")), e.map { it.atMs })
        assertEquals(TripStages.Stage.TO_PICKUP, OrderStages.trip(t, null).stage)
    }

        @Test fun stagesHoldNoCustomerData() {
        val c = DeliveryCore().also { it.start() }; atCustomer(c)
        val text = OrderStages.of(c.trips.values, { null }, ms("18:13")).toJson().toString()
        for (secret in listOf("Test A.", "Test küçəsi", "#330", "Borani")) assertFalse(secret, text.contains(secret))
    }

}
