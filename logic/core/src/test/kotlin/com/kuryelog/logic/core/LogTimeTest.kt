package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CORE.md 5.2: kayıt saati Rawstats `Store.ts/day/parseTs` ile aynı biçimde (A2.3). */
class LogTimeTest {
    private val t = 1_792_228_260_123L // 2026-10-17T09:11:00.123Z

    private fun legacy(pattern: String, zone: String, at: Long): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(at))

    @Test fun timestampAndDayMatchRawstatsInEveryZone() {
        for (zone in listOf("Asia/Baku", "UTC", "America/New_York", "Asia/Kolkata", "Europe/Istanbul")) {
            for (at in listOf(t, t + 15 * 3_600_000L, 0L, 1_700_000_000_999L)) {
                assertEquals(zone, legacy("yyyy-MM-dd'T'HH:mm:ss.SSSZ", zone, at), LogTime.ts(at, ZoneId.of(zone)))
                assertEquals(zone, legacy("yyyy-MM-dd", zone, at), LogTime.day(at, ZoneId.of(zone)))
            }
        }
        assertEquals("2026-10-17T13:11:00.123+0400", LogTime.ts(t, ZoneId.of("Asia/Baku")))
    }

    @Test fun dayFolderIsThePhonesLocalDateAtTheWrite() {
        val beforeMidnightBaku = 1_792_267_199_000L // 2026-10-17T23:59:59+04:00
        assertEquals("2026-10-17", LogTime.day(beforeMidnightBaku, ZoneId.of("Asia/Baku")))
        assertEquals("2026-10-18", LogTime.day(beforeMidnightBaku + 1_000, ZoneId.of("Asia/Baku")))
        assertEquals("2026-10-17", LogTime.day(beforeMidnightBaku + 1_000, ZoneId.of("UTC")))
    }

    @Test fun parseIsTheInverseAndUnreadableIsZero() {
        for (zone in listOf("Asia/Baku", "UTC", "America/New_York")) assertEquals(t, LogTime.parseTs(LogTime.ts(t, ZoneId.of(zone))))
        assertEquals(t, LogTime.parseTs(legacy("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "Asia/Baku", t)))
        for (bad in listOf("", "x", "2026-10-17", "2026-10-17T13:11:00+0400")) assertEquals(bad, 0L, LogTime.parseTs(bad))
    }

    @Test fun dayFolderNamesMustBeRealDates() {
        assertEquals("2026-10-17", LogTime.checkedDay("2026-10-17"))
        for (bad in listOf("2026-02-30", "2026-13-01", "2026-1-01", "../2026-10-17", "2026-10-17/..", "", "20261017")) {
            assertThrows(bad, IllegalArgumentException::class.java) { LogTime.checkedDay(bad) }
        }
    }
}
