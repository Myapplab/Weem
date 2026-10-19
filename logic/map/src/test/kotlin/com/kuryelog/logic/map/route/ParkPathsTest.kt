package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.graph.RoadGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/park_paths_test.dart` test 3–4 (rota); `MAP.md` 3.14. Park yolu onaysız sürülebilir, hız tavanı yok;
 * park dışındaki ya da `bicycle=no` yaya yolu eskisi gibi kapalı.
 */
class ParkPathsTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun m(g: RoadGraph) = CostModel(g, RideMode.TIME, RideParams(), approved = emptySet(), blocked = emptySet())

    @Test
    fun `rota park içinden geçer (onay gerekmez) - park dışındaki yaya yolu eskisi gibi kapalı`() {
        assertEquals(400.0, routeLeg(m(TestGraphs.park()), a, b).rideM, 5.0)
        assertEquals(800.0, routeLeg(m(TestGraphs.park(park = null)), a, b).rideM, 10.0)
        assertEquals(
            800.0,
            routeLeg(m(TestGraphs.park(pathTags = mapOf("highway" to "footway", "bicycle" to "no"))), a, b).rideM,
            10.0,
        )
    }

    @Test
    fun `park yolunda hız tavanı yok (onaylı yaya yolundaki 15 km-s uygulanmaz)`() {
        val leg = routeLeg(m(TestGraphs.park()), a, b)
        val kmh = leg.rideM / leg.timeS * 3.6
        assertTrue("$kmh km/s", kmh > 15.5)
    }
}
