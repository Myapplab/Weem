package com.kuryelog.logic.orders.dataset

import com.kuryelog.logic.core.BackupFiles
import com.kuryelog.logic.orders.AnalysisExport
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Günün anonimleştirme sırrı (`DailyLogSecret`, MAP.md 13.6): gün başına kalıcı 32 bayt; geçersiz gün (`../`, `2026-02-30`, `2026-1-1`) hata. */
class DailyLogSecret(private val root: File) {
    @Synchronized
    fun forDay(day: String): ByteArray {
        DayLogSnapshot.checkedDay(day)
        root.mkdirs()
        val f = File(root, day)
        if (f.exists()) return f.readBytes().also { require(it.size == 32) }
        val secret = AnalysisExport.newSecret()
        val tmp = File(root, "$day.tmp")
        tmp.outputStream().use { it.write(secret); it.flush() }
        check(tmp.renameTo(f))
        return secret
    }
}

/**
 * Gün kaydı anlık görüntüsü (Rawstats `LogFeedV3`, MAP.md 13.6): ekran/tıklama satırları müşteri verisi olmadan R18 kuralıyla; her satıra ortak alanlar; izinli dosya adları;
 * manifest (`snapshot` UUID, `files[{name, available, rows, rejectedRows, sha256}]`, `redaction.secretExported=false`). WEEM'de sağlayıcı, sayfa ve imza yok: kaynak
 * doğrudan [build] ile kopyalanır.
 */
object DayLogSnapshot {
    const val SCHEMA = 3

    /** Ekonomi kayıt şartnamesi sürümü (`EconomicsLogs.SPEC_VERSION`; `:orders` `:economics`'e bağlanamaz). */
    const val SPEC_VERSION = "2.1.0"
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private val sources = setOf("kuryeplus", "surus_paneli", "yolgo", "manual")
    private val DAY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    /** İzinli dosya adları: analiz dışa aktarmasındaki bütün ekonomi, scooter ve günlük dosyaları + `economics-payouts.jsonl`. */
    val names: Set<String> = (
        BackupFiles.logNames.filter {
            it.startsWith("economics-") || it.startsWith("scooter") ||
                it in setOf("online-sessions.jsonl", "platforms.jsonl", "events.jsonl", "errors.jsonl", "gps.csv", "screens.jsonl")
        } + "economics-payouts.jsonl"
        ).toSortedSet()

    fun checkedDay(day: String): String {
        require(DAY.matches(day))
        LocalDate.parse(day)
        return day
    }

