package com.kuryelog.logic.map.weather

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.map.graph.FormatException
import com.kuryelog.logic.map.ui.RideText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    /** Metre. */
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
    /** Rapor ≥ 40 dk eskiyse şerit "Eski tahmin" der (MAP.md 10.9). */
    fun stale(nowMs: Long): Boolean = nowMs - fetchedAtMs >= STALE_MS

    companion object {
        const val STRIP_HOURS = 4
        const val DETAIL_HOURS = 8
        const val STALE_MS = 40 * 60_000L
        private const val HOUR_MS = 3_600_000L
        private const val DEFAULT_OFFSET_S = 14400

        fun parse(raw: String, nowMs: Long): WeatherReport {
            try {
                val j = JSONObject(raw)
                val f = j.getJSONObject("forecast")
                val h = f.getJSONObject("hourly")
                val times = h.getJSONArray("time")
                val offset = f.optInt("utc_offset_seconds", DEFAULT_OFFSET_S)
                val current = f.optJSONObject("current")
                val floor = Math.floorDiv(nowMs, HOUR_MS) * HOUR_MS
                fun number(key: String, i: Int): Double? = numberAt(h.optJSONArray(key), i)
                val rows = ArrayList<WeatherHour>()
                var firstMs = 0L
                for (i in 0 until times.length()) {
                    val t = times.opt(i)
                    if (t !is Number) continue
                    val ms = t.toLong() * 1000
                    if (ms < floor || ms >= floor + DETAIL_HOURS * HOUR_MS) continue
                    fun value(key: String): Double? {
                        val n = if (ms == floor && current != null) current.opt(key) else null
                        return if (n is Number && n.toDouble().isFinite()) n.toDouble() else number(key, i)
                    }
                    if (rows.isEmpty()) firstMs = ms
                    rows += WeatherHour(
                        at = LocalDateTime.ofEpochSecond(ms / 1000 + offset, 0, ZoneOffset.UTC),
                        temperature = value("temperature_2m"), feelsLike = value("apparent_temperature"), rainChance = number("precipitation_probability", i),
                        rainMm = value("precipitation"), wind = value("wind_speed_10m"), gust = number("wind_gusts_10m", i),
                        code = value("weather_code")?.toInt(), isDay = value("is_day")?.let { it == 1.0 },
                        humidity = value("relative_humidity_2m"), cloud = value("cloud_cover"), visibility = number("visibility", i),
                        pressure = value("pressure_msl"), uv = number("uv_index", i), windDirection = value("wind_direction_10m"), dewpoint = number("dew_point_2m", i),
                    )
                }
                // Eski önbellek (6 saatlik istek) ve kısa cevaplar en az şerit kadar saatle geçerli kalır.
                if (rows.size < STRIP_HOURS || firstMs != floor) throw FormatException("Current hour and three future hours unavailable")
                return WeatherReport(j.getLong("fetchedAt"), rows)
            } catch (e: JSONException) {
                throw FormatException("Weather report unreadable: ${e.message}")
            }
        }

        internal fun numberAt(a: JSONArray?, i: Int): Double? {
            if (a == null || i >= a.length()) return null
            val v = a.opt(i)
            return if (v is Number && v.toDouble().isFinite()) v.toDouble() else null
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
            // Alım anının saatlik dilimi (unixtime sn): "current"ın vermediği değerler için.
            val times = hourly?.optJSONArray("time")
            var slot = -1
            if (times != null) for (i in 0 until times.length()) { if (times.optLong(i) * 1000L <= fetchedAt) slot = i else break }
            fun hour(key: String): Double? = if (slot < 0) null else WeatherReport.numberAt(hourly?.optJSONArray(key), slot)
            fun cur(key: String): Double? = current?.opt(key)?.takeIf { it is Number && it.toDouble().isFinite() }?.let { (it as Number).toDouble() } ?: hour(key)
            WeatherNow(
                fetchedAtMs = fetchedAt, tempC = cur("temperature_2m"), feelsC = cur("apparent_temperature"), rainMm = cur("precipitation"),
                rainPct = hour("precipitation_probability"), windKmh = cur("wind_speed_10m"), gustKmh = hour("wind_gusts_10m"), code = cur("weather_code")?.toInt(),
            )
        } catch (_: JSONException) {
            null
        }
    }
}

