package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * F8.5 (Ekim planı madde 13): the day page's "Resmî değerler" card. A different value is red with both numbers
 * ("Wolt: 18.24 ₼ · WEEM: 17.94 ₼"); under it the orders whose list fee differs from WEEM's gross. Android view
 * code: checked on the source here, compiled by android-compile.sh and CI.
 */
class PlatformTotalsF85Test {
    private val tr = { t: String, _: String -> t }
    private val az = { _: String, a: String -> a }

    @Test fun linesShowBothValues() {
        val money = PlatformTotals.Check("wolt", "earnings", PlatformTotals.WOLT_DAILY, 18.24, 17.94, false)
        assertEquals("Gün toplamı (Finances) — Wolt: 18.24 ₼ · WEEM: 17.94 ₼", PlatformTotals.line(money, tr))
        assertEquals("Gün cəmi (Finances) — Wolt: 18.24 ₼ · WEEM: 17.94 ₼", PlatformTotals.line(money, az))
        assertEquals("Teslim — Bolt: 3 · WEEM: 2", PlatformTotals.line(PlatformTotals.Check("bolt", "deliveries", PlatformTotals.BOLT_DAYS, 3.0, 2.0, false), tr))
        assertEquals("Mesafe — Wolt: 20.0 km · WEEM: —", PlatformTotals.line(PlatformTotals.Check("wolt", "distance", PlatformTotals.WOLT_STATS, 20.0, null, true), tr))
        assertEquals("Bahşiş (uygulama içi, Stats) — Wolt: 1.50 ₼ · WEEM: 1.50 ₼",
            PlatformTotals.line(PlatformTotals.Check("wolt", "tips", PlatformTotals.WOLT_STATS, 1.5, 1.5, true), tr))
    }

    @Test fun ordersWhoseFeeDiffers() {
        fun row(amount: Double, gross: Double?, platform: String = "wolt") = JSONObject().put("schemaVersion", 1).put("platform", platform)
            .put("clock", "12:40").put("restaurant", "Test Restoran").put("amount", amount).put("amount_text", "AZN $amount")
            .put("match", JSONObject().put("trip_id", "t1").put("trip_gross", gross ?: JSONObject.NULL))
        val rows = listOf(row(2.91, 2.71), row(3.10, 3.10), row(4.0, null), row(5.0, 4.5, "bolt"), JSONObject().put("schemaVersion", 1).put("platform", "wolt").put("amount", 1.0))
        val d = PlatformTotals.feeDiffs(rows, "wolt")
        assertEquals(1, d.size)
        assertEquals("12:40 · Test Restoran · Wolt: 2.91 ₼ · WEEM: 2.71 ₼", PlatformTotals.diffLine(d[0]))
        // Bolt's amount may be net: its rows are not listed as differences.
        assertTrue(PlatformTotals.feeDiffs(rows, "bolt").isEmpty())
    }

}
