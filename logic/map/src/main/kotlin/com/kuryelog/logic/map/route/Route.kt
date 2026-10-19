package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.SurfaceMark

/** Rota hatası; ileti TR kodlu metin (`MAP.md` 3.12). */
class RoutingException(override val message: String) : Exception(message)

/** Rota kesimi: kenar, itilen (yürünen) mi, çizgi üzerinde başlangıç metresi. */
class RouteSeg(val edge: Int, val walk: Boolean, val startM: Double, val lenM: Double)

class RouteTurn(val right: Boolean)

/** Tek bacak (`MAP.md` 3.7). İskelet (A6.2a): üretim A6.4'te. */
class RouteLeg(
    val rideM: Double,
    val walkM: Double,
    val startWalkM: Double,
    val pushM: Double,
    val wrongWayM: Double,
    val timeS: Double,
    val energyWh: Double,
    val climbM: Double,
    val descentM: Double,
    val parkPoint: GeoPoint,
    val walk: List<GeoPoint>,
    val startWalk: List<GeoPoint>,
    val segs: List<RouteSeg>,
    val turns: List<RouteTurn>,
    val warnings: List<String>,
    /** Rotadaki sokak adları sırayla (`MAP.md` 9.2). */
    val streets: List<String>,
    /** Rotanın ortalama kalitesi 0–1. */
    val quality: Double,
    /** Ters yönde geçilen kesimlerin ağırlıklı sorun riski. */
    val wrongWayRisk: Double,
)

class Plan(val rideM: Double, val legs: List<RouteLeg>, val order: List<Int>)

class PlanResult(val plans: Map<RideMode, Plan>, val error: String?)

/** Maliyet modeli (`MAP.md` 3.3–3.5). İskelet (A6.2a): hesap A6.4'te. */
class CostModel(
    val graph: RoadGraph,
    val mode: RideMode,
    val params: RideParams,
    val approved: Set<Long> = emptySet(),
    val blocked: Set<Long> = emptySet(),
    val steep: Set<Long> = emptySet(),
    val wwRatio: Map<Long, Double> = emptyMap(),
    val wwAvoid: Set<Long> = emptySet(),
    val ghost: Set<Int> = emptySet(),
    val wrongWayEdges: Set<Int> = emptySet(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
    val bikeways: Set<Long> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val marks: List<SurfaceMark> = emptyList(),
    val departureMs: Long? = null,
) {
    /** Kenar × yön (0 ileri, 1 geri) maliyet, süre (sn) ve enerji (Wh); dizin `kenar * 2 + yön` (YolGo `Float64List`). */
    val cost: DoubleArray get() = TODO("A6.4")
    val time: DoubleArray get() = TODO("A6.4")
    val wh: DoubleArray get() = TODO("A6.4")
    val heuristicScale: Double get() = TODO("A6.4")

    fun allowed(edge: Int, dir: Int): Boolean = TODO("A6.4")
}

/** Tek bacak A*: `from` → `to` (`MAP.md` 3.9). */
fun routeLeg(model: CostModel, from: GeoPoint, to: GeoPoint): RouteLeg = TODO("A6.4")

/** Yürünen (itilen) parçalar, çizgiden mesafeyle kesilmiş (`MAP.md` 3.8). */
fun pushedRuns(leg: RouteLeg): List<List<GeoPoint>> = TODO("A6.4")

/** Planlayıcı isteği (`MAP.md` 3.10). */
class PlanRequest(
    val graph: RoadGraph,
    val origin: GeoPoint,
    val stops: List<GeoPoint>,
    val params: RideParams,
    val approved: Set<Long> = emptySet(),
    val blocked: Set<Long> = emptySet(),
    val steep: Set<Long> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val bikeways: Set<Long> = emptySet(),
    val marks: List<SurfaceMark> = emptyList(),
    val wrongWayEdges: Set<Int> = emptySet(),
    val ghost: Set<Int> = emptySet(),
)

fun computePlans(request: PlanRequest): PlanResult = TODO("A6.4")