/** İstek adresi (MAP.md 10.7): Open-Meteo `v1/forecast`; kullanıcı anahtarı varsa `customer-api` ve `&apikey=` (anahtar değeri koda yazılmaz). */
object WeatherRequest {
    const val DEFAULT_LAT = 40.4093
    const val DEFAULT_LON = 49.8671
    const val TIMEOUT_MS = 12_000L
    const val MIN_HOURLY_ROWS = 5
    private const val HOURLY = "temperature_2m,apparent_temperature,precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m,weather_code,is_day,relative_humidity_2m,cloud_cover,visibility,pressure_msl,uv_index,wind_direction_10m,dew_point_2m"
    private const val CURRENT = "temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code,is_day"

    fun url(lat: Double, lon: Double, key: String): String {
        val host = if (key.isEmpty()) "api.open-meteo.com" else "customer-api.open-meteo.com"
        return "https://$host/v1/forecast?latitude=${coordinate(lat)}&longitude=${coordinate(lon)}&hourly=$HOURLY&current=$CURRENT" +
            "&forecast_hours=10&past_hours=1&timeformat=unixtime&timezone=auto&wind_speed_unit=kmh" +
            if (key.isEmpty()) "" else "&apikey=" + java.net.URLEncoder.encode(key, "UTF-8")
    }

    /** Eksik cevap eski doğru tahmini silmez: yanıt tamsa önbellek kaydı (`forecast` + `fetchedAt` + konum), değilse FormatException. */
    fun report(body: String, nowMs: Long, lat: Double, lon: Double, locationAtMs: Long): String {
        val forecast = try {
            JSONObject(body).also { if (it.getJSONObject("hourly").getJSONArray("time").length() < MIN_HOURLY_ROWS) throw FormatException("Incomplete forecast") }
        } catch (e: JSONException) {
            throw FormatException("Weather response unreadable: ${e.message}")
        }
        return JSONObject().put("forecast", forecast).put("fetchedAt", nowMs).put("latitude", lat).put("longitude", lon).put("locationAt", locationAtMs).toString()
    }

    private fun coordinate(v: Double) = String.format(Locale.ROOT, "%.5f", v).trimEnd('0').trimEnd('.')
}

/** Bildirim metni (`WeatherData.text`, MAP.md 10.8): ilk satır "yer · HH:mm–HH:mm", sonra şimdiden sonraki 4 saat, son satır "Open-Meteo". */
object WeatherText {
    const val HOURS = 4

    class Notification(val title: String, val content: String, val bigText: String)

    fun notification(raw: String, lang: Lang, nowMs: Long): Notification {
        val text = text(raw, lang, nowMs)
        return Notification(RideText.WEATHER_NOTIF_TITLE.get(lang), text.lineSequence().drop(1).first(), text)
    }

