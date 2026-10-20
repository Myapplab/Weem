package com.kuryelog.logic.orders.dataset

import org.json.JSONObject

/** Güne elle ekleme (MAP.md 13.9; `DatasetManual.kt`): ekran görüntüsü satırı (opak kopya, içerik okunmaz) ve yapılandırılmış haftalık ödeme / vardiya notu; serbest metin ve müşteri alanı yok. */
object DatasetManual {
    const val FILE = "manual.jsonl"
    const val PAYOUT = "weekly_payout"
    const val NOTE = "shift_note"
    val SCREENSHOT_KINDS = listOf("wolt_earnings", "bolt_earnings", "wolt_rating", "bolt_rating", "wolt_acceptance", "bolt_acceptance", "wolt_completion", "bolt_completion")

    /** Doğrulama hatası: TR/AZ metni `DatasetText`. */
    enum class Error { NUMBERS, DATE, PAYOUT_REQUIRED, KIND }

    class Result(val row: JSONObject?, val error: Error?)

    /** Seçilen görüntünün uzantısı (dosyanın kendi adı alınmaz: kişi adı taşımasın). */
    fun extension(mime: String?): String = when (mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "image" }

    /** `attachments/<tür>_<uuid>.<uzantı>`. */
    fun attachmentPath(kind: String, uuid: String, mime: String?) = "attachments/${kind}_$uuid.${extension(mime)}"

    fun screenshotRow(kind: String, path: String, mime: String, wallMs: Long, monoNs: Long): JSONObject =
        DatasetContract.common(wallMs, monoNs, "manual").put("kind", kind).put("file", path).put("content_read", false).put("ocr", false)
            .put("attachment_source", "user_selected_opaque").put("mime", mime)

    /**
     * Haftalık ödeme / vardiya notu satırı. [input]: boş ya da yok = bilinmiyor; sayılarda virgül/nokta, 0 ≤ x ≤ sınır; tarih `YYYY-MM-DD` ve gerçek gün;
     * seçimler izinli kümeden. Ödemede platform ve tutar zorunlu.
     */
    fun save(kind: String, input: Map<String, String?>, wallMs: Long, monoNs: Long): Result {
        if (kind != PAYOUT && kind != NOTE) return Result(null, Error.KIND)
        val row = DatasetContract.common(wallMs, monoNs, "manual").put("kind", kind)
        val flags = JSONObject()
        fun text(k: String) = input[k]?.trim()?.takeIf { it.isNotEmpty() }
        fun number(k: String, max: Double): Error? {
            val t = text(k)
            val n = t?.let { it.replace(',', '.').toDoubleOrNull() }
            if (t != null && (n == null || !n.isFinite() || n < 0 || n > max)) return Error.NUMBERS
            row.put(k, n ?: JSONObject.NULL)
            flags.put(k, if (n == null) "unavailable" else "user_entered")
            return null
        }
        fun choice(k: String, allowed: Set<String>) {
            val v = text(k)?.takeIf { it in allowed }
            row.put(k, v ?: JSONObject.NULL)
            flags.put(k, if (v == null) "unavailable" else "user_entered")
        }
        if (kind == PAYOUT) {
            number("amountAzn", 100000.0)?.let { return Result(null, it) }
            choice("platform", setOf("wolt", "bolt"))
            for (k in listOf("periodStart", "periodEnd")) {
                val t = text(k)
                val ok = t == null || runCatching { DatasetContract.date(t) }.isSuccess
                if (!ok) return Result(null, Error.DATE)
                row.put(k, t ?: JSONObject.NULL)
                flags.put(k, if (t == null) "unavailable" else "user_entered")
            }
            if (row.isNull("amountAzn") || row.isNull("platform")) return Result(null, Error.PAYOUT_REQUIRED)
        } else {
            for (k in listOf("breakMinutes", "declinePenaltyMinutes")) number(k, 1440.0)?.let { return Result(null, it) }
            number("declinePenaltyAzn", 100000.0)?.let { return Result(null, it) }
            choice("weather", setOf("clear", "rain", "wind", "cold", "hot", "mixed"))
            choice("otherApp", setOf("wolt", "bolt", "none"))
            choice("whileBusy", setOf("delivery", "waiting", "offline", "break", "none"))
        }
        return Result(row.put("field_sources", flags), null)
    }
}
