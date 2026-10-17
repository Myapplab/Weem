package com.kuryelog.logic.orders

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats OnlineSessions.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * Online periods per platform, from what the courier app shows during a manual shift.
 * Only positive evidence changes the state: online = "Go offline" slider, a waiting text or an
 * offer/pickup/drop-off screen; offline = "Go online" slider, an offline message or Wolt's Feedback
 * page (shown every time right after going offline). Wolt's demand map is on both the online and
 * the offline screen, so it says nothing. Menus and support pages are ignored.
 */
class OnlineSessions {
    data class Session(val pkg: String, val startAt: Long, val startObserved: Boolean, var lastOnlineAt: Long,
        var offers: Int = 0, var accepted: Int = 0, var delivered: Int = 0,
        val id: String = idFor(pkg, startAt), var rejected: Int = 0,
        val offerIds: MutableList<String> = mutableListOf(), val tripIds: MutableList<String> = mutableListOf(),
        val startEvidence: String? = null)
    data class Closed(val pkg: String, val startAt: Long, val endAt: Long, val startObserved: Boolean, val endObserved: Boolean,
        val endReason: String, val offers: Int, val accepted: Int, val delivered: Int,
        val id: String = idFor(pkg, startAt), val rejected: Int = 0,
        val offerIds: List<String> = emptyList(), val tripIds: List<String> = emptyList(),
        /** Last online screen before the end: the real end lies between this and endAt. */
        val lastOnlineAt: Long = endAt, val startEvidence: String? = null) {
        val minutes get() = (endAt - startAt).coerceAtLeast(0) / 60000.0
    }
    private val open = linkedMapOf<String, Session>()
    private val offlineSeen = HashSet<String>()
    private val lastAt = HashMap<String, Long>()
    /** OCR is noisy: its evidence changes the state only when two OCR frames agree. */
    private val ocrCandidate = HashMap<String, Pair<String, Long>>()
    fun openSessions(): List<Session> = open.values.toList()
    fun currentId(pkg: String): String? = open[pkg]?.id
    /** Platforms whose online/offline state was seen in this shift. */
    fun known(): Set<String> = open.keys + offlineSeen
    /** "online", "offline" or null when nothing was seen for the platform in this shift. */
    fun state(pkg: String): String? = if (pkg in open) "online" else if (pkg in offlineSeen) "offline" else null

    private fun availability(o: CourierObservation): String? = o.availability
        ?: if (o.stage == ScreenStage.OFFER || o.stage == ScreenStage.PICKUP || o.stage == ScreenStage.DROPOFF) "online" else null
    private fun evidence(o: CourierObservation) = o.availabilityEvidence ?: "task_screen"

    /** Returns a session that just ended, if any. */
    fun observe(o: CourierObservation): Closed? {
        if (!o.complete) return null
        val state = availability(o) ?: return null
        // A late OCR frame must not reopen or close a session.
        if (o.at < (lastAt[o.pkg] ?: Long.MIN_VALUE)) return null
        lastAt[o.pkg] = o.at
        var at = o.at
        if (o.source == "ocr" && o.availabilityEvidence != "wolt_feedback_after_go_offline") {
            val c = ocrCandidate[o.pkg]
            if (state != this.state(o.pkg) && (c == null || c.first != state)) { ocrCandidate[o.pkg] = state to o.at; return null }
            c?.takeIf { it.first == state }?.let { at = it.second }
        }
        ocrCandidate.remove(o.pkg)
        if (state == "online") {
            val s = open[o.pkg]
            if (s == null) open[o.pkg] = Session(o.pkg, at, startObserved = o.pkg in offlineSeen, lastOnlineAt = o.at, startEvidence = evidence(o))
            else if (o.at > s.lastOnlineAt) s.lastOnlineAt = o.at
            offlineSeen.remove(o.pkg)
            return null
        }
        offlineSeen.add(o.pkg)
        val s = open.remove(o.pkg) ?: return null
        val reason = if (o.availabilityEvidence == "wolt_feedback_after_go_offline") "wolt_feedback_after_go_offline" else "offline_screen"
        return closed(s, maxOf(at, s.lastOnlineAt), true, reason)
    }
    private fun closed(s: Session, end: Long, endObserved: Boolean, reason: String) = Closed(s.pkg, s.startAt, end, s.startObserved, endObserved,
        reason, s.offers, s.accepted, s.delivered, s.id, s.rejected, s.offerIds.toList(), s.tripIds.toList(), s.lastOnlineAt, s.startEvidence)
    private val countedPerTrip = HashMap<String, Int>()
    /** [tripOrders]: customers in the trip, so a bundle's final event does not count twice. [inferredBefore]: the trip was closed
     *  as an unverified delivery before ([TripRecord.deliveredInferredAt]), which counted it, also before a process restart. */
    fun event(e: CoreEvent, tripOrders: Int = 1, inferredBefore: Boolean = false) {
        val s = open[e.pkg] ?: return
        when (e.type) {
            "offer" -> { s.offers++; e.offerId?.let { if (s.offerIds.size < MAX_IDS) s.offerIds += it } }
            "accepted" -> { s.accepted++; e.tripId?.let { if (it !in s.tripIds && s.tripIds.size < MAX_IDS) s.tripIds += it } }
            "rejected" -> s.rejected++
            "customer_delivered" -> { s.delivered++; e.tripId?.let { countedPerTrip[it] = (countedPerTrip[it] ?: 0) + 1 } }
            "delivered" -> {
                val counted = e.tripId?.let { countedPerTrip.remove(it) } ?: 0
                if (!inferredBefore) s.delivered += (tripOrders.coerceAtLeast(1) - counted).coerceAtLeast(0)
            }
            // D3(c): an unverified delivery counts too; its later real signal (`delivered`) then adds nothing.
            "delivered_inferred" -> {
                val n = tripOrders.coerceAtLeast(1)
                s.delivered += (n - (e.tripId?.let { countedPerTrip[it] } ?: 0)).coerceAtLeast(0)
                e.tripId?.let { countedPerTrip[it] = maxOf(n, countedPerTrip[it] ?: 0) }
            }
        }
    }
    /** Shift end: the courier may still be online, so the end is the last online screen seen. */
    fun stop(reason: String = "shift_stop"): List<Closed> {
        val out = open.values.map { closed(it, it.lastOnlineAt, false, reason) }
        open.clear(); offlineSeen.clear(); lastAt.clear(); ocrCandidate.clear(); return out
    }

