package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.SurfaceMark
import java.time.Instant

/** Hesap isteği (düz veri; MAP.md 3.10). */
class PlanRequest(
    val graph: RoadGraph,
    val origin: GeoPoint,
    val stops: List<GeoPoint>,
    val params: RideParams,
    val approved: Set<Long>,
    val blocked: Set<Long>,
    val steep: Set<Long> = emptySet(),
    val heading: Double? = null,
    val fixedOrder: Boolean = false,
    val ghost: Set<Int> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val marks: List<SurfaceMark> = emptyList(),
    val wwRatio: Map<Long, Double> = emptyMap(),
    val wwAvoid: Set<Long> = emptySet(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
    val wrongWayEdges: Set<Int> = emptySet(),
    val bikeways: Set<Long> = emptySet(),
    val departure: Instant? = null,
)

class PlanResult(val plans: Map<RideMode, Plan>, val error: String?)

object Planner {
    /** Her iki mod için en iyi sıra + rotalar. */
    fun computePlans(r: PlanRequest): PlanResult = TODO("A6.4")
}
