package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.net.HttpPort

enum class GeoConfidence { HIGH, MEDIUM, LOW }

/** Geokod sonucu (MAP.md 8.6): [source] `google` | `ors` | `osm` | `coord`; [restaurant] null = bilinmiyor. */
data class GeocodeHit(val point: GeoPoint, val label: String, val confidence: GeoConfidence, val source: String, val restaurant: Boolean? = null)

interface Geocoder {
    suspend fun search(query: String, near: GeoPoint? = null): GeocodeHit?
}

/** Google Geocoding (MAP.md 8.6): kullanıcının anahtarı; ülke filtresi (`components`) yok. İskelet (A6.2b): gövde A6.6'da. */
class GoogleGeocoder(private val http: HttpPort, private val key: String) : Geocoder {
    override suspend fun search(query: String, near: GeoPoint?): GeocodeHit? = TODO("A6.6")
}
