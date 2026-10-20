package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vardiya bağı (MAP.md 11.2, 11.14 "Yeni"): `ShiftFollower` → gün; tek vardiya güvencesi; kalıcılık; eve dönüş ölçümü. */
class DayShiftSyncTest {
    private val t0 = DayFixtures.ms("2026-10-16T08:00:00Z")

    private fun ctl(prefs: MemoryMapPrefs = MemoryMapPrefs()) = DayController(prefs) { Lang.TR }

    @Test
    fun `vardiya açılınca gün vardiya başlangıcıyla açılır, bitince endedAt ile kapanır`() {
        val c = ctl()
        val open = ShiftState(active = true, startedAtMs = t0)
        c.onShift(open, t0 + 5_000)
        assertTrue(c.view.state.active)
        assertEquals(t0, c.view.state.startedAtMs)
        val end = ShiftState(active = false, startedAtMs = t0, endedAtMs = t0 + 3_600_000, endReason = "home_arrival")
        val step = c.onShift(end, t0 + 3_700_000)
        assertFalse(c.view.state.active)
        assertEquals("home_arrival", c.lastDay!!.endReason)
        assertEquals(t0 + 3_600_000, c.lastDay!!.endedAtMs)
        assertTrue(step.toasts.any { it.text.startsWith("Eve döndün") })
    }

    @Test
    fun `elle bitirilen gün aynı vardiyada yeniden açılmaz, işlenmiş damgalar kalıcı`() {
        val prefs = MemoryMapPrefs()
        val c = ctl(prefs)
        val open = ShiftState(active = true, startedAtMs = t0)
        c.onShift(open, t0 + 1_000)
        c.endDay(t0 + 60_000)
        c.onShift(open, t0 + 70_000)
        assertFalse(c.view.state.active)
        val again = ctl(prefs)
        again.onShift(open, t0 + 80_000)
        assertFalse(again.view.state.active)
    }

    @Test
    fun `gün durumu yeniden başlatmada shift v1 den döner`() {
        val prefs = MemoryMapPrefs()
        val c = ctl(prefs)
        c.startDay(t0)
        c.onFix(DayFix(t0 + 1_000, 40.4, 49.85, speedMps = 5.0), t0 + 1_500)
        c.onTick(t0 + 20_000)
        val back = ctl(prefs)
        assertTrue(back.view.state.active)
        assertEquals(t0, back.view.state.startedAtMs)
    }

    @Test
    fun `eski ya da çok ileri fix yok sayılır`() {
        val c = ctl()
        c.startDay(t0)
        val old = c.onFix(DayFix(t0 - 60_000, 40.4, 49.85), t0)
        assertTrue(old.events.isEmpty())
        val future = c.onFix(DayFix(t0 + 60_000, 40.4, 49.85), t0)
        assertTrue(future.events.isEmpty())
    }

    @Test
    fun `eve dönüş ölçümü birikir, sipariş açılınca biter`() {
        val c = ctl()
        c.startDay(t0)
        assertNull(c.view.state.homeReturnAtMs)
        c.startHomeReturn(t0 + 1_000)
        assertEquals(t0 + 1_000, c.view.state.homeReturnAtMs)
        val s = c.endDay(t0 + 31_000)
        assertEquals(30.0, c.lastDay!!.homeTimeS, 0.0)
        assertNull(c.lastDay!!.homeReturnAtMs)
        assertTrue(s.events.any { it.type == "day_end" })
    }

    @Test
    fun `ışık cevabı adayda öğretir, duruşa cevap yazar ve light_answer olayı verir`() {
        val c = ctl()
        c.startDay(t0)
        val cand = StopRec(t0 + 1_000, t0 + 31_000, 40.4, 49.85, "light?", onRoad = true, measured = true)
        c.view.state.stops += cand
        val step = c.reviewLight(cand.startMs, isLight = true, nowMs = t0 + 40_000)
        assertEquals("light", cand.answer)
        assertEquals(1, c.lights.confirmed)
        assertTrue(step.events.any { it.type == "light_answer" })
    }
}
