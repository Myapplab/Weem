package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

/**
 * D3 (13 Ekim planı): (b) Wolt'ta kaydırıcıdan sonra yeni siparişin restoran ekranı önceki siparişi doğrulanmamış teslim olarak
 * kapatır; (c) doğrulanmamış teslim sayıya ve nete girer, platform listesi onu doğrular; (d) Finances "Cash collection" saati
 * nakit siparişin teslim saatidir. Wolt 2.202.6 metinleri saha karelerinden; müşteri adları ve adresler uydurma.
 */
class DeliveryCountD3Test {
    private val wolt = "com.wolt.courierapp"
    private val baku = TimeZone.getTimeZone("Asia/Baku")
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun offer(at: Long, restaurant: String, fee: String) = obs(at, "Decline", "Pickup 5 min", fee, "Expected earnings for the full delivery",
        "Delivery from", restaurant, "Route distance", "2 km", "Estimated", "9 - 17 min", "Accept", "Test küç. 1, AZ1000 Baku")
    private fun pickup(at: Long, restaurant: String) = obs(at, "Pickup 6 min", "Pickup from", restaurant, "Test küç. 1, AZ1000 Baku")
    private fun card(at: Long, name: String) = obs(at, "Dropoff 3 min", "Dropoff to", name, "Test küçəsi 1, AZ1000 Baku", "Entrance", "Floor",
        "Order details", "From", "#330", "Order delivered!")
    private fun waiting(at: Long) = obs(at, "Quieter than usual", "Delivery demand", "l Low", "EXPECTED DEMAND", "Go offline")
    private fun ms(clock: String) = PlatformHistory.toMs("2026-10-11", clock, baku)

    /** Order 1 (Borani Sahil) accepted at 18:01, picked up and at its customer with the slider. */
    private fun firstAtCustomer(c: DeliveryCore): TripRecord {
        c.observe(waiting(ms("18:00"))); c.observe(waiting(ms("18:00") + 1_000))
        c.observe(offer(ms("18:01"), "Borani Sahil", "AZN 2.61")); c.observe(offer(ms("18:01") + 1_500, "Borani Sahil", "AZN 2.61"))
        c.observe(pickup(ms("18:01") + 4_000, "Borani Sahil"))
        c.observe(card(ms("18:15"), "Test A.")); c.observe(card(ms("18:15") + 2_000, "Test A."))
        return c.active(wolt)!!.also { assertTrue(it.atCustomer) }
    }

    @Test fun nextRestaurantScreenAfterTheSliderClosesTheOrder() {
        val c = DeliveryCore().also { it.start() }; val first = firstAtCustomer(c)
        val ev = c.observe(offer(ms("18:20"), "Hatay Usulü Dönər", "AZN 2.31")) + c.observe(pickup(ms("18:20") + 3_000, "Hatay Usulü Dönər"))
        val e = ev.single { it.type == "delivered_inferred" }
        assertEquals(first.id, e.tripId); assertEquals("next_order_at_restaurant_after_slider", e.reason); assertEquals(ms("18:20") + 3_000, e.at)
        assertTrue(first.deliveredInferred); assertTrue(first.countsAsDelivered); assertNull(first.completedAt)
        assertEquals(1, c.trips.values.count { it.active })
        // D3(c): counted in the day's deliveries and money, labelled unverified.
        val day = OrderHistory.Day("2026-10-11", c.trips.values.toList())
        assertEquals(1, day.orders); assertEquals(2.61, day.net, 1e-9); assertEquals(1, day.inferred); assertEquals(0, day.unconfirmed)
        assertEquals(1, OrderHistory.deliveredOrders(c.trips.values))
    }

    /** D3(c) (Faz 2 kabul kontrolü, 14 Ekim): an unverified delivery's measured values are in the Bugün averages; its order
     *  time is not (the handover was not seen). */
    @Test fun unverifiedDeliveryCountsInTheAverages() {
        val c = DeliveryCore().also { it.start() }; val first = firstAtCustomer(c)
        assertEquals(1, c.venueArrived(first.id, 0, ms("18:10"), VenueArrival.SOURCE_GEOCODE, ms("18:16")).size)
        assertTrue(OrderHistory.averages(c.trips.values.toList()) { "Wolt" }.restaurantMin == null)
        c.observe(pickup(ms("18:20"), "Hatay Usulü Dönər"))
        assertTrue(first.deliveredInferred)
        val a = OrderHistory.averages(c.trips.values.toList()) { "Wolt" }
        assertEquals(5.0, a.restaurantMin!!, 1e-9)   // GPS arrival 18:10, Collect 18:15
        assertNull(a.orderMin)
    }

