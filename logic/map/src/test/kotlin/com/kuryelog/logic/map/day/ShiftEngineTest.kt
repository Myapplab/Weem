package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.day.DayFixtures.LAT0
import com.kuryelog.logic.map.day.DayFixtures.LON0
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `shift_engine_test.dart` (9) + `light_rule_m14_test.dart` F9.2 motor (1) + `v140_features_test.dart` AVM duruşu (1) + `assistant_test.dart` kaldırım
 * duruşu JSON'u (1) = 12 test (A6.2b, MAP.md 11.3–11.6, 11.16). `_Sim` → [DaySim] (1 Hz kuzeye fix); `DateTime(2026,10,1,12)` yerel saat → sabit ms.
 */
class ShiftEngineTest {
    @Test
    fun `gün km ve hareket süresi, dururken km artmaz`() {
        val s = DaySim().ride(60, 18.0).stop(30) // 300 m
        assertEquals(300.0, s.e.s.distM, 15.0)
        assertEquals(59.0, s.e.s.movingS, 2.0)
    }

    @Test
    fun `GPS sıçraması ve eski fix kilometreye eklenmez`() {
        val s = DaySim().ride(20, 18.0)
        val before = s.e.s.distM
        s.e.feed(DayFix(s.t - 5000, 41.0, LON0, speedMps = 5.0), s::road)
        assertEquals(before, s.e.s.distM, 0.0)
        s.e.feed(DayFix(s.t + 1000, 41.0, LON0, speedMps = 5.0), s::road)
        assertEquals(before, s.e.s.distM, 0.0)
        s.e.feed(DayFix(s.t + 2000, LAT0 + s.north / 111320, LON0, speedMps = 5.0), s::road)
        assertEquals(before, s.e.s.distM, 0.0)
    }

    @Test
    fun `dururken konum titremesi kilometreye eklenmez`() {
        val s = DaySim().ride(20, 18.0)
        val before = s.e.s.distM
        for (i in 1..20) {
            s.e.feed(DayFix(s.t + i * 1000L, LAT0 + (s.north + if (i % 2 == 0) 8 else -8) / 111320, LON0), s::road)
        }
        assertEquals(before, s.e.s.distM, 0.0)
    }

    @Test
    fun `eski gün JSON okunur ve eve dönüş alanları korunur`() {
        val s = DaySim().ride(10, 18.0)
        val legacy = s.e.s.toJson().apply { remove("homeReturnAt"); remove("homeStartM"); remove("homeDistM"); remove("homeTimeS") }
        val old = DayState.fromJson(legacy)
        assertNull(old.homeReturnAtMs)
        assertEquals(0.0, old.homeDistM, 0.0)
        old.homeReturnAtMs = s.t
        old.homeStartM = old.distM
        old.homeDistM = 120.0
        old.homeTimeS = 45.0
        val again = DayState.fromJson(old.toJson())
        assertTrue(again.active)
        assertEquals(s.t, again.homeReturnAtMs)
        assertEquals(120.0, again.homeDistM, 0.0)
        assertEquals(45.0, again.homeTimeS, 0.0)
    }

    @Test
    fun `ışık sadece trafik ışığının yanında, kısa durma kaydedilmez, diğer durmalar stop`() {
        val s = DaySim().ride(20, 20.0).stop(20).ride(10, 20.0).stop(5).ride(10, 20.0)
        assertEquals(1, s.e.s.stops.size)
        assertEquals("yakında ışık yok", "stop", s.e.s.stops.single().kind)
        s.info = RoadInfo(onRoad = false)
        s.stop(30).ride(10, 20.0)
        assertEquals("stop", s.e.s.stops.last().kind)
        s.info = RoadInfo(onRoad = true, known = true, lightLat = 40.4, lightLon = 49.85) // ışığın yanında
        s.stop(15).ride(10, 20.0)
        assertEquals("light", s.e.s.stops.last().kind)
    }

    @Test
    fun `uzun duruş varış alış teslim yapmaz, soru sormaz, aşamayı yalnız ekran okuması ilerletir`() {
        val s = DaySim()
        s.events += s.e.startOrder(s.t)
        s.e.setDestination(GeoPoint(LAT0 + (30 * 20 / 3.6) / 111320, LON0), s.t, restaurant = true) // duruşun yeri
        s.ride(30, 20.0).stop(135)
        assertEquals(OrderPhase.TO_RESTAURANT, s.e.s.phase)
        assertFalse(s.saw("ask"))
        s.events += s.e.stageFromFeed(OrderPhase.AT_RESTAURANT, s.t)
        assertEquals(OrderPhase.AT_RESTAURANT, s.e.s.phase)
        assertTrue(s.e.s.current!!.restAuto)
        s.stop(20)
        s.events += s.e.stageFromFeed(OrderPhase.TO_CUSTOMER, s.t)
        assertEquals(OrderPhase.TO_CUSTOMER, s.e.s.phase)
        assertEquals(20, s.e.s.current!!.restWaitS)
        s.ride(30, 20.0).stop(135)
        assertEquals(OrderPhase.TO_CUSTOMER, s.e.s.phase)
        s.events += s.e.stageFromFeed(OrderPhase.AT_CUSTOMER, s.t)
        s.ride(10, 20.0)
        assertEquals(OrderPhase.AT_CUSTOMER, s.e.s.phase)
        assertNull(s.e.s.current!!.deliveredAtMs)
        s.events += s.e.deliverFromFeed(s.t)
        assertEquals(OrderPhase.NONE, s.e.s.phase)
        assertTrue(s.saw("delivered"))
        assertFalse(s.saw("ask"))
    }

