package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * CORE.md 1.2–1.4 (RS `GpsService.kt`): vardiya servisinin kararı ve sırası saf `ShiftMachine`'de. Rawstats'ta kaynak taramasıydı
 * (`CrashGuardTest.gpsServiceStartFixAndStopAreGuarded`, `refusedForegroundStartStillReachesStartForeground`); burada davranış.
 */
class ShiftMachineTest {
    @get:Rule val temp = TemporaryFolder()
    private fun rig(homeAt: Pair<Double, Double>? = 40.4093 to 49.8671) = ShiftRig(temp.root, homeAt)

    @Test fun manualStartOpensTheShiftInRawstatsOrder() {
        val r = rig()
        assertEquals(ShiftMachine.StartResult.STICKY, r.machine.onStartCommand(ShiftMachine.ACTION_START, null))
        assertTrue(r.machine.shiftOn())
        with(r.prefs) {
            assertEquals(true, map["shift"]); assertEquals(true, map["manualSession"]); assertEquals(false, map["autoStarted"])
            assertEquals(r.wall, map["shiftStart"]); assertEquals(0f, map["dist"]); assertEquals("manual", map["shiftStartReason"])
        }
        val start = r.events().single()
        assertEquals("shift_start", start.getString("type"))
        assertFalse(start.getBoolean("auto"))
        assertEquals("manual", start.getString("reason"))
        assertEquals(listOf("startForeground", "removeGps", "requestGps", "recheckHomeWatch"), r.calls.filter { !it.startsWith("watch:") })
        assertFalse("the home watcher stops listening for the shift", r.watcher.listening)
        assertFalse("a shift started by hand ends at home only after leaving once", r.home.engine()!!.endArmed)
        assertEquals(listOf(true), r.states.map { it.active })
        assertEquals(r.wall, r.states.single().startedAtMs)
    }

    @Test fun aLateRestartOrAStaleHomeStartNeverOpensAClosedShift() {
        // YolGo native_telemetry_test's principle: a late start request for a closed shift does not open it.
        val r = rig()
        assertEquals(ShiftMachine.StartResult.NOT_STICKY, r.machine.onStartCommand(null, null))
        assertEquals(listOf("stopSelf"), r.calls)
        r.calls.clear()
        val stale = (r.home.point()!!.updatedAt!!) - 1
        assertEquals(ShiftMachine.StartResult.NOT_STICKY, r.machine.onStartCommand(ShiftMachine.ACTION_HOME_START, stale))
        // A foreground start must still reach startForeground before it stops (Android closes the app otherwise).
        assertEquals(listOf("cancel:5101", "startForeground", "stopForeground", "stopSelf"), r.calls)
        r.calls.clear()
        assertEquals(ShiftMachine.StartResult.NOT_STICKY, r.machine.onStartCommand(ShiftMachine.ACTION_HOME_START, null))
        assertFalse(r.machine.shiftOn())
        assertTrue(r.events().isEmpty())
    }

    @Test fun noStartWhileImporting() {
        val r = rig()
        r.importing = true
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        assertFalse(r.machine.shiftOn())
        assertEquals(listOf("startForeground", "stopForeground", "stopSelf"), r.calls)
    }

    @Test fun homeStartWithTheCurrentRevisionIsADeparture() {
        val r = rig()
        r.machine.onStartCommand(ShiftMachine.ACTION_HOME_START, r.home.point()!!.updatedAt)
        assertTrue(r.machine.shiftOn())
        assertEquals("home_departure", r.prefs.map["shiftStartReason"])
        val e = r.events().single()
        assertTrue(e.getBoolean("auto"))
        assertEquals("home_departure", e.getString("reason"))
        assertTrue("a shift that began by leaving home ends on return", r.home.engine()!!.endArmed)
        assertEquals("cancel:5101", r.calls.first())
    }

    @Test fun stopWritesShiftEndAndClosesEverything() {
        val r = rig()
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        r.calls.clear()
        r.wall += 90 * 60_000L
        r.machine.track.distanceM = 1234.0
        assertEquals(ShiftMachine.StartResult.NOT_STICKY, r.machine.onStartCommand(ShiftMachine.ACTION_STOP, null))
        assertFalse(r.machine.shiftOn())
        val end = r.events().last()
        assertEquals("shift_end", end.getString("type"))
        assertEquals(1.234, end.getDouble("km"), 1e-9)
        assertEquals(1.234, end.getDouble("km_gps"), 1e-9)
        assertTrue(end.isNull("km_scooter"))
        assertFalse(end.getBoolean("km_from_scooter"))
        assertEquals(90.0, end.getDouble("min"), 1e-9)
        assertEquals("manual", end.getString("reason"))
        with(r.prefs) {
            assertEquals(false, map["shift"]); assertEquals(false, map["manualSession"]); assertEquals(1234f, map["dist"])
            assertEquals(r.wall, map["shiftEndedAt"]); assertEquals("manual", map["shiftEndReason"])
        }
        assertTrue("manual stop: no automatic start until seen at home", r.home.engine()!!.startBlocked)
        assertEquals(NotificationIds.SHIFT_END_CANCEL.map { "cancel:$it" } + listOf("recheckHomeWatch", "watch:LOW", "removeGps", "stopForeground", "stopSelf"), r.calls)
        assertTrue("the home watcher listens again", r.watcher.listening)
        assertEquals(listOf(true, false), r.states.map { it.active })
        assertEquals("manual", r.states.last().endReason)
        r.calls.clear()
        r.machine.onStartCommand(ShiftMachine.ACTION_STOP, null)
        assertEquals("no shift: only stops the service", listOf("stopSelf"), r.calls)
    }

