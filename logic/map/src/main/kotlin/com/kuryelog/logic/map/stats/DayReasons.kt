package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState

/** Gün istatistiğinde otomatik başlangıç/bitiş nedeni satırı (`dayReasons`, MAP.md 12.7): elle ya da eski kayıt (neden yok) → null. */
object DayReasons {
    fun of(s: DayState, lang: Lang = Lang.TR): String? {
        fun tl(tr: String, az: String) = if (lang == Lang.AZ) az else tr
        val parts = listOfNotNull(
            if (s.startReason == "home_departure") tl("Evden çıkınca başladı", "Evdən çıxanda başladı") else null,
            if (s.startReason == "rawstats_shift_start") tl("Rawstats vardiyasıyla başladı", "Rawstats növbəsi ilə başladı") else null,
            if (s.endReason == "home_arrival") tl("Eve dönünce bitti", "Evə qayıdanda bitdi") else null,
            if (s.endReason == "rawstats_shift_end") tl("Rawstats vardiyası bitince bitti", "Rawstats növbəsi bitəndə bitdi") else null,
        )
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }
}
