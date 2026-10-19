package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.utc
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `test/route_hours_test.dart` (3 test); `MAP.md` 4.6. */
class ReverseHoursTest {
    @Test
    fun `Bakü saatleri telefonun saat diliminden bağımsızdır`() {
        val h = ReverseHours(11 * 60, 19 * 60)
        assertTrue(h.allows(Instant.parse("2026-10-01T07:00:00Z").toEpochMilli()))
        assertTrue(h.allows(Instant.parse("2026-10-01T11:00:00+04:00").toEpochMilli()))
        assertFalse(h.allows(utc(2026, 10, 1, 6, 59, 59)))
        assertFalse(h.allows(utc(2026, 10, 1, 15)))
    }

    @Test
    fun `gece yarısını geçen aralık ve bitişten önce çıkış`() {
        val h = ReverseHours(23 * 60, 7 * 60)
        assertTrue(h.allows(utc(2026, 10, 1, 19)))
        assertTrue(h.allows(utc(2026, 10, 2, 2, 59)))
        assertFalse(h.allows(utc(2026, 10, 2, 3)))
        assertTrue(h.allowsTrip(utc(2026, 10, 2, 2, 59, 30), 20.0))
        assertFalse(h.allowsTrip(utc(2026, 10, 2, 2, 59, 30), 40.0))
        assertFalse(h.allowsTrip(utc(2026, 10, 1, 19), 24.0 * 3600))
    }

    @Test
    fun `bozuk aralık izin açmaz`() {
        assertFalse(ReverseHours(0, 0).allows(System.currentTimeMillis()))
        assertFalse(ReverseHours(-1, 60).valid)
        assertFalse(ReverseHours(0, 1440).valid)
    }
}
