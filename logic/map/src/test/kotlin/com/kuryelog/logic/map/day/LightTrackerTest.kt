package com.kuryelog.logic.map.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `light_tracker_test.dart` (4) + `light_rule_m14_test.dart` F9.2 ölçer (1) + `v140_features_test.dart` uzak ışık (1) + `yolgo_rules_test.dart` belirsiz
 * yüzey (1) = 7 test (A6.2b, MAP.md 11.9, 11.16). Saat: fix zamanı (ms); Dart `Fix(t, …)` → [DayFix].
 */
class LightTrackerTest {
    private val start = DayFixtures.ms("2026-10-01T12:00:00Z")
    private fun fix(s: Int, speed: Double = 0.0, accuracy: Double = 5.0) = DayFix(start + s * 1000L, 40.4, 49.85, speedMps = speed, accuracy = accuracy)
    private val known: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(onRoad = true, known = true) }
    private val candidate: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(onRoad = true, junctionM = 10.0) }

    @Test
    fun `kısa ışık beklemesi ilk hareket zamanında biter`() {
        val t = LightTracker()
        for (s in 0..5) t.feed(fix(s), known)
        assertEquals(start, t.sinceMs)
        assertNull(t.feed(fix(6, speed = 3.0), known))
        val rec = t.feed(fix(7, speed = 3.0), known)!!
        assertEquals("light", rec.kind)
        assertEquals(6, rec.durS)
        assertTrue(rec.measured)
        assertNull(t.sinceMs)
    }

    @Test
    fun `uzun kavşak durması onay bekler, bilinen negatif yer aday olmaz`() {
        val t = LightTracker()
        for (s in 0..12) t.feed(fix(s), candidate)
        t.feed(fix(13, speed = 2.0), candidate)
        assertEquals("light?", t.feed(fix(14, speed = 2.0), candidate)?.kind)
        val negative: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(onRoad = true, known = false, junctionM = 10.0) }
        for (s in 20..32) t.feed(fix(s), negative)
        t.feed(fix(33, speed = 2.0), negative)
        assertNull(t.feed(fix(34, speed = 2.0), negative))
    }

    @Test
    fun `GPS kesintisi ya da restoran durması ışık beklemesine eklenmez`() {
        val t = LightTracker()
        t.feed(fix(0), known)
        t.feed(fix(30), known)
        t.feed(fix(31, speed = 3.0), known)
        assertNull(t.feed(fix(32, speed = 3.0), known))
        for (s in 40 until 65) t.feed(fix(s), known, orderStop = true)
        assertNull(t.finish(start + 65_000))
    }

    @Test
    fun `gün sonunda yalnızca son geçerli fix zamanına kadar ölçer`() {
        val t = LightTracker()
        for (s in 0..5) t.feed(fix(s), known)
        assertEquals(5, t.finish(start + 10_000)?.durS)
        for (s in 20..25) t.feed(fix(s), known)
        assertNull(t.finish(start + 50_000))
    }

    // --- light_rule_m14_test F9.2 canlı ölçer ---

    @Test
    fun `canlı ölçer yaya yolunda ışığın altında 20 sn light`() {
        val t0 = DayFixtures.ms("2026-10-16T12:00:00Z")
        val lat0 = 40.4093
        val lon0 = 49.8671
        fun north(m: Double) = lat0 + m / 111195.08
        val t = LightTracker()
        val info: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(known = true, lightM = 2.0, footM = 0.3, surface = StopSurface.SIDEWALK) }
        for (s in 0..20) t.feed(DayFix(t0 + s * 1000L, north(0.0), lon0, speedMps = 0.0), info)
        assertNotNull(t.sinceMs)
        var rec: StopRec? = null
        var s = 21
        while (s <= 30 && rec == null) {
            rec = t.feed(DayFix(t0 + s * 1000L, north((s - 20) * 5.0), lon0, speedMps = 5.0), info)
            s++
        }
        assertEquals("light", rec?.kind)
    }

    // --- v140_features_test ---

    @Test
    fun `canlı ışık sayacı uzak ışıkta başlamaz`() {
        val t0 = DayFixtures.ms("2026-10-06T12:00:00Z")
        val t = LightTracker()
        val far: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(onRoad = true, known = true, lightM = 30.0) }
        for (s in 0..8) t.feed(DayFix(t0 + s * 1000L, 40.4, 49.85), far)
        assertNull(t.sinceMs)
        t.feed(DayFix(t0 + 9_000, 40.4, 49.85, speedMps = 3.0), far)
        assertNull(t.feed(DayFix(t0 + 10_000, 40.4, 49.85, speedMps = 3.0), far))
    }

    // --- yolgo_rules_test ---

    @Test
    fun `belirsiz yüzey eski yol ışık eşleşmesini kapatmaz, kaldırım dışlanır`() {
        val now = DayFixtures.ms("2026-10-02T12:00:00Z")
        val tracker = LightTracker()
        val info: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(onRoad = false, known = true, surface = StopSurface.UNKNOWN) }
        for (i in 0 until 10) tracker.feed(DayFix(now + i * 1000L, 40.4, 49.85, accuracy = 20.0), info)
        assertNotNull(tracker.sinceMs)
        assertEquals("light", tracker.finish(now + 10_000)!!.kind)
    }
}
