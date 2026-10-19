package com.kuryelog.logic.map.nav

/**
 * Konum kaynağı birleştiricisinin süzgeci (MAP.md 7.6, 7.16): aynı `utc` ikinci kez yayınlanmaz; arka planda en çok saniyede bir okunur,
 * öne dönünce hemen. Dart `NativeTelemetry.pollFix` kuralı (WEEM'de yerel telemetri köprüsü yok).
 */
class FixGate {
    /** [foreground] değilse son okumadan beri ≥1 000 ms geçmeli; geçerse okuma yapılmış sayılır. */
    private var lastReadMs: Long? = null
    private var lastUtc: Long? = null

    fun mayRead(nowMs: Long, foreground: Boolean): Boolean {
        val last = lastReadMs
        if (!foreground && last != null && nowMs - last < BACKGROUND_MS) return false
        lastReadMs = nowMs
        return true
    }

    /** [utc] bir öncekinden farklıysa true (yayınla), aynıysa false. */
    fun accept(utc: Long): Boolean {
        if (utc == lastUtc) return false
        lastUtc = utc
        return true
    }

    companion object {
        const val BACKGROUND_MS = 1_000L
    }
}
