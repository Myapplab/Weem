package com.kuryelog.logic.core

import org.json.JSONObject
import java.io.File

/**
 * `filesDir/crashes.jsonl` (CORE.md 4.1, RS `CrashLog.kt:37-48`): gün klasörü değil, yedek listesinde değil. [LogStore]'un kilidini
 * almaz (çöken iş parçacığı onu tutuyor olabilir); kendi kilidi var.
 */
class CrashFile(filesDir: File) {
    val file = File(filesDir, FILE)
    private val lock = Any()

    /** Var olan dolu satırlar + [j]; [CrashReport.trim] sonrası tümü yeniden yazılır. */
    fun save(j: JSONObject) = synchronized(lock) {
        val lines = (if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()) + j.toString()
        file.writeText(CrashReport.trim(lines).joinToString("\n", postfix = "\n"))
    }

    /** Dosyadaki en yeni ayrıştırılabilir rapor. */
    fun last(): JSONObject? = try {
        if (!file.exists()) null else file.readLines().asReversed().asSequence()
            .mapNotNull { try { JSONObject(it) } catch (_: Exception) { null } }.firstOrNull()
    } catch (_: Exception) { null }

    companion object {
        const val FILE = "crashes.jsonl"
        /** Tercih: gizlenen son çökmenin `at` değeri (Long). */
        const val SEEN_KEY = "crashSeenAt"

        /** Kartta gösterilecek: gizlenmemiş ([CrashReport.unseen]) olanların en yenisi. */
        fun unseen(candidates: List<JSONObject?>, seenAt: Long): JSONObject? =
            candidates.filterNotNull().filter { CrashReport.unseen(it, seenAt) }.maxByOrNull { it.optLong("at") }
    }
}
