package com.kuryelog.logic.capture

import com.kuryelog.logic.core.Bilingual

/** `:capture`'ın ekrana giden metinleri, iki dil yan yana (G3; CAPTURE.md 7.2). Android `:capture` kaynağında düz metin testle yakalanır. */
enum class CaptureText(override val tr: String, override val az: String) : Bilingual {
    // Son hata (CAPTURE.md 1.8; RS `WatchService.kt:230, 364`). %d = Android ekran görüntüsü hata kodu.
    OCR_UNAVAILABLE("OCR ekranı okunamadı (kod %d); erişilebilirlik okumaları sürüyor.", "OCR ekranı oxumadı (kod %d); əlçatanlıq oxunuşları davam edir."),
    BADGE_FAILED("Rozet açılamadı: ", "Nişan açılmadı: "),

    // R19/E7 ekran okuma kapalı uyarısı (CAPTURE.md 5.2; RS `GpsService.kt:126-131`; servis adı manifest etiketi "WEEM ekran kaydı").
    SCREEN_READING_CHANNEL("Ekran okuma uyarısı", "Ekran oxuma xəbərdarlığı"),
    SCREEN_READING_OFF_TITLE("Ekran okuma kapalı — kayıt yapılmıyor", "Ekran oxuma bağlıdır — qeyd aparılmır"),
    SCREEN_READING_OFF_TEXT("Wolt ve Bolt okunmuyor. Açmak için dokun: Erişilebilirlik → WEEM ekran kaydı.",
        "Wolt və Bolt oxunmur. Açmaq üçün toxun: Əlçatanlıq → WEEM ekran kaydı."),

    // Daha fazla → OCR anahtarı ve izlenen uygulamalar kartı (CAPTURE.md 1.9, 6.4; RS `MainActivity.kt:1140, 1171-1185`, `I18n.kt`).
    OCR_TOGGLE("Görünür ekran OCR yedeği", "Görünən ekran üçün OCR ehtiyatı"),
    APPS_TITLE("İzlenen uygulamalar", "İzlənən tətbiqlər"),
    APPS_PICK("Uygulamaları seç", "Tətbiqləri seç"),
    APPS_NONE("yok", "yoxdur"),
    APPS_LAST_READ("son okuma", "son oxuma"),
    APPS_NEVER("hiç okunmadı", "heç oxunmayıb"),
    APPS_OFFERS("teklif", "təklif"),
    APPS_ACCEPT_SEEN("Accept butonu görüldü", "Accept düyməsi görünüb"),
}
