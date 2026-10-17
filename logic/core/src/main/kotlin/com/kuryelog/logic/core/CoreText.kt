package com.kuryelog.logic.core

/** `:core`'un ekrana ya da kayda giden metinleri, iki dil yan yana (G3). Android `:core` kaynağında düz metin testle yakalanır. */
enum class CoreText(override val tr: String, override val az: String) : Bilingual {
    // RS `Store.kt:69`: yazılamayan kayıt satırı (`LogStore.lastError`).
    WRITE_FAILED("Kayıt yazılamadı: ", "Qeyd yazılmadı: "),

    // Vardiya (CORE.md 1.2, 1.5; RS `GpsService.kt`, `AutoStartNotice.kt`, `I18n.kt`; ad "WEEM").
    START_FAILED("Vardiya başlatılamadı: ", "Növbə başlamadı: "),
    SHIFT_CHANNEL("Vardiya kaydı", "Növbə qeydi"),
    SHIFT_NOTIF_TITLE("WEEM kayıtta", "WEEM qeyd edir"),
    SHIFT_NOTIF_TEXT("Vardiya açık; GPS kaydı etkin", "Növbə açıqdır; GPS qeydi aktivdir"),
    SHIFT_STOP_ACTION("Vardiyayı bitir", "Növbəni bitir"),
    AUTO_START_CHANNEL("Otomatik başlatma", "Avtomatik başlatma"),
    HOME_LEFT_TITLE("Evden çıktın", "Evdən çıxdın"),
    HOME_LEFT_TEXT("Vardiyayı başlatmak için dokun.", "Növbəni başlatmaq üçün toxun."),
    SHIFT_INTERRUPTED_TITLE("Vardiya durdu", "Növbə dayandı"),
    SHIFT_INTERRUPTED_TEXT("GPS kaydı yok. Devam etmek için dokun.", "GPS qeydi yoxdur. Davam etmək üçün toxun."),
}
