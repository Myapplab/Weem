package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.GraphBuilder
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.Passability
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import java.math.BigInteger
import java.time.Instant
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * YolGo rota motoruyla birebir karşılaştırma (A6.4; MAP.md 3.13, 3.16 "eşit maliyet sırası"). Veri `route/golden-v1.json.gz`:
 * `tools/route-golden/make_golden.sh` YolGo `yolgo-test` @ `870ab6e` Dart kaynağını (Flutter'sız kum havuzunda, hesaba dokunmayan üç
 * sayaç satırıyla) sabit tohumlu rastgele şehirlerde çalıştırır. Bu test aynı OSM girdisinden grafı kendisi kurar (A6.3
 * `buildRoadGraph`), KNG1 baytlarını ve her sorgunun sonucunu karşılaştırır: hata metni, kenar dizisi, dönüşler, sokaklar, uyarılar,
 * sıra ve A* sayaçları (bacak, yığın çekişi, gevşetilen kenar) birebir; ondalıklar 1e-9 göreli payla (JVM ve Dart `sin/cos`
 * son basamakta ayrışabilir; telefonda ikisi de aynı libm'i kullanır).
 */
class RouterGoldenTest {
    private val golden: JSONObject by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/route/golden-v1.json.gz")) { "golden-v1.json.gz yok" }
        JSONObject(GZIPInputStream(stream).use { it.readBytes() }.toString(Charsets.UTF_8))
    }

    private class City(val seed: Int, val graph: RoadGraph, val json: JSONObject)

    private val cities: List<City> by lazy {
        val arr = golden.getJSONArray("cities")
        (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            City(c.getInt("seed"), buildCity(c), c)
        }
    }

    @Test
    fun `KNG1 baytları YolGo ile aynı (aynı OSM ve yükseklik girdisinden)`() {
        for (c in cities) {
            val k = c.json.getJSONObject("kng1")
            val bytes = c.graph.serialize()
            assertEquals("şehir ${c.seed}: düğüm", k.getInt("nodes"), c.graph.nodeCount)
            assertEquals("şehir ${c.seed}: kenar", k.getInt("edges"), c.graph.edgeCount)
            assertEquals("şehir ${c.seed}: boy", k.getInt("len"), bytes.size)
            assertEquals("şehir ${c.seed}: FNV", k.getString("fnv"), fnv(bytes))
        }
    }

    @Test
    fun `tek bacak sorguları YolGo ile aynı`() = compareCases("leg")

    @Test
    fun `plan sorguları YolGo ile aynı`() = compareCases("plan")

    private fun compareCases(kind: String) {
        val diffs = ArrayList<String>()
        var count = 0
        for (c in cities) {
            val cases = c.json.getJSONArray("cases")
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                if (case.getString("kind") != kind) continue
                count++
                val actual = if (kind == "leg") runLeg(c.graph, case) else runPlan(c.graph, case)
                compare("şehir ${c.seed} #$i", case.getJSONObject("expect"), actual, diffs)
            }
        }
        if (diffs.isNotEmpty()) fail("${diffs.size} fark ($count sorgu):\n" + diffs.take(40).joinToString("\n"))
        assertTrue("sorgu yok", count > 0)
    }

    // ---- Girdiler (golden.dart randomOpts/modelOf ile aynı) ----

    private fun buildCity(c: JSONObject): RoadGraph {
        val raw = OsmRaw()
        val nodes = c.getJSONArray("nodes")
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONArray(i)
            raw.addNode(n.getLong(0), n.getDouble(1), n.getDouble(2))
        }
        val ways = c.getJSONArray("ways")
        for (i in 0 until ways.length()) {
            val w = ways.getJSONArray(i)
            val ns = w.getJSONArray(1)
            val tags = w.getJSONObject(2)
            raw.addWay(OsmWay(w.getLong(0), (0 until ns.length()).map { ns.getLong(it) }, tags.keySet().associateWith { tags.getString(it) }))
        }
        val bumps = c.getJSONArray("bumps").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { b -> DoubleArray(4) { k -> b.getDouble(k) } } } }
        return GraphBuilder.buildRoadGraph(raw) { la, lo -> elevation(bumps, la, lo) }
    }

    private fun elevation(bumps: List<DoubleArray>, la: Double, lo: Double): Double {
        val x = (lo - LON0) * KX
        val y = (la - LAT0) * KY
        var e = 0.0
        for (b in bumps) {
            val dx = x - b[0]
            val dy = y - b[1]
            e += b[2] / (1 + (dx * dx + dy * dy) / (b[3] * b[3]))
        }
        return e
    }

    private fun params(j: JSONObject) = RideParams(
        flatWhPerM = j.getDouble("flatWhPerM"), climbWhPerM = j.getDouble("climbWhPerM"), climbSafety = j.getDouble("climbSafety"),
        flatKmh = j.getDouble("flatKmh"), climbKmh = j.getDouble("climbKmh"), walkKmh = j.getDouble("walkKmh"),
        maxGrade = j.getDouble("maxGrade"), mainRoadPenalty = j.getDouble("mainRoadPenalty"), wrongWayKmh = j.getDouble("wrongWayKmh"),
        walkRadius = j.getDouble("walkRadius"), parkPenaltyS = j.getDouble("parkPenaltyS"), qualityWeight = j.getDouble("qualityWeight"),
        turnPenaltyS = j.getDouble("turnPenaltyS"), bikewayFactor = j.getDouble("bikewayFactor"),
    )

    private fun longs(a: JSONArray): Set<Long> = (0 until a.length()).mapTo(LinkedHashSet()) { a.getLong(it) }
    private fun ints(a: JSONArray): Set<Int> = (0 until a.length()).mapTo(LinkedHashSet()) { a.getInt(it) }
    private fun point(a: JSONArray) = GeoPoint(a.getDouble(0), a.getDouble(1))

    private fun enumName(dart: String) = dart.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

    private class Opts(
        val mode: RideMode,
        val params: RideParams,
        val approved: Set<Long>,
        val blocked: Set<Long>,
        val steep: Set<Long>,
        val ghost: Set<Int>,
        val learned: Map<Long, LearnedQuality>,
        val marks: List<SurfaceMark>,
        val wwRatio: Map<Long, Double>,
        val wwAvoid: Set<Long>,
        val reverseHours: Map<Long, ReverseHours>,
        val wrongWayEdges: Set<Int>,
        val bikeways: Set<Long>,
        val departure: Instant,
    )

    private fun opts(o: JSONObject): Opts {
        val learned = LinkedHashMap<Long, LearnedQuality>()
        o.getJSONArray("learned").let { a ->
            for (i in 0 until a.length()) {
                val l = a.getJSONArray(i)
                learned[l.getLong(0)] = LearnedQuality(l.getDouble(1), l.getDouble(2), l.getInt(3))
            }
        }
        val marks = o.getJSONArray("marks").let { a ->
            (0 until a.length()).map { i ->
                val m = a.getJSONObject(i)
                SurfaceMark(
                    id = m.getString("id"), way = m.getLong("way"), lat = m.getDouble("lat"), lon = m.getDouble("lon"), at = m.getLong("at"),
                    label = SurfaceLabel.valueOf(enumName(m.getString("label"))), access = Passability.valueOf(enumName(m.getString("access"))),
                    source = m.getString("source"), removed = m.getBoolean("removed"),
                )
            }
        }
        val ratio = LinkedHashMap<Long, Double>()
        o.getJSONArray("wwRatio").let { a -> for (i in 0 until a.length()) a.getJSONArray(i).let { ratio[it.getLong(0)] = it.getDouble(1) } }
        val hours = LinkedHashMap<Long, ReverseHours>()
        o.getJSONArray("reverseHours").let { a ->
            for (i in 0 until a.length()) a.getJSONArray(i).let { hours[it.getLong(0)] = ReverseHours(it.getInt(1), it.getInt(2)) }
        }
        return Opts(
            mode = RideMode.valueOf(o.getString("mode").uppercase()),
            params = params(o.getJSONObject("params")),
            approved = longs(o.getJSONArray("approved")),
            blocked = longs(o.getJSONArray("blocked")),
            steep = longs(o.getJSONArray("steep")),
            ghost = ints(o.getJSONArray("ghost")),
            learned = learned,
            marks = marks,
            wwRatio = ratio,
            wwAvoid = longs(o.getJSONArray("wwAvoid")),
            reverseHours = hours,
            wrongWayEdges = ints(o.getJSONArray("wrongWayEdges")),
            bikeways = longs(o.getJSONArray("bikeways")),
            departure = Instant.ofEpochMilli(o.getLong("departure")),
        )
    }

    private fun heading(case: JSONObject): Double? = if (case.isNull("heading")) null else case.getDouble("heading")

    private fun runLeg(g: RoadGraph, case: JSONObject): Map<String, Any?> {
        val o = opts(case.getJSONObject("opts"))
        val probe = RouteProbe()
        val model = CostModel(
            g, mode = o.mode, params = o.params, approved = o.approved, blocked = o.blocked, steep = o.steep,
            avoidBad = case.getBoolean("avoidBad"), ghost = o.ghost, learned = o.learned, marks = o.marks, wwRatio = o.wwRatio,
            wwAvoid = o.wwAvoid, reverseHours = o.reverseHours, wrongWayEdges = o.wrongWayEdges, bikeways = o.bikeways,
            departure = o.departure,
        )
        val out = try {
            val leg = Router.routeLeg(
                model, point(case.getJSONArray("from")), point(case.getJSONArray("to")), heading(case),
                case.getDouble("startOffsetS"), probe,
            )
            mapOf("leg" to legJson(leg, point(case.getJSONArray("probe"))))
        } catch (e: RoutingException) {
            mapOf("error" to e.message)
        }
        return out + ("stats" to listOf(probe.legs, probe.pops, probe.edges))
    }

    private fun runPlan(g: RoadGraph, case: JSONObject): Map<String, Any?> {
        val o = opts(case.getJSONObject("opts"))
        val probe = RouteProbe()
        val stops = case.getJSONArray("stops").let { a -> (0 until a.length()).map { point(a.getJSONArray(it)) } }
        val req = PlanRequest(
            graph = g, origin = point(case.getJSONArray("origin")), stops = stops, params = o.params, approved = o.approved,
            blocked = o.blocked, steep = o.steep, heading = heading(case), fixedOrder = case.getBoolean("fixedOrder"), ghost = o.ghost,
            learned = o.learned, marks = o.marks, wwRatio = o.wwRatio, wwAvoid = o.wwAvoid, reverseHours = o.reverseHours,
            wrongWayEdges = o.wrongWayEdges, bikeways = o.bikeways, departure = o.departure,
        )
        val res = Planner.computePlans(req, probe)
        val probePoint = point(case.getJSONArray("probe"))
        return mapOf(
            "error" to res.error?.tr,
            "plans" to res.plans.entries.associate { (mode, plan) ->
                mode.name.lowercase() to mapOf("order" to plan.order, "legs" to plan.legs.map { legJson(it, probePoint) })
            },
            "stats" to listOf(probe.legs, probe.pops, probe.edges),
        )
    }

    private fun line(pts: List<RoutePoint>): List<Any> {
        var la = 0.0
        var lo = 0.0
        var el = 0.0
        for (p in pts) {
            la += p.lat
            lo += p.lon
            el += p.ele
        }
        return listOf(pts.size, la, lo, el)
    }

    private fun legJson(l: RouteLeg, probe: GeoPoint): Map<String, Any?> = mapOf(
        "rideM" to l.rideM, "walkM" to l.walkM, "timeS" to l.timeS, "energyWh" to l.energyWh, "climbM" to l.climbM,
        "descentM" to l.descentM, "wrongWayM" to l.wrongWayM, "cost" to l.cost, "quality" to l.quality, "wrongWayRisk" to l.wrongWayRisk,
        "pushM" to l.pushM, "startWalkM" to l.startWalkM, "streets" to l.streets, "warnings" to l.warnings.map { it.tr },
        "segs" to l.segs.map { listOf(it.edge, it.startM, it.lenM, if (it.walk) 1 else 0) },
        "turns" to l.turns.map { listOf(it.atM, it.angle, it.name, it.lat, it.lon, it.sharpness) },
        "ride" to line(l.ride), "walk" to line(l.walk), "startWalk" to line(l.startWalk),
        "park" to listOf(l.parkPoint.lat, l.parkPoint.lon),
        "pushedRuns" to RouteGeometry.pushedRuns(l).map { line(it) },
        "progress" to RouteGeometry.progressAlong(l.ride, probe),
    )

    // ---- Karşılaştırma ----

    private fun compare(path: String, expected: Any?, actual: Any?, diffs: MutableList<String>) {
        when (expected) {
            is JSONObject -> {
                val a = actual as? Map<*, *> ?: return run { diffs += "$path: nesne bekleniyordu, $actual" }
                val keys = expected.keySet() + a.keys.map { it.toString() }
                for (k in keys.sorted()) {
                    if (!expected.has(k)) diffs += "$path.$k: fazla (${a[k]})"
                    else if (!a.containsKey(k)) diffs += "$path.$k: eksik (beklenen ${expected.opt(k)})"
                    else compare("$path.$k", expected.opt(k), a[k], diffs)
                }
            }
            is JSONArray -> {
                val a = actual as? List<*> ?: return run { diffs += "$path: dizi bekleniyordu, $actual" }
                if (a.size != expected.length()) {
                    diffs += "$path: boy ${a.size} ≠ beklenen ${expected.length()} (beklenen $expected, gelen $a)"
                    return
                }
                for (i in 0 until expected.length()) compare("$path[$i]", expected.opt(i), a[i], diffs)
            }
            JSONObject.NULL, null -> if (actual != null) diffs += "$path: null bekleniyordu, $actual"
            is Int, is Long, is BigInteger -> {
                val e = (expected as Number).toLong()
                if (actual !is Number || actual is Double || actual.toLong() != e) diffs += "$path: $actual ≠ beklenen $e"
            }
            is Number -> {
                val e = expected.toDouble()
                val v = (actual as? Number)?.toDouble()
                if (v == null || abs(v - e) > 1e-9 * max(1.0, max(abs(v), abs(e)))) diffs += "$path: $actual ≠ beklenen $e"
            }
            else -> if (expected != actual) diffs += "$path: «$actual» ≠ beklenen «$expected»"
        }
    }

    private companion object {
        const val LAT0 = 40.40
        const val LON0 = 49.85
        const val KX = 84822.0
        const val KY = 111320.0

        /** FNV-1a 64 (golden.dart ile aynı), onaltılık. */
        fun fnv(bytes: ByteArray): String {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (b in bytes) {
                h = h xor (b.toLong() and 0xFF)
                h *= 0x100000001b3L
            }
            return java.lang.Long.toUnsignedString(h, 16)
        }
    }
}
