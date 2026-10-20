package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Günün anonimleştirme sırrı (`DailyLogSecret`, MAP.md 13.6): gün başına kalıcı 32 bayt; geçersiz gün (`../`, `2026-02-30`, `2026-1-1`) hata. İskelet (A6.2b). */
class DailyLogSecret(private val root: File) {
    fun forDay(day: String): ByteArray = TODO("A6.9")
}

/**
 * Gün kaydı anlık görüntüsü (Rawstats `LogFeedV3`, MAP.md 13.6): ekran/tıklama satırları müşteri verisi olmadan R18 kuralıyla; her satıra ortak alanlar; izinli dosya adları;
 * manifest (`snapshot` UUID, `files[{name, available, rows, rejectedRows, sha256}]`, `redaction.secretExported=false`). WEEM'de sağlayıcı, sayfa ve imza yok: kaynak
 * doğrudan [build] ile kopyalanır. İskelet (A6.2b): gövdeler A6.9'da.
 */
object DayLogSnapshot {
    /** [source] gün klasörü kopyası; çıktı dosyaları [out]'a, manifest `feed-manifest.json`'a ve dönüş değerine. Geçersiz gün hata. */
    fun build(source: File, out: File, day: String, secret: ByteArray, version: String, wallMs: Long, monoNs: Long): JSONObject = TODO("A6.9")

    /** Satıra ortak alanlar (`wall_ms`, `mono_ns`, `schema_version` 3, `source`, `power_verified` false, iç içe null bayrakları); var olan saatler ve scooter kimliği korunur. */
    fun common(row: JSONObject, original: JSONObject, fileName: String): JSONObject = TODO("A6.9")

    /** Kaynağın yalnız ilk [size] baytını kopyalar (sonraki eklemeler alınmaz); kaynak kısaysa `IllegalStateException`. */
    fun copyPrefix(input: InputStream, out: OutputStream, size: Long): Unit = TODO("A6.9")

    fun sha256(file: File): String = TODO("A6.9")
}
