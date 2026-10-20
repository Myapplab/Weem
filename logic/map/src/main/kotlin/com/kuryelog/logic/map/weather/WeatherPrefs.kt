package com.kuryelog.logic.map.weather

import com.kuryelog.logic.map.settings.MapPrefs

/**
 * Hava ayarları ve çalışma durumu, `map` tercih dosyasında (MAP.md 10.11): `weather.enabled.v1` (günde bir bildirim), `weather.strip.v1` (harita şeridi),
 * `weather.key.v1` (Open-Meteo anahtarı; değeri koda ya da belgeye yazılmaz), `weather.permission.asked.v1`; konum, önbellek zamanı ve bildirim kaydı için `weather.*` yardımcı anahtarları.
 * Rapor gövdesi `files/map/weather.json`'da ([ReportStore]).
 */
class WeatherPrefs(private val prefs: MapPrefs) {
    var enabled: Boolean
        get() = prefs.getBool(ENABLED) ?: true
        set(v) = prefs.setBool(ENABLED, v)

    var strip: Boolean
        get() = prefs.getBool(STRIP) ?: true
        set(v) = prefs.setBool(STRIP, v)

    val key: String get() = prefs.getString(KEY) ?: ""

    /** Anahtar değişince önbellek geçersiz olur: çağıran [ReportStore.clear] çağırır; geri değer değişti mi. */
    fun setKey(v: String): Boolean {
        if (key == v) return false
        prefs.setString(KEY, v)
        fetched = 0
        return true
    }

    var permissionAsked: Boolean
        get() = prefs.getBool(PERMISSION_ASKED) ?: false
        set(v) = prefs.setBool(PERMISSION_ASKED, v)

    /** Son başarılı çekim (ms); 0 = yok. */
    var fetched: Long
        get() = prefs.getLong(FETCHED) ?: 0
        set(v) = prefs.setLong(FETCHED, v)

    var notified: Long
        get() = prefs.getLong(NOTIFIED) ?: 0
        set(v) = prefs.setLong(NOTIFIED, v)

    var notifiedDay: String?
        get() = prefs.getString(NOTIFIED_DAY)
        set(v) = prefs.setString(NOTIFIED_DAY, v ?: "")

    val lat: String? get() = prefs.getString(LAT)?.takeIf { it.isNotEmpty() }
    val lon: String? get() = prefs.getString(LON)?.takeIf { it.isNotEmpty() }
    val locationAt: Long get() = prefs.getLong(LOCATION_AT) ?: 0

    /** Son bilinen konum (`updateWeatherLocation`): yalnız geçerli koordinat yazılır. */
    fun setLocation(lat: Double, lon: Double, nowMs: Long): Boolean {
        if (!WeatherRequest.validLocation(lat, lon)) return false
        prefs.setString(LAT, lat.toString())
        prefs.setString(LON, lon.toString())
        prefs.setLong(LOCATION_AT, nowMs)
        return true
    }

    companion object {
        const val ENABLED = "weather.enabled.v1"
        const val STRIP = "weather.strip.v1"
        const val KEY = "weather.key.v1"
        const val PERMISSION_ASKED = "weather.permission.asked.v1"
        const val FETCHED = "weather.fetched"
        const val NOTIFIED = "weather.notified"
        const val NOTIFIED_DAY = "weather.notifiedDay"
        const val LAT = "weather.lat"
        const val LON = "weather.lon"
        const val LOCATION_AT = "weather.locationAt"
    }
}

/** Rapor önbelleği (`files/map/weather.json`; Android `:map`'ta atomik yazılan dosya). */
interface ReportStore {
    fun read(): String?
    fun write(raw: String)
    fun clear()
}
