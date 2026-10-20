package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.FormatException
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

/** Kimlik (MAP.md 8.8): anahtar + Android kısıtlaması için paket ve imza SHA-1 (onaltılık, büyük harf, iki noktasız). */
data class GoogleCredentials(val key: String, val packageName: String? = null, val cert: String? = null) {
    val headers: Map<String, String>
        get() = buildMap {
            put("X-Goog-Api-Key", key)
            if (!packageName.isNullOrEmpty()) put("X-Android-Package", packageName)
            if (!cert.isNullOrEmpty()) put("X-Android-Cert", cert)
        }
}

enum class PlacesFailureKind { OFFLINE, MISSING_KEY, KEY_INVALID, APP_BLOCKED, API_DISABLED, API_BLOCKED, BILLING, QUOTA, UNAVAILABLE }

/** [reason] Google'ın `ErrorInfo.reason` değeri; [status] HTTP kodu. Ham Google mesajı hiçbir yerde tutulmaz. */
data class PlacesFailure(val kind: PlacesFailureKind, val reason: String? = null, val status: Int = 0)

/** Places çağrısı başarısız: neden [failure]'da. */
class PlacesException(val failure: PlacesFailure) : Exception(failure.toString())

data class PlaceDetails(val point: GeoPoint, val address: String?, val restaurant: Boolean)

/** Places (New) REST (MAP.md 8.8; `places_rest.dart`), host `places.googleapis.com`. */
object PlacesRest {
    const val HOST = "places.googleapis.com"
    private val restaurantTypes = setOf("restaurant", "cafe", "meal_takeaway", "fast_food_restaurant", "bakery")
    private val random = SecureRandom()

    private fun language(lang: Lang) = if (lang == Lang.AZ) "az" else "tr"

    suspend fun autocomplete(http: HttpPort, c: GoogleCredentials?, query: String, session: String, near: GeoPoint, lang: Lang): List<SearchItem> {
        val creds = require(c)
        val body = JSONObject().put("input", query)
        if (session.isNotEmpty()) body.put("sessionToken", session)
        body.put("languageCode", language(lang)).put("regionCode", "az").put(
            "locationBias",
            JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", near.lat).put("longitude", near.lon)).put("radius", 30000.0)),
        )
        val req = HttpRequest("POST", "https://$HOST/v1/places:autocomplete", creds.headers + ("Content-Type" to "application/json"), body.toString(), 6_000)
        return parseAutocomplete(send(http, req))
    }

    suspend fun details(http: HttpPort, c: GoogleCredentials?, id: String, session: String, lang: Lang): PlaceDetails {
        val creds = require(c)
        if (id.isEmpty() || id.contains('/')) throw FormatException("place id")
        val q = buildList {
            if (session.isNotEmpty()) add("sessionToken" to session)
            add("languageCode" to language(lang))
            add("regionCode" to "az")
        }.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val req = HttpRequest("GET", "https://$HOST/v1/places/$id?$q", creds.headers + ("X-Goog-FieldMask" to "id,location,formattedAddress,types"), timeoutMs = 8_000)
        return parseDetails(send(http, req))
    }

    private fun require(c: GoogleCredentials?): GoogleCredentials {
        if (c == null || c.key.isEmpty()) throw PlacesException(PlacesFailure(PlacesFailureKind.MISSING_KEY))
        return c
    }

    private suspend fun send(http: HttpPort, req: HttpRequest): JSONObject {
        val r = try {
            http.send(req)
        } catch (_: SocketTimeoutException) {
            throw PlacesException(PlacesFailure(PlacesFailureKind.UNAVAILABLE, "TIMEOUT"))
        } catch (_: IOException) {
            throw PlacesException(PlacesFailure(PlacesFailureKind.OFFLINE))
        }
        if (r.status != 200) throw PlacesException(classify(r.status, r.body))
        return try {
            JSONObject(r.body)
        } catch (_: org.json.JSONException) {
            throw PlacesException(PlacesFailure(PlacesFailureKind.UNAVAILABLE, "BODY"))
        }
    }

