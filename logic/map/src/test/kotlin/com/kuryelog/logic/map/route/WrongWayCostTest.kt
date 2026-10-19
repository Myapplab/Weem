package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.buildRoadGraph
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `test/ww_green_two_way_test.dart` (4 test); `MAP.md` 4.6. Ekim planı madde 10b (F6.6): "Ters yönde sorun yaşadın mı?" →
 * Hayır ile onaylanan (yeşil) ve hiç sorun bildirilmemiş sokak rotada iki yönlü sayılır; "Evet, sorun" (oran > 0) eski cezayı geri
 * getirir; eşik aşılınca hiç kullanılmaz. Ana caddeler onaylanmaz.
 */
class WrongWayCostTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    /**
     * A(0,0) → B(0,400): doğrudan tek yönlü way 100 (B → A yönünde, A'dan B'ye ters; 400 m) ve yasal dolambaç
     * way 400 (doğuya 90 m açılır, ~439 m, yalnız %10 uzun).
     */
    private fun graph(cls: String = "residential"): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(400.0), lon(0.0))
        raw.addNode(4, lat(200.0), lon(90.0))
        raw.addWay(OsmWay(100, listOf(2, 1), mapOf("highway" to cls, "oneway" to "yes")))
        raw.addWay(OsmWay(400, listOf(1, 4, 2), mapOf("highway" to "residential")))
        return buildRoadGraph(raw) { _, _ -> 0.0 }
    }

    private fun model(
        g: RoadGraph,
        approved: Set<Long> = emptySet(),
        wwRatio: Map<Long, Double> = emptyMap(),
        wwAvoid: Set<Long> = emptySet(),
    ) = CostModel(g, RideMode.TIME, RideParams(), approved = approved, blocked = emptySet(), wwRatio = wwRatio, wwAvoid = wwAvoid)

    @Test
    fun `onaysız ters yön kullanılmaz`() {
        assertEquals(0.0, routeLeg(model(graph()), a, b).wrongWayM, 0.0)
    }

    @Test
    fun `onaylı + temiz (yeşil) sokak iki yönlü - yüzde 10 uzun yasal yol yerine ters yön`() {
        val leg = routeLeg(model(graph(), approved = setOf(100)), a, b)
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(400.0, leg.wrongWayM, 5.0) // bilgi olarak kalır
    }

    @Test
    fun `Evet sorun sonrası (oran büyük 0) eski ceza - yasal yol`() {
        assertEquals(0.0, routeLeg(model(graph(), approved = setOf(100), wwRatio = mapOf(100L to 0.1)), a, b).wrongWayM, 0.0)
        assertEquals(0.0, routeLeg(model(graph(), approved = setOf(100), wwAvoid = setOf(100)), a, b).wrongWayM, 0.0)
    }

    @Test
    fun `ana cadde onaylı olsa da ters kullanılmaz`() {
        assertEquals(0.0, routeLeg(model(graph(cls = "primary"), approved = setOf(100)), a, b).wrongWayM, 0.0)
    }
}
