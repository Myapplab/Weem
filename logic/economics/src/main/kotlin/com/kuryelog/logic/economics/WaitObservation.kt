package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats WaitObservation.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Visible, complete windows only. Censoring is observation loss, not a penalty.
 * One spell per availability period (K5): an hour, demand or other-app change starts a new [Segment] inside the
 * spell, and a pop-up or unreadable frame of up to [UNKNOWN_GRACE_MS] does not end it.
 */
class WaitObservation(private val maxGapMs: Long = 30_000) {
    /** Part of a spell with one hour, demand label and other-app state; segments tile the spell without gaps. */
    data class Segment(val startAt: Long, val endAt: Long, val demand: String?, val bakuHour: Int, val otherAppState: String)
    data class Episode(val pkg: String, val startAt: Long, val endAt: Long,
        val eventObserved: Boolean, val cause: String, val endReason: String,
        val demand: String?, val vehicleMode: String, val zone: String, val bakuHour: Int,
        val spellId: String = spellIdFor(pkg, startAt),
        val segments: List<Segment> = listOf(Segment(startAt, endAt, demand, bakuHour, "unknown"))) {
        val durationMinutes get() = (endAt - startAt).coerceAtLeast(0) / 60000.0
        /** The other platform's state in the last segment, where the spell ended (K9: "online_busy" is no idle wait). */
        val otherAppState: String get() = segments.lastOrNull()?.otherAppState ?: "unknown"
        /** Baku day of [startAt]: the day file this spell belongs to, also when it ends after midnight (VM5). */
        val fileDay: String get() = bakuDay(startAt)
    }
    private class Seg(val start: Long, var demand: String?, val hour: Int, val other: String)
    private class Open(val spellId: String, val start: Long, var last: Long, var cause: String,
        val vehicle: String, val zone: String, val segs: MutableList<Seg>) {
        /** First unreadable/pop-up frame since the last waiting frame. */
        var unknownSince: Long? = null
    }
    private val open = mutableMapOf<String, Open>()
    private val lastAt = mutableMapOf<String, Long>()
    private val causes = mutableMapOf<String, String>()
    /** Last meaningful stage of every package, so a wait knows what the other app was doing (K9). */
    private val lastStage = mutableMapOf<String, ScreenStage>()
    fun nextCause(pkg: String, cause: String) {
        open[pkg]?.let { it.cause = cause } ?: run { causes[pkg] = cause }
    }
    private fun close(pkg: String, event: Boolean, reason: String, at: Long? = null): Episode? {
        val v = open.remove(pkg) ?: return null
        val end = at ?: v.last
        val segs = v.segs.filter { it.start <= end }.ifEmpty { v.segs.take(1) }
        val parts = segs.mapIndexed { i, s -> Segment(s.start, if (i + 1 < segs.size) segs[i + 1].start else end, s.demand, s.hour, s.other) }
        val first = segs.first()
        return Episode(pkg, v.start, end, event, v.cause, reason, first.demand, v.vehicle, v.zone, first.hour, v.spellId, parts)
    }
    fun breakWait(pkg: String, reason: String) = close(pkg, false, reason)
    fun observe(o: CourierObservation, vehicle: String = "unknown", zone: String = "unknown", hour: Int = -1): List<Episode> {
        // An older frame is ignored, and so is a repeat of the same instant unless it shows the offer: OCR can read an
        // offer card at the instant the accessibility tree was unreadable (G14).
        val prev = lastAt[o.pkg] ?: Long.MIN_VALUE
        if (o.at < prev || (o.at == prev && o.stage != ScreenStage.OFFER)) return emptyList()
        lastAt[o.pkg] = o.at
        if (o.complete && o.source != "notification" && o.stage != ScreenStage.UNKNOWN) lastStage[o.pkg] = o.stage
        val out = mutableListOf<Episode>()
        var v = open[o.pkg]
        if (v != null && o.at - v.last > maxGapMs) {
            close(o.pkg, false, "observation_gap")?.let(out::add); v = null
        }
        if (v != null && v.unknownSince?.let { o.at - it > UNKNOWN_GRACE_MS } == true) {
            close(o.pkg, false, "window_not_idle")?.let(out::add); v = null
        }
        if (!o.complete || o.source == "notification") {
            close(o.pkg, false, if (!o.complete) "partial_window" else "window_not_idle")?.let(out::add)
            return out
        }
        when (o.stage) {
            ScreenStage.UNKNOWN -> { if (v != null && v.unknownSince == null) v.unknownSince = o.at; return out }
            ScreenStage.OFFER -> { close(o.pkg, true, "visible_offer", o.at)?.let(out::add); return out }
            ScreenStage.OFFLINE -> { close(o.pkg, false, "went_offline")?.let(out::add); return out }
            ScreenStage.PICKUP, ScreenStage.DROPOFF -> { close(o.pkg, false, "window_not_idle")?.let(out::add); return out }
            ScreenStage.IDLE -> Unit
        }
        if (v != null && (v.vehicle != vehicle || v.zone != zone)) {
            close(o.pkg, false, "context_changed")?.let(out::add); v = null
        }
        val other = otherAppState(o.pkg)
        if (v == null) open[o.pkg] = Open(spellIdFor(o.pkg, o.at), o.at, o.at, causes.remove(o.pkg) ?: "idle_observed", vehicle, zone,
            mutableListOf(Seg(o.at, o.demand, hour, other)))
        else {
            v.last = o.at; v.unknownSince = null
            val s = v.segs.last()
            // A frame without the demand label (hot-spot banner) keeps the current one; a first label fills it in.
            val demand = o.demand ?: s.demand
            if (s.hour != hour || s.other != other || (s.demand != null && demand != s.demand)) v.segs.add(Seg(o.at, demand, hour, other))
            else s.demand = demand
        }
        return out
    }
    /** "online_busy" while another package's last screen is a pickup or drop-off, "online_free" on its waiting map or an offer. */
    private fun otherAppState(pkg: String): String {
        val others = lastStage.filterKeys { it != pkg }.values
        return when {
            others.any { it == ScreenStage.PICKUP || it == ScreenStage.DROPOFF } -> "online_busy"
            others.any { it == ScreenStage.IDLE || it == ScreenStage.OFFER } -> "online_free"
            others.any { it == ScreenStage.OFFLINE } -> "offline"
            else -> "unknown"
        }
    }
    fun stop(): List<Episode> {
        val out = open.keys.toList().mapNotNull { close(it, false, "manual_stop") }
        lastAt.clear(); causes.clear(); lastStage.clear(); return out
    }

