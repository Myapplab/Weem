package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `bikeway_e13_test.dart` "uygulamada" grubunun yeniden rota testleri (2 test): elle işaret ve ayar değişince `NavController` açık rotayı
 * yeniden hesaplatır; `MAP.md` 5.2, 7.15. Konum Dart testindeki gibi A noktasında duruyor (rota başlangıcı).
 */
class NavControllerBikewayTest {
    private suspend fun NavController.rideAfter(done: (Double) -> Boolean): Double {
        repeat(200) {
            val m = state.value.plans[RideMode.TIME]?.rideM
            if (m != null && done(m)) return m
            delay(25)
        }
        return state.value.plans[RideMode.TIME]?.rideM ?: -1.0
    }

    private fun controller(g: com.kuryelog.logic.map.graph.RoadGraph, engine: RouteEngine, scope: CoroutineScope) =
        NavController(engine, graph = { g }, origin = { p(0.0, 0.0) }, settings = MapSettings(), scope = scope)

    /** Dart: "elle işaret rotayı değiştirir (NavController → RouteWorker), geri alınca eski rota". */
    @Test
    fun manualMarksChangeTheRouteAndUndoRestoresIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        RouteEngine().use { engine ->
            val nav = controller(TestGraphs.parallel(), engine, scope)
            nav.goTo(p(0.0, 400.0))
            assertEquals(440.0, nav.rideAfter { Math.abs(it - 440) < 5 }, 5.0)
            nav.setBikewayMarks(setOf(401L, 402L, 403L))
            assertEquals("işaret rotayı yeniden hesaplatır", 480.0, nav.rideAfter { Math.abs(it - 480) < 5 }, 5.0)
            nav.setBikewayMarks(emptySet())
            assertEquals("geri alınınca eski rota", 440.0, nav.rideAfter { Math.abs(it - 440) < 5 }, 5.0)
        }
        scope.cancel()
    }

    /** Dart: "ağırlık ayardan değişince rota yeniden hesaplanır (×1,00: bisiklet yolu tercih edilmez)". */
    @Test
    fun changingTheBikewayWeightReplansTheRoute() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        RouteEngine().use { engine ->
            val nav = controller(TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "track")), engine, scope)
            nav.goTo(p(0.0, 400.0))
            assertEquals(480.0, nav.rideAfter { Math.abs(it - 480) < 5 }, 5.0)
            nav.updateSettings { it.copy(bikewayCostPct = 100.0) }
            assertEquals(440.0, nav.rideAfter { Math.abs(it - 440) < 5 }, 5.0)
        }
        scope.cancel()
    }
}
