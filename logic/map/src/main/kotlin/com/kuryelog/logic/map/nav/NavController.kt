package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Navigasyon durumu (`MAP.md` 7.2); iskelet (A6.2a) yalnız planları taşır. */
data class NavState(val plans: Map<RideMode, Plan> = emptyMap(), val error: String? = null)

/**
 * Saf navigasyon durum makinesi (`MAP.md` 7.16). İskelet (A6.2a): imza A6.6'da kesinleşir; `bikeway_e13_test` "uygulamada (yeniden rota)"
 * testleri bu yüzeyi kullanır (hedefe git, elle işaret kümesi ya da ayar değişince açık rota yeniden hesaplanır).
 */
class NavController(
    private val engine: RouteEngine,
    private val graph: () -> RoadGraph?,
    private val origin: () -> GeoPoint?,
    settings: MapSettings,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<NavState> get() = TODO("A6.6")

    fun goTo(destination: GeoPoint): Unit = TODO("A6.6")

    /** Elle bisiklet yolu işaretleri değişti → açık rota yeniden hesaplanır (`replan('prefs')`). */
    fun setBikewayMarks(ways: Set<Long>): Unit = TODO("A6.6")

    /** Ayar değişti (`qualityTolerancePct`, `wwMaxProblemPct`, `turnPenaltyS`, `bikewayCostPct`) → açık rota yeniden hesaplanır. */
    fun updateSettings(transform: (MapSettings) -> MapSettings): Unit = TODO("A6.6")
}
