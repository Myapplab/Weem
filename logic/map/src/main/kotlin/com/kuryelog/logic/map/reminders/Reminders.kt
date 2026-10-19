package com.kuryelog.logic.map.reminders

import com.kuryelog.logic.core.Lang
import org.json.JSONObject
import java.time.ZoneId

enum class ReminderKind { WATER, CHECK, FOOD }

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
    /** Boşsa varsayılan metin. */
    fun message(lang: Lang): String = TODO("A6.9")
}

/** Üç hatırlatıcının ayarı: su (45 dk, açık), kontrol (kapalı), yemek (15:15, kapalı). Eksik alan varsayılan. */
data class ReminderSettings(val items: List<ReminderItem>) {
    fun of(kind: ReminderKind): ReminderItem = TODO("A6.9")

    fun toJson(): JSONObject = TODO("A6.9")

    companion object {
        val defaults: ReminderSettings get() = TODO("A6.9")

        fun fromJson(j: JSONObject?): ReminderSettings = TODO("A6.9")
    }
}

/** `reminderDue` (MAP.md 13.15): zamanlama kuralı; saatli hatırlatma en çok [LATE_MIN] dk geç gösterilir. İskelet (A6.2b). */
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
    ): Boolean = TODO("A6.9")
}

object ReminderTiming {
    /** Kart (ve bildirim) bu kadar süre sonra kendiliğinden kapanır (1.7.0: 30 sn). */
    val SHOW_FOR_MS: Long get() = TODO("A6.9")
}

/** "15:05". */
fun fmtMinuteOfDay(minute: Int): String = TODO("A6.9")