    /** Open windows that a killed process would otherwise lose. */
    data class OpenSnapshot(val pkg: String, val start: Long, val last: Long, val cause: String,
        val demand: String?, val vehicle: String, val zone: String, val hour: Int,
        val spellId: String? = null, val segments: List<Segment> = emptyList())

    fun snapshot(): List<OpenSnapshot> = open.map { (pkg, v) ->
        val segs = v.segs.filter { it.start <= v.last }.ifEmpty { v.segs.take(1) }
        val parts = segs.mapIndexed { i, s -> Segment(s.start, if (i + 1 < segs.size) segs[i + 1].start else v.last, s.demand, s.hour, s.other) }
        OpenSnapshot(pkg, v.start, v.last, v.cause, segs.first().demand, v.vehicle, v.zone, segs.first().hour, v.spellId, parts) }

    /** Windows persisted before a process death become right-censored episodes
     *  ending at the last observed instant; a wait is never assumed to continue
     *  through the kill and nothing is reopened. */
    fun restoreCensored(snaps: List<OpenSnapshot>, reason: String = "process_restart"): List<Episode> =
        snaps.map { s ->
            val parts = s.segments.filter { it.startAt <= s.last }.let { p -> p.mapIndexed { i, x -> if (i == p.lastIndex) x.copy(endAt = s.last) else x } }
            if (parts.isEmpty()) Episode(s.pkg, s.start, s.last, false, s.cause, reason, s.demand, s.vehicle, s.zone, s.hour, s.spellId ?: spellIdFor(s.pkg, s.start))
            else Episode(s.pkg, s.start, s.last, false, s.cause, reason, s.demand, s.vehicle, s.zone, s.hour, s.spellId ?: spellIdFor(s.pkg, s.start), parts)
        }

    companion object {
        /** Pop-ups and unreadable frames over the waiting map up to this long do not split a spell (K5). */
        const val UNKNOWN_GRACE_MS = 15_000L
        fun spellIdFor(pkg: String, startAt: Long): String =
            (if (pkg.contains("wolt", true)) "wolt" else if (pkg.contains("bolt", true)) "bolt" else pkg.substringAfterLast('.')) + "-wait-" + startAt
        private val BAKU = java.util.TimeZone.getTimeZone("Asia/Baku")
        /** yyyy-MM-dd in Baku time. */
        fun bakuDay(at: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = BAKU }.format(java.util.Date(at))
        fun encode(snaps: List<OpenSnapshot>): String = JSONArray().apply {
            snaps.forEach { s -> put(JSONObject().put("pkg", s.pkg).put("start", s.start).put("last", s.last)
                .put("cause", s.cause).put("demand", s.demand ?: JSONObject.NULL).put("vehicle", s.vehicle)
                .put("zone", s.zone).put("hour", s.hour).put("spellId", s.spellId ?: JSONObject.NULL)
                .put("segments", JSONArray().apply { s.segments.forEach { g -> put(JSONObject().put("start", g.startAt).put("end", g.endAt)
                    .put("demand", g.demand ?: JSONObject.NULL).put("hour", g.bakuHour).put("other", g.otherAppState)) } })) }
        }.toString()
        /** Damaged/partial rows are skipped; they can never invent a wait. */
        fun decode(raw: String?): List<OpenSnapshot> {
            if (raw.isNullOrBlank()) return emptyList()
            val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until rows.length()).mapNotNull { i -> runCatching {
                val j = rows.getJSONObject(i)
                val start = j.getLong("start"); val last = j.getLong("last")
                require(start in 1..last)
                val segs = j.optJSONArray("segments")?.let { a -> (0 until a.length()).mapNotNull { k -> runCatching {
                    val g = a.getJSONObject(k)
                    Segment(g.getLong("start"), g.getLong("end"), if (g.isNull("demand")) null else g.optString("demand").takeIf { it.isNotBlank() },
                        g.optInt("hour", -1), g.optString("other", "unknown"))
                }.getOrNull() } }?.filter { it.startAt in start..last }.orEmpty()
                OpenSnapshot(j.getString("pkg"), start, last, j.getString("cause"),
                    if (j.isNull("demand")) null else j.optString("demand").takeIf { it.isNotBlank() },
                    j.optString("vehicle", "unknown"), j.optString("zone", "unknown"), j.optInt("hour", -1),
                    if (j.isNull("spellId")) null else j.optString("spellId").takeIf { it.isNotBlank() }, segs)
            }.getOrNull() }
        }
    }
}
