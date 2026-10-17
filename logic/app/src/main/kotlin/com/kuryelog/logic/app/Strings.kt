package com.kuryelog.logic.app

/**
 * Kabuk metinleri, iki dil yan yana (G3). Her giriş iki dili birlikte taşır; eksik çeviri derlenmez.
 * Ekrana giden metin burada olmalı; `:app` kaynağında düz Türkçe/Azerice metin testle yakalanır.
 */
enum class Text(val tr: String, val az: String) {
    TAB_MAP("Harita", "Xəritə"),
    TAB_ORDERS("Siparişler", "Sifarişlər"),
    TAB_SCOOTER("Scooter", "Scooter"),
    TAB_REMOTE("Uzaktan", "Uzaqdan"),
    TAB_MORE("Daha fazla", "Daha çox"),

    PLACEHOLDER_COMING("Bu ekran sonraki aşamada gelecek", "Bu ekran növbəti mərhələdə gələcək"),
    PLACEHOLDER_COUNTER("Sekme durumu denemesi: %d", "Tab vəziyyəti yoxlaması: %d"),

    SETTINGS_TITLE("Ayarlar", "Ayarlar"),
    SETTINGS_LANGUAGE("Dil", "Dil"),
    SETTINGS_THEME("Tema", "Tema"),
    LANG_TR("Türkçe", "Türkçe"),
    LANG_AZ("Azərbaycanca", "Azərbaycanca"),
    LANG_SYSTEM("Telefon dili", "Telefonun dili"),
    THEME_SYSTEM("Telefonu izle", "Telefona uyğun"),
    THEME_LIGHT("Gündüz", "Gündüz"),
    THEME_DARK("Gece", "Gecə"),
    VERSION("Sürüm %s", "Versiya %s");

    fun get(lang: Lang): String = if (lang == Lang.AZ) az else tr
}

val Tab.label: Text
    get() = when (this) {
        Tab.MAP -> Text.TAB_MAP
        Tab.ORDERS -> Text.TAB_ORDERS
        Tab.SCOOTER -> Text.TAB_SCOOTER
        Tab.REMOTE -> Text.TAB_REMOTE
        Tab.MORE -> Text.TAB_MORE
    }
