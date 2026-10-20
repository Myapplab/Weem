package com.kuryelog.logic.map.logs

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Sürüş günlüğü yazıcısı (`RideLogger`, MAP.md 12.2–12.3): satırlar tamponlanır, [flush] ile `<dayDate>.jsonl` ve açık sürüş dosyasına eklenir; sürüş
 * olaylara göre açılıp kapanır (`ride.active.v1`); gün gece yarısı bölünmez (bütün satırlar günün başladığı takvim gününün dosyasına gider).
 * Zamanlayıcılar (5 sn boşaltma, 1 sn [env]) Android `RideLogRuntime`'dadır; her yöntem iş parçacığı güvenlidir.
 */
class RideLogWriter(private val dir: File, private val prefs: MapPrefs, private val clock: Clock) {
    private val lock = Any()
    private val ioLock = Any()
    private val buffers = LinkedHashMap<String, StringBuilder>()
    private var dayId: String? = null
    private var dayDate: String? = null
    private var rideFile: String? = prefs.getString(ACTIVE_KEY)?.takeIf { RideLogStore.RIDE_FILE.matches(it) }
    private var lastSession = 0L
    private var lastGpsAt: Long? = null

    var enabled: Boolean = true
        get() = synchronized(lock) { field }
        set(value) = synchronized(lock) {
            if (field && !value) endRideLocked("logging_off")
            field = value
        }

    val activeDayId: String? get() = synchronized(lock) { dayId }

    /** [dayId] `yolgo-<ms>`; null = gün kapalı (açık sürüş kapanır, tampon boşalır). */
    fun setDay(dayId: String?) {
        var flushNow = false
        synchronized(lock) {
            if (this.dayId == dayId) return
            if (this.dayId != null) endRideLocked("day_scope_closed")
            this.dayId = dayId
            val stamp = dayId?.removePrefix("yolgo-")?.toLongOrNull()
            dayDate = when {
                dayId == null -> null
                stamp == null -> dayOf(clock.wallMs())
                else -> dayOf(stamp)
            }
            if (dayId == null) {
                rideFile = null
                prefs.remove(ACTIVE_KEY)
                flushNow = true
            }
        }
        if (flushNow) flush()
    }

    private fun dayOf(ms: Long) = LogTime.day(ms, clock.zone())

    private fun writeLocked(m: Map<String, Any?>) {
        val id = dayId
        if (!enabled || id == null) return
        val now = clock.wallMs()
        val o = JSONObject().put("t", now).put("dayId", id)
        for ((k, v) in m) o.put(k, wrap(v))
        val line = o.toString() + "\n"
        buffers.getOrPut("${dayDate ?: dayOf(now)}.jsonl") { StringBuilder() }.append(line)
        rideFile?.let { buffers.getOrPut(it) { StringBuilder() }.append(line) }
    }

    private fun beginRideLocked(reason: String) {
        if (!enabled || dayId == null || rideFile != null) return
        val now = clock.wallMs()
        val stamp = max(now, lastSession + 1)
        lastSession = stamp
        val name = "${dayDate ?: dayOf(now)}_ride_$stamp.jsonl"
        rideFile = name
        prefs.setString(ACTIVE_KEY, name)
        writeLocked(mapOf("k" to "ev", "e" to "ride_start", "reason" to reason))
    }

    private fun endRideLocked(reason: String) {
        if (rideFile == null) return
        writeLocked(mapOf("k" to "ev", "e" to "ride_end", "reason" to reason))
        rideFile = null
        prefs.remove(ACTIVE_KEY)
    }

    /** Konum 0,5 sn'de bir gelebilir; dosya büyümesin diye en sık ~1 sn'de bir yazılır (fix zamanına göre < 900 ms atlanır). */
    fun gps(fixAtMs: Long, lat: Double, lon: Double, accuracy: Double, speedMps: Double, heading: Double, altitude: Double) {
        synchronized(lock) {
            val last = lastGpsAt
            if (last != null && kotlin.math.abs(fixAtMs - last) < 900) return
            lastGpsAt = fixAtMs
            writeLocked(
                mapOf("k" to "gps", "la" to r(lat, 6), "lo" to r(lon, 6), "ac" to r(accuracy, 1), "sp" to r(speedMps, 2), "hd" to r(heading, 1), "al" to r(altitude, 1)),
            )
        }
    }

