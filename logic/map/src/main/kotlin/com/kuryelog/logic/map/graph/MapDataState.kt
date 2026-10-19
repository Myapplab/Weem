package com.kuryelog.logic.map.graph

enum class MapDataPhase { CHECKING, UNPACKING, MISSING, BUILDING, READY, ERROR }

/** SRTM yükseklik modeli (MAP.md 2.7). İskelet (A6.2a). */
class ElevationModel

/** `lib/data/map_data_service.dart` `MapDataState` (MAP.md 2.9, 2.14). */
class MapDataState(
    val phase: MapDataPhase,
    val progress: Double = 0.0,
    val stage: String = "",
    val graph: RoadGraph? = null,
    val elevation: ElevationModel? = null,
    val error: String? = null,
    val builtAtMs: Long? = null,
    val sizeBytes: Long = 0,
    val pois: PoiSet? = null,
) {
    val ready: Boolean get() = TODO("A6.3")

    companion object {
        /** Güncelleme başarısız: hata gösterilir ama varsa eski harita kullanılmaya devam eder. */
        fun updateFailed(error: String, graph: RoadGraph? = null, elevation: ElevationModel? = null, pois: PoiSet? = null): MapDataState =
            TODO("A6.3")
    }
}
