package com.kuryelog.logic.map.logs

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.LocalDate
import java.time.ZoneId

/**
 * Kayıt klasöründeki bir dosya (Dart `RideLogEntry`, MAP.md 12.4): [kind] `day` (günlük) | `report` (gün özeti `.gun.json`) | `ride` (sürüş) | `route` (rota);
 * [startedAtMs] sürüş/rota damgası ya da raporun `_HHMM`'i, gün dosyasında null.
 */
class RideLogEntry(val file: File, val day: String, val kind: String, val sizeBytes: Long, val startedAtMs: Long?) {
    val name: String get() = file.name
}

/** `log_store.dart` (MAP.md 12.4): ad kalıbı, liste, gruplama, silme, dışa aktarma. `java.io` (Android'siz). */
class RideLogStore(private val dir: File, private val zone: ZoneId) {
    /** Kalıba uymayan dosyalar listelenmez; sıra ada göre azalan. */
    fun entries(): List<RideLogEntry> {
        val files = dir.listFiles() ?: return emptyList()
        val out = ArrayList<RideLogEntry>()
        for (f in files) {
            if (!isRegularFile(f)) continue
            val m = NAME.matchEntire(f.name) ?: continue
            val stamp = (m.groups[2] ?: m.groups[3])?.value?.toLongOrNull()
            val kind = when {
                m.groups[3] != null -> "route"
                stamp != null -> "ride"
                f.name.endsWith(".gun.json") -> "report"
                else -> "day"
            }
            val clock = REPORT_CLOCK.find(f.name)
            val started = stamp ?: if (clock != null) reportStart(m.groupValues[1], clock.groupValues[1].toInt(), clock.groupValues[2].toInt()) else null
            out += RideLogEntry(f, m.groupValues[1], kind, f.length(), started)
        }
        return out.sortedByDescending { it.name }
    }

    private fun reportStart(day: String, hour: Int, minute: Int): Long? = try {
        LocalDate.parse(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private fun isRegularFile(f: File) = Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    /** Yalnız bu klasördeki, kalıba uyan, bağlantı olmayan dosya; değilse `IllegalArgumentException("Geçersiz kayıt dosyası")`. */
    fun delete(entry: RideLogEntry) {
        val expected = File(dir, entry.name)
        if (!NAME.matches(entry.name) || expected.absoluteFile != entry.file.absoluteFile || !isRegularFile(entry.file)) {
            throw IllegalArgumentException("Geçersiz kayıt dosyası")
        }
        entry.file.delete()
    }

    /** O günün bütün kayıtları; gün `YYYY-MM-DD` değilse hata. */
    fun deleteDay(day: String) {
        require(DAY.matches(day)) { "Invalid day" }
        for (e in entries().filter { it.day == day }) delete(e)
    }

    /** `{"format": "duzyol-journal-v1", "day": gün|null, "files": [{name, day, kind, content}]}`; `content` ham metin. */
    fun export(targetDir: File, day: String? = null): File {
        val files = entries().filter { day == null || it.day == day }
        targetDir.mkdirs()
        val output = File(targetDir, "WEEM_surus_${day ?: "tum_kayitlar"}.json")
        output.bufferedWriter().use { w ->
            w.write("{\"format\":\"duzyol-journal-v1\",\"day\":${if (day == null) "null" else JSONObject.quote(day)},\"files\":[")
            files.forEachIndexed { i, e ->
                if (i > 0) w.write(",")
                w.write(JSONObject().put("name", e.name).put("day", e.day).put("kind", e.kind).put("content", e.file.readText()).toString())
            }
            w.write("]}")
        }
        return output
    }

    companion object {
        private val NAME = Regex("^(\\d{4}-\\d{2}-\\d{2})(?:\\.jsonl|(?:_\\d{4})?\\.gun\\.json|_ride_(\\d+)\\.jsonl|_route_(\\d+)\\.json)$")
        private val REPORT_CLOCK = Regex("_(\\d{2})(\\d{2})\\.gun\\.json$")
        private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        /** Ad kayıt kalıbına uyuyor mu (içe aktarma, A7.1). */
        fun isLogName(name: String): Boolean = NAME.matches(name)

        /** Aktif sürüş dosyası adı kalıbı (`ride.active.v1`). */
        val RIDE_FILE = Regex("^\\d{4}-\\d{2}-\\d{2}_ride_\\d+\\.jsonl$")

        /** Güne göre azalan; her gün için kayıtlar listeleme sırasında. */
        fun groupByDay(entries: List<RideLogEntry>): Map<String, List<RideLogEntry>> {
            val days = LinkedHashMap<String, MutableList<RideLogEntry>>()
            for (e in entries) days.getOrPut(e.day) { ArrayList() } += e
            return days.entries.sortedByDescending { it.key }.associate { it.key to it.value }
        }
    }
}
