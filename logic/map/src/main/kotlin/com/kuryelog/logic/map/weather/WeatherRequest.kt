package com.kuryelog.logic.map.weather

import java.net.URLEncoder

/** Open-Meteo istek adresi (`WeatherWorker.kt:45–76`, MAP.md 10.7). Anahtar varsa `customer-api` konağı ve `apikey`; yoksa herkese açık konak. */
object WeatherRequest {
    const val DEFAULT_LAT = "40.4093"
    const val DEFAULT_LON = "49.8671"
    const val TIMEOUT_MS = 12_000
    const val CACHE_MS = 1_200_000L

    private const val HOURLY = "temperature_2m,apparent_temperature,precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m,weather_code,is_day," +
        "relative_humidity_2m,cloud_cover,visibility,pressure_msl,uv_index,wind_direction_10m,dew_point_2m"
    private const val CURRENT = "temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code,is_day"

    fun url(lat: String?, lon: String?, key: String?): String {
        val host = if (key.isNullOrEmpty()) "api.open-meteo.com" else "customer-api.open-meteo.com"
        val tail = if (key.isNullOrEmpty()) "" else "&apikey=" + URLEncoder.encode(key, "UTF-8")
        return "https://$host/v1/forecast?latitude=${lat ?: DEFAULT_LAT}&longitude=${lon ?: DEFAULT_LON}&hourly=$HOURLY&current=$CURRENT" +
            "&forecast_hours=10&past_hours=1&timeformat=unixtime&timezone=auto&wind_speed_unit=kmh$tail"
    }

    /** Geçerli koordinat (konum güncellemesi yazılabilir mi): sonlu ve aralıkta. */
    fun validLocation(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    /** Önbellek 20 dk içinde ve saat geri gitmediyse ağ açılmaz. */
    fun cacheFresh(nowMs: Long, fetchedMs: Long) = nowMs - fetchedMs in 0..CACHE_MS
}
