package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.map.day.StageFixtures.NOW0
import com.kuryelog.logic.map.day.StageFixtures.TRIP_A
import com.kuryelog.logic.map.day.StageFixtures.TRIP_B
import com.kuryelog.logic.map.day.StageFixtures.BOLT
import com.kuryelog.logic.map.day.StageFixtures.feed
import com.kuryelog.logic.map.day.StageFixtures.trip
import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * YolGo `courier_stage_follow_test.dart` "aşama izleme" 11 test (A6.2b, MAP.md 11.7, 11.16): Y8 — sipariş aşamaları ("Restorana gidiyorsun" …) ekran okumasındaki
 * aşamayı (to_pickup | at_pickup | to_dropoff | at_dropoff) elle basmadan izler. Girdi [FollowerFeed] (feed v2 JSON'u yerine tipli `TripStages`, S7);
 * ayrıştırma testleri [StageFeedTest]'te. Saat: epoch ms ([NOW0] + sn).
 */
class CourierStageFollowerTest {
    private lateinit var e: ShiftEngine
    private lateinit var f: CourierStageFollower
    private val t = NOW0

    private fun sync(trips: List<FollowerTrip>?, s: Int = 0): List<DayEvent> =
        f.sync(e, trips?.let { FollowerFeed(it) }, t + s * 1000L, lat = 40.4, lon = 49.85)

    private fun types(ev: List<DayEvent>) = ev.map { it.type }

    @Before
    fun setUp() {
        e = ShiftEngine().also { it.startDay(t) }
        f = CourierStageFollower()
    }

    @Test
    fun `to_pickup at_pickup to_dropoff at_dropoff bitti, elle basmadan teslim`() {
        var ev = sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)))
        assertEquals(listOf("order_start"), types(ev))
        assertEquals("kuryeplus", ev.single().data["source"])
        assertEquals(OrderPhase.TO_RESTAURANT, e.s.phase)
        assertEquals(TRIP_A, f.linkedTrip)
        assertTrue("aynı aşama: bir şey yok", sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)), 5).isEmpty())

        ev = sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)), 300)
        assertEquals(listOf("arrive_restaurant"), types(ev))
        assertEquals(true, ev.single().data["auto"])
        assertEquals(OrderPhase.AT_RESTAURANT, e.s.phase)
        assertEquals(t + 300_000, e.s.current!!.restArrivedAtMs)

        ev = sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 600)
        assertEquals(listOf("leave_restaurant", "customer_journey"), types(ev))
        assertEquals(300, ev.first().data["waitS"])
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)

        ev = sync(listOf(trip(TRIP_A, Stage.AT_DROPOFF)), 1200)
        assertEquals(listOf("arrive_customer"), types(ev))
        assertEquals(OrderPhase.AT_CUSTOMER, e.s.phase)

        assertTrue("tek okumada yok: bekle", sync(emptyList(), 1260).isEmpty())
        assertNotNull(e.s.current)
        ev = sync(emptyList(), 1265)
        assertEquals(listOf("delivered"), types(ev))
        assertEquals("kuryeplus", ev.single().data["source"])
        assertEquals(1, e.s.delivered)
        assertNull(e.s.current)
        assertTrue("bitmiş yolculuk yeni sipariş açmaz", sync(emptyList(), 1270).isEmpty())
        assertEquals(1, e.s.orders.size)
    }

    @Test
    fun `atlanan aşamalar sırayla tamamlanır (to_pickup at_dropoff)`() {
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)))
        val ev = sync(listOf(trip(TRIP_A, Stage.AT_DROPOFF)), 900)
        assertEquals(listOf("arrive_restaurant", "leave_restaurant", "customer_journey", "arrive_customer"), types(ev))
        assertEquals(OrderPhase.AT_CUSTOMER, e.s.phase)
    }

    @Test
    fun `ilk görülen yolculuk mevcut aşamasına kadar ilerler`() {
        val ev = sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)))
        assertEquals(listOf("order_start", "arrive_restaurant", "leave_restaurant", "customer_journey"), types(ev))
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
    }

    @Test
    fun `geri yönlü aşama uygulanmaz`() {
        sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)))
        assertTrue(sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)), 45).isEmpty())
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
        sync(listOf(trip(TRIP_A, Stage.AT_DROPOFF)), 600)
        assertEquals(OrderPhase.AT_CUSTOMER, e.s.phase)
        assertTrue("çok duraklı yolculukta sonraki müşteri", sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 700).isEmpty())
        assertEquals(OrderPhase.AT_CUSTOMER, e.s.phase)
    }

    @Test
    fun `elle iptal edilen ya da teslim edilen sipariş aynı yolculuktan yeniden açılmaz`() {
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)))
        e.cancelOrder(t + 20_000)
        assertTrue(sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)), 25).isEmpty())
        assertTrue(sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)), 90).isEmpty())
        assertNull(e.s.current)
        assertTrue(e.s.orders.single().cancelled)
    }

    @Test
    fun `restoran adresi önceden paylaşılsa da siparişi ekran okuması açar, adres o siparişin hedefi olur`() {
        e.setDestination(GeoPoint(40.41, 49.86), t, restaurant = true, name = "Izmir Doner Tantuni No1")
        assertNull(e.s.current)
        val ev = sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)), 120)
        assertEquals(listOf("order_start", "arrive_restaurant"), types(ev))
        assertEquals(1, e.s.current!!.n)
        assertEquals(1, f.linkedOrder)
        e.setDestination(GeoPoint(40.41, 49.86), t + 3 * 60_000, restaurant = true, name = "Izmir Doner Tantuni No1")
        assertNotNull(e.s.current!!.restTarget)
        assertEquals(OrderPhase.AT_RESTAURANT, e.s.phase)
    }

    @Test
    fun `alıştan önce biten yolculuk siparişi iptal eder, okuma yoksa hiçbir şey değişmez`() {
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)))
        assertTrue("okuma yok (izin kapandı): bekle", sync(null, 10).isEmpty())
        assertTrue(sync(null, 15).isEmpty())
        assertNotNull(e.s.current)
        assertTrue(sync(emptyList(), 20).isEmpty())
        val ev = sync(emptyList(), 25)
        assertEquals(listOf("order_cancel"), types(ev))
        assertEquals("kuryeplus", ev.single().data["source"])
        assertNull(e.s.current)
    }

    @Test
    fun `okuma yoksa sipariş açılmaz, aşama değişmez`() {
        for (s in 0 until 50 step 5) assertTrue(sync(null, s).isEmpty())
        assertNull(e.s.current)
        assertNull(f.linkedTrip)
    }

    @Test
    fun `gün kapalıyken izlenmez, gün değişince süren yolculuk yeni günde sipariş açmaz`() {
        val closed = ShiftEngine()
        assertTrue(f.sync(closed, feed(trip(TRIP_A, Stage.TO_PICKUP)), t).isEmpty())
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)))
        e.endDay(t + 60_000, { _, _ -> RoadInfo() })
        f.dayChanged()
        e = ShiftEngine().also { it.startDay(t + 120_000) }
        f.dayChanged()
        assertTrue(sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)), 180).isEmpty())
        assertNull(e.s.current)
    }

    @Test
    fun `ikinci yolculuk ilki bitince yeni sipariş olur`() {
        sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF), trip(TRIP_B, Stage.TO_PICKUP, BOLT)))
        assertEquals(TRIP_A, f.linkedTrip)
        sync(listOf(trip(TRIP_A, Stage.AT_DROPOFF), trip(TRIP_B, Stage.TO_PICKUP, BOLT)), 300)
        sync(listOf(trip(TRIP_B, Stage.AT_PICKUP, BOLT)), 400)
        val ev = sync(listOf(trip(TRIP_B, Stage.AT_PICKUP, BOLT)), 405)
        assertEquals(listOf("delivered"), types(ev))
        val next = sync(listOf(trip(TRIP_B, Stage.AT_PICKUP, BOLT)), 410)
        assertEquals(listOf("order_start", "arrive_restaurant"), types(next))
        assertEquals(2, e.s.current!!.n)
        assertEquals(TRIP_B, f.linkedTrip)
    }

    @Test
    fun `kalıcı durum yalnız yolculuk kimliği, sipariş no ve aşama, geri yüklenince sürer`() {
        sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)))
        val j = JSONObject(f.toJson().toString())
        assertEquals(setOf("v", "trip", "order", "stage", "closed"), j.keys().asSequence().toSet())
        assertEquals(1, j.getInt("v"))
        assertEquals(TRIP_A, j.getString("trip"))
        assertEquals(1, j.getInt("order"))
        assertEquals("at_pickup", j.getString("stage"))
        assertEquals(0, j.getJSONArray("closed").length())
        val back = CourierStageFollower().also { it.restore(j) }
        assertTrue(back.sync(e, feed(trip(TRIP_A, Stage.AT_PICKUP)), t + 5_000).isEmpty())
        assertEquals(
            listOf("leave_restaurant", "customer_journey"),
            back.sync(e, feed(trip(TRIP_A, Stage.TO_DROPOFF)), t + 9_000).map { it.type },
        )
        assertFalse(j.has("at"))
    }
}
