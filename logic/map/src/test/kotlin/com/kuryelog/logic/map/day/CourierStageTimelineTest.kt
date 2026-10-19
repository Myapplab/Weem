package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages.Outcome
import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.map.day.StageFixtures.NOW0
import com.kuryelog.logic.map.day.StageFixtures.TRIP_A
import com.kuryelog.logic.map.day.StageFixtures.TRIP_B
import com.kuryelog.logic.map.day.StageFixtures.ev
import com.kuryelog.logic.map.day.StageFixtures.line
import com.kuryelog.logic.map.day.StageFixtures.trip
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * YolGo `courier_stages_e8_test.dart` "E8 aşama zaman çizelgesi" 11 test (A6.2b, MAP.md 11.7, 11.16): `stages` her açık ve son 10 dk'da biten siparişin aşamalarını
 * Rawstats'ın gördüğü gerçek saatlerle verir; takipçi bunları uygular (D17: 7 sn yoklama restoran varış ve çıkışını aynı ms'ye yazıyordu), varış görülmediyse bekleme
 * süresini uydurmaz, biten siparişi sonucu ve saatiyle hemen kapatır. `stages` yoksa eski kural ([CourierStageFollowerTest]). Girdi [FollowerFeed] (`stages` dolu).
 */
class CourierStageTimelineTest {
    private lateinit var e: ShiftEngine
    private lateinit var f: CourierStageFollower
    private val t0 = NOW0
    private fun at(s: Int) = t0 + s * 1000L

    private fun sync(trips: List<FollowerTrip>, s: Int, stages: List<com.kuryelog.logic.core.TripStages.Trip>? = null): List<DayEvent> =
        f.sync(e, FollowerFeed(trips, stages?.associateBy { it.tripId }), at(s), lat = 40.4, lon = 49.85)

