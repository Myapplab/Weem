package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats BoostLog.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * K27: boosts recorded continuously (docs/KURYELOG_ISTEKLER.md K27). Pure Kotlin: OrderTracker feeds every parsed frame and
 * appends the returned rows to [FILE] (append-only, schemaVersion 1, no customer data).
 *
 * - `change`: the boost a platform shows differs from the last one seen (value, unit, active flag, window). Wolt announces its
 *   boosts in advance ("Starts in …", window "18:00-20:00") and may change them during the day: the first row of a day is the
 *   plan as seen (`first_seen=true`), later rows are the changes. "No boost on the home screen" is also a value (`value=null`).
 * - `slot`: Bolt Food every 30 minutes, the multiplier seen in that half hour with the courier's location at that moment. A half
 *   hour without any Bolt home/offer frame is written as `observed=false` (never guessed), up to [MAX_GAP_SLOTS] back.
 *
 * Only complete home, offline or offer frames read from the screen count (a notification is no observation). The location is
 * the phone's own last fix, like the offer rows' `lat/lon`; it never leaves the phone except in the user's own backup.
 */
class BoostLog {
    companion object {
        const val FILE = "economics-boosts.jsonl"
        const val SCHEMA = 1
        const val SLOT_MS = 30 * 60_000L
        /** Unobserved half hours are filled at most 4 hours back (a longer gap is a break, not a shift without boosts). */
        const val MAX_GAP_SLOTS = 8
        /** A home-screen boost is used for an offer at most this long after it was seen. */
        const val FRESH_MS = 30 * 60_000L
        const val UNIT_PCT = "pct"
        const val UNIT_X = "x"
        const val SOURCE_OFFER = "offer_screen"
        const val SOURCE_HOME = "home_screen"
        /** D11/D24: Wolt's day plan ("Earn extra", waiting sheet): the slot covering the offer time as last seen before it. */
        const val SOURCE_PLAN = "wolt_plan"
        /** M21: the plan slot came from Wolt's "Earn extra → Coming up later" list, which wins over the home screen. */
        const val SOURCE_PLAN_LIST = "wolt_earn_extra"
        const val PLAN_LIST = "earn_extra"
        const val PLAN_HOME = "home_screen"
        /** A folded "… 20% from 16:00" slot without its end is taken as one hour (Wolt's slots are hourly). */
        const val FROM_SLOT_MIN = 60
        private val BAKU = java.time.ZoneId.of("Asia/Baku")
        fun minuteOfDay(at: Long): Int = java.time.Instant.ofEpochMilli(at).atZone(BAKU).let { it.hour * 60 + it.minute }
        fun dayOf(at: Long): String = java.time.Instant.ofEpochMilli(at).atZone(BAKU).toLocalDate().toString()
        const val NOT_OBSERVED = "not_observed"

        fun platform(pkg: String) = if (pkg in CourierParser.WOLT) "wolt" else "bolt"

        /** Boost change statistics of one day's rows for one platform ("wolt" / "bolt"), for the day page. */
        data class Stats(val changes: Int, val slotsObserved: Int, val slotsMissing: Int, val slotsBoosted: Int, val max: Double?, val maxUnit: String?)

        fun stats(rows: List<JSONObject>, platform: String): Stats {
            val mine = rows.filter { it.optInt("schemaVersion") == SCHEMA && it.optString("platform") == platform }
            val changes = mine.count { it.optString("kind") == "change" && it.optBoolean("prev_known") }
            val slots = mine.filter { it.optString("kind") == "slot" }.associateBy { it.optLong("slot_start") }.values
            val observed = slots.filter { it.optBoolean("observed") }
            val boosted = observed.count { it.num("value") != null && it.optBoolean("active") && (it.optString("unit") != UNIT_X || it.num("value")!! > 1.0) }
            val top = mine.filter { it.num("value") != null && it.optBoolean("active") }.maxByOrNull { it.num("value")!! }
            return Stats(changes, observed.size, slots.size - observed.size, boosted, top?.num("value"), top?.str("unit"))
        }
    }

    /** What one frame says about the boost. [value] null = the screen was seen and showed no boost. */
    data class Seen(val pkg: String, val at: Long, val value: Double?, val unit: String?, val active: Boolean, val window: String?,
                    val startsInMin: Int?, val stage: String) {
        val key get() = "$unit|$value|$active|$window"
    }

    /** Boost of an offer for its card: value/unit null with a source means "seen, no boost"; source [NOT_OBSERVED] means unknown. */
    data class AtOffer(val value: Double?, val unit: String?, val source: String, val seenAt: Long?)

