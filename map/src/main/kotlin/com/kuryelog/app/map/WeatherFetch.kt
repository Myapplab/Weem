package com.kuryelog.app.map

import android.content.Context
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.map.net.HttpRequest
import com.kuryelog.logic.map.weather.WeatherReport
import com.kuryelog.logic.map.weather.WeatherRequest
import com.kuryelog.logic.map.weather.WeatherSchedule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Hava verisi çekme ve önbellek (MAP.md 10.7; YolGo `WeatherData.fetch`): önbellek `files/map/weather.json` (`{"forecast", "fetchedAt", "latitude", "longitude", "locationAt"}`);
 * 0–20 dk yaşındaysa ağ açılmaz (`force` hariç). Konum `map` tercihlerinde (`weather.lat`/`weather.lon`/`weather.locationAt`); yoksa Bakü merkezi.
 * Eksik cevap eski doğru tahmini silmez. Ağ yalnız `HttpPort` (Open-Meteo).
 */
internal object WeatherFetch {
    const val KEY_LAT = "weather.lat"
    const val KEY_LON = "weather.lon"
    const val KEY_LOCATION_AT = "weather.locationAt"

    private val lock = Mutex()

    private fun file(app: Context) = File(File(app.filesDir, "map").also { it.mkdirs() }, "weather.json")

    /** Önbellekteki ham rapor (yoksa ya da okunamıyorsa null). */
    fun cached(app: Context): String? = runCatching { file(app).takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()

    /**
     * [day] verilmişse (vardiya kimliği) gün kapalıyken istek reddedilir ("day closed"); ağ hatası IOException, eksik ya da bozuk yanıt FormatException.
     * Dönen metin önbelleğe yazılmış rapordur.
     */
    suspend fun fetch(app: Context, day: String?, force: Boolean, nowMs: Long = System.currentTimeMillis()): String = lock.withLock {
        if (day != null && !accepts(day)) error("day closed")
        val cached = cached(app)
        if (!force && cached != null) {
            val at = runCatching { JSONObject(cached).getLong("fetchedAt") }.getOrNull()
            if (at != null && WeatherSchedule.cacheValid(at, nowMs)) return@withLock cached
        }
        val prefs = MapPrefsStore(app)
        val lat = prefs.getString(KEY_LAT)?.toDoubleOrNull() ?: WeatherRequest.DEFAULT_LAT
        val lon = prefs.getString(KEY_LON)?.toDoubleOrNull() ?: WeatherRequest.DEFAULT_LON
        val key = prefs.getString(WeatherSchedule.KEY_API_KEY).orEmpty()
        val res = AndroidHttp.send(HttpRequest("GET", WeatherRequest.url(lat, lon, key), timeoutMs = WeatherRequest.TIMEOUT_MS))
        if (res.status != 200) throw IOException("Weather request failed: ${res.status}")
        val report = WeatherRequest.report(res.body, nowMs, lat, lon, prefs.getLong(KEY_LOCATION_AT) ?: 0L)
        if (day != null && !accepts(day)) error("day closed")
        write(app, report)
        report
    }

    /** `ShiftHub` vardiyası bu kimlikle açık mı (`TelemetryService.accepts`): kimlik yoksa herkes kabul. */
    fun accepts(day: String?): Boolean {
        if (day == null) return true
        val s = ShiftHub.state.value
        return s.active && s.startedAtMs?.toString() == day
    }

    fun dayId(): String? = ShiftHub.state.value.takeIf { it.active }?.startedAtMs?.toString()

    private fun write(app: Context, raw: String) {
        val f = file(app)
        val tmp = File(f.parentFile, "weather.json.tmp")
        tmp.writeText(raw, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.writeText(raw, Charsets.UTF_8)
            tmp.delete()
        }
    }

    /** Ham rapordan görünüm raporu (bozuksa null). */
    fun parse(raw: String?, nowMs: Long = System.currentTimeMillis()): WeatherReport? = raw?.let { runCatching { WeatherReport.parse(it, nowMs) }.getOrNull() }
}
