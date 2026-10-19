package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.LocalMeters.utc
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.graph.RoadGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/router_test.dart` temel kurallar (18) + saat izni (1) + geometri (1) = 20 test, `test/v140_features_test.dart` Y3/Y8
 * rota payı (dönüş cezası, ana caddede elle ters yön = 2 test); `MAP.md` 3.14, 10.12. Fixture'lar [TestGraphs]. Çevrilmeyenler
 * (Y13 ölçülmüş yüzey, 7 test): `MAP.md` 16.4.
 */
class RouterTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun model(
        g: RoadGraph,
        mode: RideMode,
        maxGrade: Double = 0.18,
        approved: Set<Long> = emptySet(),
        blocked: Set<Long> = emptySet(),
        steep: Set<Long> = emptySet(),
    ) = CostModel(g, mode, RideParams().withMaxGrade(maxGrade), approved = approved, blocked = blocked, steep = steep)

    private fun assertRoutingError(contains: String? = null, block: () -> Unit) {
        val e = assertThrows(RoutingException::class.java) { block() }
        if (contains != null) assertTrue(e.message, e.message.contains(contains))
    }

    @Test
    fun `özel-kurum girişi onaylı olsa da araç rotası olmaz`() {
        val g = TestGraphs.main(directTags = mapOf("highway" to "residential", "access" to "private"))
        val leg = routeLeg(model(g, RideMode.TIME, approved = setOf(100)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `servis cepleri ana rota olarak kullanılmaz`() {
        val g = TestGraphs.main(directTags = mapOf("highway" to "service"))
        assertEquals(800.0, routeLeg(model(g, RideMode.TIME), a, b).rideM, 5.0)
    }

    @Test
    fun `bina içi 70 m uzak pin erişilebilir yol üzerinde biter`() {
        val g = TestGraphs.main()
        val leg = routeLeg(model(g, RideMode.TIME), a, p(-70.0, 200.0))
        assertTrue(leg.parkPoint.distanceTo(p(0.0, 200.0)) < 2)
        assertTrue(leg.warnings.isNotEmpty())
        assertTrue(leg.walk.isEmpty()) // bina-duvar üzerinden hayali çizgi yok
    }

    @Test
    fun `harita dışında kalan hedefin hatası bağlantı hatasından ayrılır`() {
        assertRoutingError("haritasının dışında") { routeLeg(model(TestGraphs.main(), RideMode.TIME), a, p(2000.0, 2000.0)) }
    }

    @Test
    fun `yürüme limitini aşan bağlı hedef harita dışında diye etiketlenmez`() {
        assertRoutingError("yürüyüş mesafesinde") { routeLeg(model(TestGraphs.walkLimit(), RideMode.TIME), a, p(200.0, 300.0)) }
    }

    @Test
    fun `düz yolda kısa rota seçilir`() {
        val leg = routeLeg(model(TestGraphs.main(), RideMode.TIME), a, b)
        assertEquals(400.0, leg.rideM, 5.0)
        assertTrue(leg.walk.isEmpty())
    }

    @Test
    fun `yokuş - ZAMAN modu tepeden, BATARYA modu dolambaçtan gider`() {
        val g = TestGraphs.main(hill = 25.0) // %12.5 eğim
        val t = routeLeg(model(g, RideMode.TIME), a, b)
        val e = routeLeg(model(g, RideMode.BATTERY), a, b)
        assertEquals(400.0, t.rideM, 5.0)
        assertEquals(800.0, e.rideM, 5.0)
        assertTrue(e.energyWh < t.energyWh)
        assertTrue(t.timeS < e.timeS)
    }

    @Test
    fun `eğim limiti - yüzde 10 limitte tepe yasak`() {
        val g = TestGraphs.main(hill = 25.0)
        val t = routeLeg(model(g, RideMode.TIME, maxGrade = 0.10), a, b)
        assertEquals(800.0, t.rideM, 5.0)
    }

    @Test
    fun `tek yön - onaysız ters giriş yok, onaylıysa var`() {
        val g = TestGraphs.main(directTags = mapOf("highway" to "residential", "oneway" to "yes"), directReversed = true) // B→A tek yön; A→B ters
        val noApproval = routeLeg(model(g, RideMode.TIME), a, b)
        assertEquals(800.0, noApproval.rideM, 5.0)

        val approved = routeLeg(model(g, RideMode.TIME, approved = setOf(100)), a, b)
        assertEquals(400.0, approved.rideM, 5.0)
        assertEquals(400.0, approved.wrongWayM, 5.0)
    }

    @Test
    fun `ana caddede onay işe yaramaz (şartname kuralı)`() {
        val g = TestGraphs.main(directTags = mapOf("highway" to "primary", "oneway" to "yes"), directReversed = true)
        val leg = routeLeg(model(g, RideMode.TIME, approved = setOf(100)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `kara liste - yasaklı sokaktan geçmez`() {
        val leg = routeLeg(model(TestGraphs.main(), RideMode.TIME, blocked = setOf(100)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `aşırı eğimli - yokuş yukarı kullanılmaz, inişte serbest`() {
        val g = TestGraphs.main(ramp = 20.0) // A→B 400 m'de 20 m tırmanış (%5)
        val up = routeLeg(model(g, RideMode.TIME, steep = setOf(100)), a, b)
        assertEquals(800.0, up.rideM, 5.0) // yokuşu çıkmaz, dolaşır
        val down = routeLeg(model(g, RideMode.TIME, steep = setOf(100)), b, a)
        assertEquals(400.0, down.rideM, 5.0) // inişte doğrudan
        val free = routeLeg(model(g, RideMode.TIME), a, b)
        assertEquals(400.0, free.rideM, 5.0) // işaret yoksa doğrudan
    }

    @Test
    fun `aşırı eğimli - yükseklik yönü belirsizse iki yön de kapalı`() {
        val g = TestGraphs.main(hill = 10.0) // ortada tepe: iki yön de tırmanıyor
        val leg = routeLeg(model(g, RideMode.TIME, steep = setOf(100)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `yol olmayan yere kuş uçuşu yürüyüş çizilmez`() {
        val g = TestGraphs.main()
        val dest = p(-90.0, 400.0) // B'nin 90 m batısı, yol yok
        assertRoutingError { routeLeg(model(g, RideMode.TIME), a, dest) }
    }

    @Test
    fun `alternatif yoksa kara listeyi gevşetmez`() {
        val r = computePlans(
            PlanRequest(TestGraphs.main(), a, listOf(b), RideParams(), approved = emptySet(), blocked = setOf(100L, 201L, 202L, 203L)),
        )
        assertTrue(r.plans.isEmpty())
        assertTrue(r.error != null)
    }

    @Test
    fun `son yürüyüş bağlı yaya yolunun dönüşlerini takip eder`() {
        val leg = routeLeg(model(TestGraphs.foot(), RideMode.TIME), a, p(-80.0, 440.0))
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(120.0, leg.walkM, 5.0)
        assertTrue(leg.walk.any { Math.abs(it.lon - lon(-80.0)) < 1e-6 && Math.abs(it.lat - lat(400.0)) < 1e-6 })
    }

    @Test
    fun `yakın fakat bağlantısız yaya yoluna atlamaz`() {
        assertRoutingError { routeLeg(model(TestGraphs.foot(connected = false), RideMode.TIME), a, p(-80.0, 440.0)) }
    }

    @Test
    fun `yol yok işareti son yürüyüşte de geçilmez`() {
        val g = TestGraphs.foot()
        val model = CostModel(
            g, RideMode.TIME, RideParams(), approved = emptySet(), blocked = emptySet(),
            ghost = (0 until g.edgeCount).filter { g.osmIdOf(it) == 300L }.toSet(),
        )
        assertRoutingError { routeLeg(model, a, p(-80.0, 440.0)) }
    }

    @Test
    fun `saatli ters yön sadece izin içinde ve geçiş bitmeden kullanılabilir`() {
        val g = TestGraphs.main(directTags = mapOf("highway" to "residential", "oneway" to "yes"), directReversed = true)
        fun at(t: Long) = CostModel(
            g, RideMode.TIME, RideParams(), approved = setOf(100), blocked = emptySet(),
            reverseHours = mapOf(100L to ReverseHours(23 * 60, 0)), departureMs = t,
        )
        assertEquals(800.0, routeLeg(at(utc(2026, 10, 1, 18, 30)), a, b).rideM, 5.0)
        assertEquals(400.0, routeLeg(at(utc(2026, 10, 1, 19)), a, b).wrongWayM, 5.0)
        assertEquals(0.0, routeLeg(at(utc(2026, 10, 1, 19, 59, 50)), a, b).wrongWayM, 0.0)
    }

    @Test
    fun `tırmanış ve iniş rota geometrisinin yüksekliklerine uyar`() {
        val leg = routeLeg(model(TestGraphs.main(ramp = 20.0), RideMode.TIME), b, a)
        assertEquals(0.0, leg.climbM, 0.1)
        assertEquals(20.0, leg.descentM, 1.0)
    }

    // --- v140_features_test.dart Y3/Y8 (rota) ---

    private fun turnModel(g: RoadGraph, turnPenaltyS: Double = 12.0, wrongWayEdges: Set<Int> = emptySet()) =
        CostModel(g, RideMode.TIME, RideParams(turnPenaltyS = turnPenaltyS), approved = emptySet(), blocked = emptySet(), wrongWayEdges = wrongWayEdges)

    @Test
    fun `dönüş cezası - az dönüşlü yol seçilir, ceza kapalıyken kısa merdiven`() {
        val g = TestGraphs.turn()
        val from = p(0.0, 0.0)
        val to = p(300.0, 300.0)
        val noPenalty = routeLeg(turnModel(g, turnPenaltyS = 0.0), from, to)
        assertEquals(600.0, noPenalty.rideM, 10.0)
        assertEquals(5, noPenalty.turns.size)
        assertTrue("kuzeyden doğuya: sağa", noPenalty.turns.first().right)
        assertFalse("doğudan kuzeye: sola", noPenalty.turns[1].right)
        val withPenalty = routeLeg(turnModel(g), from, to)
        assertTrue(withPenalty.rideM > 700)
        assertTrue(withPenalty.turns.isEmpty())
        // Ceza gerçek süreye eklenmez
        assertEquals(withPenalty.rideM / RideParams().flatMps, withPenalty.timeS, 3.0)
    }

    @Test
    fun `ana caddede elle izin verilen kesimde ters yön kullanılır`() {
        val g = TestGraphs.mainOneway()
        assertEquals(1200.0, routeLeg(turnModel(g), a, b).rideM, 5.0)
        val edges = g.edgesOfOsmWay(100).toSet()
        val leg = routeLeg(turnModel(g, wrongWayEdges = edges), a, b)
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(400.0, leg.wrongWayM, 5.0)
    }
}