    @Test
    fun `kavşakta uzun kırmızı varış sayılmaz`() {
        val s = DaySim()
        s.events += s.e.startOrder(s.t)
        s.ride(20, 20.0).stop(80).ride(10, 25.0)
        assertEquals(OrderPhase.TO_RESTAURANT, s.e.s.phase)
        assertEquals("stop", s.e.s.stops.last().kind)
    }

    @Test
    fun `sipariş yokken restoran müşteri işaretlenmez`() {
        val s = DaySim().ride(20, 20.0).stop(200).ride(10, 20.0)
        assertFalse(s.saw("ask"))
        assertTrue(s.e.s.orders.isEmpty())
    }

    @Test
    fun `JSON gidiş dönüş`() {
        val s = DaySim()
        s.events += s.e.startOrder(s.t)
        s.ride(20, 20.0).stop(130)
        s.e.stageFromFeed(OrderPhase.AT_RESTAURANT, s.t)
        val again = DayState.fromJson(s.e.s.toJson())
        assertTrue(again.active)
        assertEquals(OrderPhase.AT_RESTAURANT, again.phase)
        assertEquals(s.e.s.distM, again.distM, 1.0)
        assertEquals(1, again.current!!.n)
    }

    // --- light_rule_m14_test F9.2 motor ---

    private val t0 = DayFixtures.ms("2026-10-16T12:00:00Z")
    private val lat0 = 40.4093
    private val lon0 = 49.8671
    private fun north(m: Double) = lat0 + m / 111195.08

    @Test
    fun `motor yaya yolunda ışığın altında 30 sn light, yol kenarı light, 12 m kaldırım sidewalk, AVM mall`() {
        fun kindFor(info: RoadInfo): String {
            val e = ShiftEngine().also { it.startDay(t0) }
            var t = t0
            var m = 0.0
            fun ride(seconds: Int, kmh: Double) {
                for (i in 0 until seconds) {
                    t += 1000
                    m += kmh / 3.6
                    e.feed(DayFix(t, north(m), lon0, speedMps = kmh / 3.6)) { _, _ -> info }
                }
            }
            ride(10, 20.0); ride(30, 0.0); ride(10, 20.0)
            return e.s.stops.single().kind
        }
        assertEquals("light", kindFor(RoadInfo(known = true, lightM = 3.0, footM = 0.4, surface = StopSurface.SIDEWALK)))
        assertEquals("light", kindFor(RoadInfo(onRoad = true, known = true, lightM = 4.0, roadside = true)))
        assertEquals("sidewalk", kindFor(RoadInfo(known = true, lightM = 12.0, footM = 2.0, surface = StopSurface.SIDEWALK)))
        assertEquals("mall", kindFor(RoadInfo(onRoad = true, known = true, lightM = 4.0, mall = true)))
    }

    // --- v140_features_test AVM duruşu ---

    @Test
    fun `AVM yanındaki durma mall kaydedilir, ışık değil`() {
        val e = ShiftEngine()
        e.startDay(DayFixtures.ms("2026-10-06T12:00:00Z"))
        var t = DayFixtures.ms("2026-10-06T12:00:00Z")
        var north = 0.0
        val mall = RoadInfo(onRoad = true, known = true, lightM = 3.0, mall = true)
        fun ride(seconds: Int, kmh: Double) {
            for (i in 0 until seconds) {
                t += 1000
                north += kmh / 3.6
                e.feed(DayFix(t, LAT0 + north / 111320, LON0, speedMps = kmh / 3.6)) { _, _ -> mall }
            }
        }
        ride(10, 20.0); ride(20, 0.0); ride(10, 20.0)
        assertTrue(e.s.stops.isNotEmpty())
        assertEquals("mall", e.s.stops.last().kind)
        assertTrue(e.s.lights.isEmpty())
    }

    // --- assistant_test kaldırım duruşu ---

    @Test
    fun `kaldırım durması JSON telemetrisinde doğru etiketlenir`() {
        val now = DayFixtures.ms("2026-10-02T14:00:00Z")
        val target = GeoPoint(40.4, 49.8)
        val e = ShiftEngine()
        e.startDay(now); e.startDay(now)
        val sidewalk = RoadInfo(onRoad = true, known = true, surface = StopSurface.SIDEWALK)
        for (i in 0..20) e.feed(DayFix(now + i * 1000L, target.lat, target.lon)) { _, _ -> sidewalk }
        e.endDay(now + 21_000, { _, _ -> sidewalk })
        val stop = e.s.stops.single()
        assertEquals("sidewalk", stop.kind)
        assertFalse(stop.isLight)
        assertEquals(StopSurface.SIDEWALK, StopRec.fromJson(stop.toJson()).surface)
    }
}
