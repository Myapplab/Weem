package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** YolGo `geocoder_scope_test.dart` (1; A6.2b, MAP.md 8.11): Google geokodda ülke filtresi (`components`) yok; Tbilisi `ROOFTOP` → high. */
class GeocoderTest {
    @Test
    fun `açık adres Bakü dışında sert ülke filtresi olmadan çözülür`() = runBlocking {
        val http = FakeHttp { req ->
            assertFalse("components yok", req.url.contains("components="))
            HttpResponse(200, """{"status":"OK","results":[{"types":["street_address"],"formatted_address":"Tbilisi address",
                "geometry":{"location":{"lat":41.7,"lng":44.8},"location_type":"ROOFTOP"}}]}""")
        }
        val hit = GoogleGeocoder(http, "test-key").search("Tbilisi address")
        assertEquals(41.7, hit!!.point.lat, 0.0)
        assertEquals(44.8, hit.point.lon, 0.0)
        assertEquals(GeoConfidence.HIGH, hit.confidence)
    }
}
