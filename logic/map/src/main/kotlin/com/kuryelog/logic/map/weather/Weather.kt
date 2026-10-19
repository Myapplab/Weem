package com.kuryelog.logic.map.weather

import com.kuryelog.logic.core.WeatherNow
import java.time.LocalDateTime

/** Bir saatlik tahmin (MAP.md 10.7): [at] servis saat diliminin duvar saati; bilinmeyen değer null. */
data class WeatherHour(
    val at: LocalDateTime,
    val temperature: Double?,
    val feelsLike: Double? = null,
    val rainChance: Double? = null,
    val rainMm: Double? = null,
    val wind: Double? = null,
    val gust: Double? = null,
    val code: Int? = null,
    val isDay: Boolean? = null,
    val humidity: Double? = null,
)

/**
 * `WeatherReport.parse(raw, now)` (`weather.dart:26–59`, MAP.md 10.7): taban = şimdiki UTC saat başı; `[taban, taban + 8 sa)` içindeki saatler;
 * taban saatinde `current` varsa o; ≥4 satır ve ilk satır taban saati değilse `FormatException`. İskelet (A6.2b): gövde A6.7'de.
 */
class WeatherReport(val fetchedAtMs: Long, val hours: List<WeatherHour>) {
    companion object {
        const val STRIP_HOURS = 4
        const val DETAIL_HOURS = 8

        fun parse(raw: String, nowMs: Long): WeatherReport = TODO("A6.7")
    }
}

/** Rawstats/`:orders` için özet (MAP.md 10.10 `WeatherFeed.summary`): `current` kazanır, saatlik dilim tamamlar; konum yazılmaz; rapor yok ya da bozuksa null. */
object WeatherSummary {
    fun summary(raw: String?): WeatherNow? = TODO("A6.7")
}
