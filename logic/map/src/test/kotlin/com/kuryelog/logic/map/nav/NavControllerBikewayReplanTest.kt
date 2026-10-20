package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.EAST_WAYS
import com.kuryelog.logic.map.route.TestGraphs.p
import com.kuryelog.logic.map.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `bikeway_e13_test.dart` "uygulamada" 2 test (A6.2a, MAP.md 5.2, 7.15): elle işaret ve ağırlık ayarı açık rotayı
 * kendiliğinden yeniden hesaplatır (`replan('prefs')`). `NavController` saf durum makinesi (Riverpod, geolocator ve ekran yok);
 * sahte konum = `setOrigin`. Uygulama: A6.6.
 */
class NavControllerBikewayReplanTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()

    private fun app(settings: MapSettings = MapSettings(), g: com.kuryelog.logic.map.graph.RoadGraph): NavController {
        val nav = NavController(engine, scope)
        nav.setGraph(g)
        nav.setSettings(settings)
        nav.setOrigin(p(0.0, 0.0)) // kurye A noktasında duruyor (rota başlangıcı; GPS yok)
        return nav
    }

    /** Rota `done` olana dek (en çok ~5 sn) bekler; son değeri döner. */
    private fun rideAfter(nav: NavController, done: (Double) -> Boolean): Double = runBlocking {
        for (i in 0 until 200) {
            val m = nav.state.value.plans[RideMode.TIME]?.rideM
            if (m != null && done(m)) return@runBlocking m
            kotlinx.coroutines.delay(25)
        }
        nav.state.value.plans[RideMode.TIME]?.rideM ?: -1.0
    }

    private fun finish() {
        engine.dispose()
        scope.cancel()
    }

    @Test
    fun `elle işaret rotayı değiştirir (NavController → RouteEngine), geri alınca eski rota`() {
        try {
            val nav = app(g = TestGraphs.parallel())
            nav.goTo(p(0.0, 400.0))
            assertEquals(440.0, rideAfter(nav) { Math.abs(it - 440) < 5 }, 5.0)
            nav.setBikewayMarks(BikewayMarks(ways = EAST_WAYS))
            assertEquals("işaret rotayı yeniden hesaplatır", 480.0, rideAfter(nav) { Math.abs(it - 480) < 5 }, 5.0)
            nav.setBikewayMarks(BikewayMarks())
            assertEquals("geri alınınca eski rota", 440.0, rideAfter(nav) { Math.abs(it - 440) < 5 }, 5.0)
        } finally {
            finish()
        }
    }

    @Test
    fun `ağırlık ayardan değişince rota yeniden hesaplanır (x1,00 bisiklet yolu tercih edilmez)`() {
        try {
            val nav = app(g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "track")))
            nav.goTo(p(0.0, 400.0))
            assertEquals(480.0, rideAfter(nav) { Math.abs(it - 480) < 5 }, 5.0)
            nav.setSettings(MapSettings(bikewayCostPct = 100.0))
            assertEquals(440.0, rideAfter(nav) { Math.abs(it - 440) < 5 }, 5.0)
        } finally {
            finish()
        }
    }
}
