package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.GraphBuilder
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs.RESIDENTIAL
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.route.TestGraphs.p
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `router_test.dart` temel kurallar (20 test; A6.2a, MAP.md 3.14) + `v140_features_test.dart` dönüş cezası ve ana
 * caddede elle ters yön izni (2 test). `_model` = [model]; fixture'lar [TestGraphs]. Uygulama: A6.4.
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
    ) = CostModel(g, mode = mode, params = RideParams().withMaxGrade(maxGrade), approved = approved, blocked = blocked, steep = steep)

    private fun route(g: RoadGraph, from: com.kuryelog.logic.map.geo.GeoPoint = a, to: com.kuryelog.logic.map.geo.GeoPoint = b) =
        Router.routeLeg(model(g, RideMode.TIME), from, to)

    @Test
    fun `özel kurum girişi onaylı olsa da araç rotası olmaz`() {
        val g = TestGraphs.directAndDetour(directTags = mapOf("highway" to "residential", "access" to "private"))
        val leg = Router.routeLeg(model(g, RideMode.TIME, approved = setOf(100L)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `servis cepleri ana rota olarak kullanılmaz`() {
        val g = TestGraphs.directAndDetour(directTags = mapOf("highway" to "service"))
        assertEquals(800.0, route(g).rideM, 5.0)
    }

    @Test
    fun `bina içi 70 m uzak pin erişilebilir yol üzerinde biter`() {
        val leg = route(TestGraphs.directAndDetour(), to = p(-70.0, 200.0))
        assertTrue(leg.parkPoint.distanceTo(p(0.0, 200.0)) < 2)
        assertTrue(leg.warnings.isNotEmpty())
        assertTrue(leg.walk.isEmpty()) // bina/duvar üzerinden hayali çizgi yok
    }

    @Test
    fun `harita dışında kalan hedefin hatası bağlantı hatasından ayrılır`() {
        val e = assertThrows(RoutingException::class.java) { route(TestGraphs.directAndDetour(), to = p(2000.0, 2000.0)) }
        assertTrue(e.message.contains("haritasının dışında"))
    }

    @Test
    fun `yürüme limitini aşan bağlı hedef harita dışında diye etiketlenmez`() {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0))
        raw.addNode(2, lat(0.0), lon(200.0))
        raw.addNode(3, lat(600.0), lon(200.0))
        raw.addWay(OsmWay(1, listOf(1, 2), RESIDENTIAL))
        raw.addWay(OsmWay(2, listOf(2, 3), mapOf("highway" to "footway")))
        val g = GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
        val e = assertThrows(RoutingException::class.java) { route(g, to = p(200.0, 300.0)) }
        assertTrue(e.message.contains("yürüyüş mesafesinde"))
    }

    @Test
    fun `düz yolda kısa rota seçilir`() {
        val leg = route(TestGraphs.directAndDetour())
        assertEquals(400.0, leg.rideM, 5.0)
        assertTrue(leg.walk.isEmpty())
    }

    @Test
    fun `yokuş ZAMAN modu tepeden, BATARYA modu dolambaçtan gider`() {
        val g = TestGraphs.directAndDetour(hill = 25.0) // %12.5 eğim
        val t = Router.routeLeg(model(g, RideMode.TIME), a, b)
        val e = Router.routeLeg(model(g, RideMode.BATTERY), a, b)
        assertEquals(400.0, t.rideM, 5.0)
        assertEquals(800.0, e.rideM, 5.0)
        assertTrue(e.energyWh < t.energyWh)
        assertTrue(t.timeS < e.timeS)
    }

    @Test
    fun `eğim limiti yüzde 10 limitte tepe yasak`() {
        val g = TestGraphs.directAndDetour(hill = 25.0)
        val t = Router.routeLeg(model(g, RideMode.TIME, maxGrade = 0.10), a, b)
        assertEquals(800.0, t.rideM, 5.0)
    }

    @Test
    fun `tek yön onaysız ters giriş yok, onaylıysa var`() {
        // B→A tek yön; A→B ters
        val g = TestGraphs.directAndDetour(directTags = mapOf("highway" to "residential", "oneway" to "yes"), directReversed = true)
        assertEquals(800.0, Router.routeLeg(model(g, RideMode.TIME), a, b).rideM, 5.0)
        val approved = Router.routeLeg(model(g, RideMode.TIME, approved = setOf(100L)), a, b)
        assertEquals(400.0, approved.rideM, 5.0)
        assertEquals(400.0, approved.wrongWayM, 5.0)
    }

    @Test
    fun `ana caddede onay işe yaramaz (şartname kuralı)`() {
        val g = TestGraphs.directAndDetour(directTags = mapOf("highway" to "primary", "oneway" to "yes"), directReversed = true)
        val leg = Router.routeLeg(model(g, RideMode.TIME, approved = setOf(100L)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `kara liste yasaklı sokaktan geçmez`() {
        val leg = Router.routeLeg(model(TestGraphs.directAndDetour(), RideMode.TIME, blocked = setOf(100L)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `aşırı eğimli yokuş yukarı kullanılmaz, inişte serbest`() {
        val g = TestGraphs.directAndDetour(ramp = 20.0) // A→B 400 m'de 20 m tırmanış (%5)
        val up = Router.routeLeg(model(g, RideMode.TIME, steep = setOf(100L)), a, b)
        assertEquals(800.0, up.rideM, 5.0) // yokuşu çıkmaz, dolaşır
        val down = Router.routeLeg(model(g, RideMode.TIME, steep = setOf(100L)), b, a)
        assertEquals(400.0, down.rideM, 5.0) // inişte doğrudan
        val free = Router.routeLeg(model(g, RideMode.TIME), a, b)
        assertEquals(400.0, free.rideM, 5.0) // işaret yoksa doğrudan
    }

    @Test
    fun `aşırı eğimli yükseklik yönü belirsizse iki yön de kapalı`() {
        val g = TestGraphs.directAndDetour(hill = 10.0) // ortada tepe: iki yön de tırmanıyor
        val leg = Router.routeLeg(model(g, RideMode.TIME, steep = setOf(100L)), a, b)
        assertEquals(800.0, leg.rideM, 5.0)
    }

    @Test
    fun `yol olmayan yere kuş uçuşu yürüyüş çizilmez`() {
        val g = TestGraphs.directAndDetour()
        val dest = p(-90.0, 400.0) // B'nin 90 m batısı, yol yok
        assertThrows(RoutingException::class.java) { route(g, to = dest) }
    }

    @Test
    fun `alternatif yoksa kara listeyi gevşetmez`() {
        val r = Planner.computePlans(
            PlanRequest(TestGraphs.directAndDetour(), a, listOf(b), RideParams(), emptySet(), setOf(100L, 201L, 202L, 203L)),
        )
        assertTrue(r.plans.isEmpty())
        assertTrue(r.error != null)
    }

    @Test
    fun `son yürüyüş bağlı yaya yolunun dönüşlerini takip eder`() {
        val leg = route(TestGraphs.footGraph(), to = p(-80.0, 440.0))
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(120.0, leg.walkM, 5.0)
        assertTrue(leg.walk.any { Math.abs(it.lon - lon(-80.0)) < 1e-6 && Math.abs(it.lat - lat(400.0)) < 1e-6 })
    }

    @Test
    fun `yakın fakat bağlantısız yaya yoluna atlamaz`() {
        assertThrows(RoutingException::class.java) { route(TestGraphs.footGraph(connected = false), to = p(-80.0, 440.0)) }
    }

    @Test
    fun `yol yok işareti son yürüyüşte de geçilmez`() {
        val g = TestGraphs.footGraph()
        val model = CostModel(
            g, mode = RideMode.TIME, params = RideParams(), approved = emptySet(), blocked = emptySet(),
            ghost = (0 until g.edgeCount).filter { g.osmIdOf(it) == 300L }.toSet(),
        )
        assertThrows(RoutingException::class.java) { Router.routeLeg(model, a, p(-80.0, 440.0)) }
    }

    @Test
    fun `saatli ters yön sadece izin içinde ve geçiş bitmeden kullanılabilir`() {
        val g = TestGraphs.directAndDetour(directTags = mapOf("highway" to "residential", "oneway" to "yes"), directReversed = true)
        fun at(t: Instant) = CostModel(
            g, mode = RideMode.TIME, params = RideParams(), approved = setOf(100L), blocked = emptySet(),
            reverseHours = mapOf(100L to ReverseHours(23 * 60, 0)), departure = t,
        )
        assertEquals(800.0, Router.routeLeg(at(Instant.parse("2026-10-01T18:30:00Z")), a, b).rideM, 5.0)
        assertEquals(400.0, Router.routeLeg(at(Instant.parse("2026-10-01T19:00:00Z")), a, b).wrongWayM, 5.0)
        assertEquals(0.0, Router.routeLeg(at(Instant.parse("2026-10-01T19:59:50Z")), a, b).wrongWayM, 0.0)
    }

    @Test
    fun `tırmanış ve iniş rota geometrisinin yüksekliklerine uyar`() {
        val leg = Router.routeLeg(model(TestGraphs.directAndDetour(ramp = 20.0), RideMode.TIME), b, a)
        assertEquals(0.0, leg.climbM, 0.1)
        assertEquals(20.0, leg.descentM, 1.0)
    }

    // ---- v140_features_test.dart: Y3/Y8 (3 testin 2'si burada, 3. RouteEngineTest'te) ----

    private fun turnModel(g: RoadGraph, turnPenaltyS: Double = 12.0, wrongWayEdges: Set<Int> = emptySet()) = CostModel(
        g, mode = RideMode.TIME, params = RideParams(turnPenaltyS = turnPenaltyS), approved = emptySet(), blocked = emptySet(),
        wrongWayEdges = wrongWayEdges,
    )

    @Test
    fun `dönüş cezası az dönüşlü yol seçilir, ceza kapalıyken kısa merdiven`() {
        val g = TestGraphs.turnGraph()
        val from = p(0.0, 0.0)
        val to = p(300.0, 300.0)
        val noPenalty = Router.routeLeg(turnModel(g, turnPenaltyS = 0.0), from, to)
        assertEquals(600.0, noPenalty.rideM, 10.0)
        assertEquals(5, noPenalty.turns.size)
        assertTrue("kuzeyden doğuya sağa", noPenalty.turns.first().right)
        assertTrue("doğudan kuzeye sola", !noPenalty.turns[1].right)
        val withPenalty = Router.routeLeg(turnModel(g), from, to)
        assertTrue(withPenalty.rideM > 700)
        assertTrue(withPenalty.turns.isEmpty())
        // Ceza gerçek süreye eklenmez
        assertEquals(withPenalty.rideM / RideParams().flatMps, withPenalty.timeS, 3.0)
    }

    @Test
    fun `ana caddede elle izin verilen kesimde ters yön kullanılır`() {
        val g = TestGraphs.mainOneway()
        assertEquals(1200.0, Router.routeLeg(turnModel(g), a, b).rideM, 5.0)
        val edges = g.edgesOfOsmWay(100).toSet()
        val leg = Router.routeLeg(turnModel(g, wrongWayEdges = edges), a, b)
        assertEquals(400.0, leg.rideM, 5.0)
        assertEquals(400.0, leg.wrongWayM, 5.0)
    }
}
