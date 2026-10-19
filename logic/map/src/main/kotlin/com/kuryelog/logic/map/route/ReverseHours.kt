package com.kuryelog.logic.map.route

/**
 * Bir sokakta ters yönden geçişe izin verilen günlük saat aralığı (dakika, Bakü sabit UTC+4; `MAP.md` 4.3).
 * Zamanlar UTC epoch ms. İskelet (A6.2a): gövdeler A6.4'te.
 */
class ReverseHours(val startMinute: Int, val endMinute: Int) {
    val valid: Boolean get() = TODO("A6.4")
    fun allows(instantMs: Long): Boolean = TODO("A6.4")
    fun allowsTrip(enteredMs: Long, seconds: Double): Boolean = TODO("A6.4")
}
