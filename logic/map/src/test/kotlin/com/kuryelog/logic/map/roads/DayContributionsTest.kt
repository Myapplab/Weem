package com.kuryelog.logic.map.roads

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `day_contributions_test.dart` ilk iki test (A6.2a, MAP.md 9.11); üçüncüsü (`SurfacePassTracker`) Y13 ile (sonra).
 * Kova `yolgo-<ms>`; Dart `bakuDay` → test saat dilimi `+04:00`. Uygulama: A6.11.
 */
class DayContributionsTest {
    private val baku = ZoneOffset.ofHours(4)
    private fun ms(y: Int, mo: Int, d: Int, h: Int) = OffsetDateTime.of(y, mo, d, h, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `seçili gün yalnız kendi otomatik farkını siler, taban elle ve diğer günler kalır`() {
        val k = RoadKnowledge(
            ways = hashMapOf(1L to WayStats().apply { m = 80.0; stops = 1.0 }),
            ww = hashMapOf(1L to WwStats().apply { passes = 2; problems = 1.0 }),
            ratings = hashMapOf(1L to 30),
            names = hashMapOf(1L to "Eski yol"),
        )
        val ledger = RoadDayContributions(k, zone = baku)
        val a = "yolgo-${ms(2026, 10, 2, 21)}"
        val b = "yolgo-${ms(2026, 10, 3, 21)}"
        k.stats(1).m += 120.0; k.wwStats(1).passes++; ledger.capture(k, a)
        k.stats(1).m += 50.0; k.wwStats(1).problems += 0.5; ledger.capture(k, b)
        k.wwStats(1).passes++; k.wwStats(1).problems++; ledger.manual(k)
        val persisted = ledger.encode()
        val reload = RoadDayContributions(k, stored = persisted, zone = baku)
        assertEquals(setOf(a), reload.deleteDate(k, "2026-10-03"))
        assertEquals(130.0, k.ways[1L]!!.m, 0.0); assertEquals(1.0, k.ways[1L]!!.stops, 0.0)
        assertEquals(3, k.ww[1L]!!.passes); assertEquals(2.5, k.ww[1L]!!.problems, 0.0)
        assertEquals(30, k.ratings[1L]); assertEquals("Eski yol", k.names[1L])
        assertTrue(reload.deleteDate(k, "2026-10-03").isEmpty())
        val sameDateNewDay = "yolgo-${ms(2026, 10, 3, 10)}"
        k.stats(1).m += 25.0; reload.capture(k, sameDateNewDay)
        assertEquals(setOf(sameDateNewDay), reload.deleteDate(k, "2026-10-03")); assertEquals(130.0, k.ways[1L]!!.m, 0.0)
    }

    @Test
    fun `elle sıfırlama yeniden yüklemede kalır`() {
        val k = RoadKnowledge()
        val ledger = RoadDayContributions(k, zone = baku)
        k.stats(1).m = 40.0; ledger.capture(k, "yolgo-1"); ledger.resetWay(1); k.ways.remove(1L)
        RoadDayContributions(k, stored = ledger.encode(), zone = baku)
        assertTrue(k.ways.isEmpty())
    }
}
