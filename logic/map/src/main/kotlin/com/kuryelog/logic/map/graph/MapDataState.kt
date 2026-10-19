package com.kuryelog.logic.map.graph

/** `unpacking`: uygulamaya gömülü harita ilk açılışta (ya da güncellemede) diske açılıyor. */
enum class MapDataPhase { CHECKING, UNPACKING, MISSING, BUILDING, READY, ERROR }

/** `lib/data/map_data_service.dart` `MapDataState` (MAP.md 2.9, 2.14). [stage] ilerleme metni (dil seçilmiş), [error] hata metni. */
class MapDataState(
    val phase: MapDataPhase,
    val progress: Double = 0.0,
    val stage: String = "",
    val graph: RoadGraph? = null,
    val elevation: ElevationModel? = null,
    val error: String? = null,
    val builtAtMs: Long? = null,
    val sizeBytes: Long = 0,
    /** Restoran / kafe / ışık noktaları (yoksa null; harita yine çalışır). */
    val pois: PoiSet? = null,
) {
    val ready: Boolean get() = phase == MapDataPhase.READY && graph != null

    fun withPois(p: PoiSet?) = MapDataState(phase, progress, stage, graph, elevation, error, builtAtMs, sizeBytes, p)

    companion object {
        /** Güncelleme başarısız: hata gösterilir ama varsa eski harita kullanılmaya devam eder. */
        fun updateFailed(error: String, graph: RoadGraph? = null, elevation: ElevationModel? = null, pois: PoiSet? = null) =
            MapDataState(MapDataPhase.ERROR, error = error, graph = graph, elevation = elevation, pois = pois)
    }
}
