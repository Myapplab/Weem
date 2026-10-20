package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.net.HttpPort
import com.kuryelog.logic.map.net.HttpRequest
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Anahtar denetimi sonucu (`probeGoogleKey`): [count] gerçek öneri sayısı, [failure] varsa neden. */
data class GoogleKeyProbe(val count: Int, val failure: PlacesFailure? = null)

/** "API ve bağlantılar" ekranının saf metinleri ve kuralları (MAP.md 8.9; `api_settings.dart`). */
object ApiChecks {
    /** Aynı kart bu süre içinde yeniden denetlenmez. */
    const val RECHECK_MS = 5 * 60_000L

    /** Denetimin üst süresi; aşılırsa [checkFailed]. */
    const val CHECK_TIMEOUT_MS = 12_000L

    /** Maps denetimi anahtar sorununu haritaya bağlar; Places kapalıysa anahtarı suçlamaz. */
    fun mapsKeyVerdict(probe: GoogleKeyProbe, lang: Lang): String {
        val f = probe.failure ?: return tl(
            lang, "APK anahtarı geçerli ve bu uygulamaya izinli. Harita gri kalırsa projede Maps SDK for Android açık mı bak.",
            "APK açarı düzgündür və bu tətbiqə icazəlidir. Xəritə boz qalırsa layihədə Maps SDK for Android aktivdirmi yoxla.",
        )
        return when (f.kind) {
            PlacesFailureKind.KEY_INVALID, PlacesFailureKind.APP_BLOCKED, PlacesFailureKind.MISSING_KEY, PlacesFailureKind.BILLING ->
                placesFailureDetail(f, lang) + " " + tl(lang, "Bu durumda Google harita da yüklenmez.", "Bu halda Google xəritəsi də yüklənmir.")
            PlacesFailureKind.API_DISABLED, PlacesFailureKind.API_BLOCKED -> tl(
                lang, "APK anahtarı Google’da tanınıyor; Places API (New) bu anahtara kapalı. Harita için Maps SDK for Android ayrıca açık olmalı.",
                "APK açarı Google-da tanınır; Places API (New) bu açara bağlıdır. Xəritə üçün Maps SDK for Android ayrıca aktiv olmalıdır.",
            )
            else -> placesFailureDetail(f, lang)
        }
    }

    /** Places denetiminin sonucu ("Places araması yanıt verdi · N gerçek öneri." ya da ayrıntı). */
    fun placesVerdict(probe: GoogleKeyProbe, lang: Lang): String = probe.failure?.let { placesFailureDetail(it, lang) }
        ?: tl(lang, "Places araması yanıt verdi · ${probe.count} gerçek öneri.", "Places axtarışı cavab verdi · ${probe.count} həqiqi təklif.")

    fun checkFailed(lang: Lang) = tl(
        lang, "Bağlantı veya yetki doğrulanamadı; internet, API etkinliği ve anahtar kısıtlamasını kontrol et.",
        "Bağlantı və ya icazə təsdiqlənmədi; interneti, API aktivliyini və açar məhdudiyyətini yoxla.",
    )

    /** Kartın son denetimi [lastAtMs]'ten [RECHECK_MS] geçmediyse yeni denetim yapılmaz. */
    fun canCheck(lastAtMs: Long?, nowMs: Long) = lastAtMs == null || nowMs - lastAtMs >= RECHECK_MS

    /** Boş → "Anahtar girilmedi"; aksi `••••` + son 4 karakter. */
    fun maskedKey(key: String, lang: Lang): String =
        if (key.isEmpty()) tl(lang, "Anahtar girilmedi", "Açar yazılmayıb") else "••••" + (if (key.length <= 4) "" else key.substring(key.length - 4))

    /** Anahtarın kendisi Google sunucusunda sınanır ("Baku", Bakü merkezi, oturumsuz). */
    suspend fun probeGoogleKey(http: com.kuryelog.logic.map.net.HttpPort, creds: GoogleCredentials?, lang: Lang): GoogleKeyProbe = try {
        GoogleKeyProbe(PlacesRest.autocomplete(http, creds, "Baku", "", com.kuryelog.logic.map.geo.Baku.center, lang).size)
    } catch (e: PlacesException) {
        GoogleKeyProbe(0, e.failure)
    }

    /** Maps SDK kartı: anahtar yoksa ya da Play Services hazır değilse ağa çıkılmaz ([probe] çağrılmaz). */
    suspend fun mapsSdk(hasKey: Boolean, playServices: Int, lang: Lang, probe: suspend () -> GoogleKeyProbe): String = when {
        !hasKey -> ApiText.MAPS_NO_KEY.get(lang)
        playServices != 0 -> tl(lang, "Google Play Services hazır değil (kod $playServices).", "Google Play Services hazır deyil (kod $playServices).")
        else -> mapsKeyVerdict(probe(), lang)
    }

