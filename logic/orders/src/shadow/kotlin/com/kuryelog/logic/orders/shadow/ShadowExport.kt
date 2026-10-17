package com.kuryelog.logic.orders.shadow

import com.kuryelog.logic.core.BackupFiles
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * Bir dışa aktarmanın gün dosyaları (A3.9; CORE.md 5.3, VERI_SOZLESMESI 7.1): ZIP ya da klasör, `YYYY-MM-DD/<logNames>` ve kökte
 * `restaurants.json` (`BackupFiles.checkedPath` biçimi). Rawstats `tools/analysis/kl_export.py` `classify` gibi bir üst klasöre
 * (ör. `logs/`) izin verir; tanınmayan girdi [ignored]'a gider. Aynı dosya iki kez gelirse satırları birleşir (`BackupFiles.mergeLines`).
 */
class ShadowExport(val name: String, files: Map<String, String>) {
    /** gün → dosya adı → metin. */
    val days: Map<String, Map<String, String>>
    val root: Map<String, String>
    val ignored: List<String>
    /** Dosya başına JSON olmayan satır sayısı ("gün/ad" → n). */
    val damaged = LinkedHashMap<String, Int>()

    init {
        val d = sortedMapOf<String, MutableMap<String, String>>(); val r = sortedMapOf<String, String>(); val ign = ArrayList<String>()
        for ((path, text) in files) {
            val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            val file = parts.lastOrNull()
            val dayDir = parts.getOrNull(parts.size - 2)
            when {
                parts.isEmpty() || parts.any { it == ".." } -> ign += path
                parts.size in 2..3 && file in BackupFiles.logNames && dayDir != null && isDay(dayDir) ->
                    d.getOrPut(dayDir) { sortedMapOf() }.merge(file!!, text) { a, b -> BackupFiles.mergeLines(a, b) }
                parts.size <= 2 && file == RESTAURANTS -> r[file] = text
                else -> ign += path
            }
        }
        days = d; root = r; ignored = ign
    }

    fun text(day: String, file: String): String = days[day]?.get(file) ?: ""
    fun has(day: String, file: String) = days[day]?.containsKey(file) == true

    /** Dosyanın JSON nesne satırları (bozuk satır sayılır, atlanır). */
    fun rows(day: String, file: String): List<JSONObject> = rows.getOrPut("$day/$file") {
        text(day, file).lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            try { JSONObject(line) } catch (_: Exception) { damaged.merge("$day/$file", 1, Int::plus); null }
        }.toList()
    }
    private val rows = HashMap<String, List<JSONObject>>()

    companion object {
        const val RESTAURANTS = "restaurants.json"
        /** Rawstats `LogImporter` sınırları (CORE.md 5.3): dosya ≤32 MB, toplam ≤256 MB. */
        const val MAX_FILE = 32L * 1024 * 1024
        const val MAX_TOTAL = 256L * 1024 * 1024

        private fun isDay(s: String) = Regex("\\d{4}-\\d{2}-\\d{2}").matches(s) && runCatching { LocalDate.parse(s) }.isSuccess

        /** ZIP dosyası ya da dışa aktarma klasörü. */
        fun read(file: File): ShadowExport = when {
            file.isDirectory -> ShadowExport(file.name, file.walkTopDown().filter { it.isFile }.associate { f ->
                require(f.length() <= MAX_FILE) { "Dosya çok büyük: $f" }
                f.relativeTo(file).invariantSeparatorsPath to f.readText(Charsets.UTF_8)
            })
            file.isFile -> file.inputStream().use { zip(file.name, it) }
            else -> throw IllegalArgumentException("Bulunamadı: $file")
        }

        fun zip(name: String, input: InputStream): ShadowExport {
            val out = LinkedHashMap<String, String>(); var total = 0L
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val bytes = z.readNBytes((MAX_FILE + 1).toInt())
                    require(bytes.size <= MAX_FILE) { "ZIP girdisi çok büyük: ${e.name}" }
                    total += bytes.size; require(total <= MAX_TOTAL) { "ZIP çok büyük: $name" }
                    val text = String(bytes, Charsets.UTF_8)
                    out.merge(e.name, text) { a, b -> BackupFiles.mergeLines(a, b) }
                }
            }
            return ShadowExport(name, out)
        }
    }
}
