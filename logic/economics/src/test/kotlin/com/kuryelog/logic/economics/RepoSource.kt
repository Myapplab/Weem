package com.kuryelog.logic.economics

import java.io.File

/** "Kaynak taraması" testleri (CORE.md 6) için WEEM deposu: Gradle testi `logic/economics`'da çalıştırır; kök `AGENTS.md`'nin olduğu yer. */
object RepoSource {
    val root: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }.firstOrNull { File(it, "AGENTS.md").isFile && File(it, "logic").isDirectory }
            ?: error("WEEM kökü bulunamadı: ${File(".").absolutePath}")
    }
    fun file(path: String): File = File(root, path).also { require(it.isFile) { "yok: $path" } }
    fun text(path: String): String = file(path).readText()
    /** Uygulamanın bütün Kotlin kaynakları (Android modülleri ve `logic/`), testler ve derleme çıktısı hariç. */
    fun mainKotlin(): List<File> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".git" && it.name != "test" }
        .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.invariantSeparatorsPath }.toList()
    fun body(text: String, from: String, to: String): String {
        val start = text.indexOf(from).also { require(it >= 0) { "yok: $from" } }
        return text.substring(start, text.indexOf(to, start).also { require(it >= 0) { "yok: $to" } })
    }
}
