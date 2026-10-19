package com.kuryelog.logic.map.graph

enum class MapDataPhase { CHECKING, UNPACKING, MISSING, BUILDING, READY, ERROR }

/** Harita verisi durumu (YolGo `MapDataState`, `MAP.md` 2.8). İskelet (A6.2a). */
class MapDataState private constructor(
    val phase: MapDataPhase,
    val error: String? = null,
    val graph: RoadGraph? = null,
) {
    val ready: Boolean get() = phase == MapDataPhase.READY && graph != null

    companion object {
        /** Güncelleme hatası: `ERROR` fazı, eski veri bellekte. */
        fun updateFailed(error: String, graph: RoadGraph?): MapDataState = TODO("A6.3")
    }
}
