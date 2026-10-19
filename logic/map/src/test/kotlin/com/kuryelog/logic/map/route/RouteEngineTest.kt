package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs.EAST_WAYS
import com.kuryelog.logic.map.route.TestGraphs.p
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arka plan rota motoru (YolGo `RouteWorker`; MAP.md 3.11, 3.14): `router_test.dart` "işçi" 3 test + `bikeway_e13_test.dart`
 * "RouteWorker.compute elle işareti taşır" (1) + `v140_features_test.dart` "elle ters yön izni arka plan rota motorunda da
 * uygulanır" (1). "Çökme" benzetimi [RouteEngine.debugKillWorker]; kapatılan motor `IllegalStateException` (Dart `StateError`).
 * Uygulama: A6.4.
 */
class RouteEngineTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun request(g: RoadGraph, blocked: Set<Long> = emptySet(), bikeways: Set<Long> = emptySet(), wrongWayEdges: Set<Int> = emptySet()) =
        PlanRequest(
            graph = g, origin = a, stops = listOf(b), params = RideParams(), approved = emptySet(), blocked = blocked,
            bikeways = bikeways, wrongWayEdges = wrongWayEdges,
        )

    @Test
    fun `arka plan rota motoru sonraki sorguyu ve yeni haritayı kullanır`() = runBlocking {
        val engine = RouteEngine()
        try {
            val g = TestGraphs.directAndDetour()
            val first = engine.compute(request(g))
            assertTrue(first.plans.isNotEmpty())
            assertEquals(400.0, first.plans[RideMode.TIME]!!.rideM, 5.0)
            val second = engine.compute(request(g, blocked = setOf(100L)))
            assertEquals(800.0, second.plans[RideMode.TIME]!!.rideM, 5.0)
            val newGraph = TestGraphs.directAndDetour(directTags = mapOf("highway" to "residential", "access" to "private"))
            val updated = engine.compute(request(newGraph))
            assertEquals(800.0, updated.plans[RideMode.TIME]!!.rideM, 5.0)
        } finally {
            engine.dispose()
        }
    }

    @Test
    fun `çöken rota işçisi yenilenir, sonraki istek 25 sn kilitlenmez`() = runBlocking {
        // Regresyon: eski işçi ölünce önbellekte kalıyordu ve her yeni rota isteği 25 sn bekleyip başarısız oluyordu.
        val engine = RouteEngine(timeoutMs = 4_000)
        try {
            val g = TestGraphs.directAndDetour()
            assertTrue(engine.compute(request(g)).plans.isNotEmpty())
            engine.debugKillWorker()
            Thread.sleep(300)
            val started = System.nanoTime()
            val again = engine.compute(request(g))
            assertEquals(400.0, again.plans[RideMode.TIME]!!.rideM, 5.0)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 4_000)
        } finally {
            engine.dispose()
        }
    }

    @Test
    fun `kapatılan rota motoru yeni işi kabul etmez`() {
        val engine = RouteEngine()
        engine.dispose()
        assertThrows(IllegalStateException::class.java) { runBlocking { engine.compute(request(TestGraphs.directAndDetour())) } }
    }

    @Test
    fun `rota motoru elle bisiklet işaretini taşır`() = runBlocking {
        val engine = RouteEngine()
        try {
            val g = TestGraphs.parallel()
            assertEquals(440.0, engine.compute(request(g)).plans[RideMode.TIME]!!.rideM, 5.0)
            assertEquals(480.0, engine.compute(request(g, bikeways = EAST_WAYS)).plans[RideMode.TIME]!!.rideM, 5.0)
        } finally {
            engine.dispose()
        }
    }

    @Test
    fun `Regresyon elle ters yön izni arka plan rota motorunda da uygulanır`() = runBlocking {
        // YolGo işçisi wrongWayEdges'i isolate'e taşımıyordu: uygulamada rota hep işçide hesaplandığı için ana caddedeki elle izin
        // hiç kullanılmıyordu (RouterTest CostModel'i doğrudan sınar).
        val engine = RouteEngine()
        try {
            val g = TestGraphs.mainOneway()
            val closed = engine.compute(request(g))
            assertEquals(1200.0, closed.plans[RideMode.TIME]!!.rideM, 5.0)
            val allowed = engine.compute(request(g, wrongWayEdges = g.edgesOfOsmWay(100).toSet()))
            assertEquals(400.0, allowed.plans[RideMode.TIME]!!.rideM, 5.0)
            assertEquals(400.0, allowed.plans[RideMode.TIME]!!.legs.single().wrongWayM, 5.0)
        } finally {
            engine.dispose()
        }
    }
}
