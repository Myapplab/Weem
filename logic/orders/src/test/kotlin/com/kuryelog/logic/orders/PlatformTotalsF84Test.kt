package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * F8.4 (Ekim planı madde 13): a day's official values against WEEM's. Deliveries exactly; money to the qəpik (0.01 ₼ off
 * is red); Wolt tips only from Stats (Finances shows them late); cash from "Cash collection"; distance only red past 10 %
 * (Wolt counts online time only); Bolt's amount matches when it equals the gross or the net (its basis is unknown).
 */
class PlatformTotalsF84Test {
    private val day = "day:2026-10-16"
    private val ours = PlatformTotals.Ours(deliveries = 6, gross = 19.90, net = 17.91, platformTips = 1.50, cash = 20.40)
    private fun stats(deliveries: Int? = 6, noTips: Double? = 19.90, tips: Double? = 1.50, km: Double? = null) =
        PlatformTotals.Total("wolt", day, PlatformTotals.WOLT_STATS, deliveries = deliveries, earnings = 21.40, earningsNoTips = noTips, tips = tips, distanceKm = km)
    private fun field(c: List<PlatformTotals.Check>, f: String, src: String = PlatformTotals.WOLT_STATS) = c.single { it.field == f && it.source == src }

    @Test fun matchingStatsAreAllGreen() {
        val c = PlatformTotals.compare("wolt", listOf(stats()), ours, null)
        assertEquals(listOf("deliveries", "earnings", "tips"), c.map { it.field })
        assertTrue(c.all { it.ok })
    }

    @Test fun oneQepikOrOneDeliveryOffIsRed() {
        val c = PlatformTotals.compare("wolt", listOf(stats(deliveries = 7, noTips = 19.91)), ours, null)
        assertFalse(field(c, "deliveries").ok); assertEquals(7.0, field(c, "deliveries").official, 0.0); assertEquals(6.0, field(c, "deliveries").ours!!, 0.0)
        assertFalse(field(c, "earnings").ok)
        assertTrue(field(c, "tips").ok)
        assertTrue(PlatformTotals.compare("wolt", listOf(stats(noTips = 19.904)), ours, null).let { field(it, "earnings").ok })
    }

    @Test fun dailyDetailsAndCash() {
        val c = PlatformTotals.compare("wolt", listOf(PlatformTotals.Total("wolt", day, PlatformTotals.WOLT_DAILY, earnings = 18.24),
            PlatformTotals.Total("wolt", day, PlatformTotals.WOLT_CASH, cash = 20.40)), ours, null)
        assertFalse(field(c, "earnings", PlatformTotals.WOLT_DAILY).ok)
        assertTrue(field(c, "cash", PlatformTotals.WOLT_CASH).ok)
        // Finances never gives a tip check: no tip there is not red.
        assertTrue(c.none { it.field == "tips" })
    }

    @Test fun distanceIsRedOnlyPastTenPercent() {
        assertTrue(field(PlatformTotals.compare("wolt", listOf(stats(km = 20.0)), ours, 21.9), "distance").ok)
        assertFalse(field(PlatformTotals.compare("wolt", listOf(stats(km = 20.0)), ours, 22.5), "distance").ok)
        // WEEM's shift km unknown: shown, not red.
        val unknown = field(PlatformTotals.compare("wolt", listOf(stats(km = 20.0)), ours, null), "distance")
        assertNull(unknown.ours); assertTrue(unknown.ok)
    }

    @Test fun boltMatchesGrossOrNet() {
        val bolt = PlatformTotals.Ours(deliveries = 3, gross = 10.90, net = 9.81, platformTips = 0.0, cash = 0.0)
        fun t(v: Double) = listOf(PlatformTotals.Total("bolt", day, PlatformTotals.BOLT_DAYS, deliveries = 3, earnings = v, amountBasis = "unknown"))
        assertTrue(PlatformTotals.compare("bolt", t(10.90), bolt, null).all { it.ok })
        assertTrue(PlatformTotals.compare("bolt", t(9.81), bolt, null).all { it.ok })
        val red = PlatformTotals.compare("bolt", t(11.20), bolt, null)
        assertFalse(field(red, "earnings", PlatformTotals.BOLT_DAYS).ok); assertEquals(10.90, field(red, "earnings", PlatformTotals.BOLT_DAYS).ours!!, 1e-9)
    }

    @Test fun onlyThisPlatformAndDayPeriods() {
        val week = PlatformTotals.Total("wolt", "week:2026-10-11..2026-10-16", PlatformTotals.WOLT_STATS, deliveries = 31)
        val bolt = PlatformTotals.Total("bolt", day, PlatformTotals.BOLT_DAYS, deliveries = 2, earnings = 5.0)
        assertTrue(PlatformTotals.compare("wolt", listOf(week, bolt), ours, null).isEmpty())
    }

    @Test fun oursFromTheDaysOrders() {
        val obs = CourierObservation("com.wolt.courierapp", 0L, ScreenStage.OFFER, true, "test", emptyList(), emptyList(), gross = 5.0)
        fun trip(id: String) = TripRecord(id, obs.pkg, OfferState("o-$id", obs.pkg, 0L, obs), 0L, TripPhase.FINISHED, completedAt = 1L, commissionPct = 10.0)
        val a = trip("a").apply { extraWaitAzn = 0.4; platformTipAzn = 1.0; paymentMethod = "cash"; cashAmount = 12.0 }
        val b = trip("b")
        val open = trip("c").apply { completedAt = null; phase = TripPhase.TO_RESTAURANT }
        val o = PlatformTotals.ours(OrderHistory.Day("2026-10-16", listOf(a, b, open)))
        assertEquals(2, o.deliveries); assertEquals(10.4, o.gross, 1e-9); assertEquals(1.0, o.platformTips, 1e-9); assertEquals(12.0, o.cash, 1e-9)
    }
}
