package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EnergySegmentsTest.kt @ 6388c04, aynı girdi → aynı çıktı.

/** Energy schema rows: one per leg / idle spell, every energy value null + not_measured, never invented; scooter_id always set. */
class EnergySegmentsTest {
    private val w = OfferWeather(18.0, null, 0.0, 10.0, 12.0, 20.0, 1, 1_000L, OfferWeather.SOURCE_YOLGO)
    private val stamp = EnergySegments.Stamp(5_000L, 123_456_789L, "scooter-1", "default_single_scooter", w)
    private val legs = JSONObject().put("schemaVersion", 1).put("tripId", "t1").put("pkg", "com.wolt.courierapp").put("legs", JSONArray()
        .put(JSONObject().put("kind", "approach").put("startAtMs", 1_000).put("endAtMs", 2_000).put("km", 1.2).put("wh", JSONObject.NULL).put("source", "screen_stage"))
        .put(JSONObject().put("kind", "to_dropoff").put("startAtMs", 2_000).put("endAtMs", JSONObject.NULL).put("km", JSONObject.NULL).put("wh", JSONObject.NULL).put("source", "inferred")))

    @Test fun oneRowPerLegWithNullEnergy() {
        val rows = EnergySegments.fromLegs(legs, stamp)
        assertEquals(listOf("approach", "to_dropoff"), rows.map { it.getString("segment") })
        val r = rows[0]
        assertEquals("scooter-1", r.getString("scooter_id")); assertEquals(123_456_789L, r.getLong("mono_ns")); assertEquals("kuryeplus", r.getString("source"))
        assertEquals(1.2, r.getDouble("km"), 1e-9); assertTrue(rows[1].isNull("km")); assertTrue(rows[1].isNull("endAtMs"))
        val e = r.getJSONObject("energy")
        assertEquals("not_measured", e.getString("source")); assertFalse(e.getBoolean("power_verified"))
        for (k in listOf("wh", "vxa_w_avg", "esc_w_avg", "external_feeding", "temp_c")) assertTrue(k, e.isNull(k))
        assertTrue(e.getJSONObject("internal").isNull("pct_start")); assertTrue(e.getJSONObject("external").isNull("a_avg"))
        val c = r.getJSONObject("context")
        assertEquals(18.0, c.getDouble("ambient_c"), 1e-9); assertEquals("yolgo_open_meteo", c.getString("weather_source")); assertTrue(c.isNull("elev_gain_m"))
        assertFalse(r.toString().contains("\"lat\"") || r.toString().contains("\"lon\""))
    }

    @Test fun idleSpellAndMissingWeather() {
        val r = EnergySegments.fromIdle(JSONObject().put("spellId", "s1").put("startAtMs", 10).put("endAtMs", 20).put("kmRidden", 0.4), stamp.copy(weather = null))!!
        assertEquals("idle", r.getString("segment")); assertEquals("s1", r.getString("spellId")); assertTrue(r.isNull("tripId"))
        assertEquals("not_observed", r.getJSONObject("context").getString("weather_source")); assertTrue(r.getJSONObject("context").isNull("ambient_c"))
        assertNull(EnergySegments.fromIdle(JSONObject(), stamp)); assertTrue(EnergySegments.fromLegs(JSONObject(), stamp).isEmpty())
    }

    @Test fun offerRecordWeatherFollowsTheSpecShape() {
        val j = EconomicsLogs.weather(w)
        assertEquals(18.0, j.getDouble("ambientC"), 1e-9); assertEquals(5.56, j.getDouble("gustMs"), 1e-9)
        assertFalse(j.getBoolean("rain")); assertEquals("open_meteo", j.getString("source"))
        assertTrue(EconomicsLogs.weather(w.copy(rainMm = null, code = 61)).getBoolean("rain"))
        assertTrue(EconomicsLogs.weather(w.copy(rainMm = null, code = null)).isNull("rain"))
    }

    /** With KL_ROWS_OUT set, dumps rows for docs/ekonomi-v2/araclar/validate_rows.py (energySegment, weather). */
    @Test fun dumpForSchemaCheck() {
        val dir = System.getenv("KL_ROWS_OUT") ?: return
        File(dir).mkdirs()
        val lines = (EnergySegments.fromLegs(legs, stamp) + listOfNotNull(EnergySegments.fromIdle(JSONObject().put("spellId", "s1").put("startAtMs", 10).put("endAtMs", 20).put("kmRidden", 0.4), stamp.copy(weather = null))))
            .map { JSONObject().put("def", "energySegment").put("row", it).toString() } +
            listOf(JSONObject().put("def", "weather").put("row", EconomicsLogs.weather(w)).toString())
        File(dir, "energy.jsonl").writeText(lines.joinToString("\n") + "\n")
    }
}
