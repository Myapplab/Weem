package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs.p
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `park_paths_test.dart` 3–4 (A6.2a, MAP.md 3.14): park içindeki yaya yolu rotada sürülebilir (onay gerekmez), hız tavanı
 * yok; park dışındaki yaya yolu eskisi gibi kapalı. Bayrak payı (1–2): `graph.ParkPathsTest`. Uygulama: A6.4.
 */
class ParkPathsTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun m(g: RoadGraph) = CostModel(g, mode = RideMode.TIME, params = RideParams(), approved = emptySet(), blocked = emptySet())

    @Test
    fun `rota park içinden geçer (onay gerekmez), park dışındaki yaya yolu eskisi gibi kapalı`() {
        assertEquals(400.0, Router.routeLeg(m(TestGraphs.parkGraph()), a, b).rideM, 5.0)
        assertEquals(800.0, Router.routeLeg(m(TestGraphs.parkGraph(park = null)), a, b).rideM, 10.0)
        val noBike = TestGraphs.parkGraph(pathTags = mapOf("highway" to "footway", "bicycle" to "no"))
        assertEquals(800.0, Router.routeLeg(m(noBike), a, b).rideM, 10.0)
    }

    @Test
    fun `park yolunda hız tavanı yok (onaylı yaya yolundaki 15 km s uygulanmaz)`() {
        val leg = Router.routeLeg(m(TestGraphs.parkGraph()), a, b)
        val kmh = leg.rideM / leg.timeS * 3.6
        assertTrue(kmh > 15.5)
    }
}
