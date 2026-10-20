package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag
import com.kuryelog.logic.map.route.TestGraphs.p
import com.kuryelog.logic.map.route.Quality.blendQuality
import com.kuryelog.logic.map.route.Quality.learnedConfidence
import com.kuryelog.logic.map.route.Quality.learnedScore
import com.kuryelog.logic.map.route.Quality.priorQuality
import com.kuryelog.logic.map.route.Quality.wrongWayRatio
import com.kuryelog.logic.map.route.Quality.wrongWaySmoothed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `quality_test.dart` (9 test; A6.2a, MAP.md 9.11): yol kalitesi = akıcılık (dur-kalk, sarsıntı). Hepsi A6.4'te yeşil olur
 * (`Quality.kt` ve `CostModel` girdileri); A6.11 bozmadan korur. Uygulama: A6.4.
 */
class QualityTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun m(
        g: RoadGraph,
        tolerance: Double = 0.10,
        learned: Map<Long, LearnedQuality> = emptyMap(),
        ghost: Set<Int> = emptySet(),
        approved: Set<Long> = emptySet(),
        wwRatio: Map<Long, Double> = emptyMap(),
        wwAvoid: Set<Long> = emptySet(),
    ) = CostModel(
        g, mode = RideMode.TIME, params = RideParams(qualityWeight = tolerance), approved = approved, blocked = emptySet(),
        ghost = ghost, learned = learned, wwRatio = wwRatio, wwAvoid = wwAvoid,
    )

    private val bad = LearnedQuality(q = 0.1, c = 1.0, r = 0)
    private val good = LearnedQuality(q = 1.0, c = 1.0, r = 0)

    @Test
    fun `ön tahmin ara sokak ana caddeden, asfalt topraktan kaliteli`() {
        assertTrue(priorQuality(RoadClass.RESIDENTIAL, 0) > priorQuality(RoadClass.PRIMARY, 0))
        assertTrue(priorQuality(RoadClass.RESIDENTIAL, WayFlag.UNPAVED) < 0.4)
        assertTrue(priorQuality(RoadClass.RESIDENTIAL, WayFlag.ROUGH) <= 0.5)
    }

    @Test
    fun `öğrenilen skor dur-kalk arttıkça düşer, güven sürüşle artar`() {
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
    fun `karışım az veride ön tahmine yakın, sürücünün girdiği yüzde baskın`() {
        val prior = 0.82
        assertEquals(prior, blendQuality(prior, null), 0.0)
        assertEquals(0.748, blendQuality(prior, LearnedQuality(q = 0.1, c = 0.1, r = 0)), 0.01)
        assertEquals(0.1, blendQuality(prior, LearnedQuality(q = 0.1, c = 1.0, r = 0)), 1e-9)
        assertEquals(0.9, blendQuality(prior, LearnedQuality(q = 0.0, c = 0.0, r = QualityRating.GOOD)), 0.0)
        assertEquals(0.25, blendQuality(prior, LearnedQuality(q = 0.0, c = 0.0, r = QualityRating.BAD)), 0.0)
        assertEquals(0.4, blendQuality(prior, LearnedQuality(q = 1.0, c = 1.0, r = 40)), 0.0) // girilen %40
        assertEquals(90, QualityRating.migrate(1))
        assertEquals(25, QualityRating.migrate(-1))
    }

    @Test
    fun `eşit iki yol kalitesi yüksek olan seçilir`() {
        val g = TestGraphs.twoWays()
        val westBad = Router.routeLeg(m(g, learned = mapOf(300L to bad)), a, b)
        assertTrue(westBad.streets.contains("Şərq küçəsi"))
        assertFalse(westBad.streets.contains("Batı küçəsi"))
        val eastBad = Router.routeLeg(m(g, learned = mapOf(400L to bad)), a, b)
        assertTrue(eastBad.streets.contains("Batı küçəsi"))
        assertTrue(eastBad.quality > 0.8)
    }

    @Test
    fun `tolerans kötü işaretlenmemiş yolda küçük farkta kalite, büyük farkta süre`() {
        // Batı 400+ m (düz yakın), doğu %16 uzun
        val g = TestGraphs.twoWays(westOffset = 1.0, eastOffset = 120.0)
        val learned = mapOf(
            300L to LearnedQuality(q = 0.0, c = 0.0, r = 30), // %25 ve altı yeni kötü yol kuralına girer
            400L to LearnedQuality(q = 0.0, c = 0.0, r = QualityRating.GOOD),
        )
        val tight = Router.routeLeg(m(g, tolerance = 0.10, learned = learned), a, b)
        assertEquals("Batı küçəsi", tight.streets.first()) // %16 fark > %10 tolerans → kısa yol
        val loose = Router.routeLeg(m(g, tolerance = 0.30, learned = learned), a, b)
        assertEquals("Şərq küçəsi", loose.streets.first()) // %30 tolerans → kaliteli yol
        val off = Router.routeLeg(m(g, tolerance = 0.0, learned = mapOf(300L to LearnedQuality(q = 0.30, c = 1.0, r = 30), 400L to good)), a, b)
        assertEquals("Batı küçəsi", off.streets.first()) // tolerans 0: kalite etkisiz
    }

    @Test
    fun `yol yok işaretli parça hiçbir koşulda kullanılmaz`() {
        val g = TestGraphs.directAndDetour()
        val ghost = g.edgesOfOsmWay(100).toSet()
        val leg = Router.routeLeg(m(g, ghost = ghost), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
        // planlayıcının gevşetilmiş denemelerinde de girmez
        val r = Planner.computePlans(PlanRequest(g, a, listOf(b), RideParams(), emptySet(), emptySet(), ghost = ghost))
        assertEquals(800.0, r.plans[RideMode.TIME]!!.rideM, 5.0)
    }

    @Test
    fun `ters yön sorun oranı eşiği aşan onaylı sokağa ters girilmez`() {
        val g = TestGraphs.directAndDetour(directTags = mapOf("highway" to "residential", "oneway" to "yes"), directReversed = true)
        val ok = Router.routeLeg(m(g, approved = setOf(100L)), a, b)
        assertEquals(400.0, ok.rideM, 5.0)
        assertEquals(400.0, ok.wrongWayM, 5.0)
        // Küçük oran: hâlâ ters yönden gider, risk rotaya yazılır
        val risky = Router.routeLeg(m(g, approved = setOf(100L), wwRatio = mapOf(100L to 0.02)), a, b)
        assertEquals(400.0, risky.wrongWayM, 5.0)
        assertEquals(0.02, risky.wrongWayRisk, 1e-6)
        // Yüksek oran: ters yön pahalılaşır, dolambaç seçilir
        val costly = Router.routeLeg(m(g, approved = setOf(100L), wwRatio = mapOf(100L to 0.6)), a, b)
        assertEquals(800.0, costly.rideM, 5.0)
        val avoided = Router.routeLeg(m(g, approved = setOf(100L), wwAvoid = setOf(100L)), a, b)
        assertEquals(800.0, avoided.rideM, 5.0)
        assertEquals(0.0, avoided.wrongWayM, 0.0)
    }

    @Test
    fun `rota parçaları sırayla ve toplam uzunluk tutarlı`() {
        val g = TestGraphs.directAndDetour()
        val leg = Router.routeLeg(m(g, ghost = g.edgesOfOsmWay(100).toSet()), a, b)
        assertTrue(leg.segs.isNotEmpty())
        assertEquals(leg.rideM, leg.segs.sumOf { it.lenM }, 1.0)
        for (i in 1 until leg.segs.size) assertTrue(leg.segs[i].startM >= leg.segs[i - 1].startM)
    }

    @Test
    fun `ters yön oranı ham ve yumuşatılmış`() {
        assertEquals(0.0, wrongWayRatio(0, 0.0), 0.0)
        assertEquals(0.25, wrongWayRatio(4, 1.0), 0.0)
        assertEquals(0.55, wrongWaySmoothed(1, 1.0), 1e-9)
        assertTrue(wrongWaySmoothed(9, 0.0) < 0.02)
    }
}
