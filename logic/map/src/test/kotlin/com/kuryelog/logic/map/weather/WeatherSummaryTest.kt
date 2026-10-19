package com.kuryelog.logic.map.weather

import com.kuryelog.logic.core.WeatherNow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rawstats'tan gelen YK `WeatherFeedTest` (2; A6.2b, MAP.md 10.10, 10.12): özet yalnız şimdiki saat havasını taşır, konumu asla, eksik değer null.
 * Kaynak etiketi WEEM'de `WeatherNow.SOURCE` (kayıt dizgisi `yolgo_open_meteo` `OfferWeather` testinde, A3.6).
 */
class WeatherSummaryTest {
    private val t0 = 1_760_000_400L - 1_760_000_400L % 3600

    private fun report(current: JSONObject?) = JSONObject().put("fetchedAt", (t0 + 1200) * 1000).put("latitude", 40.41).put("longitude", 49.86)
        .put(
            "forecast",
            JSONObject().put("current", current ?: JSONObject.NULL).put(
                "hourly",
                JSONObject().put("time", JSONArray(listOf(t0 - 3600, t0, t0 + 3600)))
                    .put("temperature_2m", JSONArray(listOf(10, 12, 14))).put("precipitation_probability", JSONArray(listOf(5, 40, 90)))
                    .put("wind_gusts_10m", JSONArray(listOf(20, 33.5, 50))).put("precipitation", JSONArray(listOf(0, 0.4, 2))),
            ),
        ).toString()

    @Test
    fun `current kazanır, saatlik dilim tamamlar`() {
        val s = WeatherSummary.summary(report(JSONObject().put("temperature_2m", 12.7).put("wind_speed_10m", 18).put("weather_code", 61)))!!
        assertEquals(12.7, s.tempC!!, 1e-9)
        assertEquals(40.0, s.rainPct!!, 1e-9)
        assertEquals(33.5, s.gustKmh!!, 1e-9)
        assertEquals(0.4, s.rainMm!!, 1e-9)
        assertEquals(61, s.code)
        assertEquals(WeatherNow.SOURCE, s.source)
        assertEquals((t0 + 1200) * 1000, s.fetchedAtMs)
        assertNull(s.feelsC)
        val json = s.toJson().toString()
        assertFalse(json.contains("latitude") || json.contains("lat\"") || json.contains("40.41"))
    }

    @Test
    fun `current yokken saat dilimi kullanılır, bozuk girdi null`() {
        val s = WeatherSummary.summary(report(null))!!
        assertEquals(12.0, s.tempC!!, 1e-9)
        assertNull(WeatherSummary.summary(null))
        assertNull(WeatherSummary.summary("{bad"))
        assertNull(WeatherSummary.summary("{}"))
    }
}
