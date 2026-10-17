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
    SAVE("Kaydet", "Yadda saxla"),
    CANCEL("İptal", "Ləğv et"),

    // G1 vardiya kartı (CORE.md 1.2; RS `I18n` shift_start/shift_stop).
    SHIFT_START("▶  Vardiyayı BAŞLAT", "▶  Növbəni BAŞLAT"),
    SHIFT_STOP("■  Vardiyayı BİTİR", "■  Növbəni BİTİR"),
    SHIFT_ON("Vardiya açık · başlangıç %s", "Növbə açıqdır · başlanğıc %s"),
    SHIFT_OFF("Vardiya kapalı", "Növbə bağlıdır"),
    SHIFT_INTERRUPTED("GPS kaydı durdu; sürdürmek için başlat düğmesine dokun.", "GPS qeydi dayandı; davam etmək üçün başlat düyməsinə toxun."),
    SHIFT_NEEDS_LOCATION("Vardiya kaydı için konum izni gerekli.", "Növbə qeydi üçün məkan icazəsi lazımdır."),

    // G2 ev konumu (CORE.md 2.2; RS ev ekranı metinleri).
    HOME_TITLE("Ev konumu", "Ev məkanı"),
    HOME_OPEN("Ev konumu ve otomatik vardiya", "Ev məkanı və avtomatik növbə"),
    HOME_SAVED("Kayıtlı ev", "Saxlanılmış ev"),
    HOME_NONE("Ev konumu seçilmedi", "Ev məkanı seçilməyib"),
    HOME_UPDATED("Son güncelleme: %s", "Son yenilənmə: %s"),
    HOME_UNKNOWN("bilinmiyor", "bilinmir"),
    HOME_EDIT("Düzenle", "Düzəliş et"),
    HOME_DELETE("Sil", "Sil"),
    HOME_DELETE_ASK("Ev konumunu sil?", "Ev məkanını sil?"),
    HOME_PICK("Ev konumunu seç", "Ev məkanını seç"),
    HOME_USE_CURRENT("Şu anki konumumu kullan", "Hazırkı məkanımı istifadə et"),
    HOME_MEASURING("%d saniye ölçülüyor…", "%d saniyə ölçülür…"),
    HOME_ACCURACY("Doğruluk: ±%s m", "Dəqiqlik: ±%s m"),
    HOME_DRAFT("Seçilen konum · taslak", "Seçilmiş məkan · qaralama"),
    HOME_WARN("Doğruluk %d m'den kötü; daha açık bir yerde yeniden ölçebilirsin.", "Dəqiqlik %d m-dən pisdir; daha açıq yerdə yenidən ölçə bilərsən."),
    HOME_NO_FIX("Güncel konum alınamadı; dışarıda tekrar dene.", "Cari məkan alınmadı; çöldə yenidən cəhd et."),
    HOME_NO_PROVIDER("Konumu açıp tekrar dene.", "Məkanı açıb yenidən cəhd et."),
    HOME_BACKGROUND("Otomatik vardiya için konum iznini \"Her zaman izin ver\" yap.", "Avtomatik növbə üçün məkan icazəsini \"Həmişə icazə ver\" et."),
    HOME_BACKGROUND_BUTTON("İzni aç", "İcazəni aç"),
    HOME_RULE(
        "Ev ayarlıysa: evden kuş uçuşu %d m uzaklaşıp GPS hızı en az %d km/s olunca (art arda iki ölçüm, doğruluk ≤%d m) vardiya kendiliğinden başlar; eve (%d m) dönüp %d saniye yerinde kalınca biter (açık sipariş varken bitmez). Elle başlat/bitir her zaman çalışır. Ev konumu yalnız bu telefonun ayarlarında saklanır; kayıtlara ve dışa aktarmaya yazılmaz.",
        "Ev təyin olunubsa: evdən düz xətt %d m uzaqlaşıb GPS sürəti ən azı %d km/s olanda (ardıcıl iki ölçmə, dəqiqlik ≤%d m) növbə özü başlayır; evə (%d m) qayıdıb %d saniyə yerində qalanda bitir (açıq sifariş varkən bitmir). Əl ilə başlat/bitir həmişə işləyir. Ev məkanı yalnız bu telefonun ayarlarında saxlanılır; qeydlərə və ixraca yazılmır.",
    ),
}

val Tab.label: Text
    get() = when (this) {
        Tab.MAP -> Text.TAB_MAP
        Tab.ORDERS -> Text.TAB_ORDERS
        Tab.SCOOTER -> Text.TAB_SCOOTER
        Tab.REMOTE -> Text.TAB_REMOTE
        Tab.MORE -> Text.TAB_MORE
    }
