package com.kuryelog.logic.capture

/**
 * Customers often leave the structured address fields empty and type everything
 * into a free-text note ("BLOCK B (Domofon kodu: 68) Mertebe 6 ..."). These rules
 * pull the same facts out of that text. They never replace a value the platform
 * shows in its own field, and the original note is always kept as written.
 */
object NoteFields {
    data class Found(val building: String? = null, val entrance: String? = null, val floor: String? = null,
        val apartment: String? = null, val doorCode: String? = null, val phones: List<String> = emptyList(),
        /** F7.1: an office/room number ("16 cu otag", "ofis 16"); shown as "Oda 16" when there is no flat. */
        val room: String? = null)

    // Ordinal suffixes: 6-cı, 3-cü, 2-ci, 4-cu, 5.
    // WEEM (CAPTURE.md 7.5): explicit Unicode word edges instead of `\b` (ICU on the phone, JDK 21 in tests).
    private const val S = CourierParser.WORD_START
    private const val E = CourierParser.WORD_END
    private const val ORD = "(?:\\s*-?\\s*(?:ci|cu|ci|cu|ncı|nci|ncu|ncü|inci|uncu|nd|rd|th|st)$E|\\.)?"
    private const val B = "(?<![\\p{L}\\d])"
    private fun rx(s: String) = Regex(s)
    private val doorCode = listOf(
        rx("$B(?:domofon(?:un)?(?:\\s+kodu?)?|qapi(?:nin)?\\s+kodu?|giris\\s+kodu?|door\\s*code|entrance\\s*code|intercom(?:\\s*code)?|kod\\s+dveri|код(?:\\s+(?:двери|домофона|подъезда))?|домофон)\\s*[:=\\-–]?\\s*(?:#\\s*)?([0-9]{1,8}(?:\\s*[#*]\\s*[0-9]{1,6})?[#*]?)"),
        rx("${B}kod(?:u)?\\s*[:=\\-–]\\s*([0-9]{1,8}[#*]?)"),
        // F7.1 V1: "kod 35" without a colon.
        rx("${B}kod(?:u)?\\s+([0-9]{1,8}[#*]?)(?![\\p{L}\\d])")
    )
    private val floor = listOf(
        rx("$B(?:mertebe|kat|floor|etaj|этаж)\\s*[:=\\-–]?\\s*(\\d{1,3})$E"),
        rx("$S(\\d{1,3})$ORD\\s*(?:mertebe|kat|floor|etaj|этаж)")
    )
    private val apartment = listOf(
        rx("$B(?:menzil|daire|apartment|apt|flat|kvartira|квартира|kv|кв)\\.?\\s*[:=\\-–]?\\s*(?:no\\.?|№|#)?\\s*(\\d{1,4}[a-z]?)$E"),
        rx("$S(\\d{1,4})$ORD\\s*(?:menzil|daire|kvartira|квартира)")
    )
    private val entrance = listOf(
        rx("$B(?:podyezd|podezd|pod\\.|giris|entrance|подъезд|подьезд)\\s*[:=\\-–]?\\s*(\\d{1,2})$E"),
        rx("$S(\\d{1,2})$ORD\\s*(?:podyezd|podezd|giris|giriw|entrance|подъезд|подьезд)"),
        // F7.1 V4: a letter entrance "A giriw" / "B giriş" (giriw: OCR/keyboard for giriş).
        rx("$B([a-z])\\s*(?:giris|giriw)(?![\\p{L}])")
    )
    private val room = listOf(
        rx("$B(?:otaq|otag|ofis|office|room|комната|офис)\\s*[:=\\-–]?\\s*(?:no\\.?|№|#)?\\s*(\\d{1,4})$E"),
        rx("$S(\\d{1,4})$ORD\\s*(?:otaq|otag|ofis|office|room|комната|офис)")
    )
    private val building = listOf(
        rx("$B(?:blok|block|korpus|корпус|bldg|building)\\s*[:=\\-–]?\\s*([a-z]$E|\\d{1,3}[a-z]?$E)"),
        rx("${B}bina\\s*[:=\\-–]?\\s*(\\d{1,3}[a-z]?|[a-z])$E")
    )

