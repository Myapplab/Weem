package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.map.MemoryMapPrefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vardiya bağı (MAP.md 11.2, 11.14): `ShiftFollower` → gün, `at` sıkıştırma, işlenmiş damgalar, yeni vardiyada eski gün kapanır; kalıcılık. Karakterizasyon. */
class DayShiftSyncTest {
    private val t0 = DayFixtures.ms("2026-10-05T08:00:00Z")
    private fun ctl(prefs: MemoryMapPrefs = MemoryMapPrefs()) = DayController(prefs) { Lang.TR }
    private fun shift(active: Boolean, start: Long?, end: Long? = null, reason: String? = null) = ShiftState(active, startedAtMs = start, endedAtMs = end, endReason = reason)

    @Test
    fun `açık vardiya günü başlatır, gün kimliği vardiyanın başlangıcından`() {
        val c = ctl()
        val step = c.onShift(shift(true, t0), t0 + 5_000)
        assertEquals(listOf("day_start"), step.events.map { it.type })
        assertEquals(t0, c.view.state.startedAtMs)
        assertEquals("yolgo-$t0", c.view.state.dayId)
        assertTrue(c.onShift(shift(true, t0), t0 + 10_000).events.isEmpty())
    }

    @Test
    fun `başlangıç gelecekteyse şimdiye çekilir, okuma yokken hiçbir şey olmaz`() {
        val c = ctl()
        assertTrue(c.onShift(null, t0).events.isEmpty())
        assertFalse(c.view.state.active)
        c.onShift(shift(true, t0 + 60_000), t0)
        assertEquals(t0, c.view.state.startedAtMs)
    }

    @Test
    fun `vardiya bitince gün bitiş anıyla kapanır ve neden taşınır, at gün başı ile şimdi arasına sıkıştırılır`() {
        val c = ctl()
        c.onShift(shift(true, t0), t0)
        val step = c.onShift(shift(false, t0, end = t0 + 3_600_000, reason = "home_arrival"), t0 + 3_700_000)
        assertEquals(listOf("day_end"), step.events.map { it.type })
        assertEquals("home_arrival", c.lastDay!!.endReason)
        assertEquals(t0 + 3_600_000, c.lastDay!!.endedAtMs)
        assertFalse(c.view.state.active)
        val c2 = ctl()
        c2.onShift(shift(true, t0), t0)
        c2.onShift(shift(false, t0, end = t0 + 99_000), t0 + 10_000)
        assertEquals("gelecekteki bitiş şimdiye çekilir", t0 + 10_000, c2.lastDay!!.endedAtMs)
        val c3 = ctl()
        c3.onShift(shift(true, t0), t0)
        assertTrue("gün başından önceki bitiş (başka vardiyanın) günü kapatmaz", c3.onShift(shift(false, t0, end = t0 - 1000), t0 + 10_000).events.isEmpty())
        assertTrue(c3.view.state.active)
    }

    @Test
    fun `elle bitirilen gün aynı vardiyada yeniden açılmaz, kapanmış vardiyaya geç başlatma günü açmaz`() {
        val prefs = MemoryMapPrefs()
        val c = ctl(prefs)
        c.onShift(shift(true, t0), t0)
        c.endDay(t0 + 1000, t0 + 1000)
        assertTrue(c.onShift(shift(true, t0), t0 + 2000).events.isEmpty())
        assertFalse(c.view.state.active)
        assertTrue(ctl(prefs).onShift(shift(true, t0), t0 + 3000).events.isEmpty())
        val closed = ctl()
        assertTrue(closed.onShift(shift(false, t0, end = t0 + 500), t0 + 2000).events.isEmpty())
    }

    @Test
    fun `yeni vardiyada eski gün yeni başlangıçla kapanır, yeni gün yeni başlangıçla açılır`() {
        val c = ctl()
        c.onShift(shift(true, t0), t0)
        val t1 = t0 + 10 * 3_600_000L
        val step = c.onShift(shift(true, t1), t1 + 1000)
        assertEquals(listOf("day_end", "day_start"), step.events.map { it.type })
        assertEquals(t0, c.lastDay!!.startedAtMs)
        assertEquals(t1, c.lastDay!!.endedAtMs)
        assertEquals("manual", c.lastDay!!.endReason)
        assertEquals(t1, c.view.state.startedAtMs)
        assertTrue(c.view.state.active)
    }

    @Test
    fun `gün kalıcıdır shift v1 ve gün bitince shift last, işlenmiş damgalar day shift handled v1`() {
        val prefs = MemoryMapPrefs()
        val c = ctl(prefs)
        c.onShift(shift(true, t0), t0)
        assertEquals(t0, JSONObject(prefs.getString(DayController.STATE_KEY)!!).getLong("start"))
        assertEquals(t0, JSONObject(prefs.getString(DayController.HANDLED_KEY)!!).getLong("start"))
        assertTrue(ctl(prefs).view.state.active)
        c.onShift(shift(false, t0, end = t0 + 1000), t0 + 2000)
        assertTrue(prefs.getString(DayController.STATE_KEY).isNullOrEmpty())
        assertEquals(t0, JSONObject(prefs.getString(DayController.LAST_KEY)!!).getLong("start"))
        assertEquals(t0 + 1000, JSONObject(prefs.getString(DayController.HANDLED_KEY)!!).getLong("end"))
        val again = ctl(prefs)
        assertFalse(again.view.state.active)
        assertEquals(t0, again.lastDay!!.startedAtMs)
        assertNull(again.view.state.startedAtMs)
    }
}
