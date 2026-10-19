package com.kuryelog.logic.map.route

import java.time.Instant

/**
 * Kullanıcının bir sokakta ters yönden geçişe izin verdiği günlük saat aralığı. Bakü sabit UTC+4 kullanır;
 * telefonun saat dilimine bağlı değildir (MAP.md 4.3).
 */
data class ReverseHours(val startMinute: Int, val endMinute: Int) {
    val valid: Boolean get() = startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute

    fun allows(instant: Instant): Boolean = TODO("A6.4")

    /** Geçiş, izin aralığı bitmeden tamamlanmalı. */
    fun allowsTrip(entered: Instant, seconds: Double): Boolean = TODO("A6.4")
}
