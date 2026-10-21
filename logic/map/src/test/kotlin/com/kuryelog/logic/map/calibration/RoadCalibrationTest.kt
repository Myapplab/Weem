package com.kuryelog.logic.map.calibration

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import kotlin.math.*

class RoadCalibrationTest {
    private val base = 10_000_000_000L
    private fun run(seconds: Int = 10, mode: String = "fixed", speed: (Double) -> Double = { 6.0 },
        sideways: Boolean = false, skip: (Double) -> Boolean = { false },
        amplitude: (Double) -> Double = { 1.0 }): CalibrationResult {
        val e = RoadCalibration(CalibrationConfig("good", seconds, mode, "mount"), base)
        for (i in -2..seconds) e.fix(CalibrationFix(base+i*1_000_000_000L, 100000+i*1000L, speed(max(0.0, i.toDouble())), 40.4, 49.8, 5.0))
        for(i in 0..seconds*50) {
            val t=i/50.0; if(skip(t)) continue
            val vertical=9.80665+amplitude(t)*sin(2*PI*8*t)
            e.sample(base+i*20_000_000L, 100000+i*20L,
                if(sideways) listOf(vertical, 0.0, 0.0) else listOf(0.0, 0.0, vertical),
                if(sideways) listOf(9.80665, 0.0, 0.0) else listOf(0.0, 0.0, 9.80665))
        }
        return e.finish()
    }
    @Test fun gravityProjectionIsMountIndependentAndRmsIsTimeWeighted() {
        val a=run(); val b=run(sideways=true)
        assertTrue(a.valid); assertEquals(9.5, a.validSeconds, .03)
        assertEquals(a.rms!!, b.rms!!, 1e-8)
        // 50 Hz, 8 Hz tone, first-order 2 Hz HP discrete frequency response.
        val alpha=1/(1+2*PI*2*.02); val omega=2*PI*8/50
        val response=alpha*sqrt(2-2*cos(omega))/sqrt(1+alpha*alpha-2*alpha*cos(omega))
        assertEquals(response/sqrt(2.0), a.rms!!, .02)
    }
    @Test fun slowSamplesDoNotCount() {
        val r=run(speed={7.9/3.6}); assertFalse(r.valid); assertEquals(0.0, r.validSeconds, .0001)
        assertTrue(r.points.any { it.reason == "below_8_kmh" })
    }
    @Test fun exactlyEightKmhIsAllowed() { assertTrue(run(speed={8.0/3.6}).valid) }
    @Test fun previousGpsIntervalIsRejectedForBrakingAndTakeoff() {
        val r=run(speed={ t -> if(t<4) 6.0 else if(t<5) 6.0+2*(t-4) else 8.0 })
        // After excluding the manoeuvre and the other speed plateau, less than
        // 60% of the requested run remains in the target speed window.
        assertFalse(r.valid)
        assertTrue(r.points.filter { it.sample.ns in base+4_000_000_000L until base+5_000_000_000L }.all { !it.accepted })
        val braking=run(speed={t->if(t<4) 8.0 else if(t<5) 8.0-2*(t-4) else 6.0})
        assertTrue(braking.points.any { it.reason == "braking_or_acceleration" })
    }
    @Test fun trimmingWarningAppliesToBothSpeedModes() {
        val speed: (Double)->Double={5.0+it*.2}
        assertTrue(run(speed=speed).speedNotConstant)
        assertTrue(run(mode="variable",speed=speed).speedNotConstant)
        assertTrue(run(mode="variable",speed=speed).valid)
        assertFalse(run(speed={6.0+it*.05}).speedNotConstant)
    }
    @Test fun variableRunAtFieldMean22point2IsRejectedAfterTenPercentTrimming() {
        // Synthetic trajectory with the reported mean; these are not the raw
        // seven field runs. Slow ramps stay below the existing acceleration gate.
        val gain = 5.76 / run(seconds = 30).rms!!
        assertEquals(5.76, run(seconds = 30, amplitude = { gain }).rms!!, .001)
        val r = run(seconds = 30, mode = "variable", amplitude = { gain }, speed = { t ->
            (22.2 + 8 * sin(2 * PI * t / 30)) / 3.6
        })
        assertTrue(r.speedNotConstant)
        assertFalse(r.valid)
        assertTrue(r.validSeconds < 30 * .6)
        assertTrue(r.points.any { it.reason == "speed_outside_10_percent" })
        assertNotNull(r.rejectionReason)
    }
    @Test fun rmsAndMeanSpeedAreRecomputedOnlyFromRetainedWindows() {
        val gain = 3.01 / run(seconds = 30).rms!!
        val r = run(seconds = 30, mode = "variable", speed = { t ->
            (if (t < 22) 20.0 else if (t < 24) 20 + 4*(t-22) else 28.0) / 3.6
        }, amplitude = { t -> if (t < 25) gain else gain*4 })
        assertTrue(r.valid); assertTrue(r.speedNotConstant)
        assertTrue(r.speedTrimmedSeconds > 6)
        assertEquals(3.01, r.rms!!, .03)
        assertEquals(20.0, r.averageKmh!!, .1)
    }
    @Test fun constantFieldSpeedsAreAcceptedAndShortSlowRunIsRejected() {
        for (kmh in listOf(21.6, 21.4, 29.4)) {
            val r = run(seconds = 15, speed = { kmh / 3.6 })
            assertTrue(r.valid); assertFalse(r.speedNotConstant)
            assertEquals(kmh, r.averageKmh!!, .001)
        }
        val short = run(seconds = 15, speed = { 11.7 / 3.6 }, skip = { it > 5.2 })
        assertFalse(short.valid); assertTrue(short.validSeconds < 5)
    }
    @Test fun retainedDurationMustBeSixtyPercentOfRequestedNotElapsedTime() {
        val r = run(seconds = 15, skip = { it > 8 })
        assertTrue(r.validSeconds > 5)
        assertFalse(r.valid)
        assertTrue(r.rejectionReason!!.contains("%60"))
    }
    @Test fun exactlyTenPercentBoundaryIsKeptAndGapsAreNotBridged() {
        val r = run(seconds = 15, speed = { t -> if (t < 12) 20.0 / 3.6 else 22.0 / 3.6 })
        assertTrue(r.valid); assertFalse(r.speedNotConstant)
        val gap = run(seconds = 15, skip = { it in 6.0..7.0 })
        assertTrue(gap.valid)
        assertTrue(gap.validSeconds < 13.5)
    }
    @Test fun tenLevelsAreSavedAndOldUnscaledFilesAreUnchanged() {
        val root = Files.createTempDirectory("yolgo-calibration").toFile()
        try {
            val store = RoadCalibrationStore(root)
            store.save("legacy", CalibrationConfig("good", 10, "fixed", "mount"), 1, 10.0, run())
            val before = File(root, "legacy.json").readText()
            for (level in 1..10) {
                val q = when(level) { 9, 10 -> "veryGood"; 7, 8 -> "good"; 5, 6 -> "middle"; 3, 4 -> "bad"; else -> "veryBad" }
                store.save("level-$level", CalibrationConfig(q, 10, "fixed", "mount", level), level.toLong() + 1, 10.0, run())
            }
            val reopened = RoadCalibrationStore(root).sessions()
            assertEquals(11, reopened.size)
            assertEquals(before, File(root, "legacy.json").readText())
            assertFalse(reopened.single { it.getString("id") == "legacy" }.has("qualityLevel"))
            for (level in 1..10) {
                val row = reopened.single { it.getString("id") == "level-$level" }
                assertEquals(level, row.getInt("qualityLevel")); assertEquals(2, row.getInt("schemaVersion"))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun lessThanFiveSecondsAndSensorGapsAreInvalid() {
        assertFalse(run(seconds=4).valid)
        val gap=run(skip={it in 2.0..8.0}); assertFalse(gap.valid); assertTrue(gap.validSeconds<4)
    }
    @Test fun missingGpsAndGravityCannotFabricateAValidRun() {
        val e=RoadCalibration(CalibrationConfig("bad",15,"variable","mount"),base)
        for(i in 0..750) e.sample(base+i*20_000_000L,100000+i*20L,listOf(0.0,0.0,9.8),null)
        assertFalse(e.finish().valid); assertNull(e.finish().rms)
    }
    @Test fun staleGpsIsExcluded() {
        val e=RoadCalibration(CalibrationConfig("middle",15,"fixed","mount"),base)
        e.fix(CalibrationFix(base-1_000_000_000L,99000,6.0,40.4,49.8,5.0))
        e.fix(CalibrationFix(base,100000,6.0,40.4,49.8,5.0))
        for(i in 0..750) e.sample(base+i*20_000_000L,100000+i*20L,listOf(0.0,0.0,9.8),listOf(0.0,0.0,9.8))
        assertFalse(e.finish().valid)
        assertTrue(e.finish().points.filter { it.sample.ns>base+2_000_000_000L }.all { !it.accepted })
    }
    @Test fun highPassRejectsLowFrequencyAndResetsAfterGap() {
        fun rms(hz: Double): Double {
            val f=CalibrationHighPass(); val samples=(0..1000).mapNotNull { f.feed(base+it*20_000_000L,sin(2*PI*hz*it/50)) }.drop(100)
            return sqrt(samples.sumOf { it*it }/samples.size)
        }
        assertTrue(rms(.2)<rms(8.0)*.15)
        val f=CalibrationHighPass(); f.feed(base,1.0); f.feed(base+20_000_000L,2.0)
        assertNull(f.feed(base+1_000_000_000L,3.0))
    }
    @Test fun recordsAppendSurviveReopenDeleteAndExport() {
        val root=Files.createTempDirectory("yolgo-calibration").toFile()
        try {
            val dir=File(root,"sessions"); val s=RoadCalibrationStore(dir)
            val c=CalibrationConfig("good",10,"fixed","mount"); val result=run()
            s.save("one",c,100000,10.0,result); s.save("two",c,200000,10.0,result)
            assertEquals(2,RoadCalibrationStore(dir).sessions().size)
            assertFalse(s.sessions()[0].has("samples"))
            val exported=JSONObject(File(s.export()).readText()).getJSONArray("sessions")
            assertEquals(2,exported.length()); assertTrue(exported.getJSONObject(0).getJSONArray("samples").length()>0)
            s.delete("one"); assertEquals("two",RoadCalibrationStore(dir).sessions().single().getString("id"))
        } finally { root.deleteRecursively() }
    }
    @Test fun interruptedRunsAreInvalidAndExistingIdsCannotBeOverwritten() {
        val root=Files.createTempDirectory("yolgo-calibration").toFile()
        try {
            val s=RoadCalibrationStore(root); val c=CalibrationConfig("good",10,"fixed","mount")
            s.save("one",c,1,10.0,run(),true); assertFalse(s.sessions().single().getBoolean("valid"))
            assertThrows(IllegalStateException::class.java) { s.save("one",c,2,10.0,run()) }
        } finally { root.deleteRecursively() }
    }
    @Test fun durationAndModeAreBounded() {
        assertThrows(IllegalArgumentException::class.java) { CalibrationConfig("good",0,"fixed","mount") }
        assertThrows(IllegalArgumentException::class.java) { CalibrationConfig("bad",601,"variable","mount") }
        assertThrows(IllegalArgumentException::class.java) { CalibrationConfig("other",15,"fixed","mount") }
        assertThrows(IllegalArgumentException::class.java) { CalibrationConfig("good",15,"fixed","mount", 0) }
        assertThrows(IllegalArgumentException::class.java) { CalibrationConfig("good",15,"fixed","mount", 11) }
        assertThrows(IllegalArgumentException::class.java) { CalibrationConfig("middle",15,"fixed","mount", 8) }
    }
}