    @Test fun missedOfferRestaurantAfterTheSliderOpensANewOrder() {
        val c = DeliveryCore().also { it.start() }; val first = firstAtCustomer(c)
        // No offer card was read: the new restaurant is a new order (offer missed), never a second pickup of the first one.
        val ev = c.observe(pickup(ms("18:25"), "ZOO 28"))
        val second = c.active(wolt)!!
        assertNotSame(first, second); assertTrue(second.missedOffer); assertEquals(1, first.pickups.size)
        assertEquals(first.id, ev.single { it.type == "delivered_inferred" }.tripId)
        assertEquals("active_screen_offer_missed", ev.single { it.type == "accepted" }.reason)
    }

    @Test fun ownRestaurantReadAgainOrAnotherPlatformClosesNothing() {
        val c = DeliveryCore().also { it.start() }; val first = firstAtCustomer(c)
        // The order's own restaurant in an OCR variant (pickup panel / timeline read again).
        assertTrue(c.observe(pickup(ms("18:16"), "Boranı Sahil")).none { it.type == "delivered_inferred" || it.type == "accepted" })
        assertTrue(first.active); assertEquals(1, c.trips.size)
        // A Bolt restaurant screen (split screen, a Bolt order on the way) proves nothing about the Wolt order.
        c.observe(CourierParser.parse("com.bolt.food.courier", listOf("Decline", "Cook Box", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼"),
            listOf("22 min, 4,49 ₼", "Decline"), ms("18:17"), "accessibility"))
        c.click("com.bolt.food.courier", "22 min, 4,49 ₼", ms("18:17") + 500)
        val ev = c.observe(CourierParser.parse("com.bolt.food.courier", listOf("Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99"),
            emptyList(), ms("18:17") + 1_000, "accessibility"))
        assertTrue(ev.none { it.type == "delivered_inferred" }); assertTrue(first.active)
    }

    @Test fun financesCashRowsAreRead() {
        // 11 Oct 17:46:18 frame (OCR), the courier's own lines left out.
        val rows = PlatformHistory.cashRows(wolt, listOf("T", "Finances", "GROSS EARNINGS >", "AZN 23.28", "Next payout after 15 Oct", "CASH PENDING", "AZN 75.79",
            "Near cash limit AZN 100.00", "Recent transactions", "11 October 2026", "Cash collection", "AZN 54.25", "Bonbon PATISSERIE", "17:45",
            "Cash collection", "AZN 6.40", "King Doner Shamahinka", "16:46", "8October 2026", "Cash collection", "McDonald's & McCafe Grand", "AZN 18.69",
            "Hayat", "20:26", "Cash offload", "-AZN 70.00", "16:01"))
        assertEquals(listOf(PlatformHistory.CashRow("2026-10-11", "17:45", "Bonbon PATISSERIE", 54.25), PlatformHistory.CashRow("2026-10-11", "16:46", "King Doner Shamahinka", 6.40),
            PlatformHistory.CashRow("2026-10-08", "20:26", "McDonald's & McCafe Grand Hayat", 18.69)), rows)
        // A row cut off before its clock, rows above the first day header and other apps give nothing.
        assertEquals(emptyList<PlatformHistory.CashRow>(), PlatformHistory.cashRows(wolt, listOf("Recent transactions", "Cash collection", "AZN 6.40", "King Doner", "16:46")))
        assertEquals(emptyList<PlatformHistory.CashRow>(), PlatformHistory.cashRows(wolt, listOf("11 October 2026", "Cash collection", "AZN 54.25", "Bonbon PATISSERIE")))
        assertEquals(emptyList<PlatformHistory.CashRow>(), PlatformHistory.cashRows("com.bolt.food.courier", listOf("11 October 2026", "Cash collection", "AZN 6.40", "X", "16:46")))
    }

    @Test fun cashCollectionClockIsTheDeliveryTime() {
        val c = DeliveryCore().also { it.start() }; val t = firstAtCustomer(c)
        // Rawstats closed it 11 minutes late (King Doner 11 Oct: 16:57 instead of 16:46).
        c.observe(waiting(ms("18:32"))); c.observe(waiting(ms("18:32") + 1_100))
        assertEquals(ms("18:32") + 1_100, t.completedAt)
        val row = PlatformHistory.CashRow("2026-10-11", "18:21", "Borani Sahil", 31.40)
        val at = ms("18:21")
        assertSame(t, PlatformHistory.cashMatch(row, at, c.trips.values))
        val ev = c.cashCollected(t.id, at, row.amount, ms("18:40"))
        assertEquals(at + 59_999, t.completedAt); assertEquals(at + 59_999, t.customers.values.single().deliveredAt)
        assertEquals("delivery_time_corrected", ev.single { it.tripId == t.id && it.type != "payment_seen" }.type)
        assertEquals("cash", t.paymentMethod); assertEquals(31.40, t.cashAmount!!, 1e-9); assertEquals("wolt_cash_collection", t.verifiedBy)
        // Once per order: the same row seen again changes nothing.
        assertNull(PlatformHistory.cashMatch(row, at, c.trips.values)); assertTrue(c.cashCollected(t.id, at, row.amount, ms("18:41")).isEmpty())
        // A time inside the minute is kept.
        val c2 = DeliveryCore().also { it.start() }; val t2 = firstAtCustomer(c2)
        c2.observe(waiting(ms("18:21") + 10_000)); c2.observe(waiting(ms("18:21") + 11_200))
        assertTrue(c2.cashCollected(t2.id, at, 31.40, ms("18:40")).none { it.type == "delivery_time_corrected" }); assertEquals(ms("18:21") + 11_200, t2.completedAt)
    }

    @Test fun cashCollectionVerifiesAnUnverifiedDelivery() {
        val c = DeliveryCore().also { it.start() }; val t = firstAtCustomer(c)
        c.observe(offer(ms("18:20"), "Hatay Usulü Dönər", "AZN 2.31")); c.observe(pickup(ms("18:20") + 3_000, "Hatay Usulü Dönər"))
        assertTrue(t.deliveredInferred)
        val ev = c.cashCollected(t.id, ms("18:19"), 31.40, ms("18:40"))
        val d = ev.single { it.type == "delivered" }
        assertEquals("wolt_cash_collection", d.reason); assertEquals(ms("18:19") + 59_999, d.at)
        assertEquals(TripPhase.FINISHED, t.phase); assertEquals(ms("18:20") + 3_000, t.deliveredInferredAt)
        assertEquals("Teslim edildi", OrderHistory.status(t) { a, _ -> a })
        // Still one delivery in the day.
        assertEquals(1, OrderHistory.deliveredOrders(c.trips.values))
    }

    @Test fun cashRowFindsTheRightOrder() {
        val c = DeliveryCore().also { it.start() }; val first = firstAtCustomer(c)
        c.observe(waiting(ms("18:30"))); c.observe(waiting(ms("18:30") + 1_100))
        // Same restaurant again later in the day; the other row's clock is near the second order's end.
        c.observe(offer(ms("19:00"), "Borani Sahil", "AZN 1.90")); c.observe(pickup(ms("19:00") + 2_000, "Borani Sahil"))
        c.observe(card(ms("19:10"), "Test B.")); c.observe(waiting(ms("19:20"))); c.observe(waiting(ms("19:20") + 1_100))
        val second = c.trips.values.last()
        assertSame(second, PlatformHistory.cashMatch(PlatformHistory.CashRow("2026-10-11", "19:20", "Borani Sahil", 9.0), ms("19:20"), c.trips.values))
        assertSame(first, PlatformHistory.cashMatch(PlatformHistory.CashRow("2026-10-11", "18:30", "Borani Sahil", 9.0), ms("18:30"), c.trips.values))
        // Another restaurant, an order accepted after the clock or more than 3 h before it: no match.
        assertNull(PlatformHistory.cashMatch(PlatformHistory.CashRow("2026-10-11", "18:30", "Bonbon PATISSERIE", 9.0), ms("18:30"), c.trips.values))
        assertNull(PlatformHistory.cashMatch(PlatformHistory.CashRow("2026-10-11", "17:50", "Borani Sahil", 9.0), ms("17:50"), c.trips.values))
        assertNull(PlatformHistory.cashMatch(PlatformHistory.CashRow("2026-10-11", "23:30", "Borani Sahil", 9.0), ms("23:30"), c.trips.values))
    }

    @Test fun listRowVerifiesAnUnverifiedDeliveryOnce() {
        val c = DeliveryCore().also { it.start() }; val t = firstAtCustomer(c)
        c.observe(offer(ms("18:20"), "Hatay Usulü Dönər", "AZN 2.31")); c.observe(pickup(ms("18:20") + 3_000, "Hatay Usulü Dönər"))
        assertEquals("Teslim edildi (doğrulanmadı)", OrderHistory.status(t) { a, _ -> a })
        assertTrue(c.listVerified(t.id, "wolt_earnings_list", ms("19:00"))); assertFalse(c.listVerified(t.id, "wolt_earnings_list", ms("19:01")))
        assertEquals("Teslim edildi (listede doğrulandı)", OrderHistory.status(t) { a, _ -> a })
        assertEquals(0, OrderHistory.Day("2026-10-11", c.trips.values.toList()).inferred)
        // Written and read back.
        val back = RecordJson.trip(RecordJson.trip(t))
        assertEquals("wolt_earnings_list", back.verifiedBy); assertEquals(ms("19:00"), back.verifiedAt); assertTrue(back.countsAsDelivered)
    }
}
