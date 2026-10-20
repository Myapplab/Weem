package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ride.SpeedOptions
import java.time.LocalDateTime

/** Rota panelinin değişken metinleri (YolGo `widgets.dart:33, 396-491`; MAP.md 7.11). */
object NavPanelText {
    /** `fmtMin`: 60 sn altı "<1", aksi yuvarlanmış dakika. */
    fun minutes(seconds: Double): String = if (seconds < 60) "<1" else Math.round(seconds / 60).toString()

    /** Etkin durağın sağındaki süre (`route-eta`): "<dk> dk · HH:mm" / "<dk> dəq · HH:mm". */
    fun eta(seconds: Double, now: LocalDateTime, lang: Lang): String =
        "${minutes(seconds)} ${if (lang == Lang.AZ) "dəq" else "dk"} · ${SpeedOptions.arrivalClock(now, seconds)}"

    /** "Güzergâh: A → B → C" (ilk bacağın ilk 3 sokağı); sokak yoksa null. */
    fun streets(streets: List<String>, lang: Lang): String? =
        if (streets.isEmpty()) null else "${if (lang == Lang.AZ) "Marşrut" else "Güzergâh"}: ${streets.take(3).joinToString(" → ")}"

    /** Varış kartı başlığı: "Vardın: <etiket>" / "Çatdın: <etiket>". */
    fun arrived(label: String, lang: Lang): String = "${if (lang == Lang.AZ) "Çatdın" else "Vardın"}: $label"
}
