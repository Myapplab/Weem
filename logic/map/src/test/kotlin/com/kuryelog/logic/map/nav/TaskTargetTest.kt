package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.input.DestinationResolver
import com.kuryelog.logic.map.input.GeoConfidence
import com.kuryelog.logic.map.input.GeocodeHit
import com.kuryelog.logic.map.input.Geocoder
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import com.kuryelog.logic.map.route.RouteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Rota Oluştur" (S9, MAP.md 13.13): görev hedefi WEEM navigasyonunu açar; `_bindShiftTarget` görev hedefini siparişin restoran/müşteri hedefi saymaz. */
class TaskTargetTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()
    private val here = GeoPoint(40.4, 49.8)

    private class NoGeo : Geocoder {
        override suspend fun search(query: String, near: GeoPoint?) = GeocodeHit(GeoPoint(40.4, 49.8), "x", GeoConfidence.HIGH, "test")
    }

    private class Rec : NavShift {
        val destinations = ArrayList<Triple<GeoPoint, Boolean, String>>()
        override fun setDestination(point: GeoPoint, restaurant: Boolean, name: String) { destinations += Triple(point, restaurant, name) }
        override fun startHomeReturn() {}
    }

    @After fun close() { engine.dispose(); scope.cancel() }

    private fun nav(rec: Rec): NavController {
        val clock = FakeClock()
        return NavController(engine, scope, clock, DestinationResolver(FakeHttp { HttpResponse(500, "{}") }, NoGeo()), { Lang.TR }).also {
            it.setGraph(null); it.setOrder(dayActive = true, currentOrder = 3); it.setOrigin(here); it.shift = rec
        }
    }

    @Test
    fun `görev hedefi gün açıkken bile siparişe bağlanmaz, el hedefi bağlanır`() {
        val rec = Rec()
        val c = nav(rec)
        c.goTo(GeoPoint(40.41, 49.81), "Banka · Filial", restaurant = null, origin = com.kuryelog.logic.core.NavOrigin.MAP, purpose = StopPurpose.TASK)
        assertTrue(rec.destinations.isEmpty())
        assertEquals(StopPurpose.TASK, c.state.value.stops.single().purpose)
        c.goTo(GeoPoint(40.42, 49.82), "Restoran", restaurant = true)
        assertEquals(1, rec.destinations.size)
        assertEquals(StopPurpose.ORDER, c.state.value.stops.single().purpose)
    }
}
