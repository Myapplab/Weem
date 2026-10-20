package com.kuryelog.logic.map.weather

import com.kuryelog.logic.map.MemoryMapPrefs
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Günde bir hava bildirimi (`WeatherWorker.doWork`; MAP.md 10.8, `NotifyPolicy.weatherDue`). */
class WeatherNotifierTest {
    private val nowSec = 1_790_000_000L - 1_790_000_000L % 3600
    private val now = nowSec * 1000 + 600_000

    private fun raw() = JSONObject().put("locationAt", 1).put(
        "forecast",
        JSONObject().put("timezone", "Asia/Baku").put(
            "hourly",
            JSONObject().put("time", JSONArray((1..10).map { nowSec + it * 3600L }))
                .put("temperature_2m", JSONArray((1..10).map { 10 + it })),
        ),
    ).toString()

    private class Source(var raw: String?) : WeatherSource {
        var fetches = 0
        override suspend fun cached() = raw
        override suspend fun fetch(force: Boolean, dayId: String?): String { fetches++; return raw ?: error("ağ yok") }
    }

    private val prefs = WeatherPrefs(MemoryMapPrefs())
    private val posted = mutableListOf<Triple<String, String, String>>()
    private var dayOpen = true
    private var allowed = true
    private val source = Source(raw())
    private val notifier = WeatherNotifier(source, prefs, { dayOpen }, { allowed }) { t, s, b -> posted += Triple(t, s, b) }

    @Test
    fun ilk_çalışmada_bildirir_ve_aynı_gün_ikincide_susar() = runBlocking {
        assertEquals(WeatherNotifier.Result.POSTED, notifier.run("g1", az = false, nowMs = now))
        val (title, short, big) = posted.single()
        assertEquals("Önümüzdeki 4 saatin havası", title)
        assertTrue(short.contains("11°C"))
        assertTrue(big.startsWith("Son bilinen konum · "))
        assertEquals("g1", prefs.notifiedDay)
        assertEquals(WeatherNotifier.Result.SKIPPED, notifier.run("g1", az = false, nowMs = now + 7_200_000))
        assertEquals(1, posted.size)
        assertEquals("yeni gün yeniden", WeatherNotifier.Result.POSTED, notifier.run("g2", az = true, nowMs = now + 7_200_000))
        assertEquals("Növbəti 4 saatın havası", posted.last().first)
    }

    @Test
    fun hava_kapalı_gün_kapalı_ya_da_izin_yokken_hiçbir_şey_yapmaz() = runBlocking {
        prefs.enabled = false
        assertEquals(WeatherNotifier.Result.SKIPPED, notifier.run("g1", false, now))
        prefs.enabled = true
        dayOpen = false
        assertEquals(WeatherNotifier.Result.SKIPPED, notifier.run("g1", false, now))
        dayOpen = true
        allowed = false
        assertEquals(WeatherNotifier.Result.SKIPPED, notifier.run("g1", false, now))
        assertEquals(0, source.fetches)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun hatada_gün_açıkken_FAILED_kapandıysa_SKIPPED() = runBlocking {
        source.raw = null
        assertEquals(WeatherNotifier.Result.FAILED, notifier.run("g1", false, now))
        assertTrue(posted.isEmpty())
        assertEquals("", prefs.notifiedDay ?: "")
        source.raw = JSONObject().put("forecast", JSONObject()).toString() // 4 saat yok
        dayOpen = true
        assertEquals(WeatherNotifier.Result.FAILED, notifier.run("g1", false, now))
    }
}
