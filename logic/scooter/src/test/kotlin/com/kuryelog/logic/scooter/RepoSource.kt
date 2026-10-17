package com.kuryelog.logic.scooter

import java.io.File

/** Kaynak taraması testleri için WEEM deposu (Gradle testi `logic/scooter`'da çalıştırır; kök `AGENTS.md`'nin olduğu yer). */
object RepoSource {
    val root: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }.firstOrNull { File(it, "AGENTS.md").isFile && File(it, "logic").isDirectory }
            ?: error("WEEM kökü bulunamadı: ${File(".").absolutePath}")
    }

    /** `:scooter`'ın bütün üretim kaynakları: saf `logic/scooter` ve Android `scooter`. */
    fun scooterSources(): List<File> = listOf("logic/scooter/src/main", "scooter/src/main").map { File(root, it) }
        .flatMap { d -> d.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }

    /** Satır yorumları ve KDoc/blok yorum satırları çıkarılmış kod satırları. */
    fun code(f: File): List<String> = f.readLines().filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
}