    fun encode(): String = JSONArray(open.values.map { s -> JSONObject().put("pkg", s.pkg).put("start", s.startAt).put("startObserved", s.startObserved)
        .put("last", s.lastOnlineAt).put("offers", s.offers).put("accepted", s.accepted).put("delivered", s.delivered)
        .put("id", s.id).put("rejected", s.rejected).put("offerIds", JSONArray(s.offerIds)).put("tripIds", JSONArray(s.tripIds))
        .put("startEvidence", s.startEvidence) }).toString()
    fun restore(raw: String?) {
        if (raw.isNullOrBlank()) return
        runCatching { JSONArray(raw) }.getOrNull()?.objects()?.forEach { j -> runCatching {
            val pkg = j.getString("pkg"); val start = j.getLong("start")
            open[pkg] = Session(pkg, start, j.optBoolean("startObserved"), j.getLong("last"),
                j.optInt("offers"), j.optInt("accepted"), j.optInt("delivered"), j.str("id") ?: idFor(pkg, start), j.optInt("rejected"),
                j.optJSONArray("offerIds")?.strings()?.toMutableList() ?: mutableListOf(), j.optJSONArray("tripIds")?.strings()?.toMutableList() ?: mutableListOf(),
                j.str("startEvidence"))
        } }
    }
    companion object {
        private const val MAX_IDS = 200
        fun idFor(pkg: String, startAt: Long): String =
            (if (pkg.contains("wolt", true)) "wolt" else if (pkg.contains("bolt", true)) "bolt" else pkg.substringAfterLast('.')) + "-" + startAt
        fun json(c: Closed): JSONObject = JSONObject().put("schemaVersion", 2).put("pkg", c.pkg).put("sessionId", c.id)
            .put("startAtMs", c.startAt).put("endAtMs", c.endAt).put("durationMinutes", c.minutes)
            .put("startObserved", c.startObserved).put("endObserved", c.endObserved).put("endReason", c.endReason)
            .put("lastOnlineSeenAtMs", c.lastOnlineAt).put("startEvidence", c.startEvidence)
            .put("offersSeen", c.offers).put("accepted", c.accepted).put("rejected", c.rejected).put("delivered", c.delivered)
            .put("offerIds", JSONArray(c.offerIds)).put("tripIds", JSONArray(c.tripIds))
        /** Reads schemaVersion 1 (counters only) and 2. */
        fun closed(j: JSONObject): Closed {
            val pkg = j.getString("pkg"); val start = j.getLong("startAtMs"); val end = j.getLong("endAtMs")
            return Closed(pkg, start, end, j.optBoolean("startObserved"), j.optBoolean("endObserved"), j.optString("endReason"),
                j.optInt("offersSeen"), j.optInt("accepted"), j.optInt("delivered"), j.str("sessionId") ?: idFor(pkg, start), j.optInt("rejected"),
                j.optJSONArray("offerIds")?.strings() ?: emptyList(), j.optJSONArray("tripIds")?.strings() ?: emptyList(),
                j.longOrNull("lastOnlineSeenAtMs") ?: end, j.str("startEvidence"))
        }
    }
}
