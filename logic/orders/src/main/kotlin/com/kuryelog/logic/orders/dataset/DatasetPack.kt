package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import java.io.File

/** Gün toplama, eşleştirme, mühür (`DatasetPack.kt`, MAP.md 13.7; saf: `java.io`, `java.util.zip`, `MessageDigest`). İskelet (A6.2b): gövdeler A6.9'da. */
object DatasetPack {
    /** Tam anahtar + 5 sn yedek bire bir; belirsiz yedek tahmin edilmez; ücret yok ya da >5 sn eşleşmez. Alanlar `matched`, `onlyRawstats`, `onlyYolgo`, `ratio`, `ambiguousFallback`. */
    fun match(rawstats: Map<String, JSONObject>, yolgo: Map<String, JSONObject>): JSONObject = TODO("A6.9")

    fun sha(file: File): String = TODO("A6.9")

    fun lineCount(file: File): Long = TODO("A6.9")

    /** Yarıda kalan ham klasör değişimini onarır (`.raw-previous` → `rawstats`). */
    fun recoverRaw(dayDir: File): Unit = TODO("A6.9")

    /** Aynı gün yeniden toplama yalnız en yeni dosyaları tutar. */
    fun replaceRaw(dayDir: File, stage: File): Unit = TODO("A6.9")
}
