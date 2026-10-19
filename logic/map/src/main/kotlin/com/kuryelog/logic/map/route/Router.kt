package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.geo.LocalProjection
import com.kuryelog.logic.map.graph.EdgeHit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hesap izleyicisi (MAP.md 3.11, 3.13): [check] her 1.024 yığın çekişinde ve planlayıcıda her bacaktan önce çağrılır (fırlatırsa
 * hesap durur; `RouteEngine` coroutine işinin `ensureActive`'ini verir); sayaçlar A* işini ölçer (çekilen düğüm, gevşetilen kenar,
 * bacak denemesi, önbellekten atılan hata).
 */
class RouteProbe(private val cancel: (() -> Unit)? = null) {
    var legs = 0L
    var pops = 0L
    var edges = 0L

    /** Planlayıcının önbellekten yeniden attığı başarısız bacaklar (YolGo bunları yeniden hesaplardı). */
    var cachedFailures = 0L

    fun check() {
        cancel?.invoke()
    }
}

/**
 * Tek bacak rota hesabı — YolGo `router.dart` `routeLeg` (MAP.md 3.6–3.8): A* (sanal hedef düğümü, bitiş teklifleri, kenar içi
 * başlangıç ve varış), hedefe yalnız bağlı yollardan yürüme yaklaşımı ve yaya bölgesinden çıkış. Eşit maliyetli rotalarda aynı
 * sonucu vermek için yığın, komşuluk sırası ve "ilk en küçük" kuralları Dart'taki gibidir.
 */
object Router {
    /** Başlangıç yüzünden düşen rotalar (saha: Nəsimi/Fəvvarələr yaya bölgesi); yaya çıkışı denenir. */
    private val START_FAILURES = setOf(RouteCode.NO_RIDEABLE_NEAR_START, RouteCode.NO_ROUTE_BY_RULES, RouteCode.START_NOT_RIDEABLE)

    /** Yaya yolundan en yakın sürülebilir yola en fazla bu kadar yürünür (m). */
    const val START_WALK_MAX_M = 400.0

    /**
     * A* ile tek bacak: başlangıç → hedef (sürüş + son yürüme). Konum yaya yolundaysa ve oradan sürülemiyorsa en yakın sürülebilir
     * yola yaya yolu tarifi verilir, rota oradan scooter ile sürer. [heading] yalnız ilk bacakta; [startOffsetS] saatli ters yön
     * izni için bacağın kalkıştan sonraki başlangıç anı (sn).
     */
    fun routeLeg(
        m: CostModel,
        from: GeoPoint,
        to: GeoPoint,
        heading: Double? = null,
        startOffsetS: Double = 0.0,
        probe: RouteProbe? = null,
    ): RouteLeg {
        try {
            return routeLegFrom(m, from, to, heading, startOffsetS, probe)
        } catch (e: RoutingException) {
            if (e.msg.code !in START_FAILURES) throw e
            return walkOut(m, from, to, startOffsetS, probe) ?: throw e
        }
    }

