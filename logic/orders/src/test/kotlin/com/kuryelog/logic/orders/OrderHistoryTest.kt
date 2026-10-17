package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** K6/K7 regresyonları. Yapay restoran adları ve tutarlar; gerçek kayıt değil. */
class OrderHistoryTest {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.deliverycourier"
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private fun at(s: String) = fmt.parse(s)!!.time
    private fun day(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(t)
    private fun platform(pkg: String) = if (pkg.contains("wolt")) "Wolt" else if (pkg.contains("bolt")) "Bolt Food" else pkg.substringAfterLast('.')

    private fun trip(id: String, pkg: String, accepted: String, gross: Double?, commission: Double, done: String? = null,
                     restaurant: String? = "Test Restoran", phase: TripPhase = if (done != null) TripPhase.FINISHED else TripPhase.TO_RESTAURANT,
                     priceIsNet: Boolean = false): TripRecord {
        val obs = CourierObservation(pkg, at(accepted), ScreenStage.OFFER, true, "test", emptyList(), emptyList(), gross = gross, restaurant = restaurant)
        return TripRecord(id, pkg, OfferState("o-$id", pkg, at(accepted), obs), at(accepted), phase, mutableListOf(PickupRecord(restaurant, null)),
            completedAt = done?.let(::at), commissionPct = commission, priceIsNet = priceIsNet)
    }

    @Test fun netAlwaysDeductsCommissionEvenForOldNetRecords() {
        // 6 Ekim kayıtları price_is_net=true ile yazılmıştı; net brüte eşit görünüyordu.
        val old = trip("t1", bolt, "2026-10-06 13:28", 6.85, 10.0, "2026-10-06 14:15", priceIsNet = true)
        assertEquals(6.85, old.gross!!, 1e-9)
        assertEquals(6.165, old.net!!, 1e-9)
        val restored = RecordJson.trip(RecordJson.trip(old))
        assertTrue(restored.priceIsNet)
        assertEquals(6.165, restored.net!!, 1e-9)
    }

    @Test fun daysArePerPlatformNewestFirstWithGrossAndNet() {
        val trips = listOf(
            trip("w1", wolt, "2026-10-06 11:00", 2.25, 20.0, "2026-10-06 11:13"),
            trip("w2", wolt, "2026-10-06 11:32", 2.19, 20.0, "2026-10-06 11:47"),
            trip("b1", bolt, "2026-10-06 11:18", 2.70, 10.0, "2026-10-06 11:30"),
            trip("w3", wolt, "2026-10-04 15:00", 3.00, 20.0, "2026-10-04 15:20"),
            trip("w4", wolt, "2026-10-06 10:58", null, 20.0, phase = TripPhase.END_UNCONFIRMED))
        val days = OrderHistory.days(trips, emptyList(), "Wolt", ::platform, ::day)
        assertEquals(listOf("2026-10-06", "2026-10-04"), days.map { it.date })
        val d = days[0]
        assertEquals(listOf("w4", "w1", "w2"), d.trips.map { it.id })
        assertEquals(2, d.orders)
        assertEquals(4.44, d.gross, 1e-9)
        assertEquals(4.44 * 0.8, d.net, 1e-9)
        assertEquals(1, d.unconfirmed)
        assertEquals(listOf("b1"), OrderHistory.days(trips, emptyList(), "Bolt Food", ::platform, ::day).single().trips.map { it.id })
    }

    @Test fun phoneAndOtherWatchedAppsNeverAppearInPlatformDays() {
        val dialer = trip("p1", "com.google.android.dialer", "2026-10-06 11:00", 1.0, 0.0, "2026-10-06 11:05")
        assertTrue(OrderHistory.days(listOf(dialer), emptyList(), "Wolt", ::platform, ::day).isEmpty())
        assertTrue(OrderHistory.days(listOf(dialer), emptyList(), "Bolt Food", ::platform, ::day).isEmpty())
    }

    @Test fun liveTripReplacesStoredCopy() {
        val stored = trip("w1", wolt, "2026-10-07 09:00", 2.0, 20.0)
        val live = trip("w1", wolt, "2026-10-07 09:00", 2.0, 20.0, "2026-10-07 09:20")
        val d = OrderHistory.days(listOf(stored), listOf(live), "Wolt", ::platform, ::day).single()
        assertSame(live, d.trips.single())
        assertEquals(1, d.orders)
    }

    @Test fun listTimeMatchesPlatformEarningsPages() {
        val w = trip("w", wolt, "2026-10-06 11:32", 2.19, 20.0, "2026-10-06 11:47")
        val b = trip("b", bolt, "2026-10-06 11:18", 2.70, 10.0, "2026-10-06 11:30")
        assertEquals(at("2026-10-06 11:32"), OrderHistory.listTime(w, "Wolt"))
        assertEquals(at("2026-10-06 11:30"), OrderHistory.listTime(b, "Bolt Food"))
        assertEquals(at("2026-10-06 11:18"), OrderHistory.listTime(trip("b2", bolt, "2026-10-06 11:18", 2.0, 10.0), "Bolt Food"))
    }

    @Test fun restaurantFallsBackToOfferAndSkipsBlank() {
        assertEquals("Test Restoran", OrderHistory.restaurant(trip("a", bolt, "2026-10-06 11:00", 1.0, 0.0)))
        val blank = trip("b", bolt, "2026-10-06 11:00", 1.0, 0.0, restaurant = "")
        assertNull(OrderHistory.restaurant(blank))
        blank.pickups.add(PickupRecord("İkinci Restoran", null))
        assertEquals("İkinci Restoran", OrderHistory.restaurant(blank))
    }

    @Test fun dateLabelsInBothLanguages() {
        assertEquals("6 Ekim 2026", OrderHistory.dateLabel("2026-10-06", false))
        assertEquals("6 oktyabr 2026", OrderHistory.dateLabel("2026-10-06", true))
        assertEquals("29 Şubat 2028", OrderHistory.dateLabel("2028-02-29", false))
        assertEquals("bozuk", OrderHistory.dateLabel("bozuk", false))
        assertEquals("2026-10", OrderHistory.dateLabel("2026-10", false))
        assertEquals("2026-10", OrderHistory.dateLabel("2026-10", true))
    }

    @Test fun storedJsonRoundTripKeepsDayGrouping() {
        val j = RecordJson.trip(trip("w9", wolt, "2026-10-05 20:00", 3.38, 20.0, "2026-10-05 20:25"))
        val back = RecordJson.trip(JSONObject(j.toString()))
        assertEquals("2026-10-05", OrderHistory.days(listOf(back), emptyList(), "Wolt", ::platform, ::day).single().date)
    }

    @Test fun midnightUsesAcceptedAtInLocalTimezoneForBothPlatforms() {
        // Existing rule: acceptedAt's local day, even when delivery or the offer is on another day.
        val localDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Baku") }
        val w = trip("w-night", wolt, "2026-10-14 19:55", 4.0, 20.0, "2026-10-14 20:15")
        val b = trip("b-night", bolt, "2026-10-14 19:58", 3.0, 10.0, "2026-10-14 20:20")
        val next = trip("next", wolt, "2026-10-14 20:00", 2.0, 20.0, "2026-10-14 20:30")
        val days = OrderHistory.days(listOf(w, b), listOf(next), null, ::platform) { localDay.format(it) }
        assertEquals(listOf("2026-10-15", "2026-10-14"), days.map { it.date })
        assertEquals(listOf("w-night", "b-night"), days[1].trips.map { it.id })
        assertEquals("2026-10-15", localDay.format(OrderHistory.listTime(b, "Bolt Food")))
    }
}
