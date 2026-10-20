package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.day.StopRec
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** Gün özeti, rapor dosyası, geçmiş, sekme etiketleri, süre biçimi ve ışık istatistiği (MAP.md 12.6–12.7, 12.13 "Yeni"; değerler 12. bölümden). */
class DayStatsTest {
    private val zone = ZoneOffset.ofHours(4)
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private val t0 = ms("2026-10-02T06:00:00Z") // 10:00 Bakü

    private fun stop(kind: String, durS: Int, answer: String? = null, at: Long = t0) = StopRec(at, at + durS * 1000L, 40.4, 49.8, kind, true, answer = answer)

    private fun day(): DayState = DayState().also { d ->
        d.active = false
        d.startedAtMs = t0
        d.endedAtMs = t0 + 7_200_000
        d.distM = 12_345.0
        d.movingS = 3_000.4
        d.stops += stop("light", 40); d.stops += stop("light", 20); d.stops += stop("light?", 150); d.stops += stop("stop", 10)
        d.homeReturnAtMs = t0 + 7_000_000
        d.homeStartM = 12_000.0
        d.homeDistM = 500.0
        d.homeTimeS = 60.0
        d.orders += OrderRec(1, t0).also { it.restArrivedAtMs = t0 + 100_000; it.restLeftAtMs = t0 + 160_000; it.custArrivedAtMs = t0 + 400_000; it.deliveredAtMs = t0 + 430_000 }
        d.orders += OrderRec(2, t0 + 500_000).also { it.cancelled = true }
    }

    @Test
    fun `özet alanları ve süren eve dönüş`() {
        val s = DayStats.summarize(day(), t0 + 99_999_999)
        assertEquals(7200L, s.getLong("totalS"))
        assertEquals(3000L, s.getLong("movingS"))
        assertEquals(12.35, s.getDouble("km"), 0.0)
        assertEquals((500.0 + 345.0) / 1000, s.getDouble("homeKm"), 0.01)
        assertEquals(60.0 + 200.0, s.getDouble("homeS"), 0.0)
        assertEquals(4, s.getInt("stops")); assertEquals(2, s.getInt("lights")); assertEquals(60, s.getInt("lightWaitS")); assertEquals(1, s.getInt("lightCandidates"))
        assertEquals(2, s.getInt("orders")); assertEquals(1, s.getInt("delivered")); assertEquals(60, s.getInt("restWaitS")); assertEquals(30, s.getInt("custWaitS"))
    }

    @Test
    fun `başlangıçsız gün 0 sn, rapor adı yerel saat`() {
        assertEquals(0L, DayStats.summarize(DayState(), t0).getLong("totalS"))
        assertEquals("2026-10-02_1000.gun.json", DayReportFile.name(t0, t0, zone))
        assertEquals("2026-10-03_0030.gun.json", DayReportFile.name(null, ms("2026-10-02T20:30:00Z"), zone))
    }

    @Test
    fun `rapor kodeği gün durumunu, özeti ve öğrenilen ışıkları taşır`() {
        val text = DayReportFile.encode(day(), t0 + 8_000_000, JSONArray())
        assertTrue(text.contains("\n \"summary\"")) // bir boşluk girinti
        val back = DayReportFile.decode(text)
        assertEquals(t0, back.startedAtMs); assertEquals(12_345.0, back.distM, 0.0); assertEquals(2, back.orders.size)
    }

    @Test
    fun `geçmiş başlangıca göre tekil ve azalan, ilk gelen kazanır, en çok 60 dosya`() {
        fun d(start: Long?, tag: Double = 0.0) = DayState().also { it.startedAtMs = start; it.distM = tag }
        val active = d(300).also { it.active = true; it.distM = 1.0 }
        val files = listOf(d(300, 2.0), d(200, 3.0), d(null), d(100, 4.0))
        val last = d(200, 5.0)
        val merged = DayHistory.merge(active, files, last)
        assertEquals(listOf(300L, 200L, 100L), merged.map { it.startedAtMs })
        assertEquals(1.0, merged[0].distM, 0.0); assertEquals(3.0, merged[1].distM, 0.0)
        val many = (1L..70L).map { d(it) }
        assertEquals(60, DayHistory.merge(null, many, null).size)
        assertEquals(listOf(5L), DayHistory.merge(null, emptyList(), d(5)).map { it.startedAtMs })
    }

    @Test
    fun `sekme etiketleri bugün, ay adları ve sıra`() {
        val now = t0 + 3_600_000
        val old = DayState().also { it.startedAtMs = ms("2026-09-30T06:30:00Z") }
        assertEquals("#3", StatsTabs.label(DayState(), 2, now, zone, Lang.TR))
        assertEquals("Bugün 10:00", StatsTabs.label(day(), 0, now, zone, Lang.TR))
        assertEquals("Bu gün 10:00", StatsTabs.label(day(), 0, now, zone, Lang.AZ))
        assertEquals("30 Eyl 10:30", StatsTabs.label(old, 1, now, zone, Lang.TR))
        assertEquals("30 sen 10:30", StatsTabs.label(old, 1, now, zone, Lang.AZ))
        assertEquals(1, StatsTabs.focusIndex(listOf(old, day()), t0))
        assertEquals(0, StatsTabs.focusIndex(listOf(old, day()), 12345))
    }

    @Test
    fun `süre biçimi ışık istatistiği ve sokak etiketi`() {
        assertEquals("45 sn", DurationText.fmt(45, Lang.TR)); assertEquals("45 san", DurationText.fmt(45, Lang.AZ))
        assertEquals("5 dk", DurationText.fmt(300, Lang.TR)); assertEquals("1 sa 1 dk", DurationText.fmt(3660, Lang.TR)); assertEquals("1 saat 1 dəq", DurationText.fmt(3660, Lang.AZ))
        val ls = LightStats.of(day())
        assertEquals(2, ls.count); assertEquals(60, ls.totalS); assertEquals(30, ls.avgS); assertEquals(40, ls.maxS)
        assertEquals(0, LightStats.of(DayState()).avgS)
        val s = stop("light", 10)
        assertEquals("Nizami", StreetLabel.of(s) { _, _, m -> assertEquals(40.0, m, 0.0); "Nizami" })
        assertEquals("40.4000, 49.8000", StreetLabel.of(s) { _, _, _ -> "" })
        assertFalse(StreetLabel.of(s) { _, _, _ -> null }.isEmpty())
    }

    @Test
    fun `gün silme sırası açık günü kapatır ve yalnız harita verisini siler`() {
        val active = day().also { it.active = true }
        val report = DayState().also { it.startedAtMs = t0 - 1000 }
        val plan = DayDeletionPlan.plan("2026-10-02", active, listOf(report), zone)
        assertEquals(DeletionStep.EndActiveDay("yolgo-$t0"), plan[0])
        assertEquals(DeletionStep.FlushLogs, plan[1])
        assertEquals(DeletionStep.DeleteContributions("2026-10-02"), plan[2])
        assertEquals(DeletionStep.ForgetDay("2026-10-02", setOf("yolgo-${t0 - 1000}")), plan[3])
        assertEquals(DeletionStep.DeleteLogDay("2026-10-02"), plan.last())
        val other = DayDeletionPlan.plan("2026-10-01", active, emptyList(), zone)
        assertTrue(other.none { it is DeletionStep.EndActiveDay })
        assertNull(DayDeletionPlan.reportId(DayState()))
    }
}
