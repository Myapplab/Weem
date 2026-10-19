package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag
import com.kuryelog.logic.map.graph.buildRoadGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `test/quality_test.dart` (9 test); `MAP.md` 9.11. Sentetik graflar `OsmRaw` + `buildRoadGraph(raw, düz yükseklik)`. */
class QualityTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    /** A(0,0) → B(0,400) iki yol: batıdan (way 300) ve doğudan (way 400); [westOffset] farklıysa uzunluklar farklı olur. */
    private fun twoWays(westOffset: Double = 100.0, eastOffset: Double = 100.0): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)) // A
        raw.addNode(2, lat(400.0), lon(0.0)) // B
        raw.addNode(3, lat(200.0), lon(-westOffset)) // C (batı)
        raw.addNode(4, lat(200.0), lon(eastOffset)) // D (doğu)
        raw.addWay(OsmWay(300, listOf(1, 3, 2), mapOf("highway" to "residential", "name" to "Batı küçəsi")))
        raw.addWay(OsmWay(400, listOf(1, 4, 2), mapOf("highway" to "residential", "name" to "Şərq küçəsi")))
        return buildRoadGraph(raw) { _, _ -> 0.0 }
    }

    /** A(0,0) → B(0,400): doğrudan way 100 (400 m), dolambaç 201-203 (800 m). */
    private fun directAndDetour(directTags: Map<String, String> = mapOf("highway" to "residential"), reversed: Boolean = false): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(200.0), lon(0.0))
        raw.addNode(3, lat(400.0), lon(0.0))
        raw.addNode(4, lat(0.0), lon(200.0))
        raw.addNode(5, lat(400.0), lon(200.0))
        raw.addWay(OsmWay(100, if (reversed) listOf(3, 2, 1) else listOf(1, 2, 3), directTags))
        raw.addWay(OsmWay(201, listOf(1, 4), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(202, listOf(4, 5), mapOf("highway" to "residential")))
        raw.addWay(OsmWay(203, listOf(5, 3), mapOf("highway" to "residential")))
        return buildRoadGraph(raw) { _, _ -> 0.0 }
    }

    private fun m(
        g: RoadGraph,
        tolerance: Double = 0.10,
        learned: Map<Long, LearnedQuality> = emptyMap(),
        ghost: Set<Int> = emptySet(),
        approved: Set<Long> = emptySet(),
        wwRatio: Map<Long, Double> = emptyMap(),
        wwAvoid: Set<Long> = emptySet(),
    ) = CostModel(
        g, RideMode.TIME, RideParams(qualityWeight = tolerance), approved = approved, blocked = emptySet(),
        ghost = ghost, learned = learned, wwRatio = wwRatio, wwAvoid = wwAvoid,
    )

    private val bad = LearnedQuality(q = 0.1, c = 1.0, r = 0)
    private val good = LearnedQuality(q = 1.0, c = 1.0, r = 0)

    @Test
    fun `ön tahmin - ara sokak ana caddeden, asfalt topraktan kaliteli`() {
        assertTrue(priorQuality(RoadClass.RESIDENTIAL, 0) > priorQuality(RoadClass.PRIMARY, 0))
        assertTrue(priorQuality(RoadClass.RESIDENTIAL, WayFlag.UNPAVED) < 0.4)
        assertTrue(priorQuality(RoadClass.RESIDENTIAL, WayFlag.ROUGH) <= 0.5)
    }

    @Test
    fun `öğrenilen skor - dur-kalk arttıkça düşer, güven sürüşle artar`() {
        val s0 = learnedScore(meters = 1000.0, stops = 0.0)
        val s2 = learnedScore(meters = 1000.0, stops = 2.0)
        val s5 = learnedScore(meters = 1000.0, stops = 5.0)
        assertEquals(1.0, s0, 1e-9)
        assertEquals(0.78, s2, 0.01)
        assertTrue(s5 < s2)
        assertTrue(learnedScore(meters = 1000.0, stops = 0.0, vib = 5.0) < s0) // sarsıntı
        assertTrue(learnedConfidence(80.0) < learnedConfidence(800.0))
        assertEquals(1.0, learnedConfidence(5000.0), 0.0)
    }

    @Test
    fun `karışım - az veride ön tahmine yakın, sürücünün girdiği yüzde baskın`() {
        val prior = 0.82
        assertEquals(prior, blendQuality(prior, null), 0.0)
        assertEquals(0.748, blendQuality(prior, LearnedQuality(0.1, 0.1, 0)), 0.01)
        assertEquals(0.1, blendQuality(prior, LearnedQuality(0.1, 1.0, 0)), 1e-9)
        assertEquals(0.9, blendQuality(prior, LearnedQuality(0.0, 0.0, QualityRating.GOOD)), 0.0)
        assertEquals(0.25, blendQuality(prior, LearnedQuality(0.0, 0.0, QualityRating.BAD)), 0.0)
        assertEquals(0.4, blendQuality(prior, LearnedQuality(1.0, 1.0, 40)), 0.0) // girilen %40
        assertEquals(90, QualityRating.migrate(1))
        assertEquals(25, QualityRating.migrate(-1))
    }

    @Test
    fun `eşit iki yol - kalitesi yüksek olan seçilir`() {
        val g = twoWays()
        val westBad = routeLeg(m(g, learned = mapOf(300L to bad)), a, b)
        assertTrue(westBad.streets.contains("Şərq küçəsi"))
        assertFalse(westBad.streets.contains("Batı küçəsi"))
        val eastBad = routeLeg(m(g, learned = mapOf(400L to bad)), a, b)
        assertTrue(eastBad.streets.contains("Batı küçəsi"))
        assertTrue(eastBad.quality > 0.8)
    }

    @Test
    fun `tolerans - kötü işaretlenmemiş yolda küçük farkta kalite, büyük farkta süre`() {
        // Batı 400+ m (düz yakın), doğu %16 uzun
        val g = twoWays(westOffset = 1.0, eastOffset = 120.0)
        val learned = mapOf(
            300L to LearnedQuality(q = 0.0, c = 0.0, r = 30), // %25 ve altı yeni kötü yol kuralına girer
            400L to LearnedQuality(q = 0.0, c = 0.0, r = QualityRating.GOOD),
        )
        val tight = routeLeg(m(g, tolerance = 0.10, learned = learned), a, b)
        assertEquals("Batı küçəsi", tight.streets.first()) // %16 fark > %10 tolerans → kısa yol
        val loose = routeLeg(m(g, tolerance = 0.30, learned = learned), a, b)
        assertEquals("Şərq küçəsi", loose.streets.first()) // %30 tolerans → kaliteli yol
        val off = routeLeg(m(g, tolerance = 0.0, learned = mapOf(300L to LearnedQuality(0.30, 1.0, 30), 400L to good)), a, b)
        assertEquals("Batı küçəsi", off.streets.first()) // tolerans 0: kalite etkisiz
    }

    @Test
    fun `yol yok - işaretli parça hiçbir koşulda kullanılmaz`() {
        val g = directAndDetour()
        val ghost = g.edgesOfOsmWay(100).toSet()
        val leg = routeLeg(m(g, ghost = ghost), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
        // planlayıcının gevşetilmiş denemelerinde de girmez
        val r = computePlans(PlanRequest(g, a, listOf(b), RideParams(), approved = emptySet(), blocked = emptySet(), ghost = ghost))
        assertEquals(800.0, r.plans.getValue(RideMode.TIME).rideM, 5.0)
    }

    @Test
    fun `ters yön - sorun oranı eşiği aşan onaylı sokağa ters girilmez`() {
        val g = directAndDetour(directTags = mapOf("highway" to "residential", "oneway" to "yes"), reversed = true)
        val ok = routeLeg(m(g, approved = setOf(100)), a, b)
        assertEquals(400.0, ok.rideM, 5.0)
        assertEquals(400.0, ok.wrongWayM, 5.0)
        // Küçük oran: hâlâ ters yönden gider, risk rotaya yazılır
        val risky = routeLeg(m(g, approved = setOf(100), wwRatio = mapOf(100L to 0.02)), a, b)
        assertEquals(400.0, risky.wrongWayM, 5.0)
        assertEquals(0.02, risky.wrongWayRisk, 1e-6)
        // Yüksek oran: ters yön pahalılaşır, dolambaç seçilir
        val costly = routeLeg(m(g, approved = setOf(100), wwRatio = mapOf(100L to 0.6)), a, b)
        assertEquals(800.0, costly.rideM, 5.0)
        val avoided = routeLeg(m(g, approved = setOf(100), wwAvoid = setOf(100)), a, b)
        assertEquals(800.0, avoided.rideM, 5.0)
        assertEquals(0.0, avoided.wrongWayM, 0.0)
    }

    @Test
    fun `rota parçaları sırayla ve toplam uzunluk tutarlı`() {
        val g = directAndDetour()
        val leg = routeLeg(m(g, ghost = g.edgesOfOsmWay(100).toSet()), a, b)
        assertTrue(leg.segs.isNotEmpty())
        assertEquals(leg.rideM, leg.segs.sumOf { it.lenM }, 1.0)
        for (i in 1 until leg.segs.size) assertTrue(leg.segs[i].startM >= leg.segs[i - 1].startM)
    }

    @Test
    fun `ters yön oranı - ham ve yumuşatılmış`() {
        assertEquals(0.0, wrongWayRatio(0, 0.0), 0.0)
        assertEquals(0.25, wrongWayRatio(4, 1.0), 0.0)
        assertEquals(0.55, wrongWaySmoothed(1, 1.0), 1e-9)
        assertTrue(wrongWaySmoothed(9, 0.0) < 0.02)
    }
}
