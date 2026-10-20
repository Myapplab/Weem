package com.kuryelog.logic.map.ride

import com.kuryelog.logic.map.route.RideParams
import com.kuryelog.logic.map.route.RoutePoint
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.settings.MapSettingsCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * YolGo `v150_features_test.dart` "hız seçenekleri ve varış saati" (3) + `v140_features_test.dart` Y9 hız seçenekleri (1; A6.2b, MAP.md 10.2, 10.12):
 * hızlı = kısa süre ama daha çok Wh; ayar hızında düz yol modeli; varış saati şimdi + süre; hız listesi `settings.v1`'de saklanır.
 */
class SpeedOptionsTest {
    private val flat5km = (0..50).map { RoutePoint(40.40 + it * 0.0009, 49.85, 0.0) } // ≈5 km düz

    @Test
    fun `ayarlanan hızlar kullanılır, hız artınca süre kısalır, enerji artar`() {
        val o = SpeedOptions.speedOptions(flat5km, RideParams(), listOf(15.0, 22.0, 30.0))
        assertEquals(listOf(15.0, 22.0, 30.0), o.map { it.kmh })
        assertTrue(o[0].seconds > o[2].seconds)
        assertTrue(o[0].wh < o[2].wh)
        assertEquals(5000 / (22 / 3.6), o[1].seconds, 60.0)
    }

    @Test
    fun `varış saati şimdi artı süre`() {
        assertEquals("07:45", SpeedOptions.arrivalClock(LocalDateTime.of(2026, 10, 8, 7, 32), 13 * 60.0))
        assertEquals("00:05", SpeedOptions.arrivalClock(LocalDateTime.of(2026, 10, 8, 23, 55), 10 * 60.0))
    }

    @Test
    fun `hız listesi kaydedilir, bozuk kayıt varsayılana döner`() {
        val s = MapSettings().copy(speedChoices = listOf(18.0, 24.0, 31.0))
        val j = JSONObject(MapSettingsCodec.toJson(s).toString())
        assertEquals(listOf(18.0, 24.0, 31.0), MapSettingsCodec.decode(j.toString()).speedChoices)
        assertEquals(MapSettings.DEFAULT_SPEED_CHOICES, MapSettingsCodec.decode(JSONObject(j.toString()).put("speedChoices", org.json.JSONArray(listOf(1, 2))).toString()).speedChoices)
        assertEquals(MapSettings.DEFAULT_SPEED_CHOICES, MapSettingsCodec.decode(JSONObject(j.toString()).put("speedChoices", "x").toString()).speedChoices)
        assertEquals(listOf(20.0, 25.0, 29.0), MapSettingsCodec.decode("{}").speedChoices)
    }

    @Test
    fun `hız seçenekleri hızlı kısa süre ama daha çok Wh, ayar hızında düz yol modeli`() {
        val p = RideParams()
        fun lat(n: Double) = 40.40 + n / 111320
        val ride = (0..1000 step 50).map { RoutePoint(lat(it.toDouble()), 49.85, 0.0) }
        val opts = SpeedOptions.speedOptions(ride, p)
        assertEquals(listOf(20.0, 25.0, 29.0), opts.map { it.kmh })
        assertTrue(opts.first().seconds > opts.last().seconds)
        assertTrue(opts.first().wh < opts.last().wh)
        val ref = SpeedOptions.speedOption(ride, p, p.flatKmh)
        assertEquals(p.flatWhPerM * 1000, ref.wh, 0.5)
        assertEquals(1000 / p.flatMps, ref.seconds, 2.0)
        val hill = (0..1000 step 50).map { RoutePoint(lat(it.toDouble()), 49.85, it * 0.08) }
        assertTrue("yokuşta yavaşlar", SpeedOptions.speedOption(hill, p, 25.0).seconds > SpeedOptions.speedOption(ride, p, 25.0).seconds)
    }
}
