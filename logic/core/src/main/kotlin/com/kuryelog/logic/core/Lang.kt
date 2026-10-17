package com.kuryelog.logic.core

/** Dil ayarı (G3). Tercih anahtarı `lang` (`kuryelog` dosyası): `tr` | `az` | boş = telefon dili. */
enum class Lang(val key: String) {
    TR("tr"),
    AZ("az");

    companion object {
        const val PREF_KEY = "lang"

        /** Ayar `tr`/`az` ise o; değilse telefon dili `az` ise Azerice, aksi hâlde Türkçe (YolGo `I18n.resolve`). */
        fun resolve(setting: String?, systemLanguage: String?): Lang =
            entries.firstOrNull { it.key == setting }
                ?: if (systemLanguage?.lowercase() == "az") AZ else TR
    }
}

/** İki dilli metin (G3): her giriş iki dili birlikte taşır; eksik çeviri derlenmez. `:app` metinleri `logic/app` `Text`'te, `:core`'unkiler [CoreText]'te. */
interface Bilingual {
    val tr: String
    val az: String
    fun get(lang: Lang): String = if (lang == Lang.AZ) az else tr
}
