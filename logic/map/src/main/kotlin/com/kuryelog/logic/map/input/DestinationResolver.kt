package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import java.util.Locale

/** Çözülmüş hedef (MAP.md 8.6): [source] `coord` ya da geokodun kaynağı; [restaurant] null = bilinmiyor. */
data class ResolvedDestination(
    val point: GeoPoint,
    val label: String,
    val confidence: GeoConfidence,
    val unitNote: String? = null,
    val source: String,
    val restaurant: Boolean? = null,
)

/** Çözülemeyen hedef: mesaj kullanıcıya gösterilir ("Adres bulunamadı: …", "Linkte konum bulunamadı"); [az] Azerice karşılığı. */
class ResolveException(message: String, val az: String = message) : Exception(message) {
    fun text(lang: Lang): String = if (lang == Lang.AZ) az else message!!
}

/** Koordinat → doğrudan, kısa link → yönlendirmeleri izle, adres → geokod (MAP.md 8.6; `destination_resolver.dart`). */
class DestinationResolver(private val http: HttpPort, private val geocoder: Geocoder) {
    suspend fun resolve(parsed: ParsedDestination, near: GeoPoint?, depth: Int = 0): ResolvedDestination = when (parsed) {
        is ParsedDestination.Coordinate -> ResolvedDestination(
            parsed.point,
            shortLabel(parsed.label) ?: String.format(Locale.ROOT, "Pin %.5f, %.5f", parsed.point.lat, parsed.point.lon),
            GeoConfidence.HIGH,
            parsed.unitNote,
            "coord",
        )
        is ParsedDestination.ShortLink -> {
            if (depth > 1) throw ResolveException("Kısa link çözülemedi", "Qısa link açılmadı")
            val full = expand(parsed.url)
            val again = AddressParser.parse(full)
            if (again == null || again is ParsedDestination.ShortLink) throw ResolveException("Linkte konum bulunamadı", "Linkdə ünvan tapılmadı")
            val r = resolve(again, near, depth + 1)
            r.copy(unitNote = parsed.unitNote ?: r.unitNote)
        }
        is ParsedDestination.Address -> {
            val hit = geocoder.search(parsed.query, near) ?: throw ResolveException("Adres bulunamadı: ${parsed.query}", "Ünvan tapılmadı: ${parsed.query}")
            ResolvedDestination(
                hit.point,
                shortLabel(hit.label) ?: parsed.query,
                if (parsed.strong) hit.confidence else GeoConfidence.LOW,
                parsed.unitNote,
                hit.source,
                hit.restaurant,
            )
        }
    }

    /** "Mehdi Hüseyn küç. 99, Bakı, Azərbaycan, AZ1000" → ilk iki parça. */
    private fun shortLabel(label: String?): String? {
        if (label == null || label.isBlank()) return null
        return label.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ")
    }

    /** Yönlendirmeleri elle izler (en çok 5 sıçrama, her biri 6 sn). */
    private suspend fun expand(url: String): String {
        var current = url
        repeat(5) {
            val res = http.send(HttpRequest("GET", current, timeoutMs = 6_000, followRedirects = false))
            val location = res.headers.entries.firstOrNull { it.key.equals("location", ignoreCase = true) }?.value ?: return current
            current = resolveRef(current, location)
            val uri = LinkUri.tryParse(current)
            if (uri != null && uri.host.startsWith("consent.")) {
                val cont = try { uri.queryParameters["continue"] } catch (_: IllegalArgumentException) { null }
                if (cont != null) current = cont
            }
            val again = LinkUri.tryParse(current)
            if (again != null && AddressParser.coordsFromUri(again) != null) return current
        }
        return current
    }

    private fun resolveRef(base: String, ref: String): String = try {
        java.net.URI(base).resolve(java.net.URI(ref)).toString()
    } catch (_: Exception) {
        ref
    }
}
