package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * YolGo `home_day_controller_test` 1–3 (CORE.md 6.2), WEEM'in tek kuralıyla uçtan uca: ev izleyici (`HomeWatchCore`) evden çıkışı
 * görür → vardiya servisi (`ShiftMachine`) `HOME_START` ile açar → eve dönüşte 30 sn sonra kapanır. 4 (feed aşamaları) ve 5 (gün
 * istatistiği nedeni) `:map`'e (A6). Ayar kısımları çevrilmez (WEEM'de otomatik aç/kapa ayarı yok).
 */
class HomeDayControllerTest {
    @get:Rule val temp = TemporaryFolder()

    /** Evde düşük güç; 60 m'de 20 km/s iki GPS fix'i (≤20 m) çıkışı başlatır. */
    private fun leave(r: ShiftRig) {
        r.watcher.recheck(r.mono)
        assertEquals(HomeWatchPlan.Mode.LOW, r.watcher.mode)
        r.watch(10.0, null, acc = 15f, dtS = 30.0)
        r.watch(60.0, 20.0, acc = 8f, dtS = 5.0)
        assertEquals("the fast fix switches to GPS", HomeWatchPlan.Mode.HIGH, r.watcher.mode)
        r.watch(70.0, 20.0, acc = 8f, dtS = 2.0)
    }

    @Test fun leavingHomeStartsTheDayAndComingBackEndsItAfter30s() {
        val r = ShiftRig(temp.root)
        leave(r)
        assertTrue(r.machine.shiftOn())
        assertEquals("home_departure", r.events().single().getString("reason"))
        assertFalse("the watcher stops for the shift", r.watcher.listening)
        // Out and back at ≤20 m/s (faster steps would be dropped as GPS jumps, CORE.md 1.6).
        for (m in listOf(300.0, 800.0, 600.0, 400.0, 200.0, 40.0)) r.drive(m, 25.0, dtS = 30.0)
        var s = 0
        while (r.machine.shiftOn() && s < 120) { r.drive(15.0, 0.0, dtS = 1.0); s++ }
        assertEquals("home_arrival", r.events().last().getString("reason"))
        assertEquals("first fix at home + 30 s", 31, s)
        assertTrue("the watcher listens again", r.watcher.listening)
    }

    @Test fun aManualStopAtHomeDoesNotRestartAtHome() {
        val r = ShiftRig(temp.root)
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        assertEquals("manual", r.events().single().getString("reason"))
        r.machine.onStartCommand(ShiftMachine.ACTION_STOP, null)
        assertEquals("manual", r.events().last().getString("reason"))
        // Still at home, fast GPS fixes 60–80 m away (a car passing, GPS drift): blocked until seen at home.
        r.calls.clear()
        r.watch(60.0, 20.0, dtS = 5.0); r.watch(70.0, 20.0, dtS = 2.0); r.watch(80.0, 20.0, dtS = 2.0)
        assertFalse(r.machine.shiftOn())
        assertFalse(r.calls.contains("watch:startShift"))
        // Seen at home once, then a real departure starts it.
        r.watch(10.0, null, acc = 10f, dtS = 10.0)
        r.watch(60.0, 20.0, dtS = 5.0); r.watch(70.0, 20.0, dtS = 2.0)
        assertTrue(r.machine.shiftOn())
        assertEquals("home_departure", r.events().last().getString("reason"))
    }

    @Test fun noHomeNoAutomaticStart() {
        val r = ShiftRig(temp.root, homeAt = null)
        r.watcher.recheck(r.mono)
        assertFalse("nothing to watch without a home", r.watcher.listening)
        r.watch(60.0, 20.0, dtS = 5.0); r.watch(70.0, 20.0, dtS = 2.0); r.watch(500.0, 30.0, dtS = 60.0)
        assertFalse(r.machine.shiftOn())
        assertTrue(r.events().isEmpty())
        // A manual shift still works and never ends by itself.
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        repeat(60) { r.drive(5.0, 0.0) }
        assertTrue(r.machine.shiftOn())
    }

    @Test fun aRefusedBackgroundStartAsksForATap() {
        val r = ShiftRig(temp.root)
        r.refuseWatcherStart = IllegalStateException("ForegroundServiceStartNotAllowedException")
        leave(r)
        assertFalse(r.machine.shiftOn())
        val e = r.events().single()
        assertEquals("home_auto_start_failed", e.getString("type"))
        assertEquals("IllegalStateException", e.getString("err"))
        assertTrue(r.calls.contains("notify:5101"))
        // The tap sends the same HOME_START with the current revision.
        r.machine.onStartCommand(ShiftMachine.ACTION_HOME_START, r.home.point()!!.updatedAt)
        assertTrue(r.machine.shiftOn())
    }

    @Test fun theWatcherNeedsBackgroundLocationAndNoImport() {
        val r = ShiftRig(temp.root)
        r.permissions = true to false
        r.watcher.recheck(r.mono)
        assertFalse(r.watcher.listening)
        r.permissions = true to true
        r.importing = true
        r.watcher.recheck(r.mono)
        assertFalse(r.watcher.listening)
        r.importing = false
        r.watcher.recheck(r.mono)
        assertTrue(r.watcher.listening)
        // Changing the home stops the watcher and drops its GPS window; the next check listens again at low power.
        r.watcher.homeChanged()
        assertFalse(r.watcher.listening)
        r.watcher.refresh(r.mono)
        assertEquals(HomeWatchPlan.Mode.LOW, r.watcher.mode)
    }
}
