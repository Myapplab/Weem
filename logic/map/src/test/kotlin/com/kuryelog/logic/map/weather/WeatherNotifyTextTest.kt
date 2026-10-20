package com.kuryelog.logic.map.weather

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Hava bildirimi metni (`WeatherData.text`; MAP.md 10.8): 4 saat satırı, yer başlığı, değer yoksa "—", tam 4 saat yoksa hata. */
class WeatherNotifyTextTest {
    private val now = 1_760_000_000L - 1_760_000_000L % 3600 + 600 // sn, saat başı + 10 dk
    private fun raw(count: Int, locationAt: Long, tz: String? = "Asia/Baku") = JSONObject().put("locationAt", locationAt).put(
        "forecast",
        JSONObject().apply { if (tz != null) put("timezone", tz) }.put(
            "hourly",
            JSONObject().put("time", JSONArray((1..count).map { now - 600 + it * 3600L }))
                .put("temperature_2m", JSONArray((1..count).map { 10.0 + it }))
                .put("apparent_temperature", JSONArray((1..count).map { 9.0 + it }))
                .put("precipitation_probability", JSONArray((1..count).map { if (it == 2) JSONObject.NULL else 10 * it }))
                .put("precipitation", JSONArray((1..count).map { 0.25 * it }))
                .put("wind_speed_10m", JSONArray((1..count).map { 5.0 * it }))
                .put("wind_gusts_10m", JSONArray((1..count).map { 8.0 * it })),
        ),
    ).toString()

    @Test
    fun dört_satır_TR() {
        val lines = WeatherNotifyText.text(raw(5, 1), az = false, nowMs = now * 1000).lines()
        assertEquals(6, lines.size)
        assertTrue(lines[0].startsWith("Son bilinen konum · "))
        assertEquals("11°C (hissedilen 10°) · yağış %10 · 0.3 mm · rüzgâr 5 km/h (hamle 8)", lines[1].substringAfter(" · "))
        assertTrue(lines[2].contains("yağış %— · "))
        assertEquals("Open-Meteo", lines[5])
    }

    @Test
    fun AZ_ve_konum_yoksa_Bakı() {
        val lines = WeatherNotifyText.text(raw(4, 0), az = true, nowMs = now * 1000).lines()
        assertTrue(lines[0].startsWith("Bakı · "))
        assertTrue(lines[1].contains("(hiss 10°) · yağıntı %10 · 0.3 mm · külək 5 km/h (ani 8)"))
    }

    @Test
    fun dört_saat_yoksa_hata() {
        try {
            WeatherNotifyText.text(raw(3, 1), az = false, nowMs = now * 1000)
            fail("hata bekleniyordu")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun başlık_ve_kanal_adı_iki_dilli() {
        assertEquals("Önümüzdeki 4 saatin havası", WeatherNotifyText.title(false))
        assertEquals("Növbəti 4 saatın havası", WeatherNotifyText.title(true))
        assertEquals("Hava tahmini", WeatherNotifyText.channelName(false))
        assertEquals("Hava proqnozu", WeatherNotifyText.channelName(true))
    }
}