    @Test fun theScooterOdometerWinsWhenLonger() {
        val r = rig()
        r.hooks = object : ShiftMachine.Hooks { override fun odometer() = ShiftDistance.Odo(100.0, 103.5, 0L) }
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        r.machine.track.distanceM = 2000.0
        r.machine.stop(HomeShift.REASON_MANUAL)
        val end = r.events().last()
        assertEquals(3.5, end.getDouble("km"), 1e-9)
        assertEquals(2.0, end.getDouble("km_gps"), 1e-9)
        assertEquals(3.5, end.getDouble("km_scooter"), 1e-9)
        assertTrue(end.getBoolean("km_from_scooter"))
    }

    @Test fun aNewProcessKeepsTheSavedDistance() {
        val r = rig()
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        r.prefs.put(mapOf("dist" to 5000f))
        val restarted = ShiftRig(temp.root).also { it.prefs.map.putAll(r.prefs.map) }
        assertEquals(ShiftMachine.StartResult.STICKY, restarted.machine.onStartCommand(null, null))
        assertEquals(5000.0, restarted.machine.track.distanceM, 0.0)
        assertEquals("a sticky restart is not a new shift", 1, restarted.types().count { it == "shift_start" })
        restarted.machine.stop(HomeShift.REASON_MANUAL)
        assertEquals(5.0, restarted.events().last().getDouble("km"), 1e-9)
    }

    @Test fun failingStepsAreRecordedAndTheShiftStillStartsAndEnds() {
        val r = rig()
        r.hooks = object : ShiftMachine.Hooks {
            override fun onNewShift(nowMs: Long, reason: String) = error("pocket cash")
            override fun onRunning() = throw NoClassDefFoundError("OrderTracker")
            override fun onEnd(startMs: Long, nowMs: Long, reason: String) = error("daily pack")
            override fun odometer(): ShiftDistance.Odo? = error("scooter")
        }
        r.refuseGps = SecurityException("no permission")
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        assertTrue(r.machine.shiftOn())
        assertEquals(listOf("shift_start", "gps_no_permission"), r.types())
        r.machine.stop(HomeShift.REASON_MANUAL)
        assertFalse(r.machine.shiftOn())
        assertEquals("shift_end", r.types().last())
        assertEquals(listOf("shift_start", "shift_orders", "shift_end"), r.errors().map { it.getString("source") })
    }

    @Test fun aRefusedManualStartSaysWhyAndOpensNothing() {
        val r = rig()
        r.refuseForeground = IllegalStateException("ForegroundServiceStartNotAllowedException")
        assertEquals(ShiftMachine.StartResult.NOT_STICKY, r.machine.onStartCommand(ShiftMachine.ACTION_START, null))
        assertFalse(r.machine.shiftOn())
        assertEquals(listOf("gps_error"), r.types())
        assertEquals("Vardiya başlatılamadı: ForegroundServiceStartNotAllowedException", r.log.lastError)
        assertEquals(listOf("startForeground", "stopSelf"), r.calls)
    }

    @Test fun fixesAreWrittenAndCountedOnlyDuringTheShift() {
        val r = rig()
        r.drive(500.0, 20.0)
        assertTrue("no shift: nothing", r.gpsLines().isEmpty())
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        r.drive(500.0, 20.0); r.drive(510.0, 20.0, dtS = 2.0); r.drive(530.0, 20.0, dtS = 2.0)
        val lines = r.gpsLines()
        assertEquals(GpsFix.CSV_HEADER, lines[0])
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("2026-10-17T13:11:0"))
        assertEquals(30.0, r.machine.track.distanceM, 1e-6)
        assertEquals("not yet 15 s", 0f, r.prefs.map["dist"])
        r.wall += 15_000
        r.drive(550.0, 20.0, dtS = 2.0)
        assertEquals(50f, r.prefs.map["dist"] as Float, 1e-3f)
        assertNotNull("events carry the last fix", r.log.locJson())
        // A stale fix (Android delivered it 13 s late) is ignored.
        val stale = r.gps(600.0, 20.0)
        r.mono += 13_000
        r.machine.onLocation(stale)
        assertEquals(5, r.gpsLines().size)
    }

    @Test fun aThrowingFixIsRecordedNotThrown() {
        val r = rig()
        r.hooks = object : ShiftMachine.Hooks { override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) = error("order step") }
        r.machine.onStartCommand(ShiftMachine.ACTION_START, null)
        r.drive(500.0, 20.0)
        assertEquals(listOf("gps_orders"), r.errors().map { it.getString("source") })
        r.machine.onProviderDisabled()
        assertEquals("gps_disabled", r.types().last())
    }
}
