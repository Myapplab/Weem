package com.kuryelog.logic.map.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ölçer ve motor kayıtlarının birleşmesi (MAP.md 11.10): ölçer kazanır, sipariş duruşu ölçeri siler (±3 sn / 40 m). */
class StopReconcilerTest {
    private val t0 = DayFixtures.ms("2026-10-16T12:00:00Z")

    private fun rec(startS: Int, endS: Int, kind: String, measured: Boolean = false, lat: Double = 40.4) =
        StopRec(t0 + startS * 1000L, t0 + endS * 1000L, lat, 49.85, kind, onRoad = true, measured = measured)

    private fun ev(r: StopRec) = DayEvent("stop", r.endMs, mapOf("stop" to r.toJson()))

    @Test
    fun `ölçer kazanır motor olayı atılır`() {
        val s = DayState()
        val raw = rec(0, 30, "light").also { s.stops += it }
        val m = rec(1, 28, "light", measured = true).also { s.stops += it }
        val out = StopReconciler.reconcile(s, listOf(ev(raw)), m)
        assertEquals(listOf(m), s.stops)
        assertEquals(1, out.size)
        assertTrue(out.single().data["stop"].toString().contains("measured"))
    }

    @Test
    fun `sipariş duruşu ölçeri siler olay kalır`() {
        val s = DayState()
        val raw = rec(0, 30, "order").also { s.stops += it }
        val m = rec(1, 28, "light", measured = true).also { s.stops += it }
        val out = StopReconciler.reconcile(s, listOf(ev(raw)), null)
        assertEquals(listOf(raw), s.stops)
        assertEquals(1, out.size)
        assertTrue(!s.stops.contains(m))
    }

    @Test
    fun `eşleşme yoksa ikisi de kalır, uzak nokta eşleşmez`() {
        val s = DayState()
        val raw = rec(0, 30, "stop").also { s.stops += it }
        val m = rec(1, 28, "light", measured = true, lat = 40.4009).also { s.stops += it }
        val out = StopReconciler.reconcile(s, listOf(ev(raw)), m)
        assertEquals(2, s.stops.size)
        assertEquals(2, out.size)
    }
}
