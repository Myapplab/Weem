package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.map.day.DayState
import java.time.Instant
import java.time.ZoneId

/** İstatistik ekranı sekme etiketleri (`_tab`, MAP.md 12.7). */
object StatsTabs {
    private val TR_MONTHS = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    private val AZ_MONTHS = listOf("yan", "fev", "mar", "apr", "may", "iyn", "iyl", "avq", "sen", "okt", "noy", "dek")

    /** Başlangıç yoksa `#<sıra+1>`; süren gün ya da bugün → "Bugün HH:MM"/"Bu gün HH:MM"; değilse "<gün> <ay> HH:MM". */
    fun label(d: DayState, index: Int, nowMs: Long, zone: ZoneId, lang: Lang): String {
        val start = d.startedAtMs ?: return "#${index + 1}"
        val clock = DurationText.clock(start, zone)
        if (d.active || LogTime.day(start, zone) == LogTime.day(nowMs, zone)) return "${if (lang == Lang.AZ) "Bu gün" else "Bugün"} $clock"
        val t = Instant.ofEpochMilli(start).atZone(zone)
        val months = if (lang == Lang.AZ) AZ_MONTHS else TR_MONTHS
        return "${t.dayOfMonth} ${months[t.monthValue - 1]} $clock"
    }

    /** Odak: başlangıcı eşleşen sekmenin sırası; yoksa 0. */
    fun focusIndex(days: List<DayState>, focusStartMs: Long?): Int =
        if (focusStartMs == null) 0 else days.indexOfFirst { it.startedAtMs == focusStartMs }.takeIf { it >= 0 } ?: 0

    fun emptyText(lang: Lang): String = if (lang == Lang.AZ) {
        "Hələ gün qeydi yoxdur. Gün evdən çıxanda özü başlayır; km, vaxt, sifarişlər və işıqforlar burada toplanır."
    } else {
        "Henüz gün kaydı yok. Gün evden çıkınca kendiliğinden başlar; km, süre, siparişler ve trafik ışıkları burada toplanır."
    }
}
