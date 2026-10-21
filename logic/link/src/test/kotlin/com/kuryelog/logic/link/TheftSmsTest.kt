package com.kuryelog.logic.link

import com.kuryelog.logic.link.TheftGuard.Fix
import com.kuryelog.logic.link.TheftGuard.State
import com.kuryelog.logic.link.TheftGuard.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** LINK.md 5.3: SMS yalnız alarmda ve bağlantı kopukken; konum değişimine göre aralık; tavan; kapanış SMS'i. */
class TheftSmsTest {
    private val t0 = 1_760_000_000_000L
    private val baku = ZoneId.of("Asia/Baku")
    private val sms = TheftSms(baku)
    private val here = Fix(40.409600, 49.867100, 8.0, t0)
    private val alarm = Status(State.ALARM, TheftGuard.ArmedBy.STAGE, t0, TheftGuard.Trigger.SPEED, here)

    @Test fun noSmsWhileLinkedOrBeforeFifteenSecondsDown() {
        assertNull(sms.due(t0, alarm, linked = true))
        assertNull(sms.due(t0 + 60_000, alarm, linked = true))
        assertNull(sms.due(t0 + 61_000, alarm, linked = false))
        assertNull(sms.due(t0 + 75_000, alarm, linked = false))
        val first = sms.due(t0 + 76_000, alarm, linked = false)!!
        assertEquals("WEEM ALARM: scooter hareket etti. Konum: https://maps.google.com/?q=40.409600,49.867100 (±8 m, 12:53)", first)
    }

    @Test fun notInWarningOrOff() {
        assertNull(sms.due(t0 + 100_000, alarm.copy(state = State.WARNING), linked = false))
        assertNull(sms.due(t0 + 200_000, Status(), linked = false))
    }

    @Test fun intervalDependsOnMovement() {
        val down = t0 + 1_000
        sms.due(down, alarm, false)
        assertTrue(sms.due(down + 15_000, alarm, false) != null)
        // Konum değişmedi: 10 dk.
        assertNull(sms.due(down + 15_000 + 9 * 60_000, alarm, false))
        assertTrue(sms.due(down + 15_000 + 10 * 60_000, alarm, false) != null)
        // ≥ 50 m kaydı: 3 dk.
        val moved = alarm.copy(fix = here.copy(lat = 40.4102, atMs = t0 + 700_000))
        val at = down + 15_000 + 10 * 60_000
        assertNull(sms.due(at + 2 * 60_000, moved, false))
        assertTrue(sms.due(at + 3 * 60_000, moved, false)!!.contains("40.410200"))
        assertEquals(3, sms.sentThisAlarm)
    }

    @Test fun capAndClosingMessage() {
        var now = t0
        sms.due(now, alarm, false)
        var n = 0
        repeat(400) { now += 60_000; if (sms.due(now, alarm.copy(fix = here.copy(lat = here.lat + it * 0.001)), false) != null) n++ }
        assertEquals(20, n)
        assertEquals(TheftSms.CLOSED, sms.due(now + 1_000, Status(), false))
        assertNull(sms.due(now + 2_000, Status(), false))
    }

    @Test fun noClosingMessageIfNothingWasSentAndNoLocationText() {
        sms.due(t0, alarm, true)
        assertNull(sms.due(t0 + 1_000, Status(), true))
        val noFix = alarm.copy(fix = null)
        sms.due(t0 + 2_000, noFix, false)
        assertEquals("WEEM ALARM: scooter hareket etti. Konum yok.", sms.due(t0 + 17_000, noFix, false))
    }

    @Test fun newAlarmResetsCount() {
        sms.due(t0, alarm, false)
        sms.due(t0 + 15_000, alarm, false)
        assertEquals(1, sms.sentThisAlarm)
        val second = alarm.copy(sinceMs = t0 + 100_000)
        assertTrue(sms.due(t0 + 100_000, second, false) != null)
        assertEquals(1, sms.sentThisAlarm)
    }
}
