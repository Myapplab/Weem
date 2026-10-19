package com.kuryelog.logic.map.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** `_reconcileStops` (MAP.md 11.10): ölçer kazanır, sipariş duruşu ölçeri siler, ±3 sn ve 40 m eşleşme; karakterizasyon. */
class StopReconcilerTest {
    private val t0 = DayFixtures.SIM_START
    private val lat = 40.4
    private val lon = 49.85

    private fun rec(startS: Int, endS: Int, kind: String, measured: Boolean, la: Double = lat) =
        StopRec(t0 + startS * 1000L, t0 + endS * 1000L, la, lon, kind, onRoad = true, measured = measured)

    private fun event(r: StopRec) = DayEvent("stop", r.endMs, r.toJsonMap())

    @Test
    fun `ölçer kaydı eşleşirse motor kaydı silinir ve olay atılır, ölçer olayı eklenir`() {
        val s = DayState()
        val raw = rec(0, 14, "stop", false)
        val measured = rec(1, 13, "light", true)
        s.stops += raw; s.stops += measured
        val out = StopReconciler.reconcile(s, listOf(event(raw)), measured)
        assertEquals(listOf(measured), s.stops)
        assertEquals(1, out.size)
        assertEquals(measured.endMs, out.single().atMs)
        assertEquals("light", out.single().data["kind"])
    }

    @Test
    fun `sipariş duruşu eşleşen ölçer kayıtlarını siler, motor olayı kalır`() {
        val s = DayState()
        val raw = rec(0, 30, "order", false)
        val measured = rec(2, 20, "light", true)
        s.stops += raw; s.stops += measured
        val ev = event(raw)
        val out = StopReconciler.reconcile(s, listOf(ev), measured)
        assertEquals(listOf(raw), s.stops)
        assertSame(ev, out.single())
    }

    @Test
    fun `40 m ötesi ya da 3 sn dışı ölçer eşleşmez, ikisi de kalır`() {
        val s = DayState()
        val raw = rec(0, 12, "stop", false)
        val far = rec(1, 11, "light", true, la = lat + 60 / 111195.0)
        val late = rec(20, 30, "light", true)
        s.stops += raw; s.stops += far; s.stops += late
        val ev = event(raw)
        val out = StopReconciler.reconcile(s, listOf(ev), null)
        assertEquals(3, s.stops.size)
        assertSame(ev, out.single())
        assertTrue("eşleşmeyen olay olduğu gibi", out.all { it.type == "stop" })
    }

    @Test
    fun `stop dışı olaylar ve ham kaydı olmayan olay aynen geçer`() {
        val s = DayState()
        val other = DayEvent("stopping", t0)
        val orphan = DayEvent("stop", t0 + 5000, mapOf("start" to t0 + 99_000))
        assertEquals(listOf(other, orphan), StopReconciler.reconcile(s, listOf(other, orphan), null))
    }
}