    /**
     * Konumdan yaya yolları boyunca (yalnız mesafe; ışık, tek yön ve sürüş kuralları yok) [START_WALK_MAX_M] içindeki sürülebilir
     * yollara ulaşılır; en yakın en çok 6 bağlantıdan rota denenir, toplam maliyeti en düşük olan alınır (MAP.md 3.8).
     */
    private fun walkOut(m: CostModel, from: GeoPoint, to: GeoPoint, startOffsetS: Double, probe: RouteProbe?): RouteLeg? {
        val g = m.g
        val p = m.params
        val hit = g.nearestEdge(from.lat, from.lon, maxDist = 60.0, accept = { e -> m.walkable(e, 0) || m.walkable(e, 1) })
        // Yalnız gerçekten yaya yolundayken: en yakın yol yaya sınıfı olmalı (araç yolunun üstünden "yürü" denmez).
        if (hit == null || !g.classOf(hit.edge).isFootClass) return null
        val any = g.nearestEdge(from.lat, from.lon, maxDist = 60.0)
        if (any != null && !g.classOf(any.edge).isFootClass && any.dist + 3 < hit.dist) return null
        val dist = HashMap<Int, Double>()
        val prev = HashMap<Int, Pair<Int, Step>>()
        val head = HashMap<Int, Step>()
        val heap = MinHeap()
        fun seed(node: Int, dir: Int, f1: Double) {
            if (!m.walkable(hit.edge, dir)) return
            val d = g.edgeLen[hit.edge].toDouble() * abs(f1 - hit.fraction)
            if (d >= (dist[node] ?: Double.POSITIVE_INFINITY)) return
            dist[node] = d
            head[node] = Step(hit.edge, dir, hit.fraction, f1)
            heap.push(d, node)
        }
        seed(g.edgeTo[hit.edge], 0, 1.0)
        seed(g.edgeFrom[hit.edge], 1, 0.0)
        val closed = HashSet<Int>()
        val exits = ArrayList<Int>()
        while (!heap.isEmpty) {
            val u = heap.pop()
            if (!closed.add(u)) continue
            var exit = false
            for (a in g.adjStart[u] until g.adjStart[u + 1]) {
                if (m.rideable(g.adjEdge[a])) {
                    exit = true
                    break
                }
            }
            if (exit) {
                exits += u
                if (exits.size >= 6) break
                continue
            }
            for (a in g.adjStart[u] until g.adjStart[u + 1]) {
                val e = g.adjEdge[a]
                val dir = g.adjDir[a].toInt()
                if (!m.walkable(e, dir)) continue
                val v = if (dir == 0) g.edgeTo[e] else g.edgeFrom[e]
                if (v in closed) continue
                val d = dist.getValue(u) + g.edgeLen[e].toDouble()
                if (d > START_WALK_MAX_M || d >= (dist[v] ?: Double.POSITIVE_INFINITY)) continue
                dist[v] = d
                prev[v] = u to Step(e, dir, if (dir == 0) 0.0 else 1.0, if (dir == 0) 1.0 else 0.0)
                head.remove(v)
                heap.push(d, v)
            }
        }
        var best: RouteLeg? = null
        var bestNode = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (u in exits) {
            val walkS = dist.getValue(u) * 1.2 / p.walkMps
            try {
                val leg = routeLegFrom(m, GeoPoint(g.nodeLat[u], g.nodeLon[u]), to, null, startOffsetS + walkS, probe)
                val c = leg.cost + p.combine(m.mode, walkS, 0.0)
                if (c < bestCost) {
                    best = leg
                    bestCost = c
                    bestNode = u
                }
            } catch (_: RoutingException) {
                continue
            }
        }
        val b = best ?: return null
        val steps = ArrayList<Step>()
        var v = bestNode
        while (true) {
            val s = prev[v] ?: break
            steps += s.second
            v = s.first
        }
        head[v]?.let { steps += it }
        val walk = arrayListOf(RoutePoint(from.lat, from.lon, 0.0))
        for (s in steps.asReversed()) {
            if (abs(s.f1 - s.f0) <= 0) continue
            for (q in g.geometry(s.edge, s.f0, s.f1, reverse = s.dir == 1)) {
                if (walk.last().lat == q.lat && walk.last().lon == q.lon) continue
                walk += RoutePoint(q.lat, q.lon, q.ele)
            }
        }
        val walkM = dist.getValue(bestNode)
        return RouteLeg(
            ride = b.ride, walk = b.walk, rideM = b.rideM, walkM = b.walkM, timeS = b.timeS + walkM * 1.2 / p.walkMps,
            energyWh = b.energyWh, climbM = b.climbM, wrongWayM = b.wrongWayM, cost = bestCost, streets = b.streets,
            destination = b.destination, warnings = listOf(RouteMsg.START_WALK) + b.warnings, quality = b.quality,
            wrongWayRisk = b.wrongWayRisk, segs = b.segs, turns = b.turns, startWalk = walk, startWalkM = walkM, pushM = b.pushM,
        )
    }

