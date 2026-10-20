package com.kuryelog.logic.map.weather

import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Open-Meteo çekimi ve önbellek (`WeatherData.fetch`, `WeatherWorker.kt:39–76`, MAP.md 10.7): bir seferde bir istek; gün kimlikli istek gün kapalıysa reddedilir ("day closed");
 * [force] yoksa 20 dk içindeki önbellek döner; HTTP ≠ 200 ya da `hourly.time` < 5 → hata (eski doğru tahmin silinmez). Rapor `{forecast, fetchedAt, latitude, longitude, locationAt}`.
 *
 * @param accepts gün kimliği hâlâ geçerli mi (vardiya açık); kimliksiz (null) istek her zaman kabul
 */
class WeatherFetcher(
    private val http: HttpPort,
    private val prefs: WeatherPrefs,
    private val store: ReportStore,
    private val userAgent: String,
    private val nowMs: () -> Long,
    private val accepts: (String) -> Boolean,
) : WeatherSource {
    private val lock = Mutex()

    override suspend fun cached(): String? = store.read()

    override suspend fun fetch(force: Boolean, dayId: String?): String = lock.withLock {
        if (dayId != null && !accepts(dayId)) error("day closed")
        val now = nowMs()
        val cached = store.read()
        if (!force && cached != null && WeatherRequest.cacheFresh(now, prefs.fetched)) return cached
        val response = http.send(
            HttpRequest("GET", WeatherRequest.url(prefs.lat, prefs.lon, prefs.key), mapOf("User-Agent" to userAgent), timeoutMs = WeatherRequest.TIMEOUT_MS.toLong()),
        )
        if (response.status != 200) error("Weather request failed")
        val body = JSONObject(response.body)
        if (body.getJSONObject("hourly").getJSONArray("time").length() < 5) error("Incomplete forecast")
        val report = JSONObject().put("forecast", body).put("fetchedAt", now)
            .put("latitude", (prefs.lat ?: WeatherRequest.DEFAULT_LAT).toDouble()).put("longitude", (prefs.lon ?: WeatherRequest.DEFAULT_LON).toDouble())
            .put("locationAt", prefs.locationAt).toString()
        // İndirme sürerken gün kapandıysa kaydetme.
        if (dayId != null && !accepts(dayId)) error("day closed")
        store.write(report)
        prefs.fetched = now
        report
    }
}
