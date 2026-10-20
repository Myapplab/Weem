package com.kuryelog.logic.map.route

import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `route_hours_test.dart` (3 test; A6.2a, MAP.md 4.6). Saat Bakü sabit UTC+4. Uygulama: A6.4. */
class ReverseHoursTest {
    private fun utc(s: String) = Instant.parse(s)

    @Test
    fun `Bakü saatleri telefonun saat diliminden bağımsızdır`() {
        val h = ReverseHours(11 * 60, 19 * 60)
        assertTrue(h.allows(utc("2026-10-01T07:00:00Z")))
        assertTrue(h.allows(OffsetDateTime.parse("2026-10-01T11:00:00+04:00").toInstant()))
        assertFalse(h.allows(utc("2026-10-01T06:59:59Z")))
        assertFalse(h.allows(utc("2026-10-01T15:00:00Z")))
    }

    @Test
    fun `gece yarısını geçen aralık ve bitişten önce çıkış`() {
        val h = ReverseHours(23 * 60, 7 * 60)
        assertTrue(h.allows(utc("2026-10-01T19:00:00Z")))
        assertTrue(h.allows(utc("2026-10-02T02:59:00Z")))
        assertFalse(h.allows(utc("2026-10-02T03:00:00Z")))
        assertTrue(h.allowsTrip(utc("2026-10-02T02:59:30Z"), 20.0))
        assertFalse(h.allowsTrip(utc("2026-10-02T02:59:30Z"), 40.0))
        assertFalse(h.allowsTrip(utc("2026-10-01T19:00:00Z"), 24.0 * 3600))
    }

    @Test
    fun `bozuk aralık izin açmaz`() {
        assertFalse(ReverseHours(0, 0).allows(Instant.now()))
        assertFalse(ReverseHours(-1, 60).valid)
        assertFalse(ReverseHours(0, 1440).valid)
    }
}
