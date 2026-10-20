package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs.p
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `ww_green_two_way_test.dart` (4 test; A6.2a, MAP.md 4.6): "Ters yönde sorun yaşadın mı?" → Hayır ile onaylanan ve hiç
 * sorun bildirilmemiş sokak rotada iki yönlü sayılır (ters yön hız tavanı ve ×1,2 çarpanı yok). `wwRatio`/`wwAvoid` doğrudan
 * verilir (üretimi Bölüm 9, A6.11). Uygulama: A6.4.
 */
class WrongWayCostTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun m(
        g: RoadGraph,
        approved: Set<Long> = emptySet(),
        wwRatio: Map<Long, Double> = emptyMap(),
        wwAvoid: Set<Long> = emptySet(),
    ) = CostModel(g, mode = RideMode.TIME, params = RideParams(), approved = approved, blocked = emptySet(), wwRatio = wwRatio, wwAvoid = wwAvoid)

    @Test
    fun `onaysız ters yön kullanılmaz`() {
        assertEquals(0.0, Router.routeLeg(m(TestGraphs.wwGraph()), a, b).wrongWayM, 0.0)
    }

    @Test
    fun `onaylı ve temiz (yeşil) sokak iki yönlü, yüzde 10 uzun yasal yol yerine ters yön`() {
        val leg = Router.routeLeg(m(TestGraphs.wwGraph(), approved = setOf(100L)), a, b)
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(400.0, leg.wrongWayM, 5.0) // bilgi olarak kalır
    }

    @Test
    fun `Evet sorun sonrası (oran büyük sıfır) eski ceza, yasal yol`() {
        assertEquals(0.0, Router.routeLeg(m(TestGraphs.wwGraph(), approved = setOf(100L), wwRatio = mapOf(100L to 0.1)), a, b).wrongWayM, 0.0)
        assertEquals(0.0, Router.routeLeg(m(TestGraphs.wwGraph(), approved = setOf(100L), wwAvoid = setOf(100L)), a, b).wrongWayM, 0.0)
    }

    @Test
    fun `ana cadde onaylı olsa da ters kullanılmaz`() {
        assertEquals(0.0, Router.routeLeg(m(TestGraphs.wwGraph(cls = "primary"), approved = setOf(100L)), a, b).wrongWayM, 0.0)
    }
}
