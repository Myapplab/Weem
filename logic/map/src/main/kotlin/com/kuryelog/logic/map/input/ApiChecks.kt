package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang

/** Anahtar denetimi sonucu (`probeGoogleKey`): [count] gerçek öneri sayısı, [failure] varsa neden. */
data class GoogleKeyProbe(val count: Int, val failure: PlacesFailure? = null)

/** "API ve bağlantılar" ekranının saf metinleri (MAP.md 8.9). İskelet (A6.2b): gövdeler A6.6'da. */
object ApiChecks {
    /** Maps denetimi anahtar sorununu haritaya bağlar; Places kapalıysa anahtarı suçlamaz. */
    fun mapsKeyVerdict(probe: GoogleKeyProbe, lang: Lang): String = TODO("A6.6")

    /** Boş → "Anahtar girilmedi"; aksi `••••` + son 4 karakter. */
    fun maskedKey(key: String, lang: Lang): String = TODO("A6.6")
}
