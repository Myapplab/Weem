package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState

/** Gün istatistiğinde otomatik başlangıç/bitiş nedeni satırı (`dayReasons`, MAP.md 12.8): elle ya da eski kayıt (neden yok) → null. İskelet (A6.2b). */
object DayReasons {
    fun of(s: DayState, lang: Lang = Lang.TR): String? = TODO("A6.9")
}
