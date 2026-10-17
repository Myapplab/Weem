package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * F4.5b (Ekim planı madde 1; user 16 Oct, SORU-15): the same home end rule in both apps — within 50 m of home, below 5 km/h,
 * for at least 30 s. While an order is open the home rule never ends the shift (30 s near a customer next to home would).
 * The start rule (E6) does not change. Synthetic positions north of a made-up home.
 */
/* WEEM: `serviceTellsTheRuleWhetherAnOrderIsOpen` Rawstats'ta kaynak taramasıydı; burada `ShiftMachine` davranışı: `busy` sağlayıcısı ev kuralına geçer, A3'te `OrderEvents` bağlanır (CORE.md 6.1). */
class HomeEndRuleF45bTest {
    private val homeLat = 40.4093
    private val homeLon = 49.8671
    private fun fix(atS: Long, northM: Double, acc: Double? = 8.0, speedKmh: Double? = null) =
        HomeShift.Fix(atS * 1000, homeLat + northM / 111_195.08, homeLon, acc, speedKmh?.let { it / 3.6 })
    private fun armed() = HomeShift(homeLat, homeLon, endArmed = true)
    /** Fixes every 2 s at [northM] and [kmh] from [fromS] to [toS]; the first non-NONE decision, if any. */
    private fun stay(h: HomeShift, fromS: Long, toS: Long, northM: Double, kmh: Double, busy: Boolean = false): Pair<Long, HomeShift.Decision>? {
        var s = fromS
        while (s <= toS) {
            val d = h.observe(fix(s, northM + (s % 3), speedKmh = kmh), true, busy)
            if (d != HomeShift.Decision.NONE) return s to d
            s += 2
        }
        return null
    }

    @Test fun rule() {
        assertEquals(50.0, HomeShift.RADIUS_M, 0.0)
        assertEquals(5.0 / 3.6, HomeShift.END_SPEED_MPS, 1e-12)
        assertEquals(30_000L, HomeShift.STILL_MS)
        assertEquals(50.0, HomeShift.START_RADIUS_M, 0.0) // E6 start unchanged
        assertEquals(10.0 / 3.6, HomeShift.START_SPEED_MPS, 1e-12)
    }

    @Test fun thirtySecondsStillWithinFiftyMetresEnds() {
        val h = armed()
        val (at, d) = stay(h, 0, 120, 20.0, 1.0)!!
        assertEquals(HomeShift.Decision.END, d)
        assertTrue(at in 30..34)
        assertFalse(h.endArmed)
    }

    @Test fun twentyNineSecondsDoNotEnd() {
        assertNull(stay(armed(), 0, 28, 20.0, 1.0))
    }

    @Test fun walkingPaceIsNotStill() {
        assertNull(stay(armed(), 0, 180, 30.0, 6.0)) // 6 km/h: above 5
    }

    @Test fun trafficLightSixtyMetresAwayNeverEnds() {
        assertNull(stay(armed(), 0, 300, 60.0, 0.0))
    }

    @Test fun openOrderNeverEndsThenTheWindowRestarts() {
        val h = armed()
        assertNull(stay(h, 0, 300, 20.0, 0.0, busy = true)) // a customer next door: 5 min still, order open
        val (at, d) = stay(h, 302, 400, 20.0, 0.0)!!   // order closed: a fresh 30 s window
        assertEquals(HomeShift.Decision.END, d)
        assertTrue(at >= 332)
    }

    @get:org.junit.Rule val temp = org.junit.rules.TemporaryFolder()

    @Test fun serviceTellsTheRuleWhetherAnOrderIsOpen() {
        val r = ShiftRig(temp.root)
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        for (m in listOf(400.0, 300.0, 200.0, 100.0)) r.drive(m, 20.0, dtS = 5.0) // out once: the end is armed
        r.busy = true
        repeat(40) { r.drive(20.0, 0.0, dtS = 2.0) }
        assertTrue("an open order: 80 s at home do not end the shift", r.machine.shiftOn())
        r.busy = false
        val lines = r.gpsLines().size
        var n = 0
        while (r.machine.shiftOn() && n < 30) { r.drive(20.0, 0.0, dtS = 2.0); n++ }
        assertFalse(r.machine.shiftOn())
        assertEquals("30 s still, fixes every 2 s", 16, n)
        assertEquals("home_arrival", r.events().last().getString("reason"))
        assertEquals("home_arrival", r.prefs.map["shiftEndReason"])
        assertEquals("the ending fix is not written", lines + n - 1, r.gpsLines().size)
        assertFalse("not a manual stop: no start block", r.home.engine()!!.startBlocked)
    }
}
