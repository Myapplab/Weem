package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.GeoPoint

/** `address_parser.dart:5-33` (MAP.md 8.4): metinden çıkan hedef. [unitNote] = kapı notu ("Mənzil 45 · Mərtəbə 7 …"). */
sealed interface ParsedDestination {
    val raw: String
    val unitNote: String?

    data class Coordinate(override val raw: String, val point: GeoPoint, val label: String? = null, override val unitNote: String? = null) : ParsedDestination
    data class ShortLink(override val raw: String, val url: String, override val unitNote: String? = null) : ParsedDestination
    data class Address(override val raw: String, val query: String, val strong: Boolean, override val unitNote: String? = null) : ParsedDestination
}

/** Wolt/Bolt adresi, harita bağlantısı, koordinat ya da Plus Code ayrıştırıcısı (saf). İskelet (A6.2b): gövdeler A6.6'da. */
object AddressParser {
    /** Ayrıştırılamazsa null (uzunluk 5–800 dışı, adres olmayan metin, harita dışı bağlantı). */
    fun parse(input: String): ParsedDestination? = TODO("A6.6")

    /** Elle arama: `parse` sonuç vermezse 2–250 karakterlik, ≥2 harfli, URL'siz metin mekân adıdır. */
    fun parseExplicit(input: String): ParsedDestination? = TODO("A6.6")
}