    /** ORS kartı (`/geocode/autocomplete?text=Baku&size=1`): anahtar yoksa bilgi; 200 ve `features` dizisi değilse hata fırlatır (ekran [checkFailed] gösterir). */
    suspend fun probeOrs(http: HttpPort, key: String, lang: Lang): String {
        if (key.isEmpty()) return ApiText.ORS_NO_KEY.get(lang)
        val url = "https://api.openrouteservice.org/geocode/autocomplete?api_key=${URLEncoder.encode(key, "UTF-8")}&text=Baku&size=1"
        val r = http.send(HttpRequest("GET", url, timeoutMs = CHECK_TIMEOUT_MS))
        if (r.status != 200 || JSONObject(r.body).opt("features") !is JSONArray) throw IllegalStateException("ors")
        return ApiText.ORS_OK.get(lang)
    }

    /** Rota motoru kartı: graf yoksa "Yol ağı yüklenmedi.", varsa "<N> yol parçası hazır". */
    fun routeEngine(edgeCount: Int?, lang: Lang): String =
        if (edgeCount == null) ApiText.ROUTE_NONE.get(lang) else "$edgeCount ${ApiText.ROUTE_READY.get(lang)}"

    /** "Son kontrol HH:mm". */
    fun lastChecked(hhmm: String, lang: Lang) = "${tl(lang, "Son kontrol", "Son yoxlama")}: $hhmm"
}

/** "API ve bağlantılar" sabit metinleri (YolGo `api_settings.dart`; MAP.md 8.9). CARTO kartı ve "Google Maps uyumluluk modu" WEEM'de yok (altlıklar MAP.md 6.4). */
enum class ApiText(override val tr: String, override val az: String) : Bilingual {
    TITLE("API ve bağlantılar", "API və bağlantılar"),
    OPEN("API ve bağlantılar — anahtarlar ve denetimler", "API və bağlantılar — açarlar və yoxlamalar"),
    MAPS_TITLE("Google Maps · SDK", "Google Maps · SDK"),
    MAPS_INFO("APK içindeki Android anahtarı kullanılır. Harita, arama ve rota yetkileri ayrı kontrol edilir.", "APK daxilindəki Android açarı işlənir. Xəritə, axtarış və marşrut icazələri ayrı yoxlanılır."),
    MAPS_NO_KEY("APK içinde Google anahtarı yok; harita sade altlıkla çalışır.", "APK-da Google açarı yoxdur; xəritə sadə altlıqla işləyir."),
    MAPS_KEY_APK("Maps SDK anahtarı değişikliği yeni APK gerektirir.", "Maps SDK açarını dəyişmək üçün yeni APK lazımdır."),
    PLACES_TITLE("Google Places (New) · Adres arama", "Google Places (New) · Ünvan axtarışı"),
    PLACES_INFO("Android SDK ve oturum belirteci. Boşsa APK anahtarı denenir. Places API (New) ve faturalandırma açık olmalı.", "Android SDK və sessiya tokeni. Boşdursa APK açarı yoxlanılır. Places API (New) və ödəniş aktiv olmalıdır."),
    ORS_TITLE("OpenRouteService · Arama yedeği", "OpenRouteService · Axtarış ehtiyatı"),
    ORS_INFO("Pelias arama ve otomatik tamamlama. Scooter rotasını WEEM cihaz içinde hesaplar.", "Pelias axtarışı və avtomatik tamamlama. Skuter marşrutunu WEEM cihazda hesablayır."),
    ORS_NO_KEY("Anahtar yok; çevrimdışı sokak ve mekân sonuçları kullanılabilir.", "Açar yoxdur; oflayn küçə və obyekt nəticələri işləyə bilər."),
    ORS_OK("Pelias araması yanıt verdi.", "Pelias axtarışı cavab verdi."),
    ROUTE_TITLE("Rota motoru", "Marşrut mühərriki"),
    ROUTE_INFO("Cihaz içi yol ağı · çevrimdışı · harita API’sinden bağımsız.", "Cihazdakı yol şəbəkəsi · oflayn · xəritə API-sindən asılı deyil."),
    ROUTE_NONE("Yol ağı yüklenmedi.", "Yol şəbəkəsi yüklənməyib."),
    ROUTE_READY("yol parçası hazır", "yol hissəsi hazırdır"),
    CHECK("Bağlantıyı kontrol et", "Bağlantını yoxla"),
    EDIT_KEY("Anahtarı düzenle", "Açarı dəyiş"),
    KEY_LABEL("API anahtarı", "API açarı"),
    SHOW_HIDE("Göster / gizle", "Göstər / gizlət"),
    CANCEL("Vazgeç", "Ləğv et"),
    SAVE("Kaydet", "Yadda saxla"),
    FOOTER("Kontroller yalnızca bu düğmelere dokununca yapılır; 5 dk önbellek.", "Yoxlamalar yalnız bu düymələrə toxunanda edilir; 5 dəq keş."),
}
