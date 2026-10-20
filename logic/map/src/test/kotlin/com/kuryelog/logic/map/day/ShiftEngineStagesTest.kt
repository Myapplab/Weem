package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `yolgo_sade_2_test.dart` motor 4 test + `assistant_test.dart` gün motoru 4 test (A6.2b, MAP.md 11.6, 11.16): aşamayı yalnız ekran okuması ilerletir; hedef
 * yakınlığı, hız, uzun duruş, restoran/konut adresi sipariş ya da varış üretmez; geriye giden zaman durumu değiştirmez; sipariş günü kendiliğinden başlatmaz.
 */
class ShiftEngineStagesTest {
    private val t = DayFixtures.ms("2026-10-15T12:00:00Z")
    private val now = DayFixtures.ms("2026-10-02T14:00:00Z")
    private val target = GeoPoint(40.40, 49.85)
    private val road: (Double, Double) -> RoadInfo = { _, _ -> RoadInfo(onRoad = true) }
    private val minute = 60_000L

    @Test
    fun `restoran adresi paylaşmak sipariş açmaz`() {
        val e = ShiftEngine().also { it.startDay(t) }
        assertTrue(e.setDestination(target, t, restaurant = true, name = "Döner").none { it.type == "order_start" })
        assertNull(e.s.current)
        assertEquals(OrderPhase.NONE, e.s.phase)
    }

    @Test
    fun `adres yalnız hedef olarak yazılır, aşama geri sarılmaz, müşteri bacağı kendiliğinden başlamaz`() {
        val e = ShiftEngine().also { it.startDay(t) }
        e.startOrder(t)
        e.stageFromFeed(OrderPhase.AT_RESTAURANT, t + 3 * minute)
        e.setDestination(GeoPoint(40.41, 49.86), t + 4 * minute, restaurant = true, name = "Başka")
        assertEquals(OrderPhase.AT_RESTAURANT, e.s.phase)
        assertEquals(t + 3 * minute, e.s.current!!.restArrivedAtMs)
        assertEquals(40.41, e.s.current!!.restTarget!!.lat, 0.0)
        val o = e.s.current!!.also { it.restLeftAtMs = t + 5 * minute }
        e.s.phase = OrderPhase.READY_FOR_CUSTOMER
        e.setDestination(target, t + 6 * minute, restaurant = false)
        assertNotNull(o.customerTarget)
        assertEquals(OrderPhase.READY_FOR_CUSTOMER, e.s.phase)
        assertNull(o.customerDepartedAtMs)
    }

    @Test
    fun `hedefte uzun duruş geldin mi sormaz, aşama değişmez`() {
        val e = ShiftEngine().also { it.startDay(t) }
        e.startOrder(t)
        e.setDestination(target, t, restaurant = true)
        val ev = ArrayList<DayEvent>()
        for (i in 1..200) ev += e.feed(DayFix(t + i * 1000L, target.lat, target.lon), road)
        assertTrue(ev.none { it.type == "ask" })
        assertEquals(OrderPhase.TO_RESTAURANT, e.s.phase)
        assertFalse(e.s.toJson().toString().contains("\"question\""))
    }

    @Test
    fun `eski kayıt (soru, ikinci sipariş penceresi, alış grubu) okunur, alanlar yazılmaz`() {
        val e = ShiftEngine().also { it.startDay(t) }
        e.startOrder(t)
        val old = e.s.toJson()
            .put("question", org.json.JSONObject("""{"type":"restaurant","stop":1,"lat":40.4,"lon":49.85,"asked":2}"""))
            .put("current", e.s.current!!.toJson().put("pickupGroup", 1).put("secondOrderWindowUsed", true))
        val again = DayState.fromJson(old)
        assertEquals(1, again.current!!.n)
        assertEquals(OrderPhase.TO_RESTAURANT, again.phase)
        val json = again.toJson().toString()
        assertFalse(json.contains("question"))
        assertFalse(json.contains("secondOrderWindowUsed"))
        assertFalse(json.contains("pickupGroup"))
    }

    @Test
    fun `hedef yakınlığı ve hız varış yapmaz, varışı ve alışı ekran okuması verir`() {
        val e = ShiftEngine().also { it.startDay(now); it.startOrder(now); it.setDestination(target, now, restaurant = true) }
        assertEquals(OrderPhase.TO_RESTAURANT, e.s.phase)
        val events = e.feed(DayFix(now, target.lat, target.lon, speedMps = 4.0), road)
        assertFalse(events.any { it.type == "arrive_restaurant" })
        assertEquals(OrderPhase.TO_RESTAURANT, e.s.phase)
        e.stageFromFeed(OrderPhase.AT_RESTAURANT, now)
        assertEquals(OrderPhase.AT_RESTAURANT, e.s.phase)
        e.feed(DayFix(now + 1000, target.lat, target.lon, speedMps = 4.0), road)
        assertEquals(OrderPhase.AT_RESTAURANT, e.s.phase)
        e.stageFromFeed(OrderPhase.TO_CUSTOMER, now + 20_000)
        assertEquals(20, e.s.current!!.restWaitS)
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
    }

    @Test
    fun `restoran ya da konut adresi sipariş açmaz`() {
        val e = ShiftEngine().also { it.startDay(now) }
        e.setDestination(target, now, restaurant = false)
        assertNull(e.s.current)
        e.setDestination(target, now, restaurant = true)
        assertNull(e.s.current)
        assertEquals(OrderPhase.NONE, e.s.phase)
    }

    @Test
    fun `aynı veya geriye giden GPS zamanı sipariş durumunu değiştirmez`() {
        val e = ShiftEngine().also { it.startDay(now); it.startOrder(now); it.setDestination(target, now, restaurant = true) }
        e.feed(DayFix(now, target.lat + 0.001, target.lon, accuracy = 40.0), road)
        assertTrue(e.feed(DayFix(now, target.lat, target.lon), road).isEmpty())
        assertEquals(OrderPhase.TO_RESTAURANT, e.s.phase)
        e.feed(DayFix(now + 1000, target.lat, target.lon), road)
        assertEquals(OrderPhase.TO_RESTAURANT, e.s.phase)
    }

    @Test
    fun `sipariş veya restoran adresi aktif günü kendiliğinden başlatamaz`() {
        val e = ShiftEngine()
        assertTrue(e.startOrder(now).isEmpty())
        e.setDestination(target, now, restaurant = true)
        assertFalse(e.s.active)
        assertNull(e.s.current)
    }
}
