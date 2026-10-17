package com.kuryelog.logic.core

/** G4 az bildirim kuralları (CORE.md 3.1); Android'siz. */
object NotifyPolicy {
    /** YolGo kökenli her bildirim 30 sn sonra kendiliğinden kalkar. */
    const val TIMEOUT_MS = 30_000L

    /** Uygulama ekrandayken hatırlatıcı yalnız uygulama içi kart; "Dene" (önizleme) yine sistem bildirimi atar. */
    fun inAppOnly(foreground: Boolean, preview: Boolean): Boolean = foreground && !preview

    /** Hava özeti günde bir; saat geri giderse yeniden. */
    fun weatherDue(notifiedDay: String?, day: String?, lastMs: Long, nowMs: Long): Boolean =
        !(notifiedDay != null && notifiedDay == day && lastMs > 0 && nowMs >= lastMs)
}
