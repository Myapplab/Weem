package com.kuryelog.logic.core

/**
 * Müşteri telefonunu tanıma ve gizleme (`VERI_SOZLESMESI` 9; PhoneK25): diske, hata günlüğüne ve çökme kaydına giden her serbest metin
 * buradan geçer (telefon → `[tel]`). Kurallar `NoteFields`'tan taşındı (A10.2); `NoteFields.phones/redact` buraya bağlanır.
 */
object Phones {
    // Azerbaijani mobile/landline prefixes; a number must be 9 digits after the country/trunk code.
    private val azPhone = Regex("(?<![\\d+])\\(?\\s*(?:\\+?\\s*994\\s*|0)?\\s*\\(?\\s*(10|12|50|51|55|60|70|77|99)\\s*\\)?[\\s.\\-]*(\\d{3})[\\s.\\-]*(\\d{2})[\\s.\\-]*(\\d{2})(?!\\d)")
    private val intlPhone = Regex("(?<![\\d])\\+\\s*(?!994)(\\d[\\d\\s().\\-]{8,16}\\d)")

    /** Dialers wrap numbers in direction marks and may use no-break spaces ("‪+994 50 …‬"). */
    private fun plain(s: String) = s.replace(Regex("[‎‏‪-‮⁦-⁩]"), "").replace(Regex("[   ]"), " ")

    // Bare 9 digits are accepted only when written with a recognised operator code.
    private fun prefixed(v: String) = v.trimStart().let { it.startsWith("+") || it.startsWith("994") || it.startsWith("0") || it.startsWith("(") }

    /** Phone numbers in display form (+994 55 123 45 67). Short codes and IDs are ignored. */
    fun find(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = plain(raw)
        val out = LinkedHashSet<String>()
        for (m in azPhone.findAll(text)) {
            if (!prefixed(m.value) && m.value.count(Char::isDigit) != 9) continue
            val (op, a, b, c) = m.destructured
            out += "+994 $op $a $b $c"
        }
        for (m in intlPhone.findAll(text)) {
            val digits = m.groupValues[1].filter(Char::isDigit)
            if (digits.length in 9..15) out += "+$digits"
        }
        return out.toList()
    }

    /** Replaces phone numbers before anything is written to disk. */
    fun redact(raw: String): String {
        var s = azPhone.replace(plain(raw)) { m ->
            if (!prefixed(m.value) && m.value.count(Char::isDigit) != 9) m.value else m.value.takeWhile { it.isWhitespace() } + "[tel]"
        }
        s = intlPhone.replace(s) { m -> if (m.groupValues[1].count(Char::isDigit) in 9..15) "[tel]" else m.value }
        return s
    }

    /**
     * Hata ve çökme iletisi (A10.2): istisna mesajı ayrıştırılan metni alıntılayabilir (Android `JSONException` girdinin tamamını,
     * `NumberFormatException` sayıyı). İlk satır, telefonlar gizlenmiş, en çok [max] karakter.
     */
    fun safeMessage(message: String?, max: Int = 200): String = redact((message ?: "").lineSequence().first()).take(max)
}