    /** Harita kapsamı ve hedef adayları (≤ 80 m, en çok 4); adaylar sırayla denenir, ilk başarılı bacak döner (MAP.md 3.7). */
    private fun routeLegFrom(m: CostModel, from: GeoPoint, to: GeoPoint, heading: Double?, startOffsetS: Double, probe: RouteProbe?): RouteLeg {
        if (!m.g.contains(from.lat, from.lon) || !m.g.contains(to.lat, to.lon)) throw RoutingException(RouteMsg.OUTSIDE_MAP)
        val candidates = m.g.nearestEdges(to.lat, to.lon, maxDist = 80.0, accept = { e -> m.walkable(e, 0) || m.walkable(e, 1) })
        if (candidates.isEmpty()) throw RoutingException(RouteMsg.NO_ACCESS_NEAR_TARGET)
        var failure: RoutingException? = null
        // Bağlı bir yaya yolu yakınsa onu izle; kopuk geometri diğer yakın yol adaylarını engellemesin.
        for (hit in candidates) {
            try {
                return leg(m, from, to, heading, startOffsetS, hit, probe)
            } catch (e: RoutingException) {
                failure = e
            }
        }
        throw failure ?: RoutingException(RouteMsg.NO_ROUTE_BY_RULES)
    }

    private fun leg(
        m: CostModel,
        from: GeoPoint,
        to: GeoPoint,
        heading: Double?,
        startOffsetS: Double,
        targetHit: EdgeHit,
        probe: RouteProbe?,
    ): RouteLeg {
        probe?.let { it.legs++ }
        val g = m.g
        val p = m.params
        val mode = m.mode
        val n = g.nodeCount
        val target = n // sanal hedef düğümü

        val start = (if (heading != null) g.nearestEdge(from.lat, from.lon, maxDist = 60.0, accept = m::rideable, heading = heading) else null)
            ?: g.nearestEdge(from.lat, from.lon, maxDist = 60.0, accept = m::rideable)
            ?: throw RoutingException(RouteMsg.NO_RIDEABLE_NEAR_START)
        val approach = WalkingApproach(m, to, targetHit)
        val hit = approach.target ?: throw RoutingException(RouteMsg.TARGET_NOT_CONNECTED)
        val dest = if (m.rideable(hit.edge)) hit else null
        val walkMps = p.walkMps

        fun walkCost(meters: Double): Double = if (meters <= 20) 0.0 else p.combine(mode, meters * 1.2 / walkMps + p.parkPenaltyS, 0.0)

        // Bitiş teklifleri: düğüm → (maliyet, tür). Tür 0: düğümden yürü, 1: hedef kenarında ileri sür, 2: hedef kenarında geri sür.
        val finishCost = HashMap<Int, Double>()
        val finishKind = HashMap<Int, Int>()
        fun offer(node: Int, c: Double, kind: Int) {
            if (!c.isFinite()) return
            val old = finishCost[node]
            if (old == null || c < old) {
                finishCost[node] = c
                finishKind[node] = kind
            }
        }
        if (dest != null) {
            val e = dest.edge
            val f = dest.fraction
            val w = walkCost(dest.dist)
            if (m.allowed(e, 0)) offer(g.edgeFrom[e], m.segmentCost(e, 0, 0.0, f) + w, 1)
            if (m.allowed(e, 1)) offer(g.edgeTo[e], m.segmentCost(e, 1, f, 1.0) + w, 2)
        }
        // Yaya hedefleri kuş uçuşu değil, bağlı yol geometrisi ile sunulur.
        val proj = LocalProjection(to.lat, to.lon)
        for ((node, d) in approach.distance) {
            offer(node, if (d < 0.5) 0.0 else p.combine(mode, d * 1.2 / walkMps + p.parkPenaltyS, 0.0), 0)
        }
        if (finishCost.isEmpty()) throw RoutingException(RouteMsg.NO_VEHICLE_ROAD_IN_WALK)

        val dist = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
        val prevEdge = IntArray(n + 1) { -1 }
        val prevDir = ByteArray(n + 1)
        val closed = BooleanArray(n + 1)
        val elapsed = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
        var targetFrom = -1 // hedefe hangi düğümden ulaşıldı (-2: aynı kenar doğrudan)
        val heap = MinHeap()

        val cMin = min(p.minRideCostPerM(mode), p.minWalkCostPerM(mode)) * 0.99 * m.heuristicScale
        fun h(v: Int): Double {
            val dx = proj.x(g.nodeLon[v])
            val dy = proj.y(g.nodeLat[v])
            val d = sqrt(dx * dx + dy * dy) - p.walkRadius - 300
            return if (d > 0) d * cMin else 0.0
        }

        // Başlangıç: bulunduğun kenardan iki uca kısmi sürüş.
        val se = start.edge
        val sf = start.fraction
        fun seed(node: Int, dir: Int, c: Double, seconds: Double) {
            if (c < dist[node]) {
                dist[node] = c
                elapsed[node] = seconds
                prevEdge[node] = -2 // başlangıç kısmı
                prevDir[node] = dir.toByte()
                heap.push(c + h(node), node)
            }
        }
        var seeded = false
        if (m.allowedAt(se, 0, startOffsetS, portion = 1 - sf, f0 = sf, f1 = 1.0)) {
            seed(g.edgeTo[se], 0, m.segmentCost(se, 0, sf, 1.0), m.time[se * 2] * (1 - sf))
            seeded = true
        }
        if (m.allowedAt(se, 1, startOffsetS, portion = sf, f0 = 0.0, f1 = sf)) {
            seed(g.edgeFrom[se], 1, m.segmentCost(se, 1, 0.0, sf), m.time[se * 2 + 1] * sf)
            seeded = true
        }
        // Aynı kenar üzerinde doğrudan varış.
        if (dest != null && dest.edge == se) {
            val df = dest.fraction
            val w = walkCost(dest.dist)
            var direct: Double? = null
            if (df >= sf && m.allowedAt(se, 0, startOffsetS, portion = df - sf, f0 = sf, f1 = df)) direct = m.segmentCost(se, 0, sf, df) + w
            if (df < sf && m.allowedAt(se, 1, startOffsetS, portion = sf - df, f0 = sf, f1 = df)) direct = m.segmentCost(se, 1, sf, df) + w
            if (direct != null) {
                seeded = true
                dist[target] = direct
                targetFrom = -2
                heap.push(direct, target)
            }
        }
        if (!seeded) throw RoutingException(RouteMsg.START_NOT_RIDEABLE)

        var pops = 0L
        var relaxed = 0L
        try {
            while (!heap.isEmpty) {
                val u = heap.pop()
                pops++
                if (probe != null && (pops and 1023L) == 0L) probe.check()
                if (u == target) break
                if (closed[u]) continue
                closed[u] = true
                val du = dist[u]

                val fc = finishCost[u]
                if (fc != null) {
                    val kind = finishKind.getValue(u)
                    val canFinish = kind == 0 || (
                        dest != null && m.allowedAt(
                            dest.edge, if (kind == 1) 0 else 1, startOffsetS + elapsed[u],
                            portion = if (kind == 1) dest.fraction else 1 - dest.fraction,
                            f0 = if (kind == 1) 0.0 else dest.fraction, f1 = if (kind == 1) dest.fraction else 1.0,
                        )
                        )
                    if (canFinish && du + fc < dist[target]) {
                        dist[target] = du + fc
                        targetFrom = u
                        heap.push(dist[target], target)
                    }
                }

                for (a in g.adjStart[u] until g.adjStart[u + 1]) {
                    val e = g.adjEdge[a]
                    val dir = g.adjDir[a].toInt()
                    relaxed++
                    val c = m.segmentCost(e, dir, 0.0, 1.0)
                    if (!c.isFinite() || !m.allowedAt(e, dir, startOffsetS + elapsed[u])) continue
                    val v = if (dir == 0) g.edgeTo[e] else g.edgeFrom[e]
                    if (closed[v]) continue
                    val inE = if (prevEdge[u] == -2) se else prevEdge[u]
                    val turn = if (inE >= 0 && g.degree(u) >= 3) m.turnExtra(inE, prevDir[u].toInt(), e, dir) else 0.0
                    val nd = du + c + turn + (if (g.degree(v) >= 3) m.junctionCost else 0.0)
                    if (nd < dist[v]) {
                        dist[v] = nd
                        elapsed[v] = elapsed[u] + m.time[e * 2 + dir] + (if (g.degree(v) >= 3) 2 else 0)
                        prevEdge[v] = e
                        prevDir[v] = dir.toByte()
                        heap.push(nd + h(v), v)
                    }
                }
            }
        } finally {
            if (probe != null) {
                probe.pops += pops
                probe.edges += relaxed
            }
        }

        if (!dist[target].isFinite()) throw RoutingException(RouteMsg.NO_ROUTE_BY_RULES)

        // ---- Yolu geri kur ----
        val steps = ArrayList<Step>()
        var walkSteps: List<Step> = emptyList()
        if (targetFrom == -2) {
            val df = dest!!.fraction
            steps += Step(se, if (df >= sf) 0 else 1, sf, df)
        } else {
            val u = targetFrom
            val tail = ArrayList<Step>()
            when (finishKind.getValue(u)) {
                1 -> tail += Step(dest!!.edge, 0, 0.0, dest.fraction)
                2 -> tail += Step(dest!!.edge, 1, 1.0, dest.fraction)
                else -> walkSteps = approach.stepsFrom(u)
            }
            val mid = ArrayList<Step>()
            var v = u
            while (prevEdge[v] >= 0) {
                val e = prevEdge[v]
                val dir = prevDir[v].toInt()
                mid += Step(e, dir, if (dir == 0) 0.0 else 1.0, if (dir == 0) 1.0 else 0.0)
                v = if (dir == 0) g.edgeFrom[e] else g.edgeTo[e]
            }
            // v başlangıç tohumu: hangi yöne çıkıldı?
            val seedDir = prevDir[v].toInt()
            steps += Step(se, seedDir, sf, if (seedDir == 0) 1.0 else 0.0)
            steps.addAll(mid.asReversed())
            steps.addAll(tail)
        }

        // ---- Geometri + istatistik ----
        val ride = ArrayList<RoutePoint>()
        var rideM = 0.0
        var timeS = 0.0
        var energy = 0.0
        var climb = 0.0
        var wrongM = 0.0
        var qualSum = 0.0
        var riskSum = 0.0
        var pushM = 0.0
        val streets = ArrayList<String>()
        val segs = ArrayList<RouteSeg>()
        for (s in steps) {
            val portion = abs(s.f1 - s.f0)
            if (portion <= 0) continue
            val i = s.edge * 2 + s.dir
            val segM = g.edgeLen[s.edge].toDouble() * portion
            val walked = m.pushed[i].toInt() == 1
            segs += RouteSeg(s.edge, rideM, segM, g.nameOf(s.edge), walk = walked)
            rideM += segM
            if (walked) pushM += segM
            timeS += m.time[i] * portion
            energy += m.wh[i] * portion
            qualSum += m.quality[s.edge].toDouble() * segM
            if (m.wrong[i].toInt() == 1) {
                wrongM += segM
                riskSum += (m.wwRatio[g.osmIdOf(s.edge)] ?: 0.0) * segM
            }
            for (q in g.geometry(s.edge, s.f0, s.f1, reverse = s.dir == 1)) {
                if (ride.isNotEmpty() && ride.last().lat == q.lat && ride.last().lon == q.lon) continue
                if (ride.isNotEmpty() && q.ele > ride.last().ele) climb += q.ele - ride.last().ele
                ride += RoutePoint(q.lat, q.lon, q.ele)
            }
            val name = g.nameOf(s.edge)
            if (name.isNotEmpty() && (streets.isEmpty() || streets.last() != name)) streets += name
        }

        // Kavşaklardaki dönüşler (sesli talimat): sürüş parçaları arasındaki açı.
        val turns = ArrayList<RouteTurn>()
        var atM = 0.0
        var prevStep: Step? = null
        for (s in steps) {
            val portion = abs(s.f1 - s.f0)
            if (portion <= 0) continue
            val ps = prevStep
            if (ps != null) {
                val node = if (s.dir == 0) g.edgeFrom[s.edge] else g.edgeTo[s.edge]
                val a = m.turnAngle(ps.edge, ps.dir, s.edge, s.dir)
                if (g.degree(node) >= 3 && abs(a) >= 30) turns += RouteTurn(atM, a, g.nameOf(s.edge), g.nodeLat[node], g.nodeLon[node])
            }
            atM += g.edgeLen[s.edge].toDouble() * portion
            prevStep = s
        }

        val walk = ArrayList<RoutePoint>()
        var walkM = 0.0
        for (s in walkSteps) {
            val portion = abs(s.f1 - s.f0)
            if (portion <= 0) continue
            walkM += g.edgeLen[s.edge].toDouble() * portion
            for (q in g.geometry(s.edge, s.f0, s.f1, reverse = s.dir == 1)) {
                if (walk.isNotEmpty() && walk.last().lat == q.lat && walk.last().lon == q.lon) continue
                walk += RoutePoint(q.lat, q.lon, q.ele)
            }
        }
        if (walkM > 0) timeS += walkM * 1.2 / walkMps + p.parkPenaltyS

        val warnings = ArrayList<RouteMsg>()
        if (pushM > 0) warnings += RouteMsg.push(Math.round(pushM).toInt())
        if (hit.dist > 20) warnings += RouteMsg.door(Math.round(hit.dist).toInt())
        return RouteLeg(
            ride = ride,
            walk = walk,
            rideM = rideM,
            walkM = walkM,
            timeS = timeS,
            energyWh = energy,
            climbM = climb,
            wrongWayM = wrongM,
            cost = dist[target],
            streets = streets,
            destination = to,
            warnings = warnings,
            quality = if (rideM > 0) qualSum / rideM else 1.0,
            wrongWayRisk = if (wrongM > 0) riskSum / wrongM else 0.0,
            segs = segs,
            turns = turns,
            pushM = pushM,
        )
    }
}

