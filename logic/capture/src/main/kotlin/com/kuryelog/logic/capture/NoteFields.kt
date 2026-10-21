package com.kuryelog.logic.capture

import com.kuryelog.logic.core.Phones

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

    /** Phone numbers in display form (+994 55 123 45 67); the rules live in [Phones] (`:core`, shared with the error log). */
    fun phones(raw: String?): List<String> = Phones.find(raw)

    /** Replaces phone numbers before anything is written to disk ([Phones.redact]). */
    fun redact(raw: String?): String? = raw?.let(Phones::redact)

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
