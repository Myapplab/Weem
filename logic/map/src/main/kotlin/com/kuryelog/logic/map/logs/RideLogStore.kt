package com.kuryelog.logic.map.logs

import java.io.File
import java.time.ZoneId

/**
 * Kayıt klasöründeki bir dosya (Dart `RideLogEntry`, MAP.md 12.4): [kind] `day` (günlük) | `report` (gün özeti `.gun.json`) | `ride` (sürüş) | `route` (rota);
 * [startedAtMs] sürüş/rota damgası ya da raporun `_HHMM`'i, gün dosyasında null.
 */
class RideLogEntry(val file: File, val day: String, val kind: String, val sizeBytes: Long, val startedAtMs: Long?) {
    val name: String get() = file.name
}

/** `log_store.dart` (MAP.md 12.4): ad kalıbı, liste, gruplama, silme, dışa aktarma. `java.io` (Android'siz). İskelet (A6.2b): gövdeler A6.9'da. */
class RideLogStore(private val dir: File, private val zone: ZoneId) {
    /** Kalıba uymayan dosyalar listelenmez; sıra ada göre azalan. */
    fun entries(): List<RideLogEntry> = TODO("A6.9")

    /** Yalnız bu klasördeki, kalıba uyan, bağlantı olmayan dosya; değilse `IllegalArgumentException("Geçersiz kayıt dosyası")`. */
    fun delete(entry: RideLogEntry): Unit = TODO("A6.9")

    /** `{"format": "duzyol-journal-v1", "day": gün|null, "files": [{name, day, kind, content}]}`; `content` ham metin. */
    fun export(targetDir: File, day: String? = null): File = TODO("A6.9")

    companion object {
        /** Güne göre azalan; her gün için kayıtlar listeleme sırasında. */
        fun groupByDay(entries: List<RideLogEntry>): Map<String, List<RideLogEntry>> = TODO("A6.9")
    }
}
