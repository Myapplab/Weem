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
