package com.kuryelog.logic.map.weather

import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `weather.*` anahtarları ve varsayılanları (MAP.md 10.7, 10.11): şerit ve bildirim varsayılan açık, eski kayıtlı "kapalı" aynen okunur. */
class WeatherPrefsTest {
    @Test
    fun varsayılanlar() {
        val p = WeatherPrefs(MemoryMapPrefs())
        assertTrue(p.enabled)
        assertTrue(p.strip)
        assertEquals("", p.key)
        assertFalse(p.permissionAsked)
        assertNull(p.lat)
        assertEquals(0L, p.fetched)
    }

    @Test
    fun kayıtlı_kapalı_ayar_korunur_ve_anahtar_adları_aynı() {
        val mem = MemoryMapPrefs(mapOf("weather.strip.v1" to false, "weather.enabled.v1" to false, "weather.key.v1" to "k"))
        val p = WeatherPrefs(mem)
        assertFalse(p.strip)
        assertFalse(p.enabled)
        assertEquals("k", p.key)
        p.strip = true
        assertEquals(true, mem.getBool("weather.strip.v1"))
    }

    @Test
    fun konum_yalnız_geçerliyse_yazılır() {
        val p = WeatherPrefs(MemoryMapPrefs())
        assertFalse(p.setLocation(91.0, 10.0, 5))
        assertNull(p.lat)
        assertTrue(p.setLocation(40.41, 49.86, 5))
        assertEquals("40.41", p.lat)
        assertEquals("49.86", p.lon)
        assertEquals(5L, p.locationAt)
    }
}
