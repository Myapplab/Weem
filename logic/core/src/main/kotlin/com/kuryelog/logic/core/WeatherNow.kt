package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putN
import org.json.JSONObject

/**
 * `WeatherNow` v1 (plan 4.4; bugünkü `weather/current`, `X_protocol` P9, RS `OfferWeather.kt`): `:map` → `:orders` (R15), `:economics`.
 * Gönderilmez (S6: her telefon kendi internetinden çeker); yalnız iç olay. En çok 90 dk geçerli; eskisi "gözlenmedi" sayılır.
 */
data class WeatherNow(
    val fetchedAtMs: Long,
    val tempC: Double? = null,
    val feelsC: Double? = null,
    val rainMm: Double? = null,
    val rainPct: Double? = null,
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
    /** WMO hava kodu. */
    val code: Int? = null,
    val source: String = SOURCE,
) {
    fun fresh(now: Long): Boolean = now - fetchedAtMs in 0..MAX_AGE_MS

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("source", source).put("fetchedAtMs", fetchedAtMs)
        .putN("tempC", tempC).putN("feelsC", feelsC).putN("rainMm", rainMm).putN("rainPct", rainPct).putN("windKmh", windKmh)
        .putN("gustKmh", gustKmh).putN("code", code)

    companion object {
        const val SCHEMA = 1
        const val SOURCE = "open_meteo"
        const val MAX_AGE_MS = 5_400_000L

        fun parse(raw: String?): WeatherNow? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): WeatherNow? {
            if (Msg.version(j) != SCHEMA) return null
            return WeatherNow(Msg.long(j, "fetchedAtMs") ?: return null, Msg.num(j, "tempC"), Msg.num(j, "feelsC"), Msg.num(j, "rainMm"),
                Msg.num(j, "rainPct"), Msg.num(j, "windKmh"), Msg.num(j, "gustKmh"), Msg.int(j, "code"), Msg.str(j, "source") ?: SOURCE)
        }

        /** Bugünkü YolGo yükü (`source: "yolgo_open_meteo"`, `fetched_at`, `temp_c`…); bayat (0–90 dk dışı), yabancı ya da bozuk → null. */
        fun fromYolGoFeed(raw: String?, now: Long): WeatherNow? {
            val j = Msg.obj(raw) ?: return null
            if (Msg.version(j) != 1 || Msg.str(j, "source") != "yolgo_open_meteo") return null
            val w = WeatherNow(Msg.long(j, "fetched_at") ?: return null, Msg.num(j, "temp_c"), Msg.num(j, "feels_c"), Msg.num(j, "rain_mm"),
                Msg.num(j, "rain_pct"), Msg.num(j, "wind_kmh"), Msg.num(j, "gust_kmh"), Msg.int(j, "code"))
            return w.takeIf { it.fresh(now) }
        }
    }
}
