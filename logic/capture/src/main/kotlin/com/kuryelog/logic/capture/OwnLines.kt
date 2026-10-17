package com.kuryelog.logic.capture

import java.text.Normalizer
import java.util.Locale

// Rawstats `OverlayText` söz dağarcığı ve tanıma (OverlayText.kt:19-36, 99-134 @ 6388c04); CAPTURE.md 2.10.
// Biçimlendiriciler (`offer`, `trip`, `badge`…) `:orders` `OverlayText`'te kalır ve buradaki sabitleri kullanır (tek kaynak).
/**
 * Every line WEEM draws over the courier apps (the overlay badge), recognised in one place: CourierParser drops every
 * line [isOwn] recognises before it parses a frame (accessibility, OCR or notification). An OCR crop that contains the
 * badge can therefore never turn "● 30.1 km" or "Wolt · 2.21₼ · Teklif" into the offer's km or fee (field evidence N11).
 * Recognition accepts both languages and what OCR makes of the line (another bullet, a spaced dash, lost spaces, `AZN`
 * for `₼`, a dropped bullet after "Wolt"/"Bolt Food").
 */
object OwnLines {
    /** Separator between the parts of one panel line. */
    const val SEP = " · "
    const val BULLET = "●"
    const val CURRENCY = "₼"
    const val NET = "net"
    val OFFER = "Teklif" to "Təklif"
    val ORDER = "Sipariş" to "Sifariş"
    val WAITING = "Sipariş bekleniyor" to "Sifariş gözlənilir"
    val OFFLINE = "Çevrimdışı" to "Oflayn"
    val TODAY = "Bugün" to "Bu gün"
    val ORDERS = "sipariş" to "sifariş"
    /** Trip phase texts (TR to AZ) by `TripPhase` name; `:orders` maps its enum onto these keys. */
    val PHASES: Map<String, Pair<String, String>> = linkedMapOf(
        "TO_RESTAURANT" to ("Restorana gidiliyor" to "Restorana gedilir"),
        "WAITING" to ("Restoranda bekleniyor" to "Restoranda gözlənilir"),
        "TO_CUSTOMER" to ("Müşteriye gidiliyor" to "Müştəriyə gedilir"),
        "AT_CUSTOMER" to ("Müşteriye varıldı" to "Müştəriyə çatıldı"),
        "FINISHED" to ("Teslim edildi" to "Çatdırıldı"),
        "END_UNCONFIRMED" to ("Sonuç doğrulanamadı" to "Nəticə təsdiqlənmədi"))

    private fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT).replace('ə', 'e').replace('ı', 'i'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[\\s\\p{Z}]+"), " ").trim()
    private fun alternatives(texts: List<String>) = texts.map { Regex.escape(norm(it)) }.distinct().joinToString("|")
    private fun both(p: Pair<String, String>) = listOf(p.first, p.second)
    private const val SP = "[\\s\\p{Z}]*"
    private const val WS = "[\\s\\p{Z}]+"
    /** The separator as drawn or as OCR reads it: any bullet-like mark, or a spaced dash or dot. */
    private const val STRICT = "(?:$SP[·•●∙⋅*]$SP|$WS[.\\-–—]$WS)"
    /** After a known platform name OCR may also drop the bullet and leave only a space. */
    private const val LOOSE = "(?:$STRICT|$WS)"
    private const val KNOWN = "(?:wolt|bolt food|bolt)"
    /** `OrderTracker.platform` falls back to the package's last segment for other courier apps. */
    private const val GENERIC = "[\\p{L}\\p{N}_]{2,30}"
    private const val AMOUNT = "[0-9]+(?:[.,][0-9]{1,2})?$SP(?:₼|azn)"
    private val PHASE_RX = alternatives(PHASES.values.flatMap(::both))
    private val STATUS_RX = alternatives(both(WAITING) + both(OFFLINE))

    private fun kinds(platform: String, sep: String) = listOf(
        "$platform$sep$AMOUNT(?:${sep}$NET$SP$AMOUNT)?$sep.+",
        "$platform$sep.+$sep(?:$PHASE_RX)",
        "$platform$sep(?:$PHASE_RX)",
        "$platform$sep(?:$STATUS_RX)")

    private val OWN: List<Regex> = (kinds(KNOWN, LOOSE) + kinds(GENERIC, STRICT) + listOf(
        "[·•●∙⋅*]$SP[0-9]+(?:[.,][0-9]+)?${SP}km",
        "(?:${alternatives(both(TODAY))})$SP:?$SP[0-9]+$SP(?:${alternatives(both(ORDERS))})")).map { Regex(it) }

    /** True for a line WEEM draws itself, as the accessibility tree or OCR may read it. */
    fun isOwn(line: String): Boolean { val n = norm(line); return n.isNotEmpty() && OWN.any { it.matches(n) } }

    /** [texts] without WEEM's own lines; a multi-line text keeps its other lines. */
    fun withoutOwn(texts: List<String>): List<String> = texts.mapNotNull { t ->
        if ('\n' !in t) t.takeUnless(::isOwn)
        else t.split('\n').filterNot(::isOwn).takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
