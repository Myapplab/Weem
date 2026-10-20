package com.kuryelog.logic.map.weather

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hava bildirimi metni (`WeatherData.text`, `WeatherWorker.kt:77–107`, MAP.md 10.8): önümüzdeki 4 saat; tam 4 saat yoksa hata. */
object WeatherNotifyText {
    const val HOURS = 4

    fun title(az: Boolean) = if (az) "Növbəti 4 saatın havası" else "Önümüzdeki 4 saatin havası"
    fun channelName(az: Boolean) = if (az) "Hava proqnozu" else "Hava tahmini"

    /** Bildirim gövdesi: ilk satır `"<yer> · HH:mm–HH:mm"`, sonra 4 saat satırı, son satır "Open-Meteo". */
    fun text(raw: String, az: Boolean, nowMs: Long): String {
        val report = JSONObject(raw)
        val forecast = report.getJSONObject("forecast")
        val hours = forecast.getJSONObject("hourly")
        val times = hours.getJSONArray("time")
        val zone = runCatching { ZoneId.of(forecast.optString("timezone", "Asia/Baku")) }.getOrDefault(ZoneId.of("Asia/Baku"))
        val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)
        fun value(name: String, i: Int, digits: Int = 0): String {
            val a = hours.optJSONArray(name) ?: return "—"
            if (i >= a.length() || a.isNull(i)) return "—"
            val v = a.optDouble(i)
            return if (v.isFinite()) String.format(Locale.US, "%.${digits}f", v) else "—"
        }
        val lines = mutableListOf<String>()
        val end = nowMs + HOURS * 3_600_000L
        for (i in 0 until times.length()) {
            val t = times.getLong(i) * 1000L
            if (t <= nowMs || t > end) continue
            lines.add(
                "${fmt.format(Instant.ofEpochMilli(t))} · ${value("temperature_2m", i)}°C " +
                    "(${if (az) "hiss" else "hissedilen"} ${value("apparent_temperature", i)}°) · " +
                    "${if (az) "yağıntı" else "yağış"} %${value("precipitation_probability", i)} · " +
                    "${value("precipitation", i, 1)} mm · " +
                    "${if (az) "külək" else "rüzgâr"} ${value("wind_speed_10m", i)} km/h " +
                    "(${if (az) "ani" else "hamle"} ${value("wind_gusts_10m", i)})",
            )
            if (lines.size == HOURS) break
        }
        check(lines.size == HOURS) { "Four future hours unavailable" }
        val place = if (report.optLong("locationAt") == 0L) "Bakı" else if (az) "Son məlum məkan" else "Son bilinen konum"
        return "$place · ${fmt.format(Instant.ofEpochMilli(nowMs))}–${fmt.format(Instant.ofEpochMilli(end))}\n" + lines.joinToString("\n") + "\nOpen-Meteo"
    }
}