    /** 1 sn'de bir: basınç ≤ 3 sn tazeyse barometre yüksekliği; 1.11'de titreşim ölçülmez (`vr`, `vm` hep 0). Yükseklik yoksa yazılmaz. */
    fun env(pressureHpa: Double?, pressureAtMs: Long?) {
        val now = clock.wallMs()
        if (pressureHpa == null || pressureAtMs == null || now - pressureAtMs > 3000) return
        val hpa = pressureHpa
        val alt = 44330.0 * (1.0 - (hpa / 1013.25).pow(0.1903))
        synchronized(lock) {
            writeLocked(mapOf("k" to "env", "hpa" to r(hpa, 3), "balt" to r(alt, 2), "vr" to 0.0, "vm" to 0.0))
        }
    }

    /** `{k: "ev", e: ad, …veri}`; `route` önce açık sürüşü kapatır, sürüş açan/kapatan olay kümeleri 12.3. */
    fun event(name: String, data: Map<String, Any?> = emptyMap()) {
        var closeNow = false
        synchronized(lock) {
            if (name == "route") endRideLocked("new_route")
            if (name in BEGIN) beginRideLocked(name)
            writeLocked(mapOf("k" to "ev", "e" to name) + data)
            if (name in END) {
                endRideLocked(name)
                closeNow = true
            }
        }
        if (closeNow) flush()
    }

    /** Eşzamanlı boşaltmalar sırayı korur; hata olursa metin tamponun başına geri konur. */
    fun flush() {
        synchronized(ioLock) {
            val chunks = synchronized(lock) {
                val c = buffers.mapValues { it.value.toString() }
                buffers.clear()
                c
            }
            if (chunks.isEmpty()) return
            try {
                dir.mkdirs()
            } catch (_: Exception) {
            }
            for ((name, text) in chunks) {
                try {
                    File(dir, name).appendText(text)
                } catch (_: IOException) {
                    requeue(name, text)
                } catch (_: SecurityException) {
                    requeue(name, text)
                }
            }
        }
    }

    private fun requeue(name: String, text: String) = synchronized(lock) {
        val pending = buffers[name]?.toString().orEmpty()
        buffers[name] = StringBuilder(text).append(pending)
    }

    fun records(): List<RideLogEntry> {
        flush()
        return RideLogStore(dir, clock.zone()).entries()
    }

    fun isActive(e: RideLogEntry): Boolean = synchronized(lock) {
        enabled && dayId != null && (e.name == rideFile || e.name == "${dayDate ?: dayOf(clock.wallMs())}.jsonl")
    }

    /** Etkin sürüş ve gün dosyası silinemez (`IllegalStateException("Aktif kayıt silinemez")`). */
    fun delete(entry: RideLogEntry) {
        if (isActive(entry)) throw IllegalStateException("Aktif kayıt silinemez")
        flush()
        RideLogStore(dir, clock.zone()).delete(entry)
    }

    /** Açık günün tarihi silinemez (önce gün kapanır, 12.9). */
    fun deleteDay(day: String) {
        synchronized(lock) { if (dayId != null && dayDate == day) throw IllegalStateException("Active day must be closed before deletion") }
        flush()
        RideLogStore(dir, clock.zone()).deleteDay(day)
    }

    /** Tüm günlük (ya da bir gün) tek JSON'da; geçici klasör [exportDir]`/<µs>/` Android'de `cacheDir/exports`. */
    fun export(exportDir: File, day: String? = null): File {
        flush()
        return RideLogStore(dir, clock.zone()).export(File(exportDir, (clock.wallMs() * 1000).toString()), day)
    }

    /** Kapanışta: açık sürüş kapanmaz (süreç yeniden başlayınca `ride.active.v1` ile sürer); tampon boşaltılır. */
    fun close() = flush()

    private fun r(v: Double, digits: Int): Any {
        if (!v.isFinite()) return JSONObject.NULL
        val f = 10.0.pow(digits)
        return (v * f).roundToLong() / f
    }

    private fun wrap(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Double -> if (v.isFinite()) v else JSONObject.NULL
        is Float -> if (v.isFinite()) v else JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), wrap(x)) } }
        is Collection<*> -> JSONArray().also { a -> v.forEach { a.put(wrap(it)) } }
        else -> v
    }

    companion object {
        const val ACTIVE_KEY = "ride.active.v1"
        private val BEGIN = setOf("route", "shift_day_start", "shift_order_start", "shift_leave_restaurant", "shift_home_return_start")
        private val END = setOf(
            "arrive", "route_clear", "shift_day_end", "shift_arrive_restaurant", "shift_arrive_customer", "shift_delivered", "shift_order_cancel",
            "shift_home_return_arrived",
        )
    }
}
