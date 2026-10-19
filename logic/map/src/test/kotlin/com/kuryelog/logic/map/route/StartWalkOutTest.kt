package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.TestGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/router_test.dart` Y7: yaya bölgesinde (Nəsimi/Fəvvarələr) "sürülebilir yol yok" / "kurallara uyan rota yok" yerine en
 * yakın sürülebilir yola yaya yolu tarifi, sonra scooter rotası (3 test); `MAP.md` 3.14.
 */
class StartWalkOutTest {
    private fun m(stub: Boolean = false) =
        CostModel(TestGraphs.plaza(stub = stub), RideMode.TIME, RideParams(), approved = emptySet(), blocked = emptySet())

    @Test
    fun `Y7 yaya yolundan en yakın sürülebilir yola yürür, sonra sürer`() {
        val leg = routeLeg(m(), p(0.0, 0.0), p(400.0, 250.0))
        assertEquals(250.0, leg.startWalkM, 5.0)
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(lat(0.0), leg.startWalk.first().lat, 1e-9)
        assertEquals(lat(250.0), leg.startWalk.last().lat, 1e-6)
        assertEquals("Önce yaya yolundan en yakın sürülebilir yola yürü", leg.warnings.first())
    }

    @Test
    fun `Y7 yakındaki kopuk yol kurallara uyan rota yok verince de yürüyüşle çözülür`() {
        val leg = routeLeg(m(stub = true), p(0.0, 0.0), p(400.0, 250.0))
        assertEquals(250.0, leg.startWalkM, 5.0)
        assertEquals(400.0, leg.rideM, 5.0)
    }

    @Test
    fun `Y7 yaya yolu yoksa hata aynen kalır - normal başlangıç değişmez`() {
        assertThrows(RoutingException::class.java) { routeLeg(m(), p(-900.0, -900.0), p(400.0, 250.0)) }
        val normal = routeLeg(m(), p(0.0, 250.0), p(400.0, 250.0))
        assertTrue(normal.startWalk.isEmpty())
        assertEquals(0.0, normal.startWalkM, 0.0)
    }
}
