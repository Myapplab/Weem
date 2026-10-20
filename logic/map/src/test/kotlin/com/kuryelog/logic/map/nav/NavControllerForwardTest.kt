package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.input.DestinationResolver
import com.kuryelog.logic.map.input.GeoConfidence
import com.kuryelog.logic.map.input.GeocodeHit
import com.kuryelog.logic.map.input.Geocoder
import com.kuryelog.logic.map.nav.NavFixtures.LAT0
import com.kuryelog.logic.map.nav.NavFixtures.LON0
import com.kuryelog.logic.map.nav.NavFixtures.T0
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import com.kuryelog.logic.map.route.RouteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** WEEM A8.3 (LINK.md 4.5): Provider'da çözülen dış hedef Receiver'a gider; gitmezse rota burada açılır. */
class NavControllerForwardTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()
    private val http = FakeHttp { HttpResponse(500, "{}") }

    private class RestaurantGeocoder : Geocoder {
        override suspend fun search(query: String, near: GeoPoint?) =
            GeocodeHit(GeoPoint(40.4091, 49.8673), "Restoran", GeoConfidence.HIGH, "test", restaurant = true)
    }

    private fun nav() = NavController(engine, scope, FakeClock(T0), DestinationResolver(http, RestaurantGeocoder())).apply {
        setGraph(null)
        setOrigin(GeoPoint(LAT0, LON0))
    }

    @After
    fun close() {
        engine.dispose()
        scope.cancel()
    }

    @Test
    fun forwardedTargetDoesNotOpenHere() = runBlocking {
        val nav = nav()
        val sent = ArrayList<Stop>()
        nav.forward = { sent += it; true }
        nav.onExternalText("Restoran, Bakı", "intent", origin = NavOrigin.INTENT, referrer = "com.wolt.courierapp", requestedAtMs = T0)
        assertEquals(listOf(NavOrigin.INTENT to "com.wolt.courierapp"), sent.map { it.origin to it.sourcePackage })
        assertEquals(T0, sent.single().requestedAtMs)
        assertTrue(nav.state.value.stops.isEmpty())
        assertFalse(nav.state.value.busy)
        assertEquals(NavText.FORWARDED.tr, nav.toast.value?.text)
    }

    @Test
    fun stopBecomesANavTarget() {
        val s = Stop(GeoPoint(40.4, 49.8), "Nizami 5", restaurant = false, origin = NavOrigin.INTENT, requestedAtMs = T0, sourcePackage = "com.wolt.courierapp")
        val t = s.navTarget("r1", T0 + 5)
        assertEquals(listOf<Any?>("r1", 40.4, 49.8, com.kuryelog.logic.core.NavKind.CUSTOMER, NavOrigin.INTENT, "com.wolt.courierapp", T0, "Nizami 5"),
            listOf(t.requestId, t.lat, t.lon, t.kind, t.origin, t.sourcePackage, t.requestedAtMs, t.label))
        assertEquals(com.kuryelog.logic.core.NavKind.UNKNOWN, s.copy(restaurant = null, requestedAtMs = null).navTarget("r2", T0 + 5).kind)
        assertEquals(T0 + 5, s.copy(requestedAtMs = null).navTarget("r2", T0 + 5).requestedAtMs)
    }

    @Test
    fun unreachablePeerKeepsTheLocalRoute() = runBlocking {
        val nav = nav()
        nav.forward = { false }
        nav.onExternalText("Restoran, Bakı", "intent", origin = NavOrigin.INTENT)
        assertEquals(1, nav.state.value.stops.size)
    }
}
