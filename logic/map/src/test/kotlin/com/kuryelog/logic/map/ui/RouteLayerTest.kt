package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFixtures
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteLeg
import com.kuryelog.logic.map.route.RoutePoint
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rota çizimi (YolGo `map_view.dart:480-575, 692-753`) ve yeni rotada sığdırma (`_fitPlan`; MAP.md 7.10, 7.11). */
class RouteLayerTest {
    private val pal = MapPalette.DAY

    /** 0–200 m düz, 200–400 m %10 yokuş (40 m'lik adımlar: Δh 4 m), 400 m'den sonra 40 m yürüme. */
    private fun hillLeg(): RouteLeg {
        val ride = (0..10).map { i ->
            val m = i * 40.0
            RoutePoint(NavFixtures.LAT0 + m / 111195.0, NavFixtures.LON0, if (m <= 200) 0.0 else (m - 200) * 0.1)
        }
        val end = GeoPoint(ride.last().lat, ride.last().lon)
        return RouteLeg(
            ride, listOf(ride.last(), RoutePoint(end.lat + 40 / 111195.0, end.lon, 20.0)), 400.0, 40.0, 100.0, 5.0, 20.0, 0.0, 1.0, listOf("A"),
            GeoPoint(end.lat + 40 / 111195.0, end.lon),
        )
    }

    @Test
    fun `eğim rengi parçalara bölünür, parlama ve yürüme çizilir, öteki mod gri`() {
        val active = Plan(RideMode.BATTERY, listOf(0), listOf(hillLeg()))
        val other = NavFixtures.plan(RideMode.TIME)
        val nav = NavState(stops = listOf(Stop(active.legs[0].destination, "x")), plans = mapOf(RideMode.BATTERY to active, RideMode.TIME to other), mode = RideMode.BATTERY)
        val s = RouteLayer.strokes(nav, pal)
        val core = s.filter { it.id.startsWith("core-") }
        assertEquals(listOf(pal.blue, pal.red), core.map { it.color })
        assertEquals(6, core[0].points.size)
        assertEquals(core[0].points.last(), core[1].points.first())
        val glow = s.single { it.id == "glow-0" }
        assertEquals(14.0, glow.widthDp, 0.0)
        assertEquals(0x2E, glow.color ushr 24)
        val walk = s.single { it.id == "walk-0" }
        assertEquals(pal.walk, walk.color)
        assertEquals(false, walk.dashed)
        val grey = s.filter { it.id.startsWith("other-") }
        assertEquals(1, grey.size)
        assertEquals(MapPalette.OTHER_PLAN, grey[0].color)
        assertTrue(grey[0].z < glow.z && glow.z < core[0].z && core[0].z < walk.z)
    }

    @Test
    fun `rozetler bacak hedeflerinde sırayla, varılan iğne ayrı`() {
        val p = Plan(RideMode.TIME, listOf(1, 0), listOf(hillLeg(), NavFixtures.legWithTurns()))
        val reached = Stop(GeoPoint(40.39, 49.84), "eski")
        val pins = RouteLayer.pins(NavState(plans = mapOf(RideMode.TIME to p), mode = RideMode.TIME, reached = reached))
        assertEquals(listOf(0, 1, 2), pins.map { it.number })
        assertTrue(pins[0].reached)
        assertTrue(pins[1].first)
        assertEquals(p.legs[1].destination, pins[2].point)
        assertTrue(RouteLayer.pins(NavState()).isEmpty())
    }

    @Test
    fun `sığdırma bütün noktaları kenar paylı alana koyar, zoom en çok 16_5`() {
        val plan = NavFixtures.plan()
        val me = GeoPoint(NavFixtures.LAT0 - 0.002, NavFixtures.LON0 + 0.004)
        val pts = RouteLayer.fitPoints(plan, me)
        val w = 400.0; val h = 800.0
        val cam = RouteLayer.fit(pts, w, h)!!
        assertTrue(cam.zoom <= 16.5)
        val world = 256 * 2.0.pow(cam.zoom)
        fun x(lon: Double) = (lon + 180) / 360 * world
        fun y(lat: Double) = (1 - ln(tan(PI / 4 + lat * PI / 360)) / PI) / 2 * world
        val pad = RouteLayer.FIT_PADDING
        for (p in pts) {
            val sx = x(p.lon) - x(cam.lon) + w / 2
            val sy = y(p.lat) - y(cam.lat) + h / 2
            assertTrue("x $sx", sx >= pad[0] - 0.5 && sx <= w - pad[2] + 0.5)
            assertTrue("y $sy", sy >= pad[1] - 0.5 && sy <= h - pad[3] + 0.5)
        }
        // Tek nokta (aynı yer) en çok 16.5'e yakınlaşır; plan yoksa nokta yok.
        assertEquals(16.5, RouteLayer.fit(listOf(me, me), w, h)!!.zoom, 1e-9)
        assertTrue(RouteLayer.fitPoints(null, me).isEmpty())
        assertNull(RouteLayer.fit(emptyList(), w, h))
    }
}
