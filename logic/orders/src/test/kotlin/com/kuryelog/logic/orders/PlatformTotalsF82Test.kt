package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * F8.2 (Ekim planı madde 13): day totals as the platforms print them. Wolt Gross earnings → Daily details (`11/10/2026`
 * `AZN 18.24`), the cash collected per day (Finances "Cash collection" rows), Bolt All deliveries day headers
 * (`8 October 2026` · `10,90 ₼` · `Deliveries: 3`). Layout of the user's screens; numbers and names changed.
 */
class PlatformTotalsF82Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"

    @Test fun woltDailyDetailsDayTotals() {
        val t = PlatformTotals.woltDaily(wolt, listOf("Gross earnings", "Deliveries", "Daily details", "11/10/2026", "AZN 18.24",
            "12/10/2026", "AZN 9.80", "13/10/2026 AZN 22.05"))
        assertEquals(listOf("day:2026-10-11", "day:2026-10-12", "day:2026-10-13"), t.map { it.period })
        assertEquals(listOf(18.24, 9.80, 22.05), t.map { it.earnings })
        assertTrue(t.all { it.platform == "wolt" && it.source == PlatformTotals.WOLT_DAILY })
    }

    @Test fun woltDailyDetailsAmountBeforeDate() {
        val t = PlatformTotals.woltDaily(wolt, listOf("Daily details", "AZN 18.24", "11/10/2026", "AZN 9.80", "12/10/2026"))
        assertEquals(mapOf("day:2026-10-11" to 18.24, "day:2026-10-12" to 9.80), t.associate { it.period to it.earnings })
    }

    @Test fun woltDailyNeedsItsScreenAndWolt() {
        assertTrue(PlatformTotals.woltDaily(wolt, listOf("11/10/2026", "AZN 18.24")).isEmpty())
        assertTrue(PlatformTotals.woltDaily(bolt, listOf("Daily details", "11/10/2026", "AZN 18.24")).isEmpty())
        assertTrue(PlatformTotals.woltDaily(wolt, listOf("Daily details", "31/02/2026", "AZN 18.24")).isEmpty())
    }

    @Test fun cashCollectedPerDay() {
        val rows = listOf(PlatformHistory.CashRow("2026-10-11", "17:45", "Test A", 54.25), PlatformHistory.CashRow("2026-10-11", "20:26", "Test B", 18.69),
            PlatformHistory.CashRow("2026-10-11", "17:45", "Test A", 54.25), PlatformHistory.CashRow("2026-10-12", "12:00", null, 10.0))
        val t = PlatformTotals.woltCash(rows)
        assertEquals(listOf("day:2026-10-11", "day:2026-10-12"), t.map { it.period })
        assertEquals(72.94, t[0].cash!!, 1e-9); assertEquals(10.0, t[1].cash!!, 1e-9)
        assertTrue(t.all { it.source == PlatformTotals.WOLT_CASH && it.earnings == null })
    }

    @Test fun boltDayHeaders() {
        val t = PlatformTotals.boltDays(bolt, listOf("All deliveries", "8 October 2026", "10,90 ₼", "Deliveries: 3", "Test Restoran",
            "10/08, 13:52, #G55BB", "4,72 ₼", "7 October 2026", "6,10 ₼", "Deliveries: 2", "Test Kafe", "10/07, 12:10, #H66CC", "3,05 ₼"))
        assertEquals(listOf("day:2026-10-08", "day:2026-10-07"), t.map { it.period })
        assertEquals(listOf(10.90, 6.10), t.map { it.earnings }); assertEquals(listOf(3, 2), t.map { it.deliveries })
        assertTrue(t.all { it.platform == "bolt" && it.source == PlatformTotals.BOLT_DAYS && it.amountBasis == "unknown" })
        assertTrue(PlatformTotals.boltDays(wolt, listOf("8 October 2026", "10,90 ₼", "Deliveries: 3")).isEmpty())
    }

    @Test fun boltHeaderWithoutTotalIsSkipped() {
        assertTrue(PlatformTotals.boltDays(bolt, listOf("All deliveries", "8 October 2026", "Test Restoran", "10/08, 13:52, #G55BB", "4,72 ₼")).isEmpty())
    }
}