    fun text(raw: String, lang: Lang, nowMs: Long): String {
        try {
            val report = JSONObject(raw)
            val forecast = report.getJSONObject("forecast")
            val hours = forecast.getJSONObject("hourly")
            val times = hours.getJSONArray("time")
            val zone = runCatching { ZoneId.of(forecast.optString("timezone", "Asia/Baku")) }.getOrDefault(ZoneId.of("Asia/Baku"))
            val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)
            fun clock(ms: Long) = fmt.format(Instant.ofEpochMilli(ms))
            fun value(name: String, i: Int, digits: Int = 0): String {
                val v = WeatherReport.numberAt(hours.optJSONArray(name), i) ?: return "—"
                return String.format(Locale.US, "%.${digits}f", v)
            }
            val feels = RideText.WEATHER_NOTIF_FEELS.get(lang)
            val rain = RideText.WEATHER_NOTIF_RAIN.get(lang)
            val wind = RideText.WEATHER_NOTIF_WIND.get(lang)
            val gust = RideText.WEATHER_NOTIF_GUST.get(lang)
            val lines = ArrayList<String>()
            val end = nowMs + HOURS * 3_600_000L
            for (i in 0 until times.length()) {
                val t = times.getLong(i) * 1000L
                if (t <= nowMs || t > end) continue
                lines += "${clock(t)} · ${value("temperature_2m", i)}°C ($feels ${value("apparent_temperature", i)}°) · $rain %${value("precipitation_probability", i)} · " +
                    "${value("precipitation", i, 1)} mm · $wind ${value("wind_speed_10m", i)} km/h ($gust ${value("wind_gusts_10m", i)})"
                if (lines.size == HOURS) break
            }
            if (lines.size != HOURS) throw FormatException("Four future hours unavailable")
            val place = if (report.optLong("locationAt") == 0L) RideText.WEATHER_NOTIF_PLACE_BAKU.get(lang) else RideText.WEATHER_NOTIF_PLACE_LAST.get(lang)
            return "$place · ${clock(nowMs)}–${clock(end)}\n" + lines.joinToString("\n") + "\nOpen-Meteo"
        } catch (e: JSONException) {
            throw FormatException("Weather report unreadable: ${e.message}")
        }
    }
}

/** Simge türü (`weatherIcon`, `weather_widget.dart:13–33`, MAP.md 10.9). */
enum class WeatherKind { SUN, NIGHT, THUNDER, RAIN, SNOW, WIND, FOG, CLOUD }

object WeatherIcons {
    private const val DARK_TEXT = 0xFF17283AL
    private const val LIGHT_TEXT = 0xFFFFFFFFL

    fun kind(h: WeatherHour): WeatherKind {
        if ((h.wind ?: 0.0) >= 30 || (h.gust ?: 0.0) >= 45) return WeatherKind.WIND
        val code = h.code ?: return if ((h.rainMm ?: 0.0) > 0) WeatherKind.RAIN else WeatherKind.CLOUD
        return when {
            code >= 95 -> WeatherKind.THUNDER
            code in 71..77 || code == 85 || code == 86 -> WeatherKind.SNOW
            code >= 51 -> WeatherKind.RAIN
            code == 45 || code == 48 -> WeatherKind.FOG
            code >= 2 -> WeatherKind.CLOUD
            h.isDay == false -> WeatherKind.NIGHT
            else -> WeatherKind.SUN
        }
    }

    fun description(kind: WeatherKind): RideText = when (kind) {
        WeatherKind.WIND -> RideText.WEATHER_AIR
        WeatherKind.THUNDER -> RideText.WEATHER_THUNDER
        WeatherKind.SNOW -> RideText.WEATHER_SNOW
        WeatherKind.RAIN -> RideText.WEATHER_RAIN
        WeatherKind.FOG -> RideText.WEATHER_FOG
        WeatherKind.CLOUD -> RideText.WEATHER_CLOUD
        WeatherKind.NIGHT -> RideText.WEATHER_NIGHT
        WeatherKind.SUN -> RideText.WEATHER_SUN
    }

    fun description(h: WeatherHour): RideText = description(kind(h))

    /** Yuvarlağın arka planı (üst, alt) ARGB: güneşli sıcak sarı, gece lacivert, yağmur/fırtına mavi-gri, kar buz, bulut/sis gri-mavi. */
    fun palette(kind: WeatherKind): Pair<Long, Long> = when (kind) {
        WeatherKind.SUN -> 0xFFFFE7A3L to 0xFFFFC56BL
        WeatherKind.NIGHT -> 0xFF2B3A67L to 0xFF16203FL
        WeatherKind.THUNDER -> 0xFF5A6478L to 0xFF353C4CL
        WeatherKind.RAIN -> 0xFFB9D3EEL to 0xFF7FA6D1L
        WeatherKind.SNOW -> 0xFFEFF6FFL to 0xFFC9DDF2L
        WeatherKind.WIND -> 0xFFDDF0EAL to 0xFFA9D3C6L
        WeatherKind.FOG, WeatherKind.CLOUD -> 0xFFE3EAF2L to 0xFFB9C7D6L
    }

