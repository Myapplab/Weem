package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONObject

/** Kimlik (MAP.md 8.8): anahtar + Android kısıtlaması için paket ve imza SHA-1 (onaltılık, büyük harf, iki noktasız). */
data class GoogleCredentials(val key: String, val packageName: String? = null, val cert: String? = null)

enum class PlacesFailureKind { OFFLINE, MISSING_KEY, KEY_INVALID, APP_BLOCKED, API_DISABLED, API_BLOCKED, BILLING, QUOTA, UNAVAILABLE }

/** [reason] Google'ın `ErrorInfo.reason` değeri; [status] HTTP kodu. Ham Google mesajı hiçbir yerde tutulmaz. */
data class PlacesFailure(val kind: PlacesFailureKind, val reason: String? = null, val status: Int = 0)

data class PlaceDetails(val point: GeoPoint, val address: String?, val restaurant: Boolean)

/** Places (New) REST (MAP.md 8.8). İskelet (A6.2b): istek kurma ve ayrıştırma gövdeleri A6.6'da. */
object PlacesRest {
    /** HTTP durum + gövde → hata türü (`error.details[].reason`, `error.status`; 429 → quota; çözülemezse unavailable). */
    fun classify(status: Int, body: String): PlacesFailure = TODO("A6.6")

    /** `location` sonlu değilse `FormatException`; restoran türleri {restaurant, cafe, meal_takeaway, fast_food_restaurant, bakery}. */
    fun parseDetails(json: JSONObject): PlaceDetails = TODO("A6.6")

    /** `suggestions[].placePrediction` (placeId ve başlık zorunlu); bozuk öğeler atlanır. */
    fun parseAutocomplete(json: JSONObject): List<SearchItem> = TODO("A6.6")

    /** UUID v4'ten türetilen oturum belirteci (≤36 karakter, `[A-Za-z0-9_-]`). */
    fun newSessionToken(): String = TODO("A6.6")
}

/** `placesFailureShort` (TR/AZ tablosu MAP.md 8.8): arama çubuğunun altındaki tek satır. */
fun placesFailureShort(f: PlacesFailure, lang: Lang): String = TODO("A6.6")

/** `placesFailureDetail`: ayrıntı metni; `appBlocked`'ta paket adı `com.kuryelog.app`; kod eki `" (<reason>)"` ya da `" (HTTP <status>)"`. */
fun placesFailureDetail(f: PlacesFailure, lang: Lang): String = TODO("A6.6")
