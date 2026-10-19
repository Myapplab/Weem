package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.net.HttpPort

/** Çözülmüş hedef (MAP.md 8.6): [source] `coord` ya da geokodun kaynağı; [restaurant] null = bilinmiyor. */
data class ResolvedDestination(
    val point: GeoPoint,
    val label: String,
    val confidence: GeoConfidence,
    val unitNote: String? = null,
    val source: String,
    val restaurant: Boolean? = null,
)

/** Çözülemeyen hedef: mesaj kullanıcıya gösterilir ("Adres bulunamadı: …", "Linkte konum bulunamadı"). */
class ResolveException(message: String) : Exception(message)

/** Koordinat → doğrudan, kısa link → yönlendirmeleri izle, adres → geokod (MAP.md 8.6). İskelet (A6.2b): gövde A6.6'da. */
class DestinationResolver(private val http: HttpPort, private val geocoder: Geocoder) {
    suspend fun resolve(parsed: ParsedDestination, near: GeoPoint?, depth: Int = 0): ResolvedDestination = TODO("A6.6")
}
