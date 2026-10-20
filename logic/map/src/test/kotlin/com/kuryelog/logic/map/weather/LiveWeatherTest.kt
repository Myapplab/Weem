package com.kuryelog.logic.map.weather

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `LiveWeather` önbellek ve ağ kuralları (`weather.dart:104–128`, MAP.md 10.7). */
class LiveWeatherTest {
    private val now = 1_790_000_000_000L
    private val floor = now / 3_600_000 * 3_600_000

    private fun raw(fetchedAt: Long, hours: Int) = JSONObject().put("fetchedAt", fetchedAt).put(
        "forecast",
        JSONObject().put("utc_offset_seconds", 14400).put(
            "hourly",
            JSONObject().put("time", JSONArray((0 until hours).map { floor / 1000 + it * 3600L })).put("temperature_2m", JSONArray((0 until hours).map { 10 + it })),
        ),
    ).toString()

    private class Fake(var cached: String?, var network: String? = null) : WeatherSource {
        val fetches = mutableListOf<Pair<Boolean, String?>>()
        override suspend fun cached() = cached
        override suspend fun fetch(force: Boolean, dayId: String?): String {
            fetches += force to dayId
            return network ?: error("ağ yok")
        }
    }

    @Test
    fun taze_ve_sekiz_saatlik_önbellek_ağa_gitmez() = runBlocking {
        val src = Fake(raw(now - 5 * 60_000, 8), network = raw(now, 8))
        val w = LiveWeather(src) { now }
        w.refresh(dayId = "g1")
        assertTrue(src.fetches.isEmpty())
        assertEquals(8, w.state.value.report!!.hours.size)
        assertFalse(w.state.value.busy)
    }

    @Test
    fun eski_önbellek_ağdan_tazelenir_ve_gün_kimliği_gider() = runBlocking {
        val src = Fake(raw(now - 30 * 60_000, 8), network = raw(now, 8))
        val w = LiveWeather(src) { now }
        w.refresh(dayId = "g1")
        assertEquals(listOf(false to "g1"), src.fetches)
    }

    @Test
    fun gün_kapalıyken_yalnız_önbellek_okunur_kısa_önbellek_süreç_başına_bir_kez_tazelenir() = runBlocking {
        val src = Fake(raw(now - 5 * 60_000, 5), network = raw(now, 8))
        val w = LiveWeather(src) { now }
        w.refresh(cacheOnly = true)
        assertEquals(listOf<Pair<Boolean, String?>>(false to null), src.fetches)
        assertEquals(8, w.state.value.report!!.hours.size)
        src.cached = raw(now - 5 * 60_000, 5)
        w.refresh(cacheOnly = true)
        assertEquals("ikinci kez ağ yok", 1, src.fetches.size)
        assertEquals(5, w.state.value.report!!.hours.size)
    }

    @Test
    fun önbellek_yoksa_gün_kapalıyken_de_bir_kez_ağdan_alınır_ağ_yoksa_hata() = runBlocking {
        val ok = LiveWeather(Fake(null, network = raw(now, 8))) { now }
        ok.refresh(cacheOnly = true)
        assertNotNull(ok.state.value.report)
        val down = LiveWeather(Fake(null)) { now }
        down.refresh(cacheOnly = true)
        assertNull(down.state.value.report)
        assertTrue(down.state.value.failed)
    }

    @Test
    fun ağ_hatasında_önceki_rapor_kalır_zorla_tazelemede_ağa_gidilir() = runBlocking {
        val src = Fake(raw(now - 5 * 60_000, 8), network = raw(now, 8))
        val w = LiveWeather(src) { now }
        w.refresh()
        assertNotNull(w.state.value.report)
        src.network = null
        w.refresh(force = true)
        assertEquals(listOf(true to null), src.fetches)
        assertTrue(w.state.value.failed)
        assertNotNull("önceki rapor kalır", w.state.value.report)
    }

    @Test
    fun gün_kapanınca_rapor_kalır_hata_temizlenir() = runBlocking {
        val src = Fake(raw(now, 8))
        val w = LiveWeather(src) { now }
        w.refresh()
        w.scopeChanged(false)
        assertNotNull(w.state.value.report)
        assertFalse(w.state.value.failed)
    }
}
