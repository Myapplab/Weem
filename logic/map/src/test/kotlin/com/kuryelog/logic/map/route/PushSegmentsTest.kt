package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.route.TestGraphs.p
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `push_segments_test.dart` (5 test; A6.2a, MAP.md 3.14): sürülebilir yol yoksa ya da çok uzunsa rota ortasındaki yaya yolu
 * "it/yürü" kipinde kullanılır (yürüme hızı, iniş-biniş cezası); yürünen parça işaretlenir (çizimde kesikli). Uygulama: A6.4.
 */
class PushSegmentsTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 660.0)

    private fun m(g: RoadGraph) = CostModel(g, mode = RideMode.TIME, params = RideParams(), approved = emptySet(), blocked = emptySet())

    @Test
    fun `başka yol yoksa 60 m yaya yolu yürünür, rota bulunur`() {
        val leg = Router.routeLeg(m(TestGraphs.pushGraph()), a, b)
        assertEquals(60.0, leg.pushM, 3.0)
        assertEquals(660.0, leg.rideM, 5.0) // çizgi (sürüş + yürünen) boyu
        val walked = leg.segs.filter { it.walk }
        assertEquals(1, walked.size)
        assertEquals(300.0, walked.single().startM, 3.0)
        assertTrue(leg.warnings.any { it.startsWith("Rotada yaklaşık 60 m yaya yolu") })
        assertTrue(leg.energyWh > 0)
    }

    @Test
    fun `yürünen parça süresi yürüme hızıyla`() {
        val leg = Router.routeLeg(m(TestGraphs.pushGraph()), a, b)
        val rideOnly = Router.routeLeg(m(TestGraphs.pushGraph(detourEastM = 1.0)), a, b) // ~62 m sürülebilir yürüyüş yok
        assertEquals(0.0, rideOnly.pushM, 0.0)
        assertTrue(leg.timeS - rideOnly.timeS > 60 * 1.2 / RideParams().walkMps - 15)
    }

    @Test
    fun `kısa sürülebilir dolambaç varsa yürünmez, çok uzunsa yürünür`() {
        assertEquals(0.0, Router.routeLeg(m(TestGraphs.pushGraph(detourEastM = 100.0)), a, b).pushM, 0.0) // ~260 m sürüş
        assertEquals(60.0, Router.routeLeg(m(TestGraphs.pushGraph(detourEastM = 900.0)), a, b).pushM, 3.0) // ~1.860 m sürüş
    }

    @Test
    fun `yaya geçişi yasak ya da merdiven yürünmez`() {
        assertThrows(RoutingException::class.java) {
            Router.routeLeg(m(TestGraphs.pushGraph(footTags = mapOf("highway" to "footway", "foot" to "no"))), a, b)
        }
        assertThrows(RoutingException::class.java) {
            Router.routeLeg(m(TestGraphs.pushGraph(footTags = mapOf("highway" to "steps"))), a, b)
        }
    }

    @Test
    fun `pushedRuns tek parça, yaklaşık 60 m, 300 m'den başlar`() {
        // F6.3b: yürünen parçalar çizgiden mesafeyle kesilir (haritada kesikli).
        val leg = Router.routeLeg(m(TestGraphs.pushGraph()), p(0.0, 0.0), p(0.0, 660.0))
        val runs = RouteGeometry.pushedRuns(leg)
        assertEquals(1, runs.size)
        var len = 0.0
        val run = runs.single()
        for (i in 1 until run.size) len += Geo.haversine(run[i - 1].lat, run[i - 1].lon, run[i].lat, run[i].lon)
        assertEquals(60.0, len, 3.0)
        assertTrue(Geo.haversine(run.first().lat, run.first().lon, lat(300.0), lon(0.0)) < 3)
        assertTrue(RouteGeometry.pushedRuns(Router.routeLeg(m(TestGraphs.pushGraph(detourEastM = 100.0)), p(0.0, 0.0), p(0.0, 660.0))).isEmpty())
    }
}
