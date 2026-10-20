package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.route.TestGraphs.p
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `router_test.dart` "Y7" 3 test (A6.2a, MAP.md 3.14): yaya bölgesinde (Nəsimi/Fəvvarələr) "sürülebilir yol yok" yerine en
 * yakın sürülebilir yola yaya yolu tarifi, sonra scooter rotası. Uygulama: A6.4.
 */
class StartWalkOutTest {
    private fun model(g: com.kuryelog.logic.map.graph.RoadGraph) =
        CostModel(g, mode = RideMode.TIME, params = RideParams(), approved = emptySet(), blocked = emptySet())

    @Test
    fun `Y7 yaya yolundan en yakın sürülebilir yola yürür, sonra sürer`() {
        val leg = Router.routeLeg(model(TestGraphs.plazaGraph()), p(0.0, 0.0), p(400.0, 250.0))
        assertEquals(250.0, leg.startWalkM, 5.0)
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(TestGraphs.lat(0.0), leg.startWalk.first().lat, 1e-9)
        assertEquals(TestGraphs.lat(250.0), leg.startWalk.last().lat, 1e-6)
        assertEquals("Önce yaya yolundan en yakın sürülebilir yola yürü", leg.warnings.first().tr)
    }

    @Test
    fun `Y7 yakındaki kopuk yol kurallara uyan rota yok verince de yürüyüşle çözülür`() {
        val leg = Router.routeLeg(model(TestGraphs.plazaGraph(stub = true)), p(0.0, 0.0), p(400.0, 250.0))
        assertEquals(250.0, leg.startWalkM, 5.0)
        assertEquals(400.0, leg.rideM, 5.0)
    }

    @Test
    fun `Y7 yaya yolu yoksa hata aynen kalır, normal başlangıç değişmez`() {
        assertThrows(RoutingException::class.java) {
            Router.routeLeg(model(TestGraphs.plazaGraph()), p(-900.0, -900.0), p(400.0, 250.0))
        }
        val normal = Router.routeLeg(model(TestGraphs.plazaGraph()), p(0.0, 250.0), p(400.0, 250.0))
        assertTrue(normal.startWalk.isEmpty())
        assertEquals(0.0, normal.startWalkM, 0.0)
    }
}