    /** HTTP durum + gövde → hata türü (`error.details[].reason`, `error.status`; 429 → quota; çözülemezse unavailable). */
    fun classify(status: Int, body: String): PlacesFailure {
        var reason = ""
        var state = ""
        try {
            val error = JSONObject(body).getJSONObject("error")
            state = error.opt("status")?.takeIf { it != JSONObject.NULL }?.toString() ?: ""
            val details = error.optJSONArray("details") ?: JSONArray()
            for (i in 0 until details.length()) {
                val r = (details.opt(i) as? JSONObject)?.opt("reason") as? String ?: continue
                reason = r
                break
            }
        } catch (_: Exception) {
        }
        val kind = when (reason) {
            "API_KEY_INVALID", "API_KEY_NOT_FOUND", "API_KEY_EXPIRED" -> PlacesFailureKind.KEY_INVALID
            "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED", "API_KEY_IOS_APP_BLOCKED" -> PlacesFailureKind.APP_BLOCKED
            "SERVICE_DISABLED", "ACCESS_NOT_CONFIGURED" -> PlacesFailureKind.API_DISABLED
            "API_KEY_SERVICE_BLOCKED" -> PlacesFailureKind.API_BLOCKED
            "BILLING_DISABLED", "BILLING_NOT_ENABLED" -> PlacesFailureKind.BILLING
            "RATE_LIMIT_EXCEEDED", "RESOURCE_EXHAUSTED" -> PlacesFailureKind.QUOTA
            else -> when {
                status == 429 || state == "RESOURCE_EXHAUSTED" -> PlacesFailureKind.QUOTA
                body.contains("API key not valid") -> PlacesFailureKind.KEY_INVALID
                else -> PlacesFailureKind.UNAVAILABLE
            }
        }
        return PlacesFailure(kind, reason.ifEmpty { state }.ifEmpty { null }, status)
    }

    /** `location` sonlu değilse `FormatException`; restoran türleri {restaurant, cafe, meal_takeaway, fast_food_restaurant, bakery}. */
    fun parseDetails(json: JSONObject): PlaceDetails {
        val l = json.optJSONObject("location")
        val la = l?.opt("latitude") as? Number
        val lo = l?.opt("longitude") as? Number
        if (la == null || lo == null || !la.toDouble().isFinite() || !lo.toDouble().isFinite()) throw FormatException("location")
        val types = json.optJSONArray("types")?.let { a -> (0 until a.length()).map { a.opt(it).toString() } } ?: emptyList()
        val address = json.opt("formattedAddress")?.takeIf { it != JSONObject.NULL }?.toString() ?: ""
        return PlaceDetails(GeoPoint(la.toDouble(), lo.toDouble()), address, types.any { it in restaurantTypes })
    }

    /** `suggestions[].placePrediction` (placeId ve başlık zorunlu); bozuk öğeler atlanır. */
    fun parseAutocomplete(json: JSONObject): List<SearchItem> {
        val out = ArrayList<SearchItem>()
        val list = json.optJSONArray("suggestions") ?: return out
        for (i in 0 until list.length()) {
            val p = (list.opt(i) as? JSONObject)?.opt("placePrediction") as? JSONObject ?: continue
            val id = p.opt("placeId")?.takeIf { it != JSONObject.NULL }?.toString() ?: ""
            if (id.isEmpty()) continue
            val f = p.opt("structuredFormat") as? JSONObject ?: JSONObject()
            fun text(v: Any?) = (v as? JSONObject)?.opt("text")?.takeIf { it != JSONObject.NULL }?.toString() ?: ""
            val title = text(f.opt("mainText")).ifEmpty { text(p.opt("text")) }
            if (title.isEmpty()) continue
            out += SearchItem(id, title, text(f.opt("secondaryText")), "google")
        }
        return out
    }

    /** Places oturum belirteci: UUID v4 (36 karakter, URL-güvenli). */
    fun newSessionToken(): String {
        val b = ByteArray(16).also { random.nextBytes(it) }
        b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
        val h = b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
    }

    internal fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}

