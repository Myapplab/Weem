package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import com.kuryelog.logic.map.net.VirtualDelay
import com.kuryelog.logic.map.net.waitUntil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * YolGo `place_search_test.dart` arama testleri (6; Places hata/ayrıştırma 2 test `PlacesRestTest`'te; A6.2b, MAP.md 8.8, 8.11).
 * Sahte ağ ([FakeHttp]), sanal saat (debounce 350 ms), sahte konum, boş graf, POI listesi; `ProviderContainer` yerine saf [PlaceSearch].
 * Dart `SocketException`/`StateError`/`TimeoutException` → `IOException`/`IllegalStateException`/`SocketTimeoutException`.
 */
class PlaceSearchTest {
    private val creds = GoogleCredentials("AIzaTEST", "com.kuryelog.app", "AB12")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = VirtualDelay()
    private val prefs = MemoryMapPrefs()

    @After
    fun close() = scope.cancel()

    private fun search(
        http: FakeHttp,
        pois: List<Poi> = emptyList(),
        credentials: GoogleCredentials? = creds,
        lang: Lang = Lang.TR,
    ) = PlaceSearch(scope, http, { credentials }, { pois }, { null }, { lang }, { null }, prefs, clock::delay)

    /** Meşgul bitene dek bekler (Dart `_settle`). */
    private fun PlaceSearch.settle(): SearchView {
        waitUntil { !view.value.busy }
        return view.value
    }

    private fun json(body: String, status: Int = 200) = HttpResponse(status, body)

    private val venue = Poi(40.4, 49.8, 'r', "Test Venue")

    @Test
    fun `çevrimdışı dizin gerçek noktayı ve mekân türünü korur`() {
        val index = OfflineIndex.build(null, listOf(Poi(40.4, 49.8, 'r', "Restoran"), Poi(40.41, 49.81, 'm', "AVM")), Lang.TR)
        assertEquals(40.4, index.first().point!!.lat, 0.0)
        assertTrue(index.first().restaurant)
        assertFalse(index.last().restaurant)
    }

    @Test
    fun `kullanılamayan Places API çökmeden çevrimdışına düşer ve meşgul kalmaz`() {
        val s = search(FakeHttp { json("{}", 500) }, listOf(venue))
        s.query("Test")
        clock.advance(PlaceSearch.DEBOUNCE_MS)
        val view = s.settle()
        assertFalse(view.busy)
        assertTrue(view.offline)
        assertNotNull(view.error)
        assertEquals("offline", view.items.single().source)
        assertEquals(40.4, view.items.single().point!!.lat, 0.0)
    }

    @Test
    fun `Regresyon arama sırasında atılan her hata (Error dahil) uygulamayı kapatmaz, çevrimdışına düşer`() {
        // 1.3.0'da yerel Places SDK ServiceConfigurationError (bir Error) atıyordu; yakalanmayınca uygulama kapanıyordu.
        for (failure in listOf<Throwable>(IOException("down"), IllegalStateException("sdk"), SocketTimeoutException("slow"), java.util.ServiceConfigurationError("sdk"))) {
            val s = search(FakeHttp { throw failure }, listOf(venue))
            s.query("Test")
            clock.advance(PlaceSearch.DEBOUNCE_MS)
            val view = s.settle()
            assertFalse("$failure", view.busy)
            assertTrue(view.offline)
            assertEquals("offline", view.items.single().source)
        }
    }

    @Test
    fun `Azərbaycanca seçiliyken arama hata ve çevrimdışı etiketleri AZ gösterilir`() {
        // Regresyon: bu mesajlar ve izole iş parçacığında kurulan etiketler sabit Türkçeydi.
        val s = search(FakeHttp { throw IOException("offline") }, listOf(venue), lang = Lang.AZ)
        s.query("Test")
        clock.advance(PlaceSearch.DEBOUNCE_MS)
        val view = s.settle()
        assertEquals("İnternet yoxdur · oflayn nəticələr", view.error)
        assertEquals("Bakı · qeydə alınmış obyekt", view.items.single().address)
    }

    @Test
    fun `anahtar yoksa Google çağrılmaz, çevrimdışı sonuç ve neden gösterilir`() {
        val http = FakeHttp { json("{}") }
        val s = search(http, listOf(venue), credentials = null)
        s.query("Test")
        clock.advance(PlaceSearch.DEBOUNCE_MS)
        val view = s.settle()
        assertEquals(0, http.requests.size)
        assertTrue(view.offline)
        assertTrue(view.error!!.contains("anahtarı yok"))
    }

    @Test
    fun `debounce eski öneri oturum belirteci gerçek ayrıntı ve son arama`() = runBlocking {
        val old = CompletableDeferred<HttpResponse>()
        val http = FakeHttp { r ->
            when (java.net.URI(r.url).path) {
                "/v1/places:autocomplete" -> {
                    if (JSONObject(r.body!!).getString("input") == "Old") old.await()
                    else json("""{"suggestions":[{"placePrediction":{"placeId":"new","text":{"text":"New venue, Baku"},
                        "structuredFormat":{"mainText":{"text":"New venue"},"secondaryText":{"text":"Baku"}}}}]}""")
                }
                "/v1/places/new" -> json("""{"id":"new","location":{"latitude":40.42,"longitude":49.83},"formattedAddress":"Actual address","types":["store"]}""")
                else -> json("{}", 404)
            }
        }
        val s = search(http)
        s.query("O"); s.query("Ol"); s.query("Old")
        clock.advance(PlaceSearch.DEBOUNCE_MS)
        waitUntil { http.to("/v1/places:autocomplete").size == 1 }
        assertEquals("3 yazımda 1 istek", 1, http.to("/v1/places:autocomplete").size)
        s.query("New")
        clock.advance(PlaceSearch.DEBOUNCE_MS)
        waitUntil { http.to("/v1/places:autocomplete").size == 2 }
        old.complete(json("""{"suggestions":[{"placePrediction":{"placeId":"old","text":{"text":"Stale"}}}]}"""))
        val view = s.settle()
        assertEquals("new", view.items.single().id)
        assertEquals("Baku", view.items.single().address)
        val selected = s.select(view.items.single())!!
        assertEquals(40.42, selected.point!!.lat, 0.0)
        assertFalse(selected.restaurant)
        assertEquals("Actual address", selected.address)
        val autocomplete = http.to("/v1/places:autocomplete").first()
        val details = http.to("/v1/places/new").single()
        val session = JSONObject(autocomplete.body!!).getString("sessionToken")
        assertTrue(details.url.contains("sessionToken=$session"))
        assertTrue(session.length <= 36)
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(session))
        // Android kısıtlamalı anahtar için paket ve imza başlıkları gider.
        for (r in listOf(autocomplete, details)) {
            assertEquals("AIzaTEST", r.headers["X-Goog-Api-Key"])
            assertEquals("com.kuryelog.app", r.headers["X-Android-Package"])
            assertEquals("AB12", r.headers["X-Android-Cert"])
        }
        assertTrue(details.headers["X-Goog-FieldMask"]!!.contains("location"))
        assertFalse("son aramada gerçek adres yok", prefs.getString(PlaceSearch.RECENTS_KEY)!!.contains("Actual address"))
        s.query("")
        assertEquals("New", s.view.value.items.single().title)
        assertNull(s.view.value.items.single().point)
    }
}