    /** [source] gün klasörü kopyası; çıktı dosyaları [out]'a, manifest `feed-manifest.json`'a ve dönüş değerine. Geçersiz gün hata. */
    fun build(source: File, out: File, day: String, secret: ByteArray, version: String, wallMs: Long, monoNs: Long): JSONObject {
        checkedDay(day)
        out.mkdirs()
        val token = UUID.randomUUID().toString()
        val export = AnalysisExport(secret)
        val files = JSONArray()
        for (name in names) {
            val input = File(source, name)
            val output = File(out, name)
            var rows = 0
            var sourceRows = 0
            var rejected = 0
            if (name == "gps.csv") {
                val redacted = File(out, ".gps")
                if (input.isFile) input.bufferedReader().use { r -> redacted.bufferedWriter().use { w -> export.redact(AnalysisExport.Kind.GPS, r, w) } }
                output.bufferedWriter().use { w ->
                    if (redacted.exists()) {
                        redacted.bufferedReader().use { r ->
                            val header = r.readLine()
                            if (header != null) {
                                val cols = header.split(',')
                                w.append(header).append(",wall_ms,mono_ns,schema_version,source,scooter_id,wall_ms_source,mono_ns_source,scooter_id_source,power_verified")
                                    .append(cols.joinToString(",", prefix = ",") { "${it}_source" }).append('\n')
                                val ti = cols.indexOf("t")
                                r.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                    val fields = line.split(',')
                                    val at = fields.getOrNull(ti)?.let(::parseTime)
                                    w.append(line).append(",${at ?: ""},,3,kuryeplus,,${if (at == null) "unavailable" else "legacy_t"},unavailable,unavailable,false")
                                        .append(cols.indices.joinToString(",", prefix = ",") { if (fields.getOrNull(it).isNullOrBlank()) "unavailable" else "legacy_csv" }).append('\n')
                                    rows++
                                }
                            }
                        }
                    }
                }
                sourceRows = rows
                redacted.delete()
            } else {
                val kind = requireNotNull(AnalysisExport.kindOf("$day/$name"))
                output.bufferedWriter().use { w ->
                    if (input.isFile) {
                        input.bufferedReader().use { r ->
                            r.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                sourceRows++
                                val j = runCatching { JSONObject(line) }.getOrNull()
                                val row = j?.let { runCatching { export.row(kind, it) }.getOrNull() }
                                if (row == null) {
                                    rejected++
                                } else {
                                    common(row, requireNotNull(j), name)
                                    w.append(row.toString()).append('\n')
                                    rows++
                                }
                            }
                        }
                    }
                }
            }
            files.put(
                JSONObject().put("name", name).put("available", input.isFile).put("bytes", output.length()).put("size", output.length())
                    .put("rows", rows).put("sourceRows", sourceRows).put("rejectedRows", rejected).put("sha256", sha256(output)),
            )
        }
        val manifest = JSONObject().put("schema_version", SCHEMA).put("flowVersion", 3).put("day", day).put("snapshot", token).put("files", files)
            .put("wall_ms", wallMs).put("mono_ns", monoNs).put("source", "kuryeplus")
            .put("scooter_id", JSONObject.NULL).put("scooter_id_source", "unavailable").put("power_verified", false).put("specVersion", SPEC_VERSION)
            .put("producer", JSONObject().put("package", "com.kuryelog.app").put("versionName", version))
            .put("redaction", JSONObject().put("locDecimals", 3).put("secretExported", false).put("pseudonymScope", "day"))
        File(out, "feed-manifest.json").writeText(manifest.toString())
        return manifest
    }

    /** Satıra ortak alanlar (`wall_ms`, `mono_ns`, `schema_version` 3, `source`, `power_verified` false, iç içe null bayrakları); var olan saatler ve scooter kimliği korunur. */
    fun common(row: JSONObject, original: JSONObject, fileName: String): JSONObject {
        val wall = (original.opt("wall_ms") as? Number)?.toLong()
        val legacyTime = (original.opt("t") as? String)?.let(::parseTime)
        val mono = (original.opt("mono_ns") as? Number)?.toLong()
        val scooter = (original.opt("scooter_id") as? String)?.takeIf { it.isNotBlank() }
        val rawSource = original.opt("source") as? String
        val source = rawSource?.takeIf { it in sources } ?: when {
            fileName.startsWith("scooter") || fileName == "economics-scooter.jsonl" -> "surus_paneli"
            fileName == "economics-payouts.jsonl" -> "manual"
            else -> "kuryeplus"
        }
        // Ekran edinim kanıtı korunur, ortak kayıt kaynağı sayılmaz.
        if (rawSource in setOf("ocr", "accessibility", "notification")) row.put("capture_source", rawSource)
        row.put("wall_ms", wall ?: legacyTime ?: JSONObject.NULL).put("mono_ns", mono ?: JSONObject.NULL).put("schema_version", SCHEMA).put("source", source)
            .put("scooter_id", scooter ?: JSONObject.NULL).put("power_verified", false)
            .put("wall_ms_source", if (wall != null) "record" else if (legacyTime != null) "legacy_t" else "unavailable")
            .put("mono_ns_source", if (mono != null) "record" else "unavailable")
            .put("scooter_id_source", if (scooter != null) original.optString("scooter_id_source", "record") else "unavailable")
        return DatasetContract.flagUnknowns(row)
    }

    /** Kaynağın yalnız ilk [size] baytını kopyalar (sonraki eklemeler alınmaz); kaynak kısaysa `IllegalStateException`. */
    fun copyPrefix(input: InputStream, out: OutputStream, size: Long) {
        require(size >= 0)
        var remaining = size
        val buffer = ByteArray(65536)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(n > 0) { "source truncated during snapshot" }
            out.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun parseTime(value: String): Long? = runCatching { OffsetDateTime.parse(value, timestamp).toInstant().toEpochMilli() }.getOrNull()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32_768)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
