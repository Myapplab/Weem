package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class GeoConfidence { HIGH, MEDIUM, LOW }

/** Geokod sonucu (MAP.md 8.6): [source] `google` | `ors` | `osm` | `coord`; [restaurant] null = bilinmiyor. */
data class GeocodeHit(val point: GeoPoint, val label: String, val confidence: GeoConfidence, val source: String, val restaurant: Boolean? = null)

interface Geocoder {
    suspend fun search(query: String, near: GeoPoint? = null): GeocodeHit?
}

private fun q(vararg kv: Pair<String, String>) = kv.joinToString("&") { (k, v) -> "${PlacesRest.enc(k)}=${PlacesRest.enc(v)}" }

/** Sırayla dener; ilk high/medium sonucu döner, yoksa ilk low; bir sağlayıcı düşerse sıradakine geçer. */
class FallbackGeocoder(private val chain: List<Geocoder>) : Geocoder {
    override suspend fun search(query: String, near: GeoPoint?): GeocodeHit? {
        var best: GeocodeHit? = null
        for (g in chain) {
            val hit = try {
                g.search(query, near)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            } ?: continue
            if (hit.confidence != GeoConfidence.LOW) return hit
            if (best == null) best = hit
        }
        return best
    }

    companion object {
        /** Zincir (`destinationResolverProvider`): Google anahtarı → ORS anahtarı → her zaman Nominatim. */
        fun of(http: HttpPort, googleKey: String, orsKey: String, userAgent: String): FallbackGeocoder = FallbackGeocoder(
            buildList {
                if (googleKey.isNotEmpty()) add(GoogleGeocoder(http, googleKey))
                if (orsKey.isNotEmpty()) add(OrsGeocoder(http, orsKey))
                add(NominatimGeocoder(http, userAgent))
            },
        )
    }
}

/** Google Geocoding (MAP.md 8.6): kullanıcının anahtarı (APK anahtarı kullanılmaz); ülke filtresi (`components`) yok. */
class GoogleGeocoder(private val http: HttpPort, private val key: String) : Geocoder {
    override suspend fun search(query: String, near: GeoPoint?): GeocodeHit? {
        val url = "https://maps.googleapis.com/maps/api/geocode/json?" +
            q("address" to query, "bounds" to "40.25,49.55|40.65,50.45", "language" to "az", "key" to key)
        val res = http.send(HttpRequest("GET", url, timeoutMs = 6_000))
        if (res.status != 200) return null
        val body = JSONObject(res.body)
        if (body.optString("status") != "OK") return null
        val r = body.getJSONArray("results").getJSONObject(0)
        val types = strings(r.optJSONArray("types"))
        val precise = types.any { it in FINE }
        if (!precise && types.any { it == "locality" || it == "country" || it.startsWith("administrative_area") }) return null // yalnız şehir
        val geometry = r.getJSONObject("geometry")
        val loc = geometry.getJSONObject("location")
        val partial = r.opt("partial_match") == true
        val confidence = if (!precise) {
            GeoConfidence.LOW // semt / mahalle düzeyi
        } else {
            when (geometry.opt("location_type")) {
                "ROOFTOP", "RANGE_INTERPOLATED" -> if (partial) GeoConfidence.MEDIUM else GeoConfidence.HIGH
                "GEOMETRIC_CENTER" -> GeoConfidence.MEDIUM
                else -> GeoConfidence.LOW
            }
        }
        return GeocodeHit(
            GeoPoint(loc.getDouble("lat"), loc.getDouble("lng")),
            (r.opt("formatted_address") as? String) ?: query,
            confidence,
            "google",
            when {
                "restaurant" in types || "cafe" in types || "meal_takeaway" in types -> true
                "street_address" in types || "subpremise" in types -> false
                else -> null
            },
        )
    }

    private companion object {
        val FINE = setOf("street_address", "premise", "subpremise", "route", "establishment", "point_of_interest", "intersection", "plus_code")
    }
}