    private val lastKey = HashMap<String, String>()
    private val lastDay = HashMap<String, String>()
    private val lastSlot = HashMap<String, Long>()
    private val latest = HashMap<String, Seen>()
    /** One slot of Wolt's plan as seen at [seenAt]; minutes of the Baku day, [end] exclusive. */
    data class PlanSlot(val day: String, val start: Int, val end: Int, val pct: Int, val seenAt: Long, val active: Boolean,
                        val source: String = PLAN_HOME)
    private val plan = ArrayList<PlanSlot>()

    private fun hhmm(s: String): Int? = s.split(':').takeIf { it.size == 2 }?.let { (h, m) -> h.toIntOrNull()?.let { hh -> m.toIntOrNull()?.let { hh * 60 + it } } }

    /** D24: Wolt plan rows from the waiting sheet and the Earn extra page (every frame but an offer card). A slot is written when
     *  first seen that day or when its percentage changes (Wolt changes the plan during the day: 12 Oct 13:00-14:00 20% → 10%). */
    private fun observePlan(o: CourierObservation, loc: JSONObject?): List<JSONObject> {
        if (o.pkg !in CourierParser.WOLT || !o.complete || o.source == "notification" || o.stage == ScreenStage.OFFER) return emptyList()
        val today = dayOf(o.at); val out = ArrayList<JSONObject>()
        // M21: the "Earn extra → Coming up later" list is the plan itself; the waiting sheet's boost is sometimes wrong or late.
        val source = if (o.raw.any { CourierParser.norm(it) == "coming up later" }) PLAN_LIST else PLAN_HOME
        for (b in o.boostSlots) {
            val day = b.date?.let { slotDay(it, today) } ?: today
            val window = b.window; val from = b.from // WEEM: yerel kopya (BoostSlot başka modülde, akıllı dönüşüm yok)
            val (start, end) = when {
                window != null -> hhmm(window.substringBefore('-'))?.let { a -> hhmm(window.substringAfter('-'))?.let { e -> a to (if (e <= a) e + 1440 else e) } }
                from != null -> hhmm(from)?.let { it to it + FROM_SLOT_MIN }
                else -> null
            } ?: continue
            val last = plan.lastOrNull { it.day == day && it.start == start && it.end == end && it.source == source }
            // Same percentage again: nothing new, unless the slot is now read under "Now · Boost" (active wins ties in [planAt]).
            if (last != null && last.pct == b.pct && (last.active || !b.active)) continue
            plan += PlanSlot(day, start, end, b.pct, o.at, b.active, source)
            if (plan.size > 400) plan.removeAt(0)
            if (last?.pct == b.pct) continue
            out += JSONObject().put("schemaVersion", SCHEMA).put("kind", "plan").put("pkg", o.pkg).put("platform", platform(o.pkg))
                .put("at", o.at).put("value", b.pct).put("unit", UNIT_PCT).put("active", b.active)
                .put("window", b.window ?: JSONObject.NULL).put("from", b.from ?: JSONObject.NULL)
                .put("slot_start_min", start).put("slot_end_min", end).put("prev_value", last?.pct ?: JSONObject.NULL)
                .put("plan_source", source).put("slot_day", day)
                .put("stage", o.stage.name).put("lat", loc?.num("lat") ?: JSONObject.NULL).put("lon", loc?.num("lon") ?: JSONObject.NULL)
        }
        return out
    }

    /** The Wolt plan slot covering [at], as last seen before it (a "Now · Boost" read wins a tie); null = no plan seen. M21: a slot
     *  of the Earn extra list wins over any home-screen one. */
    fun planAt(pkg: String, at: Long): PlanSlot? {
        if (pkg !in CourierParser.WOLT) return null
        val day = dayOf(at); val m = minuteOfDay(at)
        val seen = plan.filter { it.day == day && m in it.start until it.end && it.seenAt <= at + 60_000L }
        return (seen.filter { it.source == PLAN_LIST }.ifEmpty { seen }).maxWithOrNull(compareBy<PlanSlot>({ it.seenAt }, { it.active }))
    }
    /** M21: "17/10" seen on [today] → its ISO day (next year when that date is more than half a year back). */
    private fun slotDay(dm: String, today: String): String {
        val (d, mo) = dm.split('/').map { it.toInt() }
        val t = java.time.LocalDate.parse(today)
        val x = runCatching { java.time.LocalDate.of(t.year, mo, d) }.getOrNull() ?: return today
        return (if (x.isBefore(t.minusDays(180))) x.plusYears(1) else x).toString()
    }

