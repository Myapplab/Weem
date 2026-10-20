package com.kuryelog.logic.map.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Open-Meteo isteği ve önbellek/konum kuralları (`WeatherWorker.kt:45–76`, `weather.dart:162–172`; MAP.md 10.7). */
class WeatherRequestTest {
    @Test
    fun anahtarsız_istek_herkese_açık_konağa_gider_ve_Bakü_varsayılan_konumdur() {
        val url = WeatherRequest.url(null, null, "")
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&hourly=temperature_2m,"))
        assertTrue(url.contains("&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code,is_day&forecast_hours=10&past_hours=1&timeformat=unixtime&timezone=auto&wind_speed_unit=kmh"))
        assertFalse(url.contains("apikey"))
    }

    @Test
    fun anahtarlı_istek_müşteri_konağına_gider_ve_anahtar_URL_kodlanır() {
        val url = WeatherRequest.url("40.41", "49.86", "a b&c")
        assertTrue(url.startsWith("https://customer-api.open-meteo.com/v1/forecast?latitude=40.41&longitude=49.86&"))
        assertTrue(url.endsWith("&apikey=a+b%26c"))
    }

    @Test
    fun önbellek_yirmi_dakika_geçerli_saat_geri_giderse_değil() {
        assertTrue(WeatherRequest.cacheFresh(1_200_000, 0))
        assertFalse(WeatherRequest.cacheFresh(1_200_001, 0))
        assertFalse(WeatherRequest.cacheFresh(0, 1))
    }

    @Test
    fun konum_güncellemesi_yalnız_geçerli_koordinatı_yazar() {
        assertTrue(WeatherRequest.validLocation(40.4, 49.8))
        assertFalse(WeatherRequest.validLocation(91.0, 49.8))
        assertFalse(WeatherRequest.validLocation(40.4, Double.NaN))
        assertEquals(12_000, WeatherRequest.TIMEOUT_MS)
    }
}