/** `placesFailureShort` (TR/AZ tablosu MAP.md 8.8): arama çubuğunun altındaki tek satır. */
fun placesFailureShort(f: PlacesFailure, lang: Lang): String = when (f.kind) {
    PlacesFailureKind.OFFLINE -> tl(lang, "İnternet yok · çevrimdışı sonuçlar", "İnternet yoxdur · oflayn nəticələr")
    PlacesFailureKind.MISSING_KEY -> tl(lang, "Google anahtarı yok · çevrimdışı sonuçlar", "Google açarı yoxdur · oflayn nəticələr")
    PlacesFailureKind.KEY_INVALID -> tl(lang, "Google anahtarı geçersiz · çevrimdışı sonuçlar", "Google açarı etibarsızdır · oflayn nəticələr")
    PlacesFailureKind.APP_BLOCKED -> tl(lang, "Anahtar bu APK’ya izin vermiyor · çevrimdışı sonuçlar", "Açar bu APK-ya icazə vermir · oflayn nəticələr")
    PlacesFailureKind.API_DISABLED, PlacesFailureKind.API_BLOCKED ->
        tl(lang, "Places API (New) kapalı · çevrimdışı sonuçlar", "Places API (New) bağlıdır · oflayn nəticələr")
    PlacesFailureKind.BILLING -> tl(lang, "Google faturalandırma kapalı · çevrimdışı sonuçlar", "Google ödənişi bağlıdır · oflayn nəticələr")
    PlacesFailureKind.QUOTA -> tl(lang, "Google limiti doldu · çevrimdışı sonuçlar", "Google limiti doldu · oflayn nəticələr")
    PlacesFailureKind.UNAVAILABLE -> tl(lang, "Arama API’si kullanılamıyor · API ayarlarını kontrol et", "Axtarış API-si işləmir · API tənzimləmələrini yoxla")
}

/** `placesFailureDetail`: ayrıntı metni; `appBlocked`'ta paket adı `com.kuryelog.app`; kod eki `" (<reason>)"` ya da `" (HTTP <status>)"`. */
fun placesFailureDetail(f: PlacesFailure, lang: Lang): String {
    val code = if (f.reason.isNullOrEmpty()) (if (f.status == 0) "" else " (HTTP ${f.status})") else " (${f.reason})"
    val pkg = "com.kuryelog.app"
    return when (f.kind) {
        PlacesFailureKind.OFFLINE -> tl(lang, "İnternet yok.", "İnternet yoxdur.")
        PlacesFailureKind.MISSING_KEY -> tl(lang, "Ne Ayarlarda ne APK içinde Google anahtarı var.", "Nə tənzimləmələrdə, nə də APK-da Google açarı var.")
        PlacesFailureKind.KEY_INVALID -> tl(
            lang, "Anahtar geçersiz$code. APK anahtarıysa doğru anahtarla yeni APK gerekir.",
            "Açar etibarsızdır$code. APK açarıdırsa düzgün açarla yeni APK lazımdır.",
        )
        PlacesFailureKind.APP_BLOCKED -> tl(
            lang, "Anahtar bu uygulamaya izin vermiyor$code. Google Cloud’da Android kısıtlamasına $pkg ve bu APK’nın SHA-1’ini ekle.",
            "Açar bu tətbiqə icazə vermir$code. Google Cloud-da Android məhdudiyyətinə $pkg və bu APK-nın SHA-1-ni əlavə et.",
        )
        PlacesFailureKind.API_DISABLED -> tl(
            lang, "Anahtar geçerli ama projede Places API (New) açık değil$code. Google Cloud → API Kitaplığı → Places API (New) → Etkinleştir.",
            "Açar düzgündür, amma layihədə Places API (New) aktiv deyil$code. Google Cloud → API Library → Places API (New) → Enable.",
        )
        PlacesFailureKind.API_BLOCKED -> tl(
            lang, "Anahtarın API listesinde Places API (New) yok$code. Anahtar ayarlarında API kısıtlamasına ekle.",
            "Açarın API siyahısında Places API (New) yoxdur$code. Açar parametrlərində API məhdudiyyətinə əlavə et.",
        )
        PlacesFailureKind.BILLING -> tl(lang, "Projede faturalandırma kapalı$code.", "Layihədə ödəniş hesabı bağlıdır$code.")
        PlacesFailureKind.QUOTA -> tl(lang, "Kota veya istek limiti doldu$code; biraz sonra tekrar dene.", "Kvota və ya sorğu limiti doldu$code; bir az sonra yenidən yoxla.")
        PlacesFailureKind.UNAVAILABLE -> tl(lang, "Google yanıt vermedi$code.", "Google cavab vermədi$code.")
    }
}