    /**
     * The boost of [o], or null when the frame is no observation. Wolt: the percentage line (active or announced). Bolt: one live
     * multiplier ("1.6x"; several different ones on one frame are ambiguous → no observation), else a boost percentage.
     */
    fun seen(o: CourierObservation): Seen? {
        if (!o.complete || o.source == "notification") return null
        if (o.stage != ScreenStage.IDLE && o.stage != ScreenStage.OFFLINE && o.stage != ScreenStage.OFFER) return null
        val stage = o.stage.name
        if (o.pkg !in CourierParser.WOLT && o.multipliers.isNotEmpty()) {
            val x = o.multipliers.singleOrNull() ?: return null
            return Seen(o.pkg, o.at, x, UNIT_X, true, null, null, stage)
        }
        val pct = o.boostPct ?: o.bonusPct
        if (pct != null) return Seen(o.pkg, o.at, pct.toDouble(), UNIT_PCT, o.bonusPct != null, o.boostWindow, o.boostStartsInMin, stage)
        // An offer card never shows the home-screen boost: its absence there says nothing.
        if (o.stage == ScreenStage.OFFER) return null
        return Seen(o.pkg, o.at, null, null, false, null, null, stage)
    }

    /** Rows for one frame; [day] is the Baku day of [o] (`Store.day`), [loc] the phone's last fix (`Store.locJson`) or null. */
    fun observe(o: CourierObservation, day: String, loc: JSONObject?): List<JSONObject> {
        val out = ArrayList<JSONObject>(observePlan(o, loc))
        val s = seen(o) ?: return out
        val prev = latest[s.pkg]
        val newDay = lastDay[s.pkg] != day
        if (newDay || lastKey[s.pkg] != s.key) {
            out += row("change", s, loc).put("first_seen", newDay)
                .put("prev_value", if (!newDay && prev != null) prev.value ?: JSONObject.NULL else JSONObject.NULL)
                .put("prev_unit", if (!newDay && prev != null) prev.unit ?: JSONObject.NULL else JSONObject.NULL)
                .put("prev_known", !newDay && prev != null)
            lastKey[s.pkg] = s.key; lastDay[s.pkg] = day
        }
        if (s.pkg !in CourierParser.WOLT) {
            val slot = s.at / SLOT_MS
            val before = lastSlot[s.pkg]
            if (before == null || slot > before) {
                if (before != null && !newDay && slot - before in 2..(MAX_GAP_SLOTS + 1L))
                    for (gap in (before + 1) until slot) out += JSONObject().put("schemaVersion", SCHEMA).put("kind", "slot").put("pkg", s.pkg)
                        .put("platform", platform(s.pkg)).put("slot_start", gap * SLOT_MS).put("at", JSONObject.NULL).put("observed", false)
                out += row("slot", s, loc).put("slot_start", slot * SLOT_MS).put("observed", true)
                lastSlot[s.pkg] = slot
            }
        }
        latest[s.pkg] = s
        return out
    }

    /** The boost to write on an offer seen at [at]: the offer card's own value, else a fresh home-screen observation. */
    fun atOffer(o: CourierObservation, at: Long): AtOffer {
        if (o.pkg !in CourierParser.WOLT) o.multipliers.singleOrNull()?.let { return AtOffer(it, UNIT_X, SOURCE_OFFER, at) }
        o.bonusPct?.let { return AtOffer(it.toDouble(), UNIT_PCT, SOURCE_OFFER, at) }
        planAt(o.pkg, at)?.let { return AtOffer(it.pct.toDouble(), UNIT_PCT, if (it.source == PLAN_LIST) SOURCE_PLAN_LIST else SOURCE_PLAN, it.seenAt) }
        val s = latest[o.pkg]?.takeIf { at - it.at in 0..FRESH_MS } ?: return AtOffer(null, null, NOT_OBSERVED, null)
        // An announced (not yet active) Wolt boost does not apply to this offer.
        return if (s.value != null && s.active) AtOffer(s.value, s.unit, SOURCE_HOME, s.at) else AtOffer(null, null, SOURCE_HOME, s.at)
    }

    private fun row(kind: String, s: Seen, loc: JSONObject?) = JSONObject().put("schemaVersion", SCHEMA).put("kind", kind).put("pkg", s.pkg)
        .put("platform", platform(s.pkg)).put("at", s.at).put("value", s.value ?: JSONObject.NULL).put("unit", s.unit ?: JSONObject.NULL)
        .put("active", s.active).put("window", s.window ?: JSONObject.NULL).put("starts_in_min", s.startsInMin ?: JSONObject.NULL).put("stage", s.stage)
        .put("lat", loc?.num("lat") ?: JSONObject.NULL).put("lon", loc?.num("lon") ?: JSONObject.NULL)
        .put("gps_accuracy_m", loc?.num("acc") ?: JSONObject.NULL).put("gps_age_s", loc?.num("age_s") ?: JSONObject.NULL)
}