    private val full = listOf(ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.AT_PICKUP, 300, pickup = 0, source = "gps"), ev(Stage.TO_DROPOFF, 600))

    @Before
    fun setUp() {
        e = ShiftEngine().also { it.startDay(t0) }
        f = CourierStageFollower()
    }

    @Test
    fun `aşamalar Rawstats saatleriyle uygulanır, yeni sipariş ilk aşamanın saatinde başlar`() {
        val x = sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 700, listOf(line(TRIP_A, full, stage = Stage.TO_DROPOFF)))
        assertEquals(listOf("order_start", "arrive_restaurant", "leave_restaurant", "customer_journey"), x.map { it.type })
        assertEquals(listOf(at(4), at(300), at(600), at(600)), x.map { it.atMs })
        assertTrue(x.all { it.data["source"] == ShiftEngine.FEED_SOURCE })
        val o = e.s.current!!
        assertEquals(listOf(at(4), at(300), at(600), 300), listOf(o.startedAtMs, o.restArrivedAtMs, o.restLeftAtMs, o.restWaitS))
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
        assertEquals(TRIP_A, f.linkedTrip)
        assertTrue(sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 705, listOf(line(TRIP_A, full, stage = Stage.TO_DROPOFF))).isEmpty())
    }

    @Test
    fun `D17 okuma arada kaçsa da varış ve çıkış kendi saatlerinde (aynı ms değil)`() {
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)), 10, listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0)))))
        val x = sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 700, listOf(line(TRIP_A, full, stage = Stage.TO_DROPOFF)))
        assertEquals(listOf("arrive_restaurant", "leave_restaurant", "customer_journey"), x.map { it.type })
        assertEquals(at(300), x.first().atMs)
        assertEquals(300, e.s.current!!.restWaitS)
    }

    @Test
    fun `varış görülmediyse restArrivedAt null kalır, bekleme bilinmiyor, 0 sn yazılmaz`() {
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)), 10, listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0)))))
        val x = sync(
            listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 707,
            listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.TO_DROPOFF, 600)), stage = Stage.TO_DROPOFF)),
        )
        assertEquals(listOf("leave_restaurant", "customer_journey"), x.map { it.type })
        assertNull(x.first().data["waitS"])
        val o = e.s.current!!
        assertEquals(listOf(null, at(600), null), listOf(o.restArrivedAtMs, o.restLeftAtMs, o.restWaitS))
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
    }

    @Test
    fun `biten sipariş sonucu ve saatiyle hemen teslim edilir, ikinci okuma beklenmez`() {
        sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 700, listOf(line(TRIP_A, full, stage = Stage.TO_DROPOFF)))
        val ended = line(TRIP_A, full + ev(Stage.AT_DROPOFF, 1200), outcome = Outcome.DELIVERED, endedS = 1250)
        val x = sync(emptyList(), 1300, listOf(ended))
        assertEquals(listOf("arrive_customer", "delivered"), x.map { it.type })
        assertEquals(listOf(at(1200), at(1250)), x.map { it.atMs })
        val o = e.s.orders.single()
        assertEquals(listOf(at(1200), at(1250), 50), listOf(o.custArrivedAtMs, o.deliveredAtMs, o.custWaitS))
        assertNull(e.s.current)
        assertNull(f.linkedTrip)
        assertTrue("biten yolculuk yeni sipariş açmaz", sync(emptyList(), 1305, listOf(ended)).isEmpty())
    }

    @Test
    fun `delivered_unverified ve unconfirmed teslim saatinde biter, müşteriye varış görülmediyse custArrivedAt null`() {
        for (outcome in listOf(Outcome.DELIVERED_UNVERIFIED, Outcome.UNCONFIRMED)) {
            setUp()
            sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 700, listOf(line(TRIP_A, full, stage = Stage.TO_DROPOFF)))
            val x = sync(emptyList(), 1300, listOf(line(TRIP_A, full, outcome = outcome, endedS = 1250)))
            assertEquals(outcome.key, listOf("delivered"), x.map { it.type })
            assertEquals(at(1250), x.single().atMs)
            val o = e.s.orders.single()
            assertEquals(listOf(null, at(1250), null, false), listOf(o.custArrivedAtMs, o.deliveredAtMs, o.custWaitS, o.cancelled))
        }
    }

    @Test
    fun `alıştan önce biten (cancelled) sipariş bitiş saatinde iptal, alındıysa sipariş kalır`() {
        sync(listOf(trip(TRIP_A, Stage.TO_PICKUP)), 10, listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0)))))
        val x = sync(emptyList(), 250, listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0)), outcome = Outcome.CANCELLED, endedS = 200)))
        assertEquals(listOf("order_cancel"), x.map { it.type })
        assertEquals(at(200), x.single().atMs)
        assertEquals(ShiftEngine.FEED_SOURCE, x.single().data["source"])
        assertTrue(e.s.orders.single().cancelled)

        // İkinci sipariş: Rawstats alışı gösterdi, sonra "cancelled" dedi → alınmış sipariş iptal edilmez.
        val picked = listOf(ev(Stage.TO_PICKUP, 290, pickup = 0), ev(Stage.TO_DROPOFF, 320))
        sync(listOf(trip(TRIP_B, Stage.TO_DROPOFF)), 330, listOf(line(TRIP_B, picked, stage = Stage.TO_DROPOFF)))
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
        assertTrue(sync(emptyList(), 400, listOf(line(TRIP_B, picked, outcome = Outcome.CANCELLED, endedS = 390))).isEmpty())
        assertFalse(e.s.current!!.cancelled)
        assertEquals(OrderPhase.TO_CUSTOMER, e.s.phase)
    }

    @Test
    fun `saatler son uygulanan ile şimdi aralığına kırpılır`() {
        e.startOrder(at(100)) // adres paylaşımıyla açılmış sipariş
        val x = sync(
            listOf(trip(TRIP_A, Stage.AT_PICKUP)), 400,
            listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.AT_PICKUP, 50, pickup = 0)), stage = Stage.AT_PICKUP)),
        )
        assertEquals(listOf("arrive_restaurant"), x.map { it.type })
        assertEquals("siparişin başından önce olamaz", at(100), e.s.current!!.restArrivedAtMs)
        sync(
            listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 500,
            listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.AT_PICKUP, 50, pickup = 0), ev(Stage.TO_DROPOFF, 900)), stage = Stage.TO_DROPOFF)),
        )
        assertEquals("gelecekteki saat (saat kayması) şimdiye çekilir", at(500), e.s.current!!.restLeftAtMs)
    }

    @Test
    fun `ikinci restoran (pickup 1) geri yönlü sayılmaz, çıkış son alışın saatinde`() {
        val x = sync(
            listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 800,
            listOf(
                line(
                    TRIP_A,
                    listOf(
                        ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.AT_PICKUP, 200, pickup = 0, source = "screen"), ev(Stage.TO_PICKUP, 400, pickup = 1),
                        ev(Stage.AT_PICKUP, 500, pickup = 1, source = "gps"), ev(Stage.TO_DROPOFF, 700),
                    ),
                    stage = Stage.TO_DROPOFF,
                ),
            ),
        )
        assertEquals(listOf("order_start", "arrive_restaurant", "leave_restaurant", "customer_journey"), x.map { it.type })
        assertEquals(listOf(at(4), at(200), at(700), at(700)), x.map { it.atMs })
    }

    @Test
    fun `stages yoksa eski kural, iki eksik okuma, ilk eksik okumadan sonra kısa yeniden okuma istenir`() {
        sync(listOf(trip(TRIP_A, Stage.AT_DROPOFF)), 0)
        assertFalse(f.awaitingSecondRead)
        assertTrue(sync(emptyList(), 60).isEmpty())
        assertTrue(f.awaitingSecondRead)
        assertEquals(listOf("delivered"), sync(emptyList(), 62).map { it.type })
        assertFalse(f.awaitingSecondRead)
    }

    @Test
    fun `stages var ama yolculuk listede yoksa (bitiş saati bilinmiyor) eski kurala düşer`() {
        sync(listOf(trip(TRIP_A, Stage.TO_DROPOFF)), 700, listOf(line(TRIP_A, full, stage = Stage.TO_DROPOFF)))
        assertTrue(sync(emptyList(), 760, emptyList()).isEmpty())
        assertTrue(f.awaitingSecondRead)
        assertEquals(listOf("arrive_customer", "delivered"), sync(emptyList(), 762, emptyList()).map { it.type })
    }

    @Test
    fun `kalıcı durum son uygulanan aşama ve saati saklanır, eski kayıt (saatsiz) da okunur`() {
        val arrived = listOf(ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.AT_PICKUP, 300, pickup = 0))
        sync(listOf(trip(TRIP_A, Stage.AT_PICKUP)), 310, listOf(line(TRIP_A, arrived, stage = Stage.AT_PICKUP)))
        val j = JSONObject(f.toJson().toString())
        assertEquals(listOf(1, TRIP_A, 1, "at_pickup", at(300), 0), listOf(j.getInt("v"), j.getString("trip"), j.getInt("order"), j.getString("stage"), j.getLong("at"), j.getJSONArray("closed").length()))
        val back = CourierStageFollower().also { it.restore(j) }
        assertTrue(back.sync(e, FollowerFeed(listOf(trip(TRIP_A, Stage.AT_PICKUP)), mapOf(TRIP_A to line(TRIP_A, arrived, stage = Stage.AT_PICKUP))), at(315)).isEmpty())
        val legacy = CourierStageFollower().also { it.restore(JSONObject("""{"v":1,"trip":"$TRIP_A","order":1,"stage":"at_pickup","closed":[]}""")) }
        assertEquals(TRIP_A, legacy.linkedTrip)
        assertFalse(legacy.toJson().has("at"))
    }
}
