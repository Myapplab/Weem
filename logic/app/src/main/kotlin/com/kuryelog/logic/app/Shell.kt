package com.kuryelog.logic.app

/** Alt menü sekmeleri (plan 4.1). Sıra menüdeki sıradır; açılış sekmesi Harita. */
enum class Tab(val key: String) {
    MAP("map"),
    ORDERS("orders"),
    SCOOTER("scooter"),
    REMOTE("remote"),
    MORE("more");

    companion object {
        val start: Tab = MAP

        /** Kaydedilmiş anahtardan sekme; bilinmeyen ya da boş değer açılış sekmesine döner. */
        fun fromKey(key: String?): Tab = entries.firstOrNull { it.key == key } ?: start
    }
}

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

/** Tema seçimi: gece/gündüz ya da telefonu izle. Yeni tercih anahtarı `theme` (`kuryelog` dosyası). */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        const val PREF_KEY = "theme"

        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
