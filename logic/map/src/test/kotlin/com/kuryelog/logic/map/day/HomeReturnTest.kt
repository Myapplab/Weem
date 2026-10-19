package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eve dönüş ölçümü (MAP.md 11.13): başlama koşulları, varış 35 m / 1,3 m/sn / 20 m, sipariş açılınca biter, birikme; karakterizasyon. Ev koordinatı olaylara yazılmaz. */
class HomeReturnTest {
    private val t0 = DayFixtures.SIM_START
    private val home = GeoPoint(40.4, 49.85)
    private fun day() = DayState().also { it.active = true; it.startedAtMs = t0 }

    @Test
    fun `gün açık, sipariş yok ve ölçüm sürmüyorsa başlar, yoksa hiçbir şey`() {
        val s = day()
        s.distM = 500.0
        assertEquals(listOf("home_return_start"), HomeReturn.start(s, t0 + 1000).map { it.type })
        assertEquals(500.0, s.homeStartM, 0.0)
        assertTrue("sürerken ikinci başlatma yok", HomeReturn.start(s, t0 + 2000).isEmpty())
        val closed = DayState()
        assertTrue(HomeReturn.start(closed, t0).isEmpty())
        val busy = day().also { it.current = OrderRec(1, t0) }
        assertTrue(HomeReturn.start(busy, t0).isEmpty())
    }

    @Test
    fun `eve varış 35 m, hız 1,3 altı ve doğruluk 20 m eşikleriyle ölçümü bitirir`() {
        fun arrive(dist: Double, speed: Double, acc: Double): DayEvent? {
            val s = day().also { HomeReturn.start(it, t0) }
            val fix = DayFix(t0 + 60_000, home.lat + dist / 111195.08, home.lon, speed, acc)
            return HomeReturn.arrived(s, fix, home)
        }
        assertNotNull(arrive(34.0, 1.2, 20.0))
        assertNull(arrive(36.0, 1.2, 10.0))
        assertNull(arrive(10.0, 1.3, 10.0))
        assertNull(arrive(10.0, -1.0, 10.0))
        assertNull(arrive(10.0, 0.5, 21.0))
        assertNull("ev yoksa", HomeReturn.arrived(day().also { HomeReturn.start(it, t0) }, DayFix(t0, 40.4, 49.85, 0.0), null))
        assertNull("ölçüm yoksa", HomeReturn.arrived(day(), DayFix(t0, 40.4, 49.85, 0.0), home))
    }

    @Test
    fun `bitiş süre ve mesafe biriktirir, birden çok eve dönüş toplanır`() {
        val s = day()
        s.distM = 1000.0
        HomeReturn.start(s, t0)
        s.distM = 1800.0
        HomeReturn.finish(s, t0 + 90_500)
        assertEquals(90.0, s.homeTimeS, 0.0)
        assertEquals(800.0, s.homeDistM, 0.0)
        assertNull(s.homeReturnAtMs)
        HomeReturn.start(s, t0 + 200_000)
        s.distM = 2000.0
        HomeReturn.finish(s, t0 + 230_000)
        assertEquals(120.0, s.homeTimeS, 0.0)
        assertEquals(1000.0, s.homeDistM, 0.0)
        HomeReturn.finish(s, t0 + 300_000) // ölçüm yok: değişmez
        assertEquals(120.0, s.homeTimeS, 0.0)
    }

    @Test
    fun `denetleyicide ev varışı olay verir ve koordinat yazmaz, yeni sipariş ölçümü bitirir`() {
        val c = DayController(com.kuryelog.logic.map.MemoryMapPrefs()) { com.kuryelog.logic.core.Lang.TR }
        c.home = home
        c.startDay(t0)
        assertEquals(listOf("home_return_start"), c.startHomeReturn(t0 + 1000).events.map { it.type })
        val step = c.onFix(DayFix(t0 + 5000, home.lat, home.lon, 0.0, 5.0), t0 + 5000)
        val arrived = step.events.single { it.type == "home_return_arrived" }
        assertTrue(arrived.data.isEmpty())
        assertNull(c.view.state.homeReturnAtMs)
        assertEquals(4.0, c.view.state.homeTimeS, 0.0)
        c.startHomeReturn(t0 + 10_000)
        c.onStages(StageFixtures.feed(StageFixtures.trip(StageFixtures.TRIP_A, com.kuryelog.logic.core.TripStages.Stage.TO_PICKUP)), t0 + 20_000, null)
        assertNull("sipariş açılınca ölçüm biter", c.view.state.homeReturnAtMs)
        assertEquals(14.0, c.view.state.homeTimeS, 0.0)
    }
}
