package com.kuryelog.logic.core

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * ZIP yedeğini içe aktarma (CORE.md 5.3, RS `LogImporter.kt:10-66`). Önce her girdi geçici klasöre açılıp denetlenir; biri bile
 * geçersizse hiçbir kayıt değişmez. Hepsi geçerliyse gün dosyaları satır satır birleşir ([BackupFiles.mergeLines]),
 * `restaurants.json` anahtar başına `n`'i büyük olanı alır. Vardiya açıkken başlatmamak ve sürerken `importing` bayrağı çağıranın işi.
 */
object LogImporter {
    const val MAX_FILES = 2000
    const val MAX_FILE_BYTES = 32L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    const val GPS_HEADER = "t,lat,lon,acc_m,speed_ms,bearing,alt_m"
    const val RESTAURANTS = "restaurants.json"

    /** İçe aktarılan dosya adları (`YYYY-MM-DD/<ad>` ya da `restaurants.json`). */
    fun restore(input: InputStream, filesDir: File, logsRoot: File, cacheDir: File): List<String> {
        val staging = File(cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            val files = stage(input, staging)
            for ((name, source) in files) {
                val dest = if (name == RESTAURANTS) File(filesDir, name) else File(logsRoot, name)
                dest.parentFile?.mkdirs()
                val text = if (name == RESTAURANTS) mergeRestaurants(if (dest.exists()) dest.readText() else null, source.readText())
                else BackupFiles.mergeLines(if (dest.exists()) dest.readText() else "", source.readText())
                val temp = File(dest.parentFile, dest.name + ".restore")
                temp.writeText(text)
                require(temp.renameTo(dest)) { "Cannot save restored file" }
            }
            return files.keys.toList()
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Bütün girdileri açar ve denetler; kayıtlara dokunmaz. */
    private fun stage(input: InputStream, staging: File): Map<String, File> {
        val files = LinkedHashMap<String, File>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) { zip.closeEntry(); continue }
                val name = BackupFiles.checkedPath(entry.name)
                require(!files.containsKey(name)) { "Duplicate backup entry" }
                require(files.size < MAX_FILES) { "Backup has too many files" }
                val file = File(staging, name)
                file.parentFile?.mkdirs()
                var size = 0L
                file.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        size += n
                        total += n
                        require(size <= MAX_FILE_BYTES && total <= MAX_TOTAL_BYTES) { "Backup is too large" }
                        out.write(buffer, 0, n)
                    }
                }
                if (name.endsWith(".jsonl")) file.forEachLine { if (it.isNotBlank()) JSONObject(it) }
                if (name == RESTAURANTS) JSONObject(file.readText())
                if (name.endsWith(".csv")) require(file.useLines { it.firstOrNull() } == GPS_HEADER) { "Invalid GPS file" }
                files[name] = file
                zip.closeEntry()
            }
        }
        require(files.isNotEmpty()) { "Empty backup" }
        return files
    }

    /** Restoran öğrenmesi: anahtar başına sayacı (`n`) büyük olan kalır; nesne olmayan gelen değer atlanır. */
    fun mergeRestaurants(current: String?, incoming: String): String {
        val result = if (current != null) JSONObject(current) else JSONObject()
        val inc = JSONObject(incoming)
        for (key in inc.keys()) {
            val old = inc.optJSONObject(key) ?: continue
            val saved = result.optJSONObject(key)
            if (saved == null || old.optInt("n") > saved.optInt("n")) result.put(key, old)
        }
        return result.toString()
    }
}
