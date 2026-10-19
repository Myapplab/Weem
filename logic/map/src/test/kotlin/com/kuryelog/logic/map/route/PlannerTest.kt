package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.route.TestGraphs.p
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `router_test.dart` planlayıcı 2 test (A6.2a, MAP.md 3.14): çifte sipariş ve öğrenilmiş kötü yol (Y12 girdisi). Uygulama: A6.4. */
class PlannerTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    @Test
    fun `çifte sipariş planlayıcı iki durağı sıralar`() {
        val r = Planner.computePlans(
            PlanRequest(
                graph = TestGraphs.directAndDetour(),
                origin = a,
                stops = listOf(b, p(200.0, 0.0)), // B (400 m kuzey) ve E (200 m doğu)
                params = RideParams(),
                approved = emptySet(),
                blocked = emptySet(),
            ),
        )
        assertNull(r.error)
        val plan = r.plans[RideMode.TIME]!!
        assertEquals(2, plan.legs.size)
        assertEquals(2, plan.order.size)
    }

    @Test
    fun `elle kötü puan verilen yol temiz alternatif varken tamamen çıkarılır`() {
        val g = TestGraphs.directAndDetour()
        val result = Planner.computePlans(
            PlanRequest(
                graph = g, origin = a, stops = listOf(b), params = RideParams(), approved = emptySet(), blocked = emptySet(),
                learned = mapOf(100L to LearnedQuality(q = 0.2, c = 1.0, r = 25)),
            ),
        )
        assertTrue(result.plans.isNotEmpty())
        for (plan in result.plans.values) {
            assertEquals(800.0, plan.rideM, 5.0)
            assertFalse(plan.legs.single().segs.any { g.osmIdOf(it.edge) == 100L })
        }
    }
}
