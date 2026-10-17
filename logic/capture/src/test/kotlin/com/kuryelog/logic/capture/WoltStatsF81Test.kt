package com.kuryelog.logic.capture

import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

/**
 * F8.1 (Ekim planı madde 13): Wolt "Your stats" also gives its date header (the period), the distance, the earnings without
 * tips and the tips. Layout of the user's 16 Oct screens (Today / Week / Month); numbers changed, nothing personal.
 */
class WoltStatsF81Test {
    private val wolt = "com.wolt.courierapp"
    private val utc = TimeZone.getTimeZone("UTC")
    private val oct16 = 1792152000000L // 2026-10-16 12:00 UTC
    private val jan3 = 1798977600000L  // 2027-01-03 12:00 UTC
    private fun obs(vararg text: String, at: Long = oct16) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "accessibility")
    private val tabs = arrayOf("Your stats", "TODAY", "WEEK", "MONTH", "CUSTOM")

    @Test fun todayGivesTheDayDistanceEarningsWithoutTipsAndTips() {
        val o = obs(*tabs, "16 October", "Deliveries completed", "6", "Distance on- and off-task", "23.4 km",
            "Earnings (estimate)", "AZN 21.40", "Earnings without tips", "AZN 19.90", "Tips", "AZN 1.50")
        assertEquals(ScreenStage.UNKNOWN, o.stage)
        assertEquals(6, o.statsTasksDone)
        assertEquals(21.40, o.statsEarningsAzn!!, 1e-9)
        assertEquals(19.90, o.statsEarningsNoTipsAzn!!, 1e-9)
        assertEquals(1.50, o.statsTipsAzn!!, 1e-9)
        assertEquals(23.4, o.statsDistanceKm!!, 1e-9)
        assertEquals("day:2026-10-16", o.statsPeriod)
    }

    @Test fun earningsWithoutTipsBeforeTheEstimateDoesNotBecomeTheEarnings() {
        val o = obs(*tabs, "16 Oct", "Earnings without tips", "AZN 19.90", "Tips AZN 1.50", "Earnings (estimate)", "AZN 21.40")
        assertEquals(21.40, o.statsEarningsAzn!!, 1e-9)
        assertEquals(19.90, o.statsEarningsNoTipsAzn!!, 1e-9)
        assertEquals(1.50, o.statsTipsAzn!!, 1e-9)
        assertEquals("day:2026-10-16", o.statsPeriod)
    }

    @Test fun weekAndMonthHeaders() {
        assertEquals("week:2026-10-11..2026-10-16", obs(*tabs, "11 Oct - 16 Oct", "Deliveries completed", "31").statsPeriod)
        assertEquals("week:2026-10-11..2026-10-16", obs(*tabs, "11 Oct – 16 Oct", "Deliveries completed", "31").statsPeriod)
        assertEquals("month:2026-10", obs(*tabs, "October", "Deliveries completed", "88").statsPeriod)
        // OCR reads Wolt's O as a zero (D23).
        assertEquals("day:2026-10-11", obs(*tabs, "11 0CT", "Deliveries completed", "5").statsPeriod)
    }

    @Test fun yearComesFromTheObservationTime() {
        assertEquals("month:2026-12", CourierParser.statsPeriod(listOf("december"), jan3, utc))
        assertEquals("week:2026-12-28..2027-01-03", CourierParser.statsPeriod(listOf("28 dec - 3 jan"), jan3, utc))
        assertEquals("day:2027-01-02", CourierParser.statsPeriod(listOf("2 january"), jan3, utc))
        assertEquals("day:2026-10-16", CourierParser.statsPeriod(listOf("16 october 2026"), jan3, utc))
    }

    @Test fun noOrSeveralHeadersOrBadDatesSayNothing() {
        assertNull(obs(*tabs, "Deliveries completed", "4", "Earnings (estimate)", "AZN 16.79").statsPeriod)
        assertNull(CourierParser.statsPeriod(listOf("16 october", "17 october"), oct16, utc))
        assertNull(CourierParser.statsPeriod(listOf("31 november"), oct16, utc))
        assertNull(CourierParser.statsPeriod(listOf("6 deliveries", "4 min"), oct16, utc))
    }

    @Test fun onlyOnStatsPages() {
        val o = obs("Pickup from", "TEST RESTORAN", "16 October", "Tips", "AZN 1.50")
        assertNull(o.statsPeriod); assertNull(o.statsTipsAzn)
    }
}
