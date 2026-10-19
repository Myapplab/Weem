package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.SurfaceMark
import java.time.Instant

/**
 * Bir sorgu grubu için kenar maliyetleri; her kenarın iki yönü ayrı (dizin `edge * 2 + dir`). Kurallar MAP.md 3.4–3.5.
 * Way kimlikleri OSM way id; `ghost` ve `wrongWayEdges` kenar indeksi. İskelet (A6.2a): gövde A6.4'te.
 */
class CostModel(
    val g: RoadGraph,
    val mode: RideMode,
    val params: RideParams,
    val approved: Set<Long>,
    val blocked: Set<Long>,
    val steep: Set<Long> = emptySet(),
    val avoidBad: Boolean = false,
    /** Haritada var ama gerçekte yok: hiçbir koşulda kullanılmaz (kenar indeksi). */
    val ghost: Set<Int> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val marks: List<SurfaceMark> = emptyList(),
    /** OSM way id → ters yön sorun oranı (yumuşatılmış, 0–1). */
    val wwRatio: Map<Long, Double> = emptyMap(),
    val wwAvoid: Set<Long> = emptySet(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
    /** Ana caddede bile ters yön izni verilen kesimler (kenar indeksi, elle işaretli). */
    val wrongWayEdges: Set<Int> = emptySet(),
    /** E13: elle işaretlenmiş bisiklet yolları (OSM way id); OSM bayrağı gibi. */
    val bikeways: Set<Long> = emptySet(),
    val departure: Instant = Instant.now(),
) {
    val cost: DoubleArray get() = TODO("A6.4")
    val time: DoubleArray get() = TODO("A6.4")
    val wh: DoubleArray get() = TODO("A6.4")

    /** A* sezgisinin çarpanı (E13: bisiklet yolu varken `bikewayFactor`, yoksa 1). */
    val heuristicScale: Double get() = TODO("A6.4")

    fun allowed(e: Int, dir: Int): Boolean = TODO("A6.4")
}
