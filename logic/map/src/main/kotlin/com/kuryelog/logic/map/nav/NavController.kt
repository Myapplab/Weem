package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Navigasyonun rota durumu (MAP.md 7.16 `NavState`'in A6.2a'da gereken alt kümesi). */
data class NavState(val plans: Map<RideMode, Plan> = emptyMap(), val error: String? = null)

/**
 * Saf navigasyon durum makinesi (MAP.md 7.16). İskelet (A6.2a): yalnız "ayar ya da elle işaret değişince açık rota kendiliğinden
 * yeniden hesaplanır" testlerinin (`NavControllerBikewayReplanTest`) gerektirdiği yüzey; gerisi A6.6'da.
 */
class NavController(private val engine: RouteEngine, private val scope: CoroutineScope) {
    val state: StateFlow<NavState> get() = TODO("A6.6")

    fun setGraph(graph: RoadGraph?): Unit = TODO("A6.6")
    fun setOrigin(p: GeoPoint): Unit = TODO("A6.6")
    fun setSettings(s: MapSettings): Unit = TODO("A6.6")
    fun setBikewayMarks(m: BikewayMarks): Unit = TODO("A6.6")

    /** Hedefe rota ister (`replan` tetikleri: `prefs`). */
    fun goTo(target: GeoPoint): Unit = TODO("A6.6")
}