/** Kenar kesimi: [f0] → [f1] kenar oranı (çizim yönünde), [dir] gidiş yönü. */
private class Step(val edge: Int, val dir: Int, val f0: Double, val f1: Double)

/**
 * Hedefe yalnızca grafta gerçekten bağlı yollardan yaya yaklaşımı (YolGo `_WalkingApproach`; MAP.md 3.7): hedef kenarının iki
 * ucundan yalnız yürünebilir kenarlarda uzunlukla Dijkstra (≤ `walkRadius`). Yakınlık tek başına duvarın, avlunun ya da olmayan
 * yolun aşılmasına izin vermez.
 */
private class WalkingApproach(model: CostModel, to: GeoPoint, candidate: EdgeHit?) {
    val target: EdgeHit? = candidate
        ?: model.g.nearestEdge(to.lat, to.lon, maxDist = 80.0, accept = { e -> model.walkable(e, 0) || model.walkable(e, 1) })

    /** Düğüm → hedefe yürüme mesafesi (ekleme sırasıyla). */
    val distance = LinkedHashMap<Int, Double>()
    private val next = HashMap<Int, Pair<Int, Step>>()
    private val tail = HashMap<Int, Step>()

    init {
        val hit = target
        if (hit != null) {
            val g = model.g
            val walkRadius = model.params.walkRadius
            val heap = MinHeap()
            val closed = HashSet<Int>()
            fun seed(node: Int, dir: Int, f0: Double) {
                if (!model.walkable(hit.edge, dir)) return
                val d = g.edgeLen[hit.edge].toDouble() * abs(hit.fraction - f0)
                if (d > walkRadius) return
                distance[node] = d
                tail[node] = Step(hit.edge, dir, f0, hit.fraction)
                heap.push(d, node)
            }
            seed(g.edgeFrom[hit.edge], 0, 0.0)
            seed(g.edgeTo[hit.edge], 1, 1.0)
            while (!heap.isEmpty) {
                val u = heap.pop()
                if (!closed.add(u)) continue
                for (a in g.adjStart[u] until g.adjStart[u + 1]) {
                    val e = g.adjEdge[a]
                    val dir = 1 - g.adjDir[a]
                    if (!model.walkable(e, dir)) continue
                    val v = if (dir == 0) g.edgeFrom[e] else g.edgeTo[e]
                    if (v in closed) continue
                    val d = distance.getValue(u) + g.edgeLen[e].toDouble()
                    if (d > walkRadius || d >= (distance[v] ?: Double.POSITIVE_INFINITY)) continue
                    distance[v] = d
                    next[v] = u to Step(e, dir, if (dir == 0) 0.0 else 1.0, if (dir == 0) 1.0 else 0.0)
                    tail.remove(v)
                    heap.push(d, v)
                }
            }
        }
    }

