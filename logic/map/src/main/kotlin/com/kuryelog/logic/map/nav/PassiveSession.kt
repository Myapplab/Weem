package com.kuryelog.logic.map.nav

/**
 * Pasif oturum (YolGo `day_scope.dart:8-9`, `main.dart:63-74, 93-108`; MAP.md 6.2): gün kapalıyken uygulama arka plana geçince an yazılır; açılışta ya da öne
 * dönüşte gün hâlâ kapalı ve en az 5 dk geçmişse rota silinir. Anahtar her öne dönüşte silinir.
 */
object PassiveSession {
    /** `map` tercih dosyasında, `Long` ms. */
    const val KEY = "passive.pausedAt.v2"
    const val CLEAR_AFTER_MS = 5 * 60_000L

    /** Arka plana geçerken yazılacak değer: gün kapalıysa şimdi, açıksa null (yazılmaz). */
    fun pausedAtOnBackground(dayActive: Boolean, nowMs: Long): Long? = if (dayActive) null else nowMs

    /** Öne dönüşte (ya da açılışta) rota silinsin mi? */
    fun shouldClearRoute(dayActive: Boolean, pausedAtMs: Long?, nowMs: Long): Boolean =
        !dayActive && pausedAtMs != null && nowMs - pausedAtMs >= CLEAR_AFTER_MS
}
