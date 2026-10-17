package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** K28: weather on the offer from YolGo's feed; stale, foreign or broken rows are "not observed", never an old value. */
class OfferWeatherK28Test {
    private val now = 1_760_000_000_000L
    private fun feed(at: Long, source: String = "yolgo_open_meteo") = JSONObject().put("schemaVersion", 1).put("source", source).put("fetched_at", at)
        .put("temp_c", 18.4).put("feels_c", JSONObject.NULL).put("rain_mm", 0.4).put("rain_pct", 40).put("wind_kmh", 22).put("gust_kmh", 35)
        .put("code", 61).put("is_day", 1).toString()

    @Test fun freshFeedIsReadAndStaleOrForeignIsNot() {
        val w = OfferWeather.parse(feed(now - 20 * 60_000L), now)!!
        assertEquals(18.4, w.tempC!!, 1e-9); assertNull(w.feelsC); assertEquals(40.0, w.rainPct!!, 1e-9); assertEquals(61, w.code)
        assertEquals("yağmurlu", w.kind { t, _ -> t })
        assertNull(OfferWeather.parse(feed(now - 91 * 60_000L), now))
        assertNull(OfferWeather.parse(feed(now + 60_000L), now))
        assertNull(OfferWeather.parse(feed(now, "other"), now))
        assertNull(OfferWeather.parse("{oops", now)); assertNull(OfferWeather.parse(null, now))
    }

    @Test fun offerRecordRoundTripAndOlderRecords() {
        val o = OfferState("o1", "com.wolt.courierapp", now, CourierParser.parse("com.wolt.courierapp", listOf("Decline", "₼3.94", "Accept"), emptyList(), now))
        o.weather = OfferWeather.parse(feed(now - 60_000L), now)
        val j = RecordJson.offer(o)
        assertEquals("yolgo_open_meteo", j.getString("weather_source")); assertTrue(j.isNull("weather_feels_c"))
        val back = RecordJson.offer(JSONObject(j.toString()))
        assertEquals(o.weather, back.weather)
        o.weather = null
        assertFalse(RecordJson.offer(o).has("weather_source")); assertNull(RecordJson.offer(JSONObject(RecordJson.offer(o).toString())).weather)
    }
}
