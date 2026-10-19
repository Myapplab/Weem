package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.graph.RoadGraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/router_test.dart` işçi (3 test) + `test/v140_features_test.dart` "Regresyon: elle ters yön izni arka plan rota motorunda
 * da uygulanır" (1 test); `MAP.md` 3.14. Dart `RouteWorker` → `RouteEngine`.
 */
class RouteEngineTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun request(g: RoadGraph, blocked: Set<Long> = emptySet(), edges: Set<Int> = emptySet()) =
        PlanRequest(g, a, listOf(b), RideParams(), approved = emptySet(), blocked = blocked, wrongWayEdges = edges)

    /** Dart: "arka plan rota motoru sonraki sorguyu ve yeni haritayı kullanır". */
    @Test
    fun engineUsesNextQueryAndNewMap() = runBlocking {
        RouteEngine().use { engine ->
            val g = TestGraphs.main()
            val first = engine.compute(request(g))
            assertTrue(first.plans.isNotEmpty())
            assertEquals(400.0, first.plans.getValue(RideMode.TIME).rideM, 5.0)
            val second = engine.compute(request(g, blocked = setOf(100)))
            assertEquals(800.0, second.plans.getValue(RideMode.TIME).rideM, 5.0)
            val newGraph = TestGraphs.main(directTags = mapOf("highway" to "residential", "access" to "private"))
            val updated = engine.compute(request(newGraph))
            assertEquals(800.0, updated.plans.getValue(RideMode.TIME).rideM, 5.0)
        }
    }

    /**
     * Dart: "çöken rota işçisi yenilenir; sonraki istek 25 sn kilitlenmez". Regresyon: eski işçi ölünce başlatma/gönderme önbellekte kalıyordu ve her yeni rota isteği 25 sn bekleyip
     * başarısız oluyordu.
     */
    @Test
    fun crashedWorkerIsRenewedAndNextRequestDoesNotStall() = runBlocking {
        RouteEngine(timeoutMs = 4_000).use { engine ->
            val g = TestGraphs.main()
            assertTrue(engine.compute(request(g)).plans.isNotEmpty())
            engine.debugKillWorker()
            Thread.sleep(300)
            val start = System.nanoTime()
            val again = engine.compute(request(g))
            assertEquals(400.0, again.plans.getValue(RideMode.TIME).rideM, 5.0)
            assertTrue((System.nanoTime() - start) / 1_000_000 < 4_000)
        }
    }

    /** Dart: "kapatılan rota motoru yeni işi kabul etmez". */
    @Test
    fun closedEngineRejectsNewWork() {
        val engine = RouteEngine().also { it.close() }
        assertThrows(IllegalStateException::class.java) { runBlocking { engine.compute(request(TestGraphs.main())) } }
    }

    /** Dart: "Regresyon: elle ters yön izni arka plan rota motorunda da uygulanır". */
    @Test
    fun manualWrongWayPermissionAlsoAppliesInEngine() = runBlocking {
        val g = TestGraphs.mainOneway()
        RouteEngine().use { engine ->
            val closed = engine.compute(request(g))
            assertEquals(1200.0, closed.plans.getValue(RideMode.TIME).rideM, 5.0)
            val allowed = engine.compute(request(g, edges = g.edgesOfOsmWay(100).toSet()))
            assertEquals(400.0, allowed.plans.getValue(RideMode.TIME).rideM, 5.0)
            assertEquals(400.0, allowed.plans.getValue(RideMode.TIME).legs.single().wrongWayM, 5.0)
        }
    }

    /** `bikeway_e13_test.dart` "RouteWorker.compute elle işareti isolate'e taşır". */
    @Test
    fun engineCarriesManualBikewayMarks() = runBlocking {
        val g = TestGraphs.parallel()
        fun bikeRequest(bikeways: Set<Long>) = PlanRequest(
            g, p(0.0, 0.0), listOf(p(0.0, 400.0)), RideParams(), approved = emptySet(), blocked = emptySet(), bikeways = bikeways,
        )
        RouteEngine().use { engine ->
            assertEquals(440.0, engine.compute(bikeRequest(emptySet())).plans.getValue(RideMode.TIME).rideM, 5.0)
            assertEquals(480.0, engine.compute(bikeRequest(setOf(401L, 402L, 403L))).plans.getValue(RideMode.TIME).rideM, 5.0)
        }
    }
}