    // Azerbaijani mobile/landline prefixes; a number must be 9 digits after the country/trunk code.
    private val azPhone = Regex("(?<![\\d+])\\(?\\s*(?:\\+?\\s*994\\s*|0)?\\s*\\(?\\s*(10|12|50|51|55|60|70|77|99)\\s*\\)?[\\s.\\-]*(\\d{3})[\\s.\\-]*(\\d{2})[\\s.\\-]*(\\d{2})(?!\\d)")
    private val intlPhone = Regex("(?<![\\d])\\+\\s*(?!994)(\\d[\\d\\s().\\-]{8,16}\\d)")

    /** Phone numbers in display form (+994 55 123 45 67). Short codes and IDs are ignored. */
    /** Dialers wrap numbers in direction marks and may use no-break spaces ("\u202A+994 50 …\u202C"). */
    private fun plain(s: String) = s.replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]"), "").replace(Regex("[\u00A0\u2007\u202F]"), " ")
    fun phones(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = plain(raw)
        val out = LinkedHashSet<String>()
        for (m in azPhone.findAll(text)) {
            val raw = m.value
            val digits = raw.filter(Char::isDigit)
            // Bare 9 digits are accepted only when written with a recognised operator code.
            val prefixed = raw.trimStart().let { it.startsWith("+") || it.startsWith("994") || it.startsWith("0") || it.startsWith("(") }
            if (!prefixed && digits.length != 9) continue
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
    fun redact(raw: String?): String? {
        if (raw == null) return null
        val text = plain(raw)
        var s = azPhone.replace(text) { m ->
            val digits = m.value.filter(Char::isDigit)
            val prefixed = m.value.trimStart().let { it.startsWith("+") || it.startsWith("994") || it.startsWith("0") || it.startsWith("(") }
            if (!prefixed && digits.length != 9) m.value else m.value.takeWhile { it.isWhitespace() } + "[tel]"
        }
        s = intlPhone.replace(s) { m -> if (m.groupValues[1].count(Char::isDigit) in 9..15) "[tel]" else m.value }
        return s
    }

    private fun first(rules: List<Regex>, n: String): String? = rules.firstNotNullOfOrNull { it.find(n)?.groupValues?.get(1)?.trim() }

    fun extract(vararg texts: String?): Found {
        val joined = texts.filterNotNull().filter { it.isNotBlank() }
        if (joined.isEmpty()) return Found()
        // Phone digits must not be read as a door code, floor or flat.
        val phones = joined.flatMap(::phones).distinct()
        val n = CourierParser.norm(joined.joinToString("\n") { redact(it) ?: it }).replace("[tel]", " ")
        return Found(
            building = first(building, n)?.uppercase(),
            entrance = first(entrance, n)?.uppercase(),
            floor = first(floor, n),
            apartment = first(apartment, n)?.uppercase(),
            doorCode = first(doorCode, n)?.replace(" ", ""),
            phones = phones,
            room = first(room, n)
        )
    }

    /** F7.1 V3/V4: words that name another field; a structured Entrance/Floor/Apartment value holding one is free text. */
    private val otherFieldWord = Regex("(?:menzil|daire|kvartira|квартира|${S}kv$E|кв$E|kod|mertebe|${S}kat$E|floor|этаж|otaq|otag|ofis|office|room|giris|giriw|podyezd)")
    fun holdsOtherFields(value: String?): Boolean = value != null && otherFieldWord.containsMatchIn(CourierParser.norm(value))

    /** Wolt and Bolt shorten long notes with an ellipsis until the note is opened. */
    fun truncated(text: String?): Boolean = text?.trimEnd()?.let { it.endsWith("…") || it.endsWith("...") } == true
}
