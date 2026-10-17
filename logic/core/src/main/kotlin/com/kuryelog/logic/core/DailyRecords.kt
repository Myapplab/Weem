package com.kuryelog.logic.core

import java.io.File
import java.time.LocalDate

/** Gün kayıtları listesi, arşiv işaretleri, silme (CORE.md 5.3). Arşiv yalnız liste görünürlüğünü değiştirir; kayıtlar dışa aktarma ve istatistik için durur. */
class DailyRecords(private val root: File) {
    data class Day(val date: String, val bytes: Long, val archived: Boolean, val files: List<File>)
    private fun directory(date: String): File {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date))
        LocalDate.parse(date)
        return File(root, date)
    }
    fun days(): List<Day> = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
        try {
            directory(dir.name)
            val files = files(dir.name)
            if (files.isEmpty()) null else Day(dir.name, files.sumOf { it.length() }, File(dir, ".archived").exists(), files)
        } catch (_: Exception) { null }
    }.sortedByDescending { it.date }
    fun files(date: String): List<File> = directory(date).listFiles().orEmpty()
        .filter { it.isFile && it.name in BackupFiles.logNames }.sortedBy { it.name }
    private fun file(date: String, name: String): File {
        require(name in BackupFiles.logNames)
        return File(directory(date), name)
    }
    fun archived(date: String, name: String): Boolean {
        file(date, name)
        return File(directory(date), ".archived-$name").exists()
    }
    fun archive(date: String, name: String? = null, enabled: Boolean) {
        val dir = directory(date)
        require(dir.exists())
        if (name != null) file(date, name)
        val marker = File(dir, if (name == null) ".archived" else ".archived-$name")
        if (enabled) marker.writeText("") else if (marker.exists()) check(marker.delete())
    }
    fun delete(date: String, name: String? = null) {
        val dir = directory(date)
        if (name == null) {
            // Delete recognized logs and our markers only; never recurse through unknown files.
            files(date).forEach { check(it.delete()) }
            dir.listFiles().orEmpty().filter { it.name == ".archived" || it.name.removePrefix(".archived-") in BackupFiles.logNames }
                .forEach { check(it.delete()) }
            if (dir.listFiles().orEmpty().isEmpty()) dir.delete()
        } else {
            val f = file(date, name)
            if (f.exists()) check(f.delete())
            File(dir, ".archived-$name").delete()
        }
    }
}
