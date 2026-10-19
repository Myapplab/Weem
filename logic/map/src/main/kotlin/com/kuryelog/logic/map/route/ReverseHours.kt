package com.kuryelog.logic.map.route

import java.time.Instant
import kotlin.math.ceil

/**
 * Kullanıcının bir sokakta ters yönden geçişe izin verdiği günlük saat aralığı — `lib/routing/route_hours.dart` (MAP.md 4.3).
 * Bakü sabit UTC+4 kullanır; telefonun saat dilimine bağlı değildir.
 */
data class ReverseHours(val startMinute: Int, val endMinute: Int) {
    val valid: Boolean get() = startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute

    fun allows(instant: Instant): Boolean = allowsAt(instant.epochSecond)

    /** Geçiş, izin aralığı bitmeden tamamlanmalı: girişte, her 60 sn'de ve çıkışta (yukarı yuvarlanmış ms) izinli. */
    fun allowsTrip(entered: Instant, seconds: Double): Boolean {
        if (!allows(entered) || !seconds.isFinite() || seconds < 0) return false
        var s = 60.0
        while (s < seconds) {
            if (!allows(entered.plusMillis(Math.round(s * 1000)))) return false
            s += 60
        }
        return allows(entered.plusMillis(ceil(seconds * 1000).toLong()))
    }

    /** `HH:MM–HH:MM`. */
    val label: String get() = "${clock(startMinute)}–${clock(endMinute)}"

    private fun allowsAt(epochSecond: Long): Boolean {
        if (!valid) return false
        val minute = (Math.floorMod(epochSecond + BAKU_OFFSET_S, 86_400L) / 60).toInt()
        return if (startMinute < endMinute) minute >= startMinute && minute < endMinute else minute >= startMinute || minute < endMinute
    }

    private companion object {
        const val BAKU_OFFSET_S = 4 * 3600L

        fun clock(m: Int) = "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }
}
