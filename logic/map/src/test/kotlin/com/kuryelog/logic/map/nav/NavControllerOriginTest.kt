package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavKind
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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * YolGo `nav_feed_test.dart` "hedef: siparişe bağlanan durak" (1) + "NavController + yayıncı" (2; A6.2b, MAP.md 7.2, 7.9, 8.7, 7.15): kurye uygulamasının
 * navigasyon isteği hedefi `intent` olarak yayınlanır, siparişe bağlanınca da korunur; pano ve arama kaynakları. Riverpod/yayıncı yerine [NavController.routeStatus];
 * sipariş (YolGo'da Rawstats feed v2) `setOrder` girdisiyle. Harita verisi yok (boş graf): rota hesaplanmaz, hedef yine yayınlanır.
 */
class NavControllerOriginTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()
    private val http = FakeHttp { HttpResponse(500, "{}") }

    private class RestaurantGeocoder : Geocoder {
        override suspend fun search(query: String, near: GeoPoint?) =
            GeocodeHit(GeoPoint(40.4091, 49.8673), "Restoran", GeoConfidence.HIGH, "test", restaurant = true)
    }

    private fun nav(): NavController {
        val nav = NavController(engine, scope, FakeClock(T0), DestinationResolver(http, RestaurantGeocoder()))
        nav.setGraph(null)
        nav.setOrigin(GeoPoint(LAT0, LON0))
        return nav
    }

    @After
    fun close() {
        engine.dispose()
        scope.cancel()
    }

    @Test
    fun `siparişe bağlanan durak (forOrder) kaynağını saatini ve göndereni korur`() {
        val s = Stop(GeoPoint(40.4, 49.8), "R", origin = NavOrigin.INTENT, requestedAtMs = T0, sourcePackage = "com.wolt.courierapp").forOrder(3, true)
        assertEquals(
            listOf(StopPurpose.ORDER, 3, NavOrigin.INTENT, T0, "com.wolt.courierapp"),
            listOf(s.purpose, s.orderId, s.origin, s.requestedAtMs, s.sourcePackage),
        )
    }

    @Test
    fun `kurye uygulamasının navigasyon isteği hedefi intent olarak yayınlanır, siparişe bağlanınca da korunur`() = runBlocking {
        val nav = nav()
        assertNull("açılış: hedef yok", nav.routeStatus.value!!.target)
        nav.setOrder(dayActive = true, currentOrder = 1) // sipariş yalnız Rawstats akışından (feed v2'de açık yolculuk) açılır
        nav.onExternalText("Restoran, Bakı", "intent", origin = NavOrigin.INTENT, referrer = "com.wolt.courierapp", requestedAtMs = T0)
        val stop = nav.state.value.stops.single()
        assertEquals(
            listOf(StopPurpose.ORDER, NavOrigin.INTENT, T0, "com.wolt.courierapp"),
            listOf(stop.purpose, stop.origin, stop.requestedAtMs, stop.sourcePackage),
        )
        val target = nav.routeStatus.value!!.target!!
        assertEquals(listOf(NavKind.RESTAURANT, NavOrigin.INTENT, T0, "com.wolt.courierapp"), listOf(target.kind, target.origin, target.requestedAtMs, target.sourcePackage))
        assertNull("harita yok: rota yok", nav.routeStatus.value!!.route)

        // Haritadan seçilen nokta `map`.
        nav.goTo(GeoPoint(40.41, 49.81), restaurant = false)
        assertEquals(NavOrigin.MAP, nav.routeStatus.value!!.target!!.origin)
        nav.clearRoute()
        assertNull(nav.routeStatus.value!!.target)
    }

    @Test
    fun `pano ve arama kaynakları`() = runBlocking {
        val nav = nav()
        nav.onExternalText("Restoran, Bakı", "clipboard")
        assertEquals(NavOrigin.CLIPBOARD, nav.state.value.stops.single().origin)
        nav.clearRoute()
        nav.search("Restoran, Bakı")
        assertEquals(NavOrigin.SEARCH, nav.state.value.stops.single().origin)
        assertEquals(NavOrigin.SEARCH, nav.routeStatus.value!!.target!!.origin)
    }
}
