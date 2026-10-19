package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.graph.RoadGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/bikeway_e13_test.dart` "E13 rota" grubunun maliyet payı (5 test; 6'ncısı `RouteEngineTest`); `MAP.md` 5.1, 5.2.
 * Fixture'lar: `_single(tags)` ve `_parallel(east)` ([TestGraphs]).
 */
class BikewayCostTest {
    private val residential = mapOf("highway" to "residential")
    private val eastWays = setOf(401L, 402L, 403L)

    private fun model(g: RoadGraph, params: RideParams = RideParams(), approved: Set<Long> = emptySet(), bikeways: Set<Long> = emptySet()) =
        CostModel(g, RideMode.TIME, params, approved = approved, blocked = emptySet(), bikeways = bikeways)

    private fun ride(g: RoadGraph, params: RideParams = RideParams(), bikeways: Set<Long> = emptySet()): Double =
        computePlans(PlanRequest(g, p(0.0, 0.0), listOf(p(0.0, 400.0)), params, approved = emptySet(), blocked = emptySet(), bikeways = bikeways))
            .plans.getValue(RideMode.TIME).rideM

    @Test
    fun `maliyet - bisiklet yolu kenarı 0,85 (ayarlanabilir), süre ve enerji aynı`() {
        val plain = model(TestGraphs.single(residential))
        val bike = model(TestGraphs.single(mapOf("highway" to "residential", "cycleway" to "right")))
        val lane = model(TestGraphs.single(mapOf("highway" to "residential", "cycleway:right" to "lane")))
        assertEquals(0.85, RideParams().bikewayFactor, 0.0)
        assertEquals(plain.cost[0], bike.cost[0], 1e-9) // cycleway=right bisiklet yolu değil
        for (dir in 0..1) {
            assertEquals(plain.cost[dir] * 0.85, lane.cost[dir], 1e-9)
            assertEquals(plain.time[dir], lane.time[dir], 1e-9)
            assertEquals(plain.wh[dir], lane.wh[dir], 1e-9)
        }
        val g = TestGraphs.single(mapOf("highway" to "residential", "cycleway:right" to "lane"))
        assertEquals(plain.cost[0], model(g, RideParams(bikewayFactor = 1.0)).cost[0], 1e-9)
        assertEquals(plain.cost[0] * 0.6, model(g, RideParams(bikewayFactor = 0.6)).cost[0], 1e-9)
    }

    @Test
    fun `elle işaret OSM bayrağı gibi davranır (yalnız işaretli sokak)`() {
        val g = TestGraphs.single(residential)
        assertEquals(model(g).cost[0] * 0.85, model(g, bikeways = setOf(500)).cost[0], 1e-9)
        assertEquals(model(g).cost[0], model(g, bikeways = setOf(999)).cost[0], 1e-9)
    }

    @Test
    fun `değişmez - tek yön ve yalnız onaylı ters yön kuralı - ters yönde bisiklet indirimi yok`() {
        val oneway = mapOf("highway" to "residential", "oneway" to "yes")
        val onewayLane = mapOf("highway" to "residential", "oneway" to "yes", "cycleway:right" to "lane")
        val plain = TestGraphs.single(oneway)
        val bike = TestGraphs.single(onewayLane)
        assertTrue(model(bike).allowed(0, 0))
        assertFalse(model(bike).allowed(0, 1)) // bisiklet şeridi ters yönü açmaz
        assertFalse(model(plain, bikeways = setOf(500)).allowed(0, 1)) // elle işaret de açmaz
        assertEquals(model(plain).cost[0] * 0.85, model(bike).cost[0], 1e-9)
        // Onaylı (ana cadde olmayan) sokakta ters yön serbest ama indirim almaz.
        assertTrue(model(bike, approved = setOf(500)).allowed(0, 1))
        assertEquals(model(plain, approved = setOf(500)).cost[1], model(bike, approved = setOf(500)).cost[1], 1e-9)
        // Ana caddede onay ters yönü açmaz (bisiklet pisti olsa da).
        val main = TestGraphs.single(mapOf("highway" to "primary", "oneway" to "yes", "cycleway:right" to "track"))
        assertFalse(model(main, approved = setOf(500)).allowed(0, 1))
    }

    @Test
    fun `rota tercihi - 40 m uzun bisiklet yolu 0,85 ile seçilir, 0,95 ve 1,00 ile seçilmez`() {
        assertEquals(440.0, ride(TestGraphs.parallel()), 5.0) // bisiklet yolu yok: kısa batı
        val lane = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway:both" to "lane"))
        assertEquals(480.0, ride(lane), 5.0)
        assertEquals(440.0, ride(lane, RideParams(bikewayFactor = 0.95)), 5.0)
        assertEquals(440.0, ride(lane, RideParams(bikewayFactor = 1.0)), 5.0)
        assertEquals(480.0, ride(TestGraphs.parallel(), bikeways = eastWays), 5.0) // elle işaret
    }

    @Test
    fun `A yıldız sezgisi bisiklet çarpanı kadar küçülür (rota en iyi kalır) - bisiklet yolu yoksa aynen`() {
        assertEquals(1.0, model(TestGraphs.parallel()).heuristicScale, 0.0)
        assertEquals(0.85, model(TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))).heuristicScale, 0.0)
        assertEquals(0.85, model(TestGraphs.parallel(), bikeways = setOf(401)).heuristicScale, 0.0)
        assertEquals(1.0, model(TestGraphs.parallel(), RideParams(bikewayFactor = 1.0), bikeways = setOf(401)).heuristicScale, 0.0)
    }
}
