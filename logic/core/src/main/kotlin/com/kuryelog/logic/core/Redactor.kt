package com.kuryelog.logic.core

// Rawstats Redactor.kt:1-186 @ 6388c04, satır satır (CORE.md 5.3, R18). Python ikizi tools/analysis/redact.py (= docs/ekonomi-v2/redact.py).

import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Port of `docs/ekonomi-v2/redact.py`: label-aware redaction of courier-app screen text before
 * any analysis of real exports. Personal values follow known Wolt/Bolt UI labels; UI labels,
 * prices, minutes, km and restaurant names/addresses stay; order ids keep their shape.
 * Pseudonyms are keyed (HMAC-SHA256), so they are consistent only for one secret.
 * Regexes use explicit classes (section 0 of IS_PAKETLERI.md) so the JVM, Android (ICU) and
 * Python agree on non-ASCII text. Not thread-safe (one cached [Mac]).
 */
class Redactor(secret: ByteArray) {
    private val mac: Mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }

    private fun digest(s: String): ByteArray = mac.doFinal(s.toByteArray(Charsets.UTF_8))

    /** Keyed hash of the normalised value: first [n] hex digits, upper case. */
    fun h(kind: String, value: String, n: Int = 6): String {
        val d = digest(kind + ":" + norm(value))
        val sb = StringBuilder(d.size * 2)
        for (b in d) { val v = b.toInt() and 0xFF; sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F]) }
        return sb.substring(0, n).uppercase(Locale.ROOT)
    }

    /** Shape-preserving keyed pseudonym: digits stay digits, A-Z stay A-Z, a-z stay a-z. */
    fun shape(kind: String, value: String): String {
        val d = digest("$kind:$value")
        val out = StringBuilder(value.length)
        var off = 0; var i = 0
        while (off < value.length) {
            val cp = value.codePointAt(off)
            val b = d[i % d.size].toInt() and 0xFF
            when {
                Character.isDigit(cp) -> out.append(b % 10)
                Character.isUpperCase(cp) -> out.append((65 + b % 26).toChar())
                Character.isLowerCase(cp) -> out.append((97 + b % 26).toChar())
                else -> out.appendCodePoint(cp)
            }
            off += Character.charCount(cp); i++
        }
        return out.toString()
    }

    /** Free text: phone numbers, door codes and order ids. */
    fun free(s: String): String {
        var t = AZ_PHONE.replace(s) { "[tel]" }
        t = INTL_PHONE.replace(t) { "[tel]" }
        t = CODE.replace(t) { "[code]" }
        t = ORDER_ID.replace(t) { "#" + shape("id", it.groupValues[1]) }
        return t
    }

    /** One output line per input line, in order. [pkg] is unused, as in redact.py. */
    @Suppress("UNUSED_PARAMETER")
    fun lines(pkg: String, lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size)
        var mode: String? = null
        var smallLeft = 0
        var prevSmall = false
        val menu = lines.count { norm(it) in MENU } >= 3
        // G1 (13 Oct 2026): the profile header before the first menu entry ("Name Surname FLEET ., Online") is the courier's own account.
        val firstMenu = if (menu) lines.indexOfFirst { norm(it) in MENU }.let { if (it < 0) lines.size else it } else 0
        for ((i, raw) in lines.withIndex()) {
            val n = norm(raw)
            val wasSmall = prevSmall
            prevSmall = false
            if (menu && (i == 0 || i < firstMenu)) {
                out.add(if (n == "online") raw else if (n.endsWith(", online")) "[self], Online" else "[self]"); continue
            }
            if (n in SECTION_END || n.startsWith("items (") || n.startsWith("drop off in") || n.startsWith("dropoff ")) {
                out.add(raw)
                mode = when (n) {
                    in NAME_AFTER -> "name"
                    in ADDR_AFTER -> "addr"
                    in NOTE_AFTER -> "note"
                    in SMALL_AFTER -> "small"
                    in COLLECT_AFTER -> "collect"
                    else -> null
                }
                // OCR may emit "Entrance / Floor / Apartment" first and the values after them.
                if (mode == "small") { smallLeft = if (wasSmall) smallLeft + 1 else 1; prevSmall = true }
                continue
            }
            val stripped = strip(raw)
            val personId = PERSON_ID_LINE.matchEntire(stripped)
            if (personId != null && !NUMERIC_HINT.containsMatchIn(raw)) {
                out.add("Name" + h("name", personId.groupValues[1], 4) + " #" + shape("id", personId.groupValues[2].substring(1)))
                mode = null
                continue
            }
            val idPerson = ID_PERSON_LINE.matchEntire(stripped)
            // "#408 hazırdır" is not a name.
            if (idPerson != null && !NUMERIC_HINT.containsMatchIn(raw) && Character.isUpperCase(idPerson.groupValues[2].codePointAt(0))) {
                out.add("#" + shape("id", idPerson.groupValues[1].substring(1)) + " Name" + h("name", idPerson.groupValues[2], 4))
                mode = null
                continue
            }
            when (mode) {
                "collect" -> if (ID_ONLY_LINE.matches(stripped)) {
                    out.add(free(raw))
                    mode = "name_only" // the line after the order id is the customer's name
                } else {
                    out.add("Name" + h("name", raw, 4))
                    mode = null
                }
                "name_only" -> { out.add("Name" + h("name", raw, 4)); mode = null }
                "name" -> { out.add("Name" + h("name", raw, 4)); mode = "addr" } // Wolt: the address line follows the name
                "addr" -> { out.add("[addr:" + h("addr", raw) + "]"); mode = null }
                "note" -> out.add("[note]") // every line until the next known label
                "small" -> {
                    out.add("[n]")
                    smallLeft -= 1
                    if (smallLeft <= 0) { mode = null; smallLeft = 0 }
                }
                else -> when {
                    // G1: a Wolt order id read without its "Collect order" label (map labels in between) is still followed by the
                    // customer's name; after "From" it is followed by the restaurant.
                    ID_ONLY_LINE.matches(stripped) && (if (i > 0) norm(lines[i - 1]) else "") != "from" -> { out.add(free(raw)); mode = "name_only" }
                    isNameInitial(stripped) -> out.add("Name" + h("name", raw, 4))
                    else -> out.add(free(raw))
                }
            }
        }
        return out
    }

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()
        /** Python `\s` (str patterns), written out so JVM, ICU and Python match the same characters. */
        private const val WS_SET = "\\t\\n\\u000B\\f\\r\\u001C-\\u001F\\u0085\\p{Z}"
        private const val WS = "[$WS_SET]"
        /** Python `\w` boundary after a word character: next is not a letter, number or underscore. */
        private const val NOT_WORD_AHEAD = "(?![\\p{L}\\p{N}_])"
        private val IC = setOf(RegexOption.IGNORE_CASE) // Kotlin adds UNICODE_CASE on the JVM; ICU folds Unicode anyway
        private val WS_RUN = Regex("$WS+")

        val AZ_PHONE = Regex("(?<![0-9+])\\(?$WS*(?:\\+?$WS*994$WS*|0)?$WS*\\(?$WS*(10|12|50|51|55|60|70|77|99)$WS*\\)?[$WS_SET.\\-]*([0-9]{3})[$WS_SET.\\-]*([0-9]{2})[$WS_SET.\\-]*([0-9]{2})(?![0-9])")
        val INTL_PHONE = Regex("(?<![0-9])\\+$WS*[0-9][0-9$WS_SET().\\-]{8,16}[0-9]")
        val ORDER_ID = Regex("#([A-Za-z0-9]{3,12})$NOT_WORD_AHEAD")
        val CODE = Regex("(?:domofon(?:un)?(?:$WS+kodu?)?|qap[ıi](?:n[ıi]n)?$WS+kodu?|door$WS*code|entrance$WS*code|intercom|код(?:$WS+[\\p{L}\\p{N}_]+)?|kod)" +
            "$WS*[:=\\-–]?$WS*#?$WS*[0-9][0-9#*$WS_SET]{0,12}", IC)
        /** Bolt: "Alici A. #8P6D2". */
        private val PERSON_ID_LINE = Regex("([^#\\n]{1,40}?)$WS+(#[A-Za-z0-9]{3,12})")
        /** "#014 Aysel T." (one OCR line). */
        private val ID_PERSON_LINE = Regex("(#[A-Za-z0-9]{3,12})$WS+([^#0-9\\n]{1,40})")
        private val ID_ONLY_LINE = Regex("#[A-Za-z0-9]{3,12}")
        /** G1: "Aysel T." / "O Aysel T." (Wolt timeline icon) anywhere is a customer's name (redact.py NAME_INITIAL). */
        private val NAME_INITIAL = Regex("^(?:[^$WS_SET]{1,2}$WS+)?((?:\\p{L}|['’\\-]){2,21}) (\\p{L})\\.$")
        fun isNameInitial(s: String): Boolean = NAME_INITIAL.matchEntire(s)?.let { m ->
            Character.isUpperCase(m.groupValues[1].codePointAt(0)) && Character.isUpperCase(m.groupValues[2].codePointAt(0)) } == true
        private val NUMERIC_HINT = Regex("[0-9]+$WS*(?:min|km|₼|azn)", IC)

        val NAME_AFTER = setOf("dropoff to", "drop off to")
        val ADDR_AFTER = setOf("address", "customer drop-off", "customer drop off", "delivery address", "drop-off address", "unvan", "ünvan", "ünvanı",
            "çatdırılma ünvanı", "adres", "адрес")
        val NOTE_AFTER = setOf("instructions", "notes", "note", "additional note", "address details", "qeyd", "qeydlər", "elave qeyd", "əlavə qeyd", "təlimat",
            "təlimatlar", "комментарий", "инструкции")
        val SMALL_AFTER = setOf("floor", "apartment", "flat", "entrance", "building", "block", "kat", "daire", "mertebe", "mərtəbə", "menzil", "mənzil", "giriş",
            "bina", "blok", "podyezd", "этаж", "квартира", "подъезд")
        /** Wolt pickup card: "#id" then the customer's name. */
        val COLLECT_AFTER = setOf("collect order", "pick up order", "pickup order")
        val SECTION_END = NAME_AFTER + ADDR_AFTER + NOTE_AFTER + SMALL_AFTER + COLLECT_AFTER + setOf(
            "translate", "call", "chat", "items", "info", "order details", "from", "delivery issues?", "difficulties with the delivery?",
            "send a message", "order delivered!", "order delivered", "delivered", "arrived", "slide to confirm", "check id", "accept", "decline",
            "delivery timeline", "get help", "view")
        val MENU = setOf("delivery settings", "insights", "finances", "stats", "earn extra", "benefits and insurance", "referrals", "help center",
            "all deliveries", "earnings", "campaigns", "inbox", "performance", "profile", "help", "invite friends", "about", "settings", "support")

        /** Python `str.isspace()` for one char. */
        private fun isPySpace(c: Char): Boolean = c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r' ||
            c in '\u001C'..'\u001F' || c == '\u0085' || Character.getType(c).let {
                it == Character.SPACE_SEPARATOR.toInt() || it == Character.LINE_SEPARATOR.toInt() || it == Character.PARAGRAPH_SEPARATOR.toInt() }

        /** Python `str.strip()`. */
        fun strip(s: String): String = s.trim(::isPySpace)

        /** redact.py `norm`: strip, lower case (Locale.ROOT), whitespace runs to one space. */
        fun norm(s: String): String = WS_RUN.replace(strip(s).lowercase(Locale.ROOT), " ")

        /** A known UI label of the courier apps (never customer data by itself). */
        fun isLabel(s: String): Boolean = norm(s).let { it in SECTION_END || it in MENU }
    }
}
