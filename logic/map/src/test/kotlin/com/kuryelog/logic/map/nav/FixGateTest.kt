package com.kuryelog.logic.map.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `native_fast_fix_test.dart` (2; A6.2b, MAP.md 7.6, 7.15): hızlı konum okuması günlük satırı istemez ve aynı fix'i tekrar yayınlamaz;
 * arka planda en çok saniyede bir okunur. WEEM'de yerel telemetri köprüsü yok: kural `:map` konum kaynağı birleştiricisinin saf [FixGate]'ine taşındı.
 */
class FixGateTest {
    @Test
    fun `hızlı konum okuması aynı fix i tekrar yayınlamaz`() {
        val gate = FixGate()
        val got = ArrayList<Long>()
        var reads = 0
        fun poll(utc: Long, nowMs: Long) {
            if (gate.mayRead(nowMs, foreground = true)) {
                reads++
                if (gate.accept(utc)) got += utc
            }
        }
        poll(1000, 0)
        poll(1500, 10)
        poll(1500, 20)
        assertEquals(listOf(1000L, 1500L), got)
        assertEquals("latest 3 çağrı → 2 yayın", 3, reads)
    }

    @Test
    fun `1_7_1 arka planda konum en çok saniyede bir okunur`() {
        val gate = FixGate()
        var reads = 0
        fun poll(nowMs: Long, foreground: Boolean) {
            if (gate.mayRead(nowMs, foreground)) reads++
        }
        poll(5_000, false); poll(5_000, false); poll(5_000, false)
        assertEquals(1, reads)
        poll(5_500, true) // öne dönünce hemen
        assertEquals(2, reads)
        poll(6_600, false)
        assertEquals(3, reads)
    }
}
