package com.kuryelog.logic.map.nav

/**
 * Konum kaynağı birleştiricisinin süzgeci (MAP.md 7.6, 7.16): aynı `utc` ikinci kez yayınlanmaz; arka planda en çok saniyede bir okunur,
 * öne dönünce hemen. Dart `NativeTelemetry.pollFix` kuralı (WEEM'de yerel telemetri köprüsü yok). İskelet (A6.2b).
 */
class FixGate {
    /** [foreground] değilse son okumadan beri ≥1 000 ms geçmeli; geçerse okuma yapılmış sayılır. */
    fun mayRead(nowMs: Long, foreground: Boolean): Boolean = TODO("A6.6")

    /** [utc] bir öncekinden farklıysa true (yayınla), aynıysa false. */
    fun accept(utc: Long): Boolean = TODO("A6.6")
}
