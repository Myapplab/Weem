package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.SurfaceMark
import java.time.Instant

/** Hesap isteği (düz veri; YolGo `PlanRequest`, MAP.md 3.3, 3.10). Y13 yüzey girdileri 1.0'da yok (MAP.md 3.16 SORU 1). */
class PlanRequest(
    val graph: RoadGraph,
    val origin: GeoPoint,
    val stops: List<GeoPoint>,
    val params: RideParams,
    val approved: Set<Long>,
    val blocked: Set<Long>,
    /** Aşırı eğimli: yokuş yukarı kapalı. */
    val steep: Set<Long> = emptySet(),
    /** Derece; yalnız ilk noktadan çıkan bacakta (hız ≥ 2 m/s iken çağıran verir). */
    val heading: Double? = null,
    /** true: durak sırasını değiştirme (Y19). */
    val fixedOrder: Boolean = false,
    /** Kenar: haritada var, gerçekte yok. */
    val ghost: Set<Int> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val marks: List<SurfaceMark> = emptyList(),
    val wwRatio: Map<Long, Double> = emptyMap(),
    val wwAvoid: Set<Long> = emptySet(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
    /** Kenar: ana caddede elle verilmiş ters yön izni. */
    val wrongWayEdges: Set<Int> = emptySet(),
    /** E13 sokak: elle işaretlenmiş bisiklet yolu (OSM bayrağı gibi). */
    val bikeways: Set<Long> = emptySet(),
    /** UTC an; null → şimdi. */
    val departure: Instant? = null,
)

/** [error]: hiç plan yoksa ilk rota hatası (yoksa "Rota bulunamadı"); plan varsa null. */
class PlanResult(val plans: Map<RideMode, Plan>, val error: RouteMsg?)

/** Her iki mod için en iyi sıra + rotalar — YolGo `planner.dart` (MAP.md 3.10). */
object Planner {
    /** Durak sırası denenirken en çok bu kadar durak (4 → 24 sıra); fazlası verilen sırayla. */
    private const val MAX_PERMUTED_STOPS = 4

    fun computePlans(r: PlanRequest, probe: RouteProbe? = null): PlanResult {
        val plans = LinkedHashMap<RideMode, Plan>()
        var error: RouteMsg? = null
        // Bütün denemeler aynı kalkış anıyla (YolGo her CostModel'de ayrı `now` alırdı; fark mikrosaniye).
        val departure = r.departure ?: Instant.now()
        for (mode in RideMode.entries) {
            try {
                plans[mode] = planWithAvoid(r, mode, departure, probe)
            } catch (e: RoutingException) {
                if (error == null) error = e.msg
            }
        }
        return PlanResult(plans, if (plans.isEmpty()) error ?: RouteMsg.NO_ROUTE else null)
    }

    /**
     * Önce kötü yollar dışlanarak; bağlantı yoksa kötü yollara izin verilir ve her bacağa uyarı eklenir. Kara liste, özel alan,
     * "yol yok" ve eğim kuralları gevşetilmez. (YolGo `_planForMode` ölçülmüş yüzey adayı Y13 ile gelir; 1.0'da tek aday.)
     */
    private fun planWithAvoid(r: PlanRequest, mode: RideMode, departure: Instant, probe: RouteProbe?): Plan {
        fun model(avoid: Boolean) = CostModel(
            r.graph, mode = mode, params = r.params, approved = r.approved, blocked = r.blocked, steep = r.steep, avoidBad = avoid,
            ghost = r.ghost, learned = r.learned, marks = r.marks, wwRatio = r.wwRatio, wwAvoid = r.wwAvoid,
            reverseHours = r.reverseHours, wrongWayEdges = r.wrongWayEdges, bikeways = r.bikeways,
            departure = departure,
        )
        return try {
            bestOrder(model(true), r, mode, probe)
        } catch (_: RoutingException) {
            val plan = bestOrder(model(false), r, mode, probe)
            Plan(plan.mode, plan.order, plan.legs.map { it.withWarnings(it.warnings + RouteMsg.BAD_ROADS_NEEDED) })
        }
    }

    /** Çifte sipariş: bütün sıralar denenir (en çok 4 durak → 24), toplam maliyeti en düşük sıra (eşitlikte ilk) seçilir. */
    private fun bestOrder(model: CostModel, r: PlanRequest, mode: RideMode, probe: RouteProbe?): Plan {
        val points = listOf(r.origin) + r.stops // 0 = başlangıç
        val cache = HashMap<Int, RouteLeg>()
        fun leg(i: Int, j: Int, elapsed: Double): RouteLeg {
            probe?.check()
            val heading = if (i == 0) r.heading else null
            // Saatli izin varken bacak başlangıç anına bağlıdır: önbellek yok.
            if (r.reverseHours.isNotEmpty()) return Router.routeLeg(model, points[i], points[j], heading, startOffsetS = elapsed, probe = probe)
            return cache.getOrPut(i * 100 + j) { Router.routeLeg(model, points[i], points[j], heading, probe = probe) }
        }

        val idx = (1..r.stops.size).toList()
        val orders = if (r.fixedOrder || idx.size > MAX_PERMUTED_STOPS) listOf(idx) else permutations(idx)

        var bestOrder: List<Int>? = null
        var bestLegs: List<RouteLeg>? = null
        var bestCost = Double.POSITIVE_INFINITY
        var lastErr: RoutingException? = null
        for (order in orders) {
            try {
                var prev = 0
                var total = 0.0
                var elapsed = 0.0
                val legs = ArrayList<RouteLeg>()
                for (k in order) {
                    val l = leg(prev, k, elapsed)
                    legs += l
                    total += l.cost
                    elapsed += l.timeS
                    prev = k
                }
                if (total < bestCost) {
                    bestCost = total
                    bestOrder = order
                    bestLegs = legs
                }
            } catch (e: RoutingException) {
                lastErr = e
            }
        }
        if (bestOrder == null || bestLegs == null) throw lastErr ?: RoutingException(RouteMsg.NO_ROUTE)
        return Plan(mode, bestOrder.map { it - 1 }, bestLegs)
    }

    /** Özyinelemeli permütasyonlar, ilk eleman sırasıyla (YolGo `_permutations`). */
    private fun permutations(items: List<Int>): List<List<Int>> {
        if (items.size <= 1) return listOf(items.toList())
        val out = ArrayList<List<Int>>()
        for (i in items.indices) {
            val rest = items.toMutableList().also { it.removeAt(i) }
            for (p in permutations(rest)) out += listOf(items[i]) + p
        }
        return out
    }
}
