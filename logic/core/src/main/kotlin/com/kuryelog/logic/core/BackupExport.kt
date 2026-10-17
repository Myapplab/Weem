package com.kuryelog.logic.core

import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tam yedek ZIP'i (R17; RS `MainActivity.exportLogs/stage`, `:1557-1610`). Girdiler `YYYY-MM-DD/<logNames>` ve yalnız bütün günlerin dışa
 * aktarmasında kökte `restaurants.json`; işaret dosyası yok (Rawstats biçimi). [LogImporter] (Rawstats `LogImporter` kuralları) ve
 * `tools/analysis/kl_export.py` aynı ZIP'i okur. Gün sırları (`noBackupFilesDir`), `courier.db`, tercihler ve tanınmayan dosyalar hiç girmez:
 * yalnız [DailyRecords.files] (= [BackupFiles.logNames]) ve `restaurants.json` kopyalanır.
 */
object BackupExport {
    /** Kullanıcıya görünen ad ve İndirilenler klasörü (RS `APP_NAME = "Rawstats"`; WEEM'de "WEEM": iki uygulamanın ZIP'leri karışmaz). */
    const val APP_NAME = "WEEM"
    const val FOLDER = APP_NAME
    const val RESTAURANTS = "restaurants.json"
    private val stamp = DateTimeFormatter.ofPattern("HHmmssSSS", Locale.US)

    /** RS `${APP_NAME}_${date ?: "all"}_${filename?.substringBefore('.') ?: "logs"}_$stamp.zip`. */
    fun fileName(date: String?, file: String?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${APP_NAME}_${date ?: "all"}_${file?.substringBefore('.') ?: "logs"}_${stamp(nowMs, zone)}.zip"

    /** RS `exportAnalysis`: `${APP_NAME}_analiz_anonim_<bugün>_$stamp.zip`. */
    fun analysisName(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${APP_NAME}_analiz_anonim_${LogTime.day(nowMs, zone)}_${stamp(nowMs, zone)}.zip"

    private fun stamp(ms: Long, zone: ZoneId) = stamp.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** Dışa aktarılacak kayıt yok (RS `toast_no_logs`). */
    class NoLogs : IllegalStateException("no logs")

    /**
     * RS `stage`: [date] günün (null = bütün günler; [file] ile tek dosya) kayıtlarını ve bütün günlerde `restaurants.json`'u [staging]'e kopyalar.
     * Yazanla aynı kilit altında çağrılmalı (RS `synchronized(Store)`, `importing` iken değil). Dönen liste ZIP girdileri (ada göre sıralı).
     */
    fun stage(logsRoot: File, filesDir: File, date: String?, file: String?, staging: File): List<Pair<String, File>> {
        val records = DailyRecords(logsRoot)
        val files = if (date == null) records.days().flatMap { it.files } else records.files(date).filter { file == null || it.name == file }
        if (files.isEmpty()) throw NoLogs()
        for (f in files) { val copy = File(staging, f.relativeTo(logsRoot).invariantSeparatorsPath); copy.parentFile?.mkdirs(); f.copyTo(copy) }
        if (date == null) { val rf = File(filesDir, RESTAURANTS); if (rf.isFile) rf.copyTo(File(staging, RESTAURANTS)) }
        return entries(staging)
    }

    /** [staging] altındaki dosyalar: ZIP adı (`/` ayraçlı göreli yol) → dosya, ada göre sıralı. */
    fun entries(staging: File): List<Pair<String, File>> =
        staging.walkTopDown().filter { it.isFile }.map { it.relativeTo(staging).invariantSeparatorsPath to it }.sortedBy { it.first }.toList()

    /** RS `exportLogs`: her girdi değişmeden; [out] sonunda kapanır. */
    fun zip(entries: List<Pair<String, File>>, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            for ((name, f) in entries) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }
    }
}
