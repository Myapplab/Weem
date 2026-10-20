package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavOrigin
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hedefin kabulü (MAP.md 8.7; A6.6 yeni karakterizasyon): `NavAssistantTest`'in navigasyon kısmı gün motorundan (A6.8) bağımsız — konuma <60 m düşen
 * restoran olmayan dış hedef rota çizmez (hata metninde gelen metin), restoran hedefi kabul edilir; durak varken gelen adres bekleyen karttır.
 */
class NavControllerTargetTest {
    private val here = GeoPoint(40.4, 49.8)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()

    private class At(private val p: GeoPoint, private val restaurant: Boolean?) : Geocoder {
        override suspend fun search(query: String, near: GeoPoint?) = GeocodeHit(p, "Yer, Bakı", GeoConfidence.HIGH, "test", restaurant)
    }

    private fun nav(p: GeoPoint, restaurant: Boolean?): NavController {
        val clock = FakeClock()
        val nav = NavController(engine, scope, clock, DestinationResolver(FakeHttp { HttpResponse(500, "{}") }, At(p, restaurant)))
        nav.onFix(NavFix(clock.nowMs, here.lat, here.lon, 5.0, speedMps = 4.0, headingDeg = 90.0))
        return nav
    }

    @After
    fun close() {
        engine.dispose()
        scope.cancel()
    }

    @Test
    fun `yakına düşen müşteri adresi rota çizmez, restoran kabul edilir`() = runBlocking {
        val customer = nav(here, restaurant = false)
        customer.onExternalText("Nizami küçəsi 5", "intent")
        assertTrue(customer.state.value.stops.isEmpty())
        val error = customer.state.value.error!!
        assertTrue(error, error.startsWith("Adres bulunduğun yere çıktı (0 m)"))
        assertTrue(error, error.contains("«Nizami küçəsi 5»"))

        val restaurant = nav(here, restaurant = true)
        restaurant.onExternalText("Nizami küçəsi 5", "intent")
        assertEquals(1, restaurant.state.value.stops.size)
    }

    @Test
    fun `durak varken gelen adres bekleyen karttır, durak ekle ya da yeni rota`() = runBlocking {
        val far = GeoPoint(40.42, 49.83)
        val nav = nav(far, restaurant = false)
        nav.goTo(GeoPoint(40.41, 49.81), label = "İlk")
        nav.onExternalText("Nizami küçəsi 5", "clipboard")
        val pending = nav.state.value.pending
        assertNotNull(pending)
        assertEquals(NavOrigin.CLIPBOARD, pending!!.origin)
        assertEquals(1, nav.state.value.stops.size)
        nav.acceptPending(addStop = true)
        assertEquals(listOf("İlk", "Yer, Bakı"), nav.state.value.stops.map { it.label })
        assertNull(nav.state.value.pending)
        nav.onExternalText("geo:40.43,49.84", "intent")
        nav.acceptPending(addStop = false)
        assertEquals(1, nav.state.value.stops.size)
        assertEquals(40.43, nav.state.value.stops.single().point.lat, 1e-9)
    }

    @Test
    fun `konumsuz pano metni sessiz, bağlantı uyarı verir`() = runBlocking {
        val nav = nav(here, restaurant = false)
        nav.onExternalText("Salam, 5 dəqiqəyə gəlirəm", "clipboard")
        assertNull(nav.toast.value)
        nav.onExternalText("https://instagram.com/xyz", "intent")
        assertEquals("Bu linkte konum bulunamadı (https)", nav.toast.value!!.text)
        assertEquals("a".repeat(80) + "…", NavController.snippet("a".repeat(81)))
    }
}
