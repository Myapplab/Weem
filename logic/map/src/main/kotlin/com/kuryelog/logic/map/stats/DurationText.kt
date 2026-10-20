package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.Lang
import java.time.Instant
import java.time.ZoneId

/** Süre ve saat biçimleri (`fmtDur`, `fmtClock`; `shift_widgets.dart:15-20`, MAP.md 12.7). */
object DurationText {
    /** < 60 → "N sn"/"N san"; saat varsa "H sa M dk"/"H saat M dəq"; değilse "M dk"/"M dəq". */
    fun fmt(seconds: Long, lang: Lang): String {
        val az = lang == Lang.AZ
        if (seconds < 60) return if (az) "$seconds san" else "$seconds sn"
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        return when {
            h > 0 -> if (az) "$h saat $m dəq" else "$h sa $m dk"
            else -> if (az) "$m dəq" else "$m dk"
        }
    }

    /** `HH:MM` telefon yerel saati. */
    fun clock(ms: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return "%02d:%02d".format(t.hour, t.minute)
    }
}
