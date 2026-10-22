package com.kuryelog.logic.map.surface

import java.io.BufferedWriter
import java.io.File
import org.json.JSONObject

// A10.4c (kullanıcı kararı 22 Ekim; MAP.md 18.2): "son 10 dakika ham tanı kaydı". YolGo `android_overlay/RawDiagnostics.kt` @ 870ab6e.
// İsteğe bağlı ve sınırlıdır: kapalıyken ham sensör yazılmaz; açıkken dakikalık dosyalar (en çok 10 dk) tutulur, kalite özetlerine karışmaz.
// Satırda konum (enlem/boylam) yoktur; yalnız ivme, jiroskop, GPS hız ve doğruluk.

/** Bir ivme örneğinin ham tanı satırı (YolGo alan adları, `v: 2`). */
class RawSample(
    val day: String, val boot: String, val leg: String?, val atNs: Long, val receivedNs: Long, val utc: Long, val receivedUtc: Long,
    val linear: Boolean, val acceleration: DoubleArray, val worldUp: DoubleArray?, val gyro: Double?, val speed: Double, val accuracy: Double,
    val profile: String, val freshGps: Boolean, val stationary: Boolean, val offsetReady: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", 2).put("day", day).put("boot", boot).put("leg", leg ?: JSONObject.NULL)
        .put("atNs", atNs).put("receivedNs", receivedNs).put("utc", utc).put("receivedUtc", receivedUtc)
        .put("linear", linear).put("accelerationMS2", org.json.JSONArray(acceleration.take(3)))
        .put("worldUp", worldUp?.let { org.json.JSONArray(it.toList()) } ?: JSONObject.NULL)
        .put("gyroRadS", gyro ?: JSONObject.NULL).put("speedMS", speed).put("accuracyM", accuracy)
        .put("profile", profile).put("freshGps", freshGps).put("stationary", stationary).put("offsetReady", offsetReady)
}

/**
 * Dakikalık JSONL dosyaları: `<gün>-<boot>-<dakika>.jsonl`; yeni dakikaya geçince 10 dakikadan eski ve başka boot'a ait dosyalar silinir
 * (boot sayacı sıfırlanınca eski değerler anlamsızdır). Tüm çağrılar kilitli; yazım sensör iş parçacığında, tamponlu.
 */
class RawDiagnostics(private val dir: File, private val keepMinutes: Int = 10) {
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var slot = ""
    private var lastFlush = 0L

    private fun files(): Array<File> = dir.apply { mkdirs() }.listFiles() ?: emptyArray()

    fun close() = synchronized(lock) { closeLocked() }

    private fun closeLocked() { runCatching { writer?.close() }; writer = null; slot = "" }

    /** [atMs] sensör zamanı (ms, boot'tan beri); satır sırası yazım sırasıdır. */
    fun append(day: String, boot: String, atMs: Long, row: JSONObject) = synchronized(lock) {
        val minute = atMs / 60_000
        val next = "$day-$boot-$minute"
        if (slot != next) {
            closeLocked(); slot = next
            files().forEach { f ->
                val n = f.name.substringBeforeLast('.').substringAfterLast('-').toLongOrNull()
                if (!f.name.startsWith("$day-$boot-") || n == null || n < minute - (keepMinutes - 1)) f.delete()
            }
            writer = File(dir, "$next.jsonl").bufferedWriter()
        }
        writer?.write(row.toString()); writer?.newLine()
        if (atMs - lastFlush >= 1_000) { writer?.flush(); lastFlush = atMs }
    }

    /** Gün silinince o güne ait ham dosyalar gider. */
    fun removeDay(day: String) = synchronized(lock) {
        if (slot.startsWith("$day-")) closeLocked()
        files().filter { it.name.startsWith("$day-") }.forEach { it.delete() }
    }

    /** Kapatılınca ya da ölçüm kapanınca tüm ham dosyaları siler. */
    fun clear() = synchronized(lock) { closeLocked(); files().forEach { it.delete() } }

    /** Dosyaları sırayla (en eski önce) [out]'a birleştirir; satır sayısını döndürür. */
    fun export(out: File): Int = synchronized(lock) {
        writer?.flush()
        var rows = 0
        out.bufferedWriter().use { target ->
            files().sortedBy { it.name.substringBeforeLast('.').substringAfterLast('-').toLongOrNull() ?: 0L }.forEach { f ->
                f.forEachLine { target.write(it); target.newLine(); rows++ }
            }
        }
        rows
    }

    /** Diskte tutulan dosya sayısı (ekran bilgisi ve test). */
    fun fileCount(): Int = synchronized(lock) { files().size }
}
