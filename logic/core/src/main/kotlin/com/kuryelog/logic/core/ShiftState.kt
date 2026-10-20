package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putN
import org.json.JSONException
import org.json.JSONObject

/**
 * `ShiftState` v1 (CORE.md 1.8, plan 4.4; bugünkü `shift/v1/state` alanları): `:core` → hepsi (P → R). Koordinat yok. Başlangıç ve
 * bitiş 0/yok ise null; [interrupted] yalnız süren vardiya için (H4).
 */
data class ShiftState(
    val active: Boolean,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val endReason: String? = null,
    val interrupted: Boolean = false,
    val generatedAtMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("active", active).putN("startedAtMs", startedAtMs)
        .putN("endedAtMs", endedAtMs).putN("endReason", endReason).put("interrupted", interrupted).put("generatedAtMs", generatedAtMs)

    companion object {
        const val SCHEMA = 1

        /** Bozuk, boş ya da başka şema → null (bilinmiyor; izleyen modül hiçbir şeyi değiştirmez). */
        fun parse(raw: String?): ShiftState? {
            if (raw.isNullOrEmpty()) return null
            return try {
                val j = JSONObject(raw)
                if (j.opt("schemaVersion") != 1 || j.opt("active") !is Boolean) return null
                ShiftState(
                    active = j.getBoolean("active"),
                    startedAtMs = ms(j, "startedAtMs"),
                    endedAtMs = ms(j, "endedAtMs"),
                    endReason = j.opt("endReason") as? String,
                    interrupted = j.opt("interrupted") == true,
                    generatedAtMs = (j.opt("generatedAtMs") as? Number)?.toLong() ?: 0L,
                )
            } catch (_: JSONException) {
                null
            }
        }

        private fun ms(j: JSONObject, key: String): Long? = (j.opt(key) as? Number)?.toLong()?.takeIf { it > 0 }
    }
}

/** `ShiftState` v1 yükünü tercih alanlarından kurar (0 = bilinmiyor → null; `interrupted` yalnız çalışan vardiya için sayılır). */
object ShiftStateSnapshot {
    fun json(active: Boolean, startedAtMs: Long, endedAtMs: Long, endReason: String?, interruptedAtMs: Long, now: Long): JSONObject {
        fun ms(v: Long): Any = if (v > 0) v else JSONObject.NULL
        return JSONObject().put("schemaVersion", 1).put("active", active)
            .put("startedAtMs", ms(startedAtMs)).put("endedAtMs", ms(endedAtMs))
            .put("endReason", endReason ?: JSONObject.NULL)
            .put("interrupted", ManualSessionPolicy.interrupted(active, interruptedAtMs, startedAtMs))
            .put("generatedAtMs", now)
    }
}

/**
 * `:map` gün motoru gibi kendi "gün"ünü tutan modülün vardiyayı izlemesi (CORE.md 1.11): kapalı vardiya + işlenmemiş `endedAtMs`
 * (gün başından önce değil) → günü o anla bitir; açık vardiya + kapalı gün + işlenmemiş `startedAtMs` → o anla başlat; gün açıkken
 * görülen başlangıç işlendi sayılır (elle bitirilen gün aynı vardiyada yeniden açılmaz). Okuma yok → hiçbir şey. Saf.
 */
class ShiftFollower(private var handledEndMs: Long? = null, private var handledStartMs: Long? = null) {
    enum class Action { NONE, END_DAY, START_DAY }

    /** [handledEndMs] / [handledStartMs]: işlendi diye saklanacak damga (null: değişmez). */
    data class Decision(val action: Action, val atMs: Long? = null, val handledEndMs: Long? = null, val handledStartMs: Long? = null)

    /** Saklanan damgalar `(bitiş, başlangıç)` (tercihe yazmak için). */
    fun handled(): Pair<Long?, Long?> = handledEndMs to handledStartMs

    /** Damgaları saklayarak [decide] çağırır. */
    fun follow(s: ShiftState?, dayActive: Boolean, dayStartedAtMs: Long?): Decision =
        decide(s, dayActive, dayStartedAtMs, handledEndMs, handledStartMs).also {
            it.handledEndMs?.let { v -> handledEndMs = v }
            it.handledStartMs?.let { v -> handledStartMs = v }
        }

    companion object {
        fun decide(s: ShiftState?, dayActive: Boolean, dayStartedAtMs: Long?, handledEndMs: Long?, handledStartMs: Long?): Decision {
            if (s == null) return Decision(Action.NONE)
            val start = s.startedAtMs
            if (dayActive) {
                val end = s.endedAtMs
                if (!s.active && end != null && end != handledEndMs && (dayStartedAtMs == null || end >= dayStartedAtMs)) {
                    return Decision(Action.END_DAY, atMs = end, handledEndMs = end)
                }
                if (s.active && start != null && start != handledStartMs) return Decision(Action.NONE, handledStartMs = start)
                return Decision(Action.NONE)
            }
            if (s.active && start != null && start != handledStartMs) return Decision(Action.START_DAY, atMs = start, handledStartMs = start)
            return Decision(Action.NONE)
        }
    }
}
