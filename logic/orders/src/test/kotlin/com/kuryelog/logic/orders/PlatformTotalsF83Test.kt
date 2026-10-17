package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * F8.3 (Ekim planı madde 13): the platforms' totals go to `economics-platform-totals.jsonl` (schemaVersion 1), only numbers;
 * the newest row of a platform, period and source screen wins. The file is in the backup and the analysis lists.
 */
class PlatformTotalsF83Test {
    private val wolt = "com.wolt.courierapp"
    private val oct16 = 1792152000000L

    @Test fun recordHoldsOnlyNumbers() {
        val t = PlatformTotals.Total("wolt", "day:2026-10-16", PlatformTotals.WOLT_STATS, deliveries = 6, earnings = 21.4, earningsNoTips = 19.9, tips = 1.5, distanceKm = 23.4)
        val j = PlatformTotals.record(t, oct16)
        assertEquals(1, j.getInt("schemaVersion")); assertEquals("platform_total", j.getString("kind"))
        assertEquals("wolt", j.getString("platform")); assertEquals("day:2026-10-16", j.getString("period"))
        assertEquals(PlatformTotals.WOLT_STATS, j.getString("source_screen")); assertEquals(oct16, j.getLong("observedAtMs"))
        assertEquals(6, j.getInt("deliveries")); assertEquals(21.4, j.getDouble("earnings_azn"), 1e-9)
        assertEquals(19.9, j.getDouble("earnings_no_tips_azn"), 1e-9); assertEquals(1.5, j.getDouble("tips_azn"), 1e-9)
        assertEquals(23.4, j.getDouble("distance_km"), 1e-9)
        assertFalse(j.has("cash_azn")); assertFalse(j.has("amount_basis"))
        val keys = j.keys().asSequence().toSet()
        for (k in keys) assertFalse(k, k.contains("name") || k.contains("phone") || k.contains("balance") || k.contains("pending"))
        assertEquals(t, PlatformTotals.parse(j))
    }

    @Test fun newestRowWinsPerPlatformPeriodAndSource() {
        val a = PlatformTotals.record(PlatformTotals.Total("wolt", "day:2026-10-16", PlatformTotals.WOLT_STATS, deliveries = 5), oct16)
        val b = PlatformTotals.record(PlatformTotals.Total("wolt", "day:2026-10-16", PlatformTotals.WOLT_STATS, deliveries = 6), oct16 + 60_000)
        val c = PlatformTotals.record(PlatformTotals.Total("wolt", "day:2026-10-16", PlatformTotals.WOLT_DAILY, earnings = 19.9), oct16 - 60_000)
        val latest = PlatformTotals.latest(listOf(b.toString(), a.toString(), c.toString(), "not json"))
        assertEquals(2, latest.size)
        assertEquals(6, latest.first { it.source == PlatformTotals.WOLT_STATS }.deliveries)
        assertEquals(19.9, latest.first { it.source == PlatformTotals.WOLT_DAILY }.earnings!!, 1e-9)
    }

    @Test fun statsObservationBecomesATotal() {
        val o = CourierParser.parse(wolt, listOf("Your stats", "TODAY", "WEEK", "MONTH", "CUSTOM", "16 October", "Deliveries completed", "6",
            "Earnings (estimate)", "AZN 21.40", "Earnings without tips", "AZN 19.90", "Tips", "AZN 1.50"), emptyList(), oct16, "accessibility")
        val t = PlatformTotals.fromStats(o)!!
        assertEquals("wolt", t.platform); assertEquals(PlatformTotals.WOLT_STATS, t.source)
        assertEquals(o.statsPeriod, t.period); assertEquals(6, t.deliveries); assertEquals(1.5, t.tips!!, 1e-9)
        // Without a date header the period is not known: nothing is recorded.
        assertNull(PlatformTotals.fromStats(CourierParser.parse(wolt, listOf("Your stats", "Deliveries completed", "6"), emptyList(), oct16, "accessibility")))
    }

    @Test fun dayPeriodsGoToTheirDayFolder() {
        assertEquals("2026-10-11", PlatformTotals.dayOf("day:2026-10-11"))
        assertNull(PlatformTotals.dayOf("week:2026-10-11..2026-10-16")); assertNull(PlatformTotals.dayOf("month:2026-10"))
    }


    // A4.3: Rawstats taraması (A3.3'te yazılmadı); `tools/analysis` = Rawstats @ 6388c04.
    @Test fun fileIsBackedUpAndExported() {
        assertEquals("economics-platform-totals.jsonl", PlatformTotals.FILE)
        assertTrue(PlatformTotals.FILE in BackupFiles.logNames)
        assertEquals(AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("2026-10-16/" + PlatformTotals.FILE))
        val py = RepoSource.text("tools/analysis/kl_export.py")
        assertTrue(py.contains("\"${PlatformTotals.FILE}\""))
    }
}
