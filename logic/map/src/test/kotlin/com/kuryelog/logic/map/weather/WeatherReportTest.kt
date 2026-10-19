package com.kuryelog.logic.map.weather

import com.kuryelog.logic.map.graph.FormatException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/**
 * YolGo `v160_y1_y2_test.dart` hava ayrıştırma (3), `v150_features_test.dart`/`yolgo_rules_test.dart` hava göstergesi (1), `assistant_test.dart` gece yarısı (1)
 * (A6.2b, MAP.md 10.7, 10.12, 16.3): şerit 4 saat, ayrıntı 8 saat; güncel API değeri ilk saatte kazanır.
 */
class WeatherReportTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = Instant.parse(String.format("%04d-%02d-%02dT%02d:%02d:00Z", y, mo, d, h, mi)).toEpochMilli()
    private fun raw(firstMs: Long, count: Int, fetchedMs: Long) = JSONObject().put("fetchedAt", fetchedMs).put(
        "forecast",
        JSONObject().put("utc_offset_seconds", 14400).put(
            "hourly",
            JSONObject().put("time", JSONArray((0 until count).map { firstMs / 1000 + it * 3600L }))
                .put("temperature_2m", JSONArray((0 until count).map { 10 + it })),
        ),
    ).toString()

    private val clock = utc(2026, 10, 9, 8, 20)
    private val floor = utc(2026, 10, 9, 8)
    private val hour = 3_600_000L

    @Test
    fun `11 saatlik cevaptan şimdi artı 7 saat alınır`() {
        val report = WeatherReport.parse(raw(floor - hour, 11, clock), clock)
        assertEquals(WeatherReport.DETAIL_HOURS, report.hours.size)
        assertEquals(12, report.hours.first().at.hour)
        assertEquals(19, report.hours.last().at.hour)
        assertEquals(11.0, report.hours.first().temperature!!, 0.0)
    }

    @Test
    fun `eski 6 saatlik önbellek hâlâ geçerli (4-6 saat)`() {
        val report = WeatherReport.parse(raw(floor - hour, 7, clock), clock)
        assertTrue(report.hours.size in WeatherReport.STRIP_HOURS..WeatherReport.DETAIL_HOURS)
        assertEquals(12, report.hours.first().at.hour)
    }

    @Test
    fun `şimdiki saat yoksa ya da 4 saatten azsa hata`() {
        for (bad in listOf(raw(floor + hour, 10, clock), raw(floor, 3, clock))) {
            try {
                WeatherReport.parse(bad, clock)
                fail("FormatException bekleniyordu")
            } catch (_: FormatException) {
            }
        }
    }

    @Test
    fun `hava göstergesi güncel API değerini, ardından üç tahmini alır`() {
        val now = utc(2026, 10, 2, 12)
        val first = now / 1000
        val body = JSONObject().put("fetchedAt", now).put(
            "forecast",
            JSONObject().put("utc_offset_seconds", 14400)
                .put("current", JSONObject().put("temperature_2m", 23).put("weather_code", 61).put("is_day", 1))
                .put(
                    "hourly",
                    JSONObject().put("time", JSONArray((0 until 6).map { first + it * 3600L }))
                        .put("temperature_2m", JSONArray(listOf(20, 21, 22, 23, 24, 25))).put("weather_code", JSONArray(listOf(0, 1, 2, 3, 45, 61))),
                ),
        ).toString()
        val report = WeatherReport.parse(body, now + 20 * 60_000)
        assertEquals(listOf(16, 17, 18, 19), report.hours.take(WeatherReport.STRIP_HOURS).map { it.at.hour })
        assertEquals(23.0, report.hours.first().temperature!!, 0.0)
        assertEquals(61, report.hours.first().code)
        assertEquals(true, report.hours.first().isDay)
    }

    @Test
    fun `hava parserı mevcut saat ve sonraki saatleri gece yarısında doğru gösterir`() {
        val clockMs = utc(2026, 10, 2, 19, 30)
        val first = utc(2026, 10, 2, 19) / 1000
        val body = JSONObject().put("fetchedAt", clockMs).put(
            "forecast",
            JSONObject().put("utc_offset_seconds", 14400).put(
                "hourly",
                JSONObject().put("time", JSONArray((0 until 6).map { first + it * 3600L }))
                    .put("temperature_2m", JSONArray(listOf(1, 2, 3, 4, 5, 6)))
                    .put("precipitation_probability", JSONArray(listOf(0, 10, 20, JSONObject.NULL, 40, 50))),
            ),
        ).toString()
        val report = WeatherReport.parse(body, clockMs)
        // Y1: 6 saatlik cevabın hepsi (ayrıntı en çok 8 saat; şerit ilk 4'ü gösterir).
        assertEquals(6, report.hours.size)
        assertEquals(2, report.hours.first().at.dayOfMonth)
        assertEquals(23, report.hours.first().at.hour)
        assertEquals(1.0, report.hours.first().temperature!!, 0.0)
        assertNull(report.hours[3].rainChance)
        assertNull(report.hours.first().wind)
    }
}
