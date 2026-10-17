package com.kuryelog.logic.capture

import java.util.Locale

// Rawstats RecordingPolicy.kt:64-80, 99-111 @ 6388c04 (CAPTURE.md 2.9). MotionGate ve OfferIdentity WEEM'de yazılmaz; ManualSessionPolicy logic/core'da.
object OfferRules {
    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("[\\s\\p{Z}]+"), " ")
    private val accept = setOf("accept", "accept order", "accept delivery", "qəbul", "qəbul et",
        "qəbul etmək", "kabul", "kabul et", "принять", "принять заказ")
    private val history = setOf("history", "order history", "delivery history", "completed orders",
        "past orders", "geçmiş", "sipariş geçmişi", "tamamlanan siparişler", "tarixçə",
        "sifariş tarixçəsi", "история", "история заказов", "завершённые заказы", "завершенные заказы")

    fun isAccept(s: String): Boolean {
        val label = norm(s).replace(Regex("(?:\\s*\\(\\d+\\s*(?:s|sec)?\\)|\\s*\\[\\d+\\s*(?:s|sec)?\\])$"), "").trim()
        return label in accept
    }

    fun isHistory(texts: List<String>) = texts.any { norm(it) in history }
    fun isOffer(actions: List<String>, texts: List<String>, hasDetails: Boolean) =
        hasDetails && actions.any { isAccept(it) } && !isHistory(texts)
}

class ScreenDeduper {
    private val previous = HashMap<String, Set<String>>()
    private val clock = Regex("${CourierParser.WORD_START}\\d{1,2}:\\d{2}${CourierParser.WORD_END}")
    private val metric = Regex("${CourierParser.WORD_START}\\d+(?:[.,]\\d+)?[\\s\\p{Z}]*(?:km|min|dk|dəq)${CourierParser.WORD_END}", RegexOption.IGNORE_CASE)
    private val countdown = Regex("^\\d+[\\s\\p{Z}]*(?:s|sec|seconds|san|с)$", RegexOption.IGNORE_CASE)
    fun shouldSave(pkg: String, texts: List<String>): Boolean {
        val signature = texts.map { it.trim() }.filter { it.isNotEmpty() && !countdown.matches(it) }
            .map { metric.replace(clock.replace(it, "<time>"), "<metric>") }.toSet()
        if (signature.isEmpty() || previous[pkg] == signature) return false
        previous[pkg] = signature
        return true
    }
}
