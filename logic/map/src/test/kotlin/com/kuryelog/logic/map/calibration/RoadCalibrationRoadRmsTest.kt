package com.kuryelog.logic.map.calibration

import com.kuryelog.logic.map.surface.SurfaceSignal
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class RoadCalibrationRoadRmsTest {
    private fun points(end: Int, rejected: IntRange = IntRange.EMPTY) = (300..end step 2).map { i ->
        CalibrationPoint(CalibrationSample(i*10_000_000L, 0, emptyList(), emptyList(), 0.0, 0.0),
            null, 5.0, 0.0, i !in rejected, if (i in rejected) "speed_outside_10_percent" else "accepted")
    }
    @Test fun identicalSyntheticStreamsMatchDirectSurfaceSignalAndMeterWeightedPassEvidence() {
        for (frequency in listOf(.2, 8.0, 30.0)) for (linear in listOf(true, false)) {
            val direct = SurfaceSignal(); val calibration = RoadCalibrationRoadRms()
            val expected = arrayListOf<Pair<SurfaceSignal.Window, Double>>()
            calibration.beginRun(3_000_000_000L)
            val up = doubleArrayOf(sin(.8), 0.0, cos(.8))
            for (i in 1..1200) {
                val stopped = i <= 300
                val z = .3 + (if (stopped) 0.0 else sin(2*PI*frequency*i/100)) + (if (linear) 0.0 else 9.80665)
                val a = DoubleArray(3) { z*up[it] }
                val w = direct.feed(i*10_000_000L, a, up, linear, stopped, if(stopped) 0.0 else 5.0)
                val c = calibration.feed(i*10_000_000L, a, up, linear, stopped, if(stopped) 0.0 else 5.0)
                assertEquals(w, c)
                if (c != null) {
                    val meters = if((i / 150) % 2 == 0) 7.0 else 11.0
                    calibration.window(c, meters)
                    if(c.valid && c.startNs >= 3_000_000_000L) expected.add(c to meters)
                }
            }
            val result = calibration.finish(points(1200), 9)
            assertNull(result.rejectionReason)
            assertEquals(1, result.version)
            assertEquals(sqrt(expected.sumOf { it.first.rms.pow(2)*it.second } / expected.sumOf { it.second }), result.rms!!, 1e-12)
            assertEquals(expected.size, result.windows)
        }
    }
    @Test fun trimmedIntervalsRejectWholeStraddlingWindowsWithoutBridgingGaps() {
        val c = RoadCalibrationRoadRms(); c.beginRun(3_000_000_000L)
        for(i in 1..1500) c.feed(i*10_000_000L, doubleArrayOf(0.0,0.0, if(i<=300) 0.0 else 1.0),
            doubleArrayOf(0.0,0.0,1.0), true, i<=300, if(i<=300) 0.0 else 5.0)?.let { c.window(it, 8.0) }
        val r = c.finish(points(1500, 700..710), 12)
        // The 6.01–7.51 s window intersects the speed rejection and is excluded.
        assertEquals(6, r.windows)
        assertEquals(48.0, r.meters, 1e-9)
        assertNull(r.rejectionReason)
    }
    @Test fun noStationaryOffsetIsAnExplicitRoadOnlyRejection() {
        val c = RoadCalibrationRoadRms(); c.beginRun(3_000_000_000L)
        for(i in 1..1200) c.feed(i*10_000_000L, doubleArrayOf(0.0,0.0,1.0),
            doubleArrayOf(0.0,0.0,1.0), true, false, 5.0)?.let { c.window(it,8.0) }
        val r = c.finish(points(1200),9)
        assertNull(r.rms); assertTrue(r.rejectionReason!!.contains("durağan ofset"))
    }
    @Test fun sensorGapDropsHalfWindowButKeepsStationaryOffset() {
        val c = RoadCalibrationRoadRms(); c.beginRun(3_000_000_000L)
        val starts = arrayListOf<Long>()
        for(i in 1..1200) {
            if(i in 601..630) continue // 6,00 → 6,31 sn: 0,31 sn sensör boşluğu (> 0,12 sn)
            c.feed(i*10_000_000L, doubleArrayOf(0.0,0.0, if(i<=300) 0.0 else 1.0),
                doubleArrayOf(0.0,0.0,1.0), true, i<=300, if(i<=300) 0.0 else 5.0)?.let {
                    starts.add(it.startNs); if(it.startNs >= 3_000_000_000L) assertTrue(it.reason, it.valid)
                    c.window(it, 8.0)
                }
        }
        assertTrue(c.offsetReady)
        // 4,51 sn'de başlayan yarım pencere atılır; yeni pencere boşluktan sonraki ilk örnekte başlar.
        assertEquals(listOf(10_000_000L, 1_510_000_000L, 3_010_000_000L, 6_310_000_000L,
            7_810_000_000L, 9_310_000_000L), starts)
        val r = c.finish(points(1200), 9)
        assertNull(r.rejectionReason)
        assertEquals(4, r.windows); assertEquals(32.0, r.meters, 1e-9); assertNotNull(r.rms)
    }
    @Test fun offsetLostDuringRecordingHasItsOwnRejection() {
        assertEquals("Yol RMS: ofset kayıt sırasında kayboldu; yeniden kalibre et",
            RoadCalibrationRoadRms.rejectionReason(offsetReady = false, offsetSeen = true, seconds = 9.0, requestedSeconds = 9))
        val never = RoadCalibrationRoadRms.rejectionReason(offsetReady = false, offsetSeen = false, seconds = 9.0, requestedSeconds = 9)!!
        assertTrue(never.contains("durağan ofset hazırlanmadı")); assertFalse(never.contains("kayboldu"))
        assertNull(RoadCalibrationRoadRms.rejectionReason(offsetReady = true, offsetSeen = true, seconds = 9.0, requestedSeconds = 9))
        // Hiç hazırlanmamış ofset yine eski nedenle reddedilir (instance yolu).
        val c = RoadCalibrationRoadRms(); c.beginRun(0)
        for(i in 1..600) c.feed(i*10_000_000L, doubleArrayOf(0.0,0.0,1.0), doubleArrayOf(0.0,0.0,1.0), true, false, 5.0)
        assertEquals(never, c.finish(points(600), 3).rejectionReason)
    }
    @Test fun storeAddsRoadMetricWithoutChangingLegacyRmsOrOldFiles() {
        val root = java.nio.file.Files.createTempDirectory("road-rms-test").toFile()
        try {
            val old = java.io.File(root,"old.json"); old.writeText("{\"id\":\"old\",\"at\":1,\"rms\":2.5}")
            val before = old.readBytes()
            val result = CalibrationResult(12.0,2.5,20.0,true,false,emptyList(),
                roadRms=1.2, roadRmsVersion=1, roadValidSeconds=9.0, roadMeters=45.0, roadWindows=6)
            val store = RoadCalibrationStore(root)
            store.save("new",CalibrationConfig("good",15,"fixed","mount",7),2,15.0,result)
            val j = store.sessions().first()
            assertEquals(2.5,j.getDouble("rms"),0.0); assertEquals(1.2,j.getDouble("roadRms"),0.0)
            assertEquals(1,j.getInt("roadRmsVersion")); assertArrayEquals(before,old.readBytes())
        } finally { root.deleteRecursively() }
    }
}
