package com.kuryelog.logic.map.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** YolGo `reminders_test.dart` zamanlama 4 test (A6.2b, MAP.md 13.15, 13.18): aralık, gün kapalıyken susar, saatli, erteleme. `DateTime(2026,10,1)` yerel → bölge Bakü. */
class ReminderDueTest {
    private val zone = ZoneOffset.ofHours(4)
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 10, 1, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `aralık gün başladıktan N dk sonra, sonra her N dk`() {
        val item = ReminderItem(ReminderKind.WATER, enabled = true, intervalMin = 45, sinceMs = at(8, 0))
        val start = at(11, 0)
        fun due(now: Long, last: Long? = null) = ReminderDue.due(item, now, zone, lastMs = last, shiftStartMs = start, shiftActive = true)
        assertFalse(due(at(11, 44)))
        assertTrue(due(at(11, 45)))
        assertFalse(due(at(12, 20), last = at(11, 45)))
        assertTrue(due(at(12, 30), last = at(11, 45)))
    }

    @Test
    fun `sadece gün içinde gün başlamadıysa susar, kapalıysa hiç`() {
        val item = ReminderItem(ReminderKind.CHECK, enabled = true, intervalMin = 10)
        assertFalse(ReminderDue.due(item, at(12, 0), zone, shiftActive = false))
        assertFalse(ReminderDue.due(item.copy(onlyShift = false), at(12, 0), zone, shiftActive = false))
        assertFalse(ReminderDue.due(item.copy(enabled = false, onlyShift = false), at(12, 0), zone, shiftActive = false))
    }

    @Test
    fun `saatte yalnız belirlenen saatte bir kez, 3 dk geçtiyse atlanır`() {
        val item = ReminderItem(ReminderKind.FOOD, enabled = true, useTimes = true, times = listOf(15 * 60 + 15, 21 * 60), onlyShift = false)
        fun due(now: Long, last: Long? = null) = ReminderDue.due(item, now, zone, lastMs = last, shiftActive = true)
        assertFalse(due(at(15, 14)))
        assertTrue(due(at(15, 15)))
        assertFalse("bugün gösterildi", due(at(15, 20), last = at(15, 15)))
        assertTrue("20 sn'lik yoklama gecikmesi", due(at(15, 17)))
        assertFalse("3 dk geçmiş: belirlenen zaman değil, atla", due(at(15, 18)))
        assertFalse(due(at(15, 50)))
        assertTrue("ikinci saat", due(at(21, 1), last = at(15, 15)))
    }

    @Test
    fun `ertele süre dolunca tekrar`() {
        val item = ReminderItem(ReminderKind.WATER, enabled = true, intervalMin = 45, onlyShift = false)
        val snooze = at(13, 10)
        assertFalse(ReminderDue.due(item, at(13, 5), zone, shiftActive = true, snoozeUntilMs = snooze))
        assertTrue(ReminderDue.due(item, at(13, 10), zone, shiftActive = true, snoozeUntilMs = snooze))
    }
}
