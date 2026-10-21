package com.kuryelog.logic.core

import org.json.JSONObject

/**
 * R20 çökme raporu (CORE.md 4.1): süreci kapatan her istisna ve çizilemeyen sayfa tek satır JSON olarak yazılır; ana sayfa
 * gizlenene dek son çökmeyi gösterir (kopyala/paylaş). Yalnız istisna girer: mesajlar ayrıştırılan şeyi (ekran satırı, JSON) alıntılayabilir,
 * bu yüzden her mesaj satırı kısaltılır ve telefonu gizlenir (A10.2, [Phones]).
 */
object CrashReport {
    const val STACK_CHARS = 8_000
    const val MESSAGE_CHARS = 200

    fun json(at: Long, version: String, thread: String, e: Throwable): JSONObject = JSONObject()
        .put("at", at).put("version", version).put("thread", thread)
        .put("error", e.javaClass.name).put("message", Phones.safeMessage(e.message, MESSAGE_CHARS))
        .put("stack", stack(e))

    /** The stack with its causes; the header lines ("X: message", "Caused by: …") are cut to [MESSAGE_CHARS]. */
    fun stack(e: Throwable): String = e.stackTraceToString().lineSequence()
        .map { if (it.startsWith("\t") || it.startsWith(" ")) it else Phones.redact(it).take(MESSAGE_CHARS) }
        .joinToString("\n").take(STACK_CHARS)

    private val appFrame = Regex("""at (?:com\.kuryelog\.(?:app|logic)|az\.ninebot\.logger)\.[^(\s]*\(([^)]+)\)""")
    /** The first Rawstats or Sürüş Paneli frame ("MainActivity.kt:412"), where the fix goes. */
    fun appFrame(stack: String): String? = appFrame.find(stack)?.groupValues?.get(1)

    /** One line for the card: "IllegalStateException: … (MainActivity.kt:412)". */
    fun summary(j: JSONObject): String {
        val message = j.optString("message").takeIf { it.isNotBlank() }?.let { ": " + it.take(120) } ?: ""
        return j.optString("error").substringAfterLast('.') + message + (appFrame(j.optString("stack"))?.let { " ($it)" } ?: "")
    }

    /** What the courier copies and sends. */
    fun text(j: JSONObject, time: String): String =
        "WEEM ${j.optString("version")} · $time · ${j.optString("thread")}\n${j.optString("stack")}"

    /** Shown until hidden; a newer crash shows again. */
    fun unseen(j: JSONObject?, seenAt: Long): Boolean = j != null && j.optLong("at", 0L) > seenAt

    /** Android's exit reasons (ApplicationExitInfo, API 30) that no Java handler sees; a Java crash is in our own report. */
    const val EXIT_NATIVE = 5
    const val EXIT_ANR = 6
    const val EXIT_INIT_FAILURE = 7
    const val EXIT_MAX_AGE_MS = 3 * 86_400_000L
    fun exitLabel(reason: Int): String? = when (reason) {
        EXIT_NATIVE -> "NativeCrash"; EXIT_ANR -> "ANR"; EXIT_INIT_FAILURE -> "InitializationFailure"; else -> null
    }
    /** Shown for those reasons only, and only for the last few days (Android keeps older records too). */
    fun exitShown(reason: Int, at: Long, now: Long): Boolean = exitLabel(reason) != null && now - at in 0..EXIT_MAX_AGE_MS
    /** The same shape as [json], so the card and the copied text treat both alike. */
    fun exitJson(at: Long, version: String, reason: Int, description: String?, process: String?): JSONObject {
        val message = Phones.safeMessage(description, MESSAGE_CHARS)
        return JSONObject().put("at", at).put("version", version).put("thread", "android").put("exit", reason)
            .put("error", exitLabel(reason) ?: "Exit$reason").put("message", message)
            .put("stack", "Android exit reason $reason (${exitLabel(reason)}) · ${process ?: "?"}\n$message")
    }

    /** The crash file keeps the newest [keep] reports once it passes [maxChars]. */
    fun trim(lines: List<String>, maxChars: Int = 200_000, keep: Int = 10): List<String> =
        if (lines.sumOf { it.length + 1 } <= maxChars) lines else lines.takeLast(keep)
}