    /** [node]'dan hedefe yürüme adımları. */
    fun stepsFrom(node: Int): List<Step> {
        val out = ArrayList<Step>()
        var u = node
        while (true) {
            val n = next[u] ?: break
            out += n.second
            u = n.first
        }
        tail[u]?.let { out += it }
        return out
    }
}

/**
 * Dizi tabanlı ikili yığın (YolGo `_Heap`, MAP.md 3.6): yukarı çıkarken `ebeveyn ≤ çocuk` ise durur, aşağı inerken sol/sağ yalnız
 * kesin küçükse seçilir. Eşit anahtarlarda çekiş sırası Dart'takiyle aynı olsun diye `java.util.PriorityQueue` kullanılmaz.
 */
internal class MinHeap {
    private var keys = DoubleArray(64)
    private var vals = IntArray(64)
    private var size = 0

    val isEmpty: Boolean get() = size == 0

    fun push(key: Double, value: Int) {
        if (size == keys.size) {
            keys = keys.copyOf(size * 2)
            vals = vals.copyOf(size * 2)
        }
        keys[size] = key
        vals[size] = value
        var i = size++
        while (i > 0) {
            val p = (i - 1) shr 1
            if (keys[p] <= keys[i]) break
            swap(i, p)
            i = p
        }
    }

    fun pop(): Int {
        val top = vals[0]
        size--
        if (size > 0) {
            keys[0] = keys[size]
            vals[0] = vals[size]
            var i = 0
            val n = size
            while (true) {
                val l = 2 * i + 1
                val r = l + 1
                var m = i
                if (l < n && keys[l] < keys[m]) m = l
                if (r < n && keys[r] < keys[m]) m = r
                if (m == i) break
                swap(i, m)
                i = m
            }
        }
        return top
    }

    private fun swap(a: Int, b: Int) {
        val k = keys[a]
        keys[a] = keys[b]
        keys[b] = k
        val v = vals[a]
        vals[a] = vals[b]
        vals[b] = v
    }
}
