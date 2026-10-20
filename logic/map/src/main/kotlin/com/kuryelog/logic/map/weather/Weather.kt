package com.kuryelog.logic.map.weather

import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.map.graph.FormatException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Bir saatlik tahmin (MAP.md 10.7): [at] servis saat diliminin duvar saati; bilinmeyen değer null. */
data class WeatherHour(
    val at: LocalDateTime,
    val temperature: Double?,
    val feelsLike: Double? = null,
    val rainChance: Double? = null,
    val rainMm: Double? = null,
    val wind: Double? = null,
    val gust: Double? = null,
    val code: Int? = null,
    val isDay: Boolean? = null,
    val humidity: Double? = null,
    val cloud: Double? = null,
    val visibility: Double? = null,
    val pressure: Double? = null,
    val uv: Double? = null,
    val windDirection: Double? = null,
    val dewpoint: Double? = null,
)

/**
 * `WeatherReport.parse(raw, now)` (`weather.dart:26–59`, MAP.md 10.7): taban = şimdiki UTC saat başı; `[taban, taban + 8 sa)` içindeki saatler;
 * taban saatinde `current` varsa o; ≥4 satır ve ilk satır taban saati değilse `FormatException`.
 */
class WeatherReport(val fetchedAtMs: Long, val hours: List<WeatherHour>) {
    companion object {
        /** Üst şerit: şimdi + 3 saat. Ayrıntı listesi: şimdi + 7 saat. */
        const val STRIP_HOURS = 4
        const val DETAIL_HOURS = 8
        private const val HOUR_MS = 3_600_000L
        private const val DEFAULT_OFFSET_S = 14_400

        fun parse(raw: String, nowMs: Long): WeatherReport {
            try {
                val j = JSONObject(raw)
                val forecast = j.getJSONObject("forecast")
                val hourly = forecast.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val offset = (forecast.opt("utc_offset_seconds") as? Number)?.toInt() ?: DEFAULT_OFFSET_S
                val current = forecast.optJSONObject("current")
                val floor = Math.floorDiv(nowMs, HOUR_MS) * HOUR_MS
                val rows = ArrayList<WeatherHour>()
                for (i in 0 until times.length()) {
                    val t = times.opt(i) as? Number ?: continue
                    val ms = t.toLong() * 1000
                    if (ms < floor || ms >= floor + DETAIL_HOURS * HOUR_MS) continue
                    fun number(key: String) = finite(hourly.optJSONArray(key), i)
                    // `current` yalnız taban saatinde kazanır.
                    fun value(key: String): Double? = (if (ms == floor) current?.opt(key) else null).let { c ->
                        if (c is Number && c.toDouble().isFinite()) c.toDouble() else number(key)
                    }
                    rows.add(
                        WeatherHour(
                            at = LocalDateTime.ofEpochSecond(Math.floorDiv(ms + offset * 1000L, 1000L), 0, ZoneOffset.UTC),
                            temperature = value("temperature_2m"), feelsLike = value("apparent_temperature"),
                            rainChance = number("precipitation_probability"), rainMm = value("precipitation"),
                            wind = value("wind_speed_10m"), gust = number("wind_gusts_10m"),
                            code = value("weather_code")?.toInt(), isDay = value("is_day")?.let { it == 1.0 },
                            humidity = value("relative_humidity_2m"), cloud = value("cloud_cover"), visibility = number("visibility"),
                            pressure = value("pressure_msl"), uv = number("uv_index"), windDirection = value("wind_direction_10m"),
                            dewpoint = number("dew_point_2m"),
                        ),
                    )
                }
                // Eski önbellek (6 saatlik istek) ve kısa cevaplar en az şerit kadar saatle geçerli kalır.
                if (rows.size < STRIP_HOURS || rows.first().at.toInstant(ZoneOffset.UTC).toEpochMilli() - offset * 1000L != floor) {
                    throw FormatException("Current hour and three future hours unavailable")
                }
                return WeatherReport(j.getLong("fetchedAt"), rows)
            } catch (e: JSONException) {
                throw FormatException("Weather report unreadable: ${e.message}")
            }
        }

        /** [a][i] sonlu bir sayıysa değeri, yoksa null. */
        internal fun finite(a: JSONArray?, i: Int): Double? {
            if (a == null || i < 0 || i >= a.length()) return null
            val n = a.opt(i) as? Number ?: return null
            return n.toDouble().takeIf { it.isFinite() }
        }
    }
}

/** Rawstats/`:orders` için özet (MAP.md 10.10 `WeatherFeed.summary`): `current` kazanır, saatlik dilim tamamlar; konum yazılmaz; rapor yok ya da bozuksa null. */
object WeatherSummary {
    fun summary(raw: String?): WeatherNow? {
        if (raw == null) return null
        return try {
            val report = JSONObject(raw)
            val forecast = report.getJSONObject("forecast")
            val current = forecast.optJSONObject("current")
            val hourly = forecast.optJSONObject("hourly")
            val fetchedAt = report.getLong("fetchedAt")
            // `current`in vermediği değerler için fetch anının saatlik dilimi (unixtime sn, sıralı).
            val times = hourly?.optJSONArray("time")
            var slot = -1
            if (times != null) {
                for (i in 0 until times.length()) {
                    if (times.optLong(i) * 1000L <= fetchedAt) slot = i else break
                }
            }
            fun hour(key: String) = WeatherReport.finite(hourly?.optJSONArray(key), slot)
            fun cur(key: String): Double? = (current?.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: hour(key)
            WeatherNow(
                fetchedAtMs = fetchedAt, tempC = cur("temperature_2m"), feelsC = cur("apparent_temperature"), rainMm = cur("precipitation"),
                rainPct = hour("precipitation_probability"), windKmh = cur("wind_speed_10m"), gustKmh = hour("wind_gusts_10m"),
                code = cur("weather_code")?.toInt(),
            )
        } catch (_: JSONException) {
            null
        }
    }
}
