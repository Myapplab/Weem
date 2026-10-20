package com.kuryelog.logic.map.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Hava simgesi sırası, açıklama, yuvarlağın üçüncü satırı ve yazı rengi (`weather_widget.dart:13–33`, `weatherCompactExtra`; MAP.md 10.9). */
class WeatherIconsTest {
    private fun hour(
        code: Int? = null, wind: Double? = null, gust: Double? = null, rainMm: Double? = null, isDay: Boolean? = true, rainChance: Double? = null,
    ) = WeatherHour(LocalDateTime.of(2026, 10, 2, 12, 0), 20.0, wind = wind, gust = gust, rainMm = rainMm, code = code, isDay = isDay, rainChance = rainChance)

    @Test
    fun rüzgâr_koddan_önce_gelir() {
        assertEquals(WeatherKind.WIND, WeatherIcons.kind(hour(code = 95, wind = 30.0)))
        assertEquals(WeatherKind.WIND, WeatherIcons.kind(hour(code = 0, gust = 45.0)))
        assertEquals(WeatherKind.SUN, WeatherIcons.kind(hour(code = 0, wind = 29.9, gust = 44.9)))
    }

    @Test
    fun kod_sırası_ve_gece()  {
        assertEquals(WeatherKind.THUNDER, WeatherIcons.kind(hour(code = 96)))
        for (c in listOf(71, 75, 77, 85, 86)) assertEquals("kod $c", WeatherKind.SNOW, WeatherIcons.kind(hour(code = c)))
        assertEquals(WeatherKind.RAIN, WeatherIcons.kind(hour(code = 61)))
        assertEquals(WeatherKind.RAIN, WeatherIcons.kind(hour(code = 78))) // 78 kar aralığında değil, ≥ 51
        assertEquals(WeatherKind.FOG, WeatherIcons.kind(hour(code = 45)))
        assertEquals(WeatherKind.FOG, WeatherIcons.kind(hour(code = 48)))
        assertEquals(WeatherKind.CLOUD, WeatherIcons.kind(hour(code = 3)))
        assertEquals(WeatherKind.NIGHT, WeatherIcons.kind(hour(code = 0, isDay = false)))
        assertEquals(WeatherKind.SUN, WeatherIcons.kind(hour(code = 1, isDay = null)))
    }

    @Test
    fun kod_yokken_yağışa_bakılır() {
        assertEquals(WeatherKind.RAIN, WeatherIcons.kind(hour(rainMm = 0.1)))
        assertEquals(WeatherKind.CLOUD, WeatherIcons.kind(hour(rainMm = 0.0)))
    }

    @Test
    fun açıklama_ve_yazı_rengi() {
        assertEquals("Güneşli", WeatherKind.SUN.tr)
        assertEquals("Günəşli", WeatherKind.SUN.az)
        assertEquals(0xFF17283A, WeatherKind.SUN.onColor)
        assertEquals(0xFFFFFFFF, WeatherKind.NIGHT.onColor)
        assertEquals(0xFFFFFFFF, WeatherKind.THUNDER.onColor)
    }

    @Test
    fun üçüncü_satır_yağış_ihtimali_yoksa_rüzgâr() {
        assertEquals(CompactExtra(true, "40%"), WeatherIcons.compactExtra(hour(rainChance = 40.0, wind = 12.0)))
        assertEquals(CompactExtra(false, "12"), WeatherIcons.compactExtra(hour(rainChance = 9.0, wind = 12.4)))
        assertNull(WeatherIcons.compactExtra(hour()))
    }
}
