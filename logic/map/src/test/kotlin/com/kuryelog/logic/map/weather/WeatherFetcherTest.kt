package com.kuryelog.logic.map.weather

import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Open-Meteo çekimi, önbellek, gün kapısı ve anahtar değişimi (`WeatherData.fetch`; MAP.md 10.7). */
class WeatherFetcherTest {
    private class MemoryStore : ReportStore {
        var raw: String? = null
        override fun read() = raw
        override fun write(raw: String) { this.raw = raw }
        override fun clear() { raw = null }
    }

    private fun body(hours: Int) = JSONObject().put("utc_offset_seconds", 14400).put(
        "hourly",
        JSONObject().put("time", JSONArray((0 until hours).map { 1_790_000_000L + it * 3600 })).put("temperature_2m", JSONArray((0 until hours).map { 10 + it })),
    ).toString()

    private var now = 1_790_000_000_000L
    private var dayOpen = true
    private val store = MemoryStore()
    private val prefs = WeatherPrefs(MemoryMapPrefs())
    private var response = HttpResponse(200, body(10))
    private val http = FakeHttp { response }
    private val fetcher = WeatherFetcher(http, prefs, store, "WEEM/test", { now }) { dayOpen }

    @Test
    fun ilk_çekim_raporu_yazar_ve_konumu_ekler() = runBlocking {
        prefs.setLocation(40.41, 49.86, 123)
        val raw = fetcher.fetch(false, "g1")
        val j = JSONObject(raw)
        assertEquals(now, j.getLong("fetchedAt"))
        assertEquals(40.41, j.getDouble("latitude"), 0.0)
        assertEquals(123L, j.getLong("locationAt"))
        assertEquals(raw, store.raw)
        assertEquals(now, prefs.fetched)
        assertTrue(http.requests.single().url.contains("latitude=40.41&longitude=49.86"))
        assertEquals("WEEM/test", http.requests.single().headers["User-Agent"])
    }

    @Test
    fun yirmi_dakikalık_önbellek_ağa_gitmez_force_gider() = runBlocking {
        fetcher.fetch(false, null)
        now += 19 * 60_000
        fetcher.fetch(false, null)
        assertEquals(1, http.requests.size)
        fetcher.fetch(true, null)
        assertEquals(2, http.requests.size)
        now += 21 * 60_000
        fetcher.fetch(false, null)
        assertEquals(3, http.requests.size)
    }

    @Test
    fun gün_kapalıyken_gün_kimlikli_istek_reddedilir_kimliksiz_çalışır() = runBlocking {
        dayOpen = false
        try {
            fetcher.fetch(false, "g1")
            fail("day closed bekleniyordu")
        } catch (e: IllegalStateException) {
            assertEquals("day closed", e.message)
        }
        assertTrue(http.requests.isEmpty())
        assertNotNull(fetcher.fetch(false, null))
    }

    @Test
    fun indirme_sürerken_gün_kapanırsa_kaydedilmez() = runBlocking {
        val closing = WeatherFetcher(FakeHttp { dayOpen = false; response }, prefs, store, "ua", { now }) { dayOpen }
        try {
            closing.fetch(false, "g1")
            fail("day closed bekleniyordu")
        } catch (_: IllegalStateException) {
        }
        assertNull(store.raw)
        assertEquals(0L, prefs.fetched)
    }

    @Test
    fun hatalı_cevap_eski_doğru_tahmini_silmez() = runBlocking {
        val good = fetcher.fetch(false, null)
        now += 30 * 60_000
        response = HttpResponse(500)
        try { fetcher.fetch(false, null); fail() } catch (_: IllegalStateException) {}
        response = HttpResponse(200, body(4)) // 5'ten az saat
        try { fetcher.fetch(false, null); fail() } catch (_: IllegalStateException) {}
        assertEquals(good, store.raw)
    }

    @Test
    fun anahtar_varsa_müşteri_konağı_anahtar_değişince_önbellek_geçersiz() = runBlocking {
        prefs.setKey("secret")
        fetcher.fetch(false, null)
        assertTrue(http.requests.last().url.startsWith("https://customer-api.open-meteo.com/"))
        assertFalse(prefs.setKey("secret"))
        assertTrue(prefs.setKey("other"))
        assertEquals("anahtar değişince çekim zamanı sıfır", 0L, prefs.fetched)
    }
}
