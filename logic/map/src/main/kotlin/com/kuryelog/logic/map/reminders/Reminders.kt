package com.kuryelog.logic.map.reminders

import com.kuryelog.logic.core.Lang
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

enum class ReminderKind { WATER, CHECK, FOOD;

    /** JSON/tercih anahtarı (`water`, `check`, `food`). */
    val key: String get() = name.lowercase()
}

/** Bir hatırlatıcı (Dart `ReminderItem`, MAP.md 13.15): [times] günün dakikası (15:15 → 915); [sinceMs] aralık sayacının başı. */
data class ReminderItem(
    val kind: ReminderKind,
    val enabled: Boolean = false,
    val text: String = "",
    val useTimes: Boolean = false,
    val intervalMin: Int = 45,
    val times: List<Int> = emptyList(),
    val onlyShift: Boolean = true,
    val sinceMs: Long = 0,
) {
    fun title(lang: Lang): String = when (kind) {
        ReminderKind.WATER -> "Su iç"
        ReminderKind.CHECK -> if (lang == Lang.AZ) "Yoxla" else "Kontrol et"
        ReminderKind.FOOD -> if (lang == Lang.AZ) "Yemək ye" else "Yemek ye"
    }

    val emoji: String get() = when (kind) { ReminderKind.WATER -> "💧"; ReminderKind.CHECK -> "🔧"; ReminderKind.FOOD -> "🍽️" }

    /** Boşsa varsayılan metin. */
    fun message(lang: Lang): String = text.trim().ifEmpty { defaultText(kind, lang) }

    fun toJson(): JSONObject = JSONObject().put("on", enabled).put("text", text).put("useTimes", useTimes).put("every", intervalMin)
        .put("at", JSONArray(times)).put("shift", onlyShift).put("since", sinceMs)

    companion object {
        fun defaultText(kind: ReminderKind, lang: Lang): String {
            val az = lang == Lang.AZ
            return when (kind) {
                ReminderKind.WATER -> if (az) "Bir neçə qurtum su iç" else "Birkaç yudum su iç"
                ReminderKind.CHECK -> if (az) "Batareyanı, təkəri, əyləci və çantanı yoxla" else "Batarya, lastik, fren ve çantayı kontrol et"
                ReminderKind.FOOD -> if (az) "Yemək vaxtıdır — bir şey ye" else "Yemek zamanı — bir şeyler ye"
            }
        }

        /** Her alanın tipi doğruysa alınır, değilse o alanın varsayılanı. */
        fun fromJson(def: ReminderItem, j: Any?): ReminderItem {
            if (j !is JSONObject) return def
            fun b(k: String, d: Boolean) = (j.opt(k) as? Boolean) ?: d
            fun n(k: String, d: Int) = (j.opt(k) as? Number)?.toInt() ?: d
            val at = j.opt("at") as? JSONArray
            return ReminderItem(
                kind = def.kind,
                enabled = b("on", def.enabled),
                text = (j.opt("text") as? String) ?: def.text,
                useTimes = b("useTimes", def.useTimes),
                intervalMin = n("every", def.intervalMin),
                times = if (at != null) (0 until at.length()).mapNotNull { (at.opt(it) as? Number)?.toInt() } else def.times,
                onlyShift = b("shift", def.onlyShift),
                sinceMs = (j.opt("since") as? Number)?.toLong() ?: def.sinceMs,
            )
        }
    }
}

/** Ekranda gösterilen hatırlatıcı (kart ya da bildirim): [atMs] gösterildiği an. */
data class ActiveReminder(val item: ReminderItem, val atMs: Long)

/** Üç hatırlatıcının ayarı: su (45 dk, açık), kontrol (kapalı), yemek (15:15, kapalı). Eksik alan varsayılan. */
data class ReminderSettings(val items: List<ReminderItem>) {
    fun of(kind: ReminderKind): ReminderItem = items.firstOrNull { it.kind == kind } ?: defaults.items[kind.ordinal]

    val anyEnabled: Boolean get() = items.any { it.enabled }

    fun toJson(): JSONObject = JSONObject().also { o -> items.forEach { o.put(it.kind.key, it.toJson()) } }

    companion object {
        val defaults: ReminderSettings = ReminderSettings(
            listOf(
                ReminderItem(ReminderKind.WATER, enabled = true, intervalMin = 45),
                ReminderItem(ReminderKind.CHECK, intervalMin = 60),
                ReminderItem(ReminderKind.FOOD, useTimes = true, times = listOf(15 * 60 + 15), onlyShift = false),
            ),
        )

        fun fromJson(j: JSONObject?): ReminderSettings = ReminderSettings(defaults.items.map { ReminderItem.fromJson(it, j?.opt(it.kind.key)) })
    }
}

/** `reminderDue` (MAP.md 13.15): zamanlama kuralı; saatli hatırlatma en çok [LATE_MIN] dk geç gösterilir. */
object ReminderDue {
    const val LATE_MIN = 3

    fun due(
        item: ReminderItem,
        nowMs: Long,
        zone: ZoneId,
        lastMs: Long? = null,
        shiftStartMs: Long? = null,
        shiftActive: Boolean,
        snoozeUntilMs: Long? = null,
    ): Boolean {
        if (!item.enabled || !shiftActive) return false
        if (snoozeUntilMs != null) return nowMs >= snoozeUntilMs
        if (item.useTimes) {
            val midnight = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone)
            for (m in item.times) {
                val at = midnight.plusMinutes(m.toLong()).toInstant().toEpochMilli()
                // Yalnız belirlenen saatte: 3 dk'dan geç kalındıysa (uygulama kapalıydı) atlanır.
                if (nowMs < at || (nowMs - at) / 60_000 >= LATE_MIN) continue
                if (lastMs != null && lastMs >= at) continue // bugün bu saat gösterildi
                return true
            }
            return false
        }
        var from = item.sinceMs
        if (item.onlyShift && shiftStartMs != null && shiftStartMs > from) from = shiftStartMs
        if (lastMs != null && lastMs > from) from = lastMs
        return (nowMs - from) / 60_000 >= item.intervalMin
    }
}

object ReminderTiming {
    /** Kart (ve bildirim) bu kadar süre sonra kendiliğinden kapanır (1.7.0: 30 sn). */
    const val SHOW_FOR_MS: Long = 30_000L
}

/** "15:05". */
fun fmtMinuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
