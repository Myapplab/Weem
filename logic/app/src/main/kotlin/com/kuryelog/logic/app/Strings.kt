package com.kuryelog.logic.app

import com.kuryelog.logic.core.Bilingual
/**
 * Kabuk metinleri, iki dil yan yana (G3). Her giriş iki dili birlikte taşır; eksik çeviri derlenmez.
 * Ekrana giden metin burada olmalı; `:app` kaynağında düz Türkçe/Azerice metin testle yakalanır.
 */
enum class Text(override val tr: String, override val az: String) : Bilingual {
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
    VERSION("Sürüm %s", "Versiya %s"),

    // R20 çökme kartı ve çizilemeyen sekme (CORE.md 4.1; Rawstats metinleri, ad "WEEM").
    CRASH_TITLE("WEEM kapandı · %s", "WEEM bağlandı · %s"),
    CRASH_HINT("Hata metnini kopyalayıp gönderirsen nedeni bulunur.", "Xəta mətnini kopyalayıb göndərsən, səbəbi tapılar."),
    CRASH_COPY("Hata metnini kopyala", "Xəta mətnini kopyala"),
    CRASH_SHARE("Hata metnini paylaş", "Xəta mətnini paylaş"),
    CRASH_HIDE("Gizle", "Gizlət"),
    PAGE_FAILED("Bu sayfa açılamadı", "Bu səhifə açılmadı"),
    ERROR("Hata: ", "Xəta: "),

    // Daha fazla → Kayıtlar (CORE.md 5.3; A2.6 eski Rawstats ZIP'i burada içe aktarılır).
    RECORDS_TITLE("Kayıtlar", "Qeydlər"),
    RECORDS_OPEN("Kayıtlar ve içe aktarma", "Qeydlər və yükləmə"),
    RECORDS_IMPORT("Eski kayıtları içe aktar (.zip)", "Köhnə qeydləri yüklə (.zip)"),
    RECORDS_IMPORT_STOP("İçe aktarmadan önce vardiyayı durdur.", "Yükləməzdən əvvəl növbəni dayandır."),
    RECORDS_PREPARING("Hazırlanıyor…", "Hazırlanır…"),
    RECORDS_IMPORT_DONE("Kayıtlar aktarıldı; aynı kayıtlar tekrar sayılmadı.", "Qeydlər yükləndi; eyni qeydlər təkrar sayılmadı."),
    RECORDS_DAILY("Günlük kayıtlar", "Günlük qeydlər"),
    RECORDS_EMPTY("Henüz kayıt yok.", "Hələ qeyd yoxdur."),
    RECORDS_DAY("%d dosya · %s", "%d fayl · %s"),
    RECORDS_ARCHIVED("Arşivde", "Arxivdə"),
    BACK("Geri", "Geri"),
}

val Tab.label: Text
    get() = when (this) {
        Tab.MAP -> Text.TAB_MAP
        Tab.ORDERS -> Text.TAB_ORDERS
        Tab.SCOOTER -> Text.TAB_SCOOTER
        Tab.REMOTE -> Text.TAB_REMOTE
        Tab.MORE -> Text.TAB_MORE
    }
