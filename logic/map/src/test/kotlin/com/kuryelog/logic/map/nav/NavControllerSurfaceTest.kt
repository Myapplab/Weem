package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.route.TestGraphs.p
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.surface.SurfaceKey
import com.kuryelog.logic.map.surface.SurfaceSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Y13 (A10.4b): ölçülmüş yüzey rota hesabında kullanılır (`NavController.surfaceInputs` → `PlanRequest.surface`); bağlı değilse eski rota. */
class NavControllerSurfaceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()

    private fun bad(g: RoadGraph): SurfaceSummary {
        val from = g.edgeFrom[TestGraphs.edgeOf(g, 100)]
        return SurfaceSummary(SurfaceKey.of(100, SurfaceKey.anchor(g.nodeLat[from], g.nodeLon[from]), 0, 0), 100, 0, 0, "profile", 15.0, .9, 200.0, 200.0, 4, 0, 1.2, 4.0, 0, 0.0,
            lat(25.0), lon(0.0), calibrated = true)
    }

    private fun ride(nav: NavController, want: Double): Double = runBlocking {
        for (i in 0 until 200) {
            val m = nav.state.value.plans[RideMode.TIME]?.rideM
            if (m != null && Math.abs(m - want) < 5) return@runBlocking m
            kotlinx.coroutines.delay(25)
        }
        nav.state.value.plans[RideMode.TIME]?.rideM ?: -1.0
    }

    @Test
    fun `ölçülmüş kötü kesim rotayı dolambaça çevirir, yüzey bağlı değilse en kısa yol`() {
        try {
            val g = TestGraphs.directAndDetour()
            fun nav(withSurface: Boolean): NavController = NavController(engine, scope).also {
                it.setGraph(g); it.setSettings(MapSettings()); it.setOrigin(p(0.0, 0.0))
                if (withSurface) it.surfaceInputs = { SurfaceInputs(listOf(bad(g))) }
                it.goTo(p(0.0, 400.0))
            }
            assertEquals(400.0, ride(nav(false), 400.0), 5.0)
            assertEquals(800.0, ride(nav(true), 800.0), 5.0)
        } finally {
            engine.dispose(); scope.cancel()
        }
    }

    @Test
    fun `yüzey kancası hata verirse rota yüzeysiz hesaplanır`() {
        try {
            val g = TestGraphs.directAndDetour()
            val nav = NavController(engine, scope).also {
                it.setGraph(g); it.setSettings(MapSettings()); it.setOrigin(p(0.0, 0.0))
                it.surfaceInputs = { error("okunamadı") }
                it.goTo(p(0.0, 400.0))
            }
            assertEquals(400.0, ride(nav, 400.0), 5.0)
        } finally {
            engine.dispose(); scope.cancel()
        }
    }
}
