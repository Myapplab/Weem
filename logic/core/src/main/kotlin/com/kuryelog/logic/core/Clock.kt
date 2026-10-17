package com.kuryelog.logic.core

import java.time.ZoneId

/**
 * Saat (CORE.md 1.10): duvar saati (ms) kayıt ve mesajlarda; monoton saat (Android `elapsedRealtime`) ev kuralı, izleyici planı ve fix
 * yaşında; gün klasörü ve saat biçimi telefonun saat diliminde. Testlerde sahte saat.
 */
interface Clock {
    fun wallMs(): Long
    fun monoMs(): Long
    fun zone(): ZoneId

    /** JVM: monoton = `System.nanoTime`. Android katmanı `SystemClock.elapsedRealtime` kullanır (fix zamanları onunla gelir). */
    object Jvm : Clock {
        override fun wallMs() = System.currentTimeMillis()
        override fun monoMs() = System.nanoTime() / 1_000_000
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
