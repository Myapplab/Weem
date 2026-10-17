package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats OfferWeather.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * K28: weather at offer time, from YolGo's phone-local feed (`content://az.duzyol.app.weather/weather/current`, one row,
 * column `json`). KuryePlus has no network permission, so YolGo's Open-Meteo fetch is the only source (user decision,
 * 9 Oct 2026). A summary older than [MAX_AGE_MS] is not used: the offer then says `not_observed`, never an old value.
 */
data class OfferWeather(val tempC: Double?, val feelsC: Double?, val rainMm: Double?, val rainPct: Double?, val windKmh: Double?,
                        val gustKmh: Double?, val code: Int?, val fetchedAt: Long, val source: String) {
    companion object {
        const val SOURCE_YOLGO = "yolgo_open_meteo"
        const val NOT_OBSERVED = "not_observed"
        const val MAX_AGE_MS = 90 * 60_000L

        /** Parsed feed row, or null when unreadable, from another source/schema, or stale at [now]. */
        fun parse(json: String?, now: Long): OfferWeather? {
            val j = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
            if (j.optInt("schemaVersion", -1) != 1 || j.optString("source") != SOURCE_YOLGO) return null
            val at = j.longOrNull("fetched_at") ?: return null
            if (now - at !in 0..MAX_AGE_MS) return null
            return OfferWeather(j.num("temp_c"), j.num("feels_c"), j.num("rain_mm"), j.num("rain_pct"), j.num("wind_kmh"), j.num("gust_kmh"),
                j.num("code")?.toInt(), at, SOURCE_YOLGO)
        }

        fun fromRecord(j: JSONObject): OfferWeather? = j.str("weather_source")?.takeIf { it == SOURCE_YOLGO }?.let {
            OfferWeather(j.num("weather_temp_c"), j.num("weather_feels_c"), j.num("weather_rain_mm"), j.num("weather_rain_pct"), j.num("weather_wind_kmh"),
                j.num("weather_gust_kmh"), j.num("weather_code")?.toInt(), j.longOrNull("weather_fetched_at") ?: 0L, it)
        }
    }

    fun into(j: JSONObject): JSONObject = j.put("weather_source", source).put("weather_fetched_at", fetchedAt)
        .put("weather_temp_c", tempC ?: JSONObject.NULL).put("weather_feels_c", feelsC ?: JSONObject.NULL).put("weather_rain_mm", rainMm ?: JSONObject.NULL)
        .put("weather_rain_pct", rainPct ?: JSONObject.NULL).put("weather_wind_kmh", windKmh ?: JSONObject.NULL)
        .put("weather_gust_kmh", gustKmh ?: JSONObject.NULL).put("weather_code", code ?: JSONObject.NULL)

    /** Open-Meteo WMO code group as the card shows it. */
    fun kind(tl: (String, String) -> String): String? = code?.let { c -> when {
        c >= 95 -> tl("gök gürültülü", "göy gurultulu")
        c in 71..77 || c == 85 || c == 86 -> tl("karlı", "qarlı")
        c >= 51 -> tl("yağmurlu", "yağışlı")
        c == 45 || c == 48 -> tl("sisli", "dumanlı")
        c >= 2 -> tl("bulutlu", "buludlu")
        else -> tl("açık", "açıq")
    } }
}

/**
 * WEEM (ORDERS.md 12): kaynak `WeatherPort.current()` (`logic/core` `WeatherNow`); Rawstats YolGo'nun hava akışını okuyordu
 * ([OfferWeather.parse]). Teklife yazılan alanlar ve `weather_source` değeri (`yolgo_open_meteo`) aynı; [OfferWeather.MAX_AGE_MS]
 * dışındaki (eski ya da gelecekteki) değer yazılmaz.
 */
fun OfferWeather.Companion.from(w: WeatherNow, now: Long): OfferWeather? =
    if (now - w.fetchedAtMs !in 0..MAX_AGE_MS) null
    else OfferWeather(w.tempC, w.feelsC, w.rainMm, w.rainPct, w.windKmh, w.gustKmh, w.code, w.fetchedAtMs, SOURCE_YOLGO)