/** ORS (Pelias) geokodu; ücretsiz anahtarla. */
class OrsGeocoder(private val http: HttpPort, private val key: String) : Geocoder {
    override suspend fun search(query: String, near: GeoPoint?): GeocodeHit? {
        val params = buildList {
            add("api_key" to key)
            add("text" to query)
            if (near != null) {
                add("focus.point.lat" to near.lat.toString())
                add("focus.point.lon" to near.lon.toString())
            }
            add("size" to "1")
        }
        val res = http.send(HttpRequest("GET", "https://api.openrouteservice.org/geocode/search?" + q(*params.toTypedArray()), timeoutMs = 6_000))
        if (res.status != 200) return null
        val features = JSONObject(res.body).optJSONArray("features") ?: return null
        if (features.length() == 0) return null
        val f = features.getJSONObject(0)
        val c = f.getJSONObject("geometry").getJSONArray("coordinates")
        val props = f.getJSONObject("properties")
        val layer = props.opt("layer")?.toString() ?: ""
        if (layer in COARSE) return null // yalnız şehir
        val score = (props.opt("confidence") as? Number)?.toDouble() ?: 0.0
        val confidence = when {
            layer == "street" || layer == "neighbourhood" -> GeoConfidence.LOW
            score >= 0.8 -> GeoConfidence.HIGH
            score >= 0.5 -> GeoConfidence.MEDIUM
            else -> GeoConfidence.LOW
        }
        return GeocodeHit(GeoPoint(c.getDouble(1), c.getDouble(0)), (props.opt("label") as? String) ?: query, confidence, "ors")
    }

    private companion object {
        val COARSE = setOf("country", "macroregion", "region", "macrocounty", "county", "localadmin", "locality", "borough")
    }
}

/**
 * OSM Nominatim: anahtarsız yedek. WEEM'de istekler arasında en az 1 sn (OSM kullanım kuralı, MAP.md 8.13 F-N1; YolGo'da sınırlayıcı yoktu).
 * [now]/[delay] testte sahte.
 */
class NominatimGeocoder(
    private val http: HttpPort,
    private val userAgent: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : Geocoder {
    override suspend fun search(query: String, near: GeoPoint?): GeocodeHit? {
        throttle()
        val url = "https://nominatim.openstreetmap.org/search?" + q(
            "q" to query, "format" to "jsonv2", "viewbox" to "49.55,40.65,50.45,40.25", "limit" to "1", "addressdetails" to "0", "accept-language" to "az",
        )
        val res = http.send(HttpRequest("GET", url, mapOf("User-Agent" to userAgent), timeoutMs = 6_000))
        if (res.status != 200) return null
        val list = JSONArray(res.body)
        if (list.length() == 0) return null
        val r = list.getJSONObject(0)
        val point = GeoPoint(r.get("lat").toString().toDouble(), r.get("lon").toString().toDouble())
        val type = (r.opt("addresstype") ?: r.opt("type"))?.takeIf { it != JSONObject.NULL }?.toString() ?: ""
        if (type in COARSE) return null // yalnız şehir / ilçe
        val confidence = when {
            type == "house" || type == "building" || r.opt("class") == "amenity" -> GeoConfidence.HIGH
            type == "road" || type == "street" || type in AREA -> GeoConfidence.LOW
            else -> GeoConfidence.MEDIUM
        }
        return GeocodeHit(
            point,
            (r.opt("display_name") as? String) ?: query,
            confidence,
            "osm",
            when {
                r.opt("type")?.toString() in setOf("restaurant", "fast_food", "cafe") -> true
                type in setOf("house", "apartments", "residential") -> false
                else -> null
            },
        )
    }

    private suspend fun throttle() = gate.withLock {
        val wait = lastAt + MIN_GAP_MS - now()
        if (lastAt > 0 && wait > 0) delay(wait)
        lastAt = now()
    }

    companion object {
        const val MIN_GAP_MS = 1_000L
        private val gate = Mutex()
        private var lastAt = 0L
        private val COARSE = setOf(
            "country", "state", "region", "province", "county", "municipality", "city", "town", "village", "city_district", "district", "borough", "administrative",
        )
        private val AREA = setOf("suburb", "quarter", "neighbourhood", "residential", "hamlet", "locality", "isolated_dwelling")
    }
}

private fun strings(a: JSONArray?): Set<String> = if (a == null) emptySet() else (0 until a.length()).map { a.opt(it).toString() }.toSet()
