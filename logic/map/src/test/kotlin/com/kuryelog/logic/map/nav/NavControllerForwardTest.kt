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

    /** A10.2 (S5): kopyalanan metindeki ad, telefon ve not Receiver'a gitmez, geokod sorgusuna da girmez. Yapay veri. */
    @Test
    fun copiedTextSendsOnlyTheAddressToThePeerAndTheGeocoder() = runBlocking {
        val queries = ArrayList<String>()
        val geocoder = object : Geocoder {
            override suspend fun search(query: String, near: GeoPoint?): GeocodeHit {
                queries += query
                return GeocodeHit(GeoPoint(40.4091, 49.8673), "", GeoConfidence.HIGH, "test")
            }
        }
        val nav = NavController(engine, scope, FakeClock(T0), DestinationResolver(http, geocoder)).apply {
            setGraph(null)
            setOrigin(GeoPoint(LAT0, LON0))
        }
        val sent = ArrayList<Stop>()
        nav.forward = { sent += it; true }
        nav.onExternalText("Test Müştəri +994 50 123 45 67 Not: zəngi basmayın 9RQW+344 Baku\nqapıda gözləyin", "clipboard")
        nav.onExternalText("Test Müştəri\nNizami küç. 5\nqapıda gözləyin\n+994 50 123 45 67", "clipboard")
        assertEquals(listOf("9RQW+344 Baku", "Nizami küçəsi 5, Bakı"), sent.map { it.navTarget("r", T0).label })
        assertEquals(listOf("Nizami küçəsi 5, Bakı"), queries)
        // Etikette kalmış telefon (`geo:…(etiket)`) öteki telefona gizli gider.
        assertEquals("Restoran [tel]", Stop(GeoPoint(40.4, 49.8), "Restoran +994 50 123 45 67").navTarget("r", T0).label)
    }

    @Test
    fun unreachablePeerKeepsTheLocalRoute() = runBlocking {
        val nav = nav()
        nav.forward = { false }
        nav.onExternalText("Restoran, Bakı", "intent", origin = NavOrigin.INTENT)
        assertEquals(1, nav.state.value.stops.size)
    }

    /** A10.4e (N5): uyarılı teklif "eve dön?" kartını açar; açıkken ikinci çağrı durumu değiştirmez, kapatılınca kalkar. */
    @Test
    fun rangeWarningOpensTheHomeCard() {
        val nav = nav()
        assertFalse(nav.state.value.homeSuggest)
        nav.suggestHome()
        val st = nav.state.value
        assertTrue(st.homeSuggest)
        nav.suggestHome()
        assertTrue(st === nav.state.value)
        nav.dismissHomeSuggest()
        assertFalse(nav.state.value.homeSuggest)
    }
}
