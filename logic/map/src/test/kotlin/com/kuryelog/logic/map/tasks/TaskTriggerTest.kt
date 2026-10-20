package com.kuryelog.logic.map.tasks

import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * YolGo `assistant_test.dart` görev tetikleyici (Y20) 5 test (A6.2b, MAP.md 13.14, 13.18): görev saati/günü ve 1 km sınırı · kapatmak tamamlamaz, sonraki teslimat tekrar
 * tetikler · yeniden giriş, histerezis, aktif sipariş · eski ya da hatalı GPS bildirim üretmez · kapatma ve tamamlanma yeniden açılışta okunur.
 * `DateTime(2026,10,2,14)` yerel → sabit ms, bölge Bakü.
 */
class TaskTriggerTest {
    private val zone = ZoneOffset.ofHours(4)
    private val now = Instant.parse("2026-10-02T10:00:00Z").toEpochMilli() // 14:00 Bakü
    private val target = GeoPoint(40.4, 49.8)
    private fun task(due: Long = now) = DailyTask("a", "Banka", "Şube", target, due)

    private fun check(
        e: TaskTrigger,
        metres: Double = 500.0,
        delivered: Boolean = true,
        active: Boolean = false,
        accuracy: Double = 5.0,
        at: Long = now,
        clock: Long = now,
        item: DailyTask = task(),
    ): List<DailyTask> = e.evaluate(
        listOf(item), GeoPoint(target.lat + metres / 111195, target.lon), nowMs = clock, fixAtMs = at, accuracy = accuracy,
        deliveryFinished = delivered, activeOrder = active,
    )

    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun `görev saati günü ve 1 km sınırı`() {
        assertEquals(1, check(TaskTrigger(zone), metres = 999.0).size)
        assertTrue(check(TaskTrigger(zone), metres = 1001.0).isEmpty())
        assertTrue(check(TaskTrigger(zone), item = task(due = now + minute)).isEmpty())
        assertTrue(check(TaskTrigger(zone), clock = now + day).isEmpty())
        assertTrue(check(TaskTrigger(zone), item = task().complete(now)).isEmpty())
    }

    @Test
    fun `kapatmak tamamlamaz, sonraki teslimat aynı bölgede tekrar tetikler`() {
        val e = TaskTrigger(zone)
        assertEquals(1, check(e).size)
        assertTrue(check(e).isEmpty())
        e.dismiss("a", now)
        assertEquals(null, task().completedAtMs)
        assertEquals(1, check(e).size)
    }

    @Test
    fun `bölgeye tekrar giriş, histerezis ve aktif sipariş`() {
        val e = TaskTrigger(zone)
        check(e); e.dismiss("a", now)
        check(e, metres = 1100.0, delivered = false)
        assertTrue(check(e, delivered = false).isEmpty())
        check(e, metres = 1300.0, delivered = false)
        assertEquals(1, check(e, delivered = false).size)
        e.dismiss("a", now); check(e, metres = 1300.0, delivered = false)
        assertTrue(check(e, delivered = false, active = true).isEmpty())
        assertEquals(1, check(e, delivered = true, active = true).size)
    }

    @Test
    fun `eski veya hatalı GPS görev bildirimi üretmez`() {
        assertTrue(check(TaskTrigger(zone), accuracy = 100.0).isEmpty())
        assertTrue(check(TaskTrigger(zone), at = now - 40_000).isEmpty())
        assertTrue(check(TaskTrigger(zone), at = now + minute).isEmpty())
    }

    @Test
    fun `kapatma ve görev tamamlanması yeniden açılışta okunur`() {
        val restored = DailyTask.fromJson(task().complete(now).toJson())
        assertFalse(restored.eligible(now, zone))
        val e = TaskTrigger(zone).also { it.restoreDismissed("a", now) }
        check(e, metres = 1300.0, delivered = false)
        assertEquals(1, check(e, delivered = false).size)
    }
}
