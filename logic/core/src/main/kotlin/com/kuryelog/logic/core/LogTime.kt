package com.kuryelog.logic.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Kayıt satırlarının saati ve gün klasörü (CORE.md 0, 5.2; RS `Store.kt:43-51`): telefonun o anki yerel saati. Biçim Rawstats
 * `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")` ile aynı ("2026-10-17T13:11:00.123+0400"); gün `yyyy-MM-dd`.
 */
object LogTime {
    private val tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val dayPattern = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun ts(t: Long, zone: ZoneId = ZoneId.systemDefault()): String = tsFormat.format(Instant.ofEpochMilli(t).atZone(zone))
    fun day(t: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toString()
    /** [ts]'nin tersi; okunamayan değer 0 (Rawstats `parseTs`). */
    fun parseTs(s: String): Long = try { OffsetDateTime.parse(s, tsFormat).toInstant().toEpochMilli() } catch (_: Exception) { 0L }

    /** Gün klasörü adı: `yyyy-MM-dd` ve gerçek bir tarih; değilse `IllegalArgumentException` (yol geçişi reddi). */
    fun checkedDay(day: String): String {
        require(dayPattern.matches(day)) { "Invalid date" }
        try { LocalDate.parse(day) } catch (e: Exception) { throw IllegalArgumentException("Invalid date", e) }
        return day
    }
}
