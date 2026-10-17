package com.kuryelog.logic.core

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Bugünün biten vardiya dakikaları toplamı (CORE.md 1.8). Rozet 500 ms'de bir sorar ve `events.jsonl` vardiya boyunca uzar:
 * değişmemiş dosya yeniden okunmaz, uzayan dosya kalınan yerden (tam satırlar) okunur, yalnız `shift_end` satırları ayrıştırılır.
 * Başka dosya (yeni gün), kısalan ya da yerinde yeniden yazılan dosya baştan okunur.
 */
class EndedShiftMinutes {
    private var path: String? = null
    private var modified = 0L
    /** File length at the last read, so an unfinished last line is not read again every 500 ms. */
    private var size = 0L
    /** Bytes of complete lines already summed into [value]. */
    private var offset = 0L
    private var value = 0.0
    var reads = 0
        private set

    fun of(f: File): Double {
        if (!f.exists()) { clear(); return 0.0 }
        val p = f.absolutePath; val len = f.length(); val mod = f.lastModified()
        if (p == path && len == size && mod == modified) return value
        // Another file, or the same one shorter or rewritten in place: sum again from the start.
        if (p != path || len <= size || len < offset) { path = p; offset = 0; value = 0.0 }
        size = len; modified = mod
        reads++
        FileInputStream(f).use { input ->
            var skipped = 0L
            while (skipped < offset) { val s = input.skip(offset - skipped); if (s <= 0) break; skipped += s }
            val line = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var pos = skipped
            while (true) {
                val n = input.read(buf); if (n < 0) break
                var start = 0
                for (i in 0 until n) if (buf[i] == NL) {
                    line.write(buf, start, i - start); add(line.toString(Charsets.UTF_8.name())); line.reset()
                    start = i + 1; offset = pos + i + 1
                }
                line.write(buf, start, n - start) // an unfinished last line is read again next time
                pos += n
            }
        }
        return value
    }

    private fun add(line: String) {
        if (!line.contains("shift_end")) return
        try {
            val j = JSONObject(line)
            if (j.optString("type") == "shift_end") value += j.num("min") ?: 0.0
        } catch (_: JSONException) { /* bozuk satır atlanır */ }
    }

    fun clear() { path = null; modified = 0L; size = 0L; offset = 0L; value = 0.0 }

    private fun JSONObject.num(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { it.isFinite() } else null

    private companion object { const val NL = '\n'.code.toByte() }
}