    /** Yazı rengi: son rengin parlaklığı > 0,35 ise koyu `#17283A`, değilse beyaz. */
    fun onColor(palette: Pair<Long, Long>): Long = if (luminance(palette.second) > .35) DARK_TEXT else LIGHT_TEXT

    /** Flutter `Color.computeLuminance` (sRGB → doğrusal). */
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Yuvarlağın 3. satırı: yağış ihtimali ≥ %10 ise damla ve "40%", yoksa rüzgâr "12" (`weatherCompactExtra`). */
    class Extra(val rain: Boolean, val text: String)

    fun compactExtra(h: WeatherHour): Extra? {
        val rain = h.rainChance
        if (rain != null && rain >= 10) return Extra(true, "${Math.round(rain)}%")
        val wind = h.wind ?: return null
        return Extra(false, Math.round(wind).toString())
    }

    /** `_value(n, unit, digits)`: yoksa "—". */
    fun value(n: Double?, unit: String, digits: Int = 0): String = if (n == null) "—" else String.format(Locale.ROOT, "%.${digits}f", n) + unit
}

/** Çekme ve yenileme kuralları (MAP.md 10.7, 10.8): saf karar, Android `WeatherRuntime`/`WeatherWork` uygular. */
object WeatherSchedule {
    const val WORK_NAME = "duzyol_weather_v1"
    const val CHANNEL = "duzyol_weather"
    const val WORK_HOURS = 2L
    const val CACHE_MS = 1_200_000L
    const val FOREGROUND_REFRESH_MS = 600_000L
    const val LOCATION_EVERY_MS = 120_000L
    const val WORK_ATTEMPTS = 3

    const val KEY_ENABLED = "weather.enabled.v1"
    const val KEY_STRIP = "weather.strip.v1"
    const val KEY_API_KEY = "weather.key.v1"
    const val KEY_PERMISSION_ASKED = "weather.permission.asked.v1"

    /** Önbellek 0–20 dk yaşındaysa ağ açılmaz (`force` hariç). */
    fun cacheValid(fetchedMs: Long, nowMs: Long): Boolean = nowMs - fetchedMs in 0..CACHE_MS

    /** Hava konumu için fix süzgeci: doğruluk 0–65 m, yaş −5…+30 sn (`weather.dart:157-160`). */
    fun locationAccepted(accuracy: Double, ageMs: Long): Boolean = accuracy.isFinite() && accuracy in 0.0..65.0 && ageMs in -5_000..30_000

    /** `LiveWeather.refresh` kararı: ağdan çekilsin mi, bu çekme "süreç başına bir kez" hakkını kullanır mı. */
    class RefreshPlan(val network: Boolean, val usesShortFetch: Boolean)

    /**
     * [cached] önbellekteki rapor (yok ya da bozuksa null), [fetchedAtMs] raporun alınış anı; [cacheOnly] gün kapalı; [shortFetched] bu süreçte kısa önbellek için ağa çıkıldı mı.
     * Önbellek 20 dk'dan yeni ve ≥ 8 saatse ağ yok; < 8 saatse süreç başına bir kez (gün kapalı da olsa) ağdan tazelenir.
     */
    fun plan(cached: WeatherReport?, nowMs: Long, force: Boolean, cacheOnly: Boolean, shortFetched: Boolean): RefreshPlan {
        val fresh = cached != null && nowMs - cached.fetchedAtMs < CACHE_MS
        val short = cached == null || cached.hours.size < WeatherReport.DETAIL_HOURS
        val shortOnce = cacheOnly && short && !shortFetched
        val network = (!cacheOnly || shortOnce) && (force || !fresh || short)
        return RefreshPlan(network, shortOnce)
    }
}
