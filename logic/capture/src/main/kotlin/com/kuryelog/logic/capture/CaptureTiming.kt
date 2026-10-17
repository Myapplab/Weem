package com.kuryelog.logic.capture

/** `:capture` süreleri ve sınırları tek yerde (CAPTURE.md 1.3–1.6, 3.1, 3.4, 4.1); Android katmanında düz sayı yok. OCR temposu [OfferCapture]'da. */
object CaptureTiming {
    /** Bir olaydan sonra ikinci ekran okuması (RS `WatchService.kt:103`). */
    const val SECOND_READ_MS = 180L
    /** Bu kadar eski ekran görüntüsünün OCR satırları atılır (RS `WatchService.kt:265`). */
    const val OCR_STALE_MS = 1_800L
    /** Tik aralığı: vardiyada / vardiya dışında (0.22.2 pil; RS `WatchService.kt:329`). */
    const val TICK_SHIFT_MS = 500L
    const val TICK_IDLE_MS = 2_000L
    /** Bildirim dinleyicisinin ev izleyici tiki (E7; RS `NotifService.kt:81`). */
    const val HOME_TICK_MS = 5_000L

    /** Tıklama etiketleri: kaynak düğümden bu derinliğe kadar, bu sayıyı aşınca durur (RS `WatchService.kt:80-87`). */
    const val CLICK_DEPTH = 3
    const val CLICK_LABELS = 25
    /** Pencere gezintisi: derinlik ve düğüm satırı sınırı (RS `WatchService.kt:178-195`). */
    const val WALK_DEPTH = 50
    const val WALK_NODES = 4_000
}
