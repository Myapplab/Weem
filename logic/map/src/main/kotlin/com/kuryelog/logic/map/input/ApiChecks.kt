package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang

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
}
