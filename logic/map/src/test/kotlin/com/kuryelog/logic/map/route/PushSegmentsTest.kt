package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.geo.haversine
import com.kuryelog.logic.map.graph.RoadGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * YolGo `test/push_segments_test.dart` (5 test); `MAP.md` 3.14. Ekim planı madde 9 (F6.3): sürülebilir yol yoksa ya da çok uzunsa rota
 * ortasındaki yaya yolu "it/yürü" kipinde kullanılır (yürüme hızı, iniş-biniş cezası); yürünen parça işaretlenir, sürüşe geri dönülür.
 */
class PushSegmentsTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 660.0)

    private fun m(g: RoadGraph) = CostModel(g, RideMode.TIME, RideParams(), approved = emptySet(), blocked = emptySet())

    private fun assertRoutingFails(g: RoadGraph) {
        try {
            routeLeg(m(g), a, b)
            fail("RoutingException bekleniyordu")
        } catch (_: RoutingException) {
        }
    }

    @Test
    fun `başka yol yoksa - 60 m yaya yolu yürünür, rota bulunur`() {
        val leg = routeLeg(m(TestGraphs.push()), a, b)
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
        val leg = routeLeg(m(TestGraphs.push()), a, b)
        val rideOnly = routeLeg(m(TestGraphs.push(detourEastM = 1.0)), a, b) // ~62 m sürülebilir: yürüyüş yok
        assertEquals(0.0, rideOnly.pushM, 0.0)
        assertTrue(leg.timeS - rideOnly.timeS > 60 * 1.2 / RideParams().walkMps - 15)
    }

    @Test
    fun `kısa sürülebilir dolambaç varsa yürünmez - çok uzunsa yürünür`() {
        assertEquals(0.0, routeLeg(m(TestGraphs.push(detourEastM = 100.0)), a, b).pushM, 0.0) // ~260 m sürüş
        assertEquals(60.0, routeLeg(m(TestGraphs.push(detourEastM = 900.0)), a, b).pushM, 3.0) // ~1.860 m sürüş
    }

    @Test
    fun `yaya geçişi yasak ya da merdiven - yürünmez`() {
        assertRoutingFails(TestGraphs.push(footTags = mapOf("highway" to "footway", "foot" to "no")))
        assertRoutingFails(TestGraphs.push(footTags = mapOf("highway" to "steps")))
    }

    /** F6.3b: yürünen parçalar çizgiden mesafeyle kesilir (haritada kesikli). */
    @Test
    fun `pushedRuns - tek parça, 60 m, 300 m'den başlar`() {
        val leg = routeLeg(m(TestGraphs.push()), p(0.0, 0.0), p(0.0, 660.0))
        val runs = pushedRuns(leg)
        assertEquals(1, runs.size)
        val run = runs.single()
        var len = 0.0
        for (i in 1 until run.size) len += haversine(run[i - 1].lat, run[i - 1].lon, run[i].lat, run[i].lon)
        assertEquals(60.0, len, 3.0)
        assertTrue(haversine(run.first().lat, run.first().lon, lat(300.0), lon(0.0)) < 3)
        assertTrue(pushedRuns(routeLeg(m(TestGraphs.push(detourEastM = 100.0)), p(0.0, 0.0), p(0.0, 660.0))).isEmpty())
    }
}
