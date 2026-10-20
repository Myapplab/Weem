package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages
import org.json.JSONArray
import org.json.JSONObject

/** Açık yolculuk (feed `activeTrips`): [stage] bilinmeyen aşamada null. */
data class FollowerTrip(val id: String, val platform: String, val stage: TripStages.Stage?)

/**
 * Aşama izleyicinin girdisi (MAP.md 11.7): [trips] açık yolculuklar (sıralı); [stages] Rawstats'ın gerçek saatli aşama satırları (yolculuk kimliğine göre),
 * yoksa null (eski kural: iki eksik okuma).
 */
data class FollowerFeed(val trips: List<FollowerTrip>, val stages: Map<String, TripStages.Trip>? = null)

/** `TripStages` (`:core`) → [FollowerFeed]; okuma yoksa (`OrderEvents` bağlı değil) null. */
object StageFeed {
    fun from(stages: TripStages?): FollowerFeed? {
        if (stages == null) return null
        val byId = LinkedHashMap<String, TripStages.Trip>()
        for (t in stages.trips) byId.putIfAbsent(t.tripId, t)
        val open = byId.values.filter { it.active }.map { FollowerTrip(it.tripId, it.platformPackage, it.stage) }
        return FollowerFeed(open, byId)
    }

    /** `to_pickup` → toRestaurant, `at_pickup` → atRestaurant, `to_dropoff` → toCustomer, `at_dropoff` → atCustomer. */
    fun phaseOf(stage: TripStages.Stage): OrderPhase = when (stage) {
        TripStages.Stage.TO_PICKUP -> OrderPhase.TO_RESTAURANT
        TripStages.Stage.AT_PICKUP -> OrderPhase.AT_RESTAURANT
        TripStages.Stage.TO_DROPOFF -> OrderPhase.TO_CUSTOMER
        TripStages.Stage.AT_DROPOFF -> OrderPhase.AT_CUSTOMER
    }
}

/**
 * Ekran okumasının aşamalarını gün motoruna işler (Dart `CourierStageFollower`, MAP.md 11.7): elle basmadan sipariş açar/ilerletir/teslim eder; geri yön uygulanmaz;
 * teslim için iki eksik okuma (ya da bitiş saatli `stages`); gün kapalıyken izlenmez. Kalıcı durum `{"v":1,"trip","order","stage","at"?,"closed":[]}`.
 */
class CourierStageFollower {
    private var trip: String? = null
    private var order: Int? = null
    private var stage: TripStages.Stage? = null
    private var missing = 0
    private var appliedAtMs: Long? = null
    private val closed = ArrayList<String>()
    private val released = ArrayList<Int>()

    val linkedTrip: String? get() = trip
    val linkedOrder: Int? get() = order

    /** İlk eksik okumadan sonra kısa yeniden okuma istenir. */
    val awaitingSecondRead: Boolean get() = trip != null && missing > 0 && missing < MISSING_READS

    /** [feed] null = okuma yok (hiçbir şey değişmez). */
    fun sync(engine: ShiftEngine, feed: FollowerFeed?, atMs: Long, lat: Double? = null, lon: Double? = null): List<DayEvent> {
        if (feed == null || !engine.s.active) return emptyList()
        val now = atMs
        val ev = ArrayList<DayEvent>()
        if (trip != null && engine.s.current?.n != order) close()
        val bound = trip
        if (bound != null) {
            val open = feed.trips.firstOrNull { it.id == bound }
            val row = feed.stages?.get(bound)
            if (open == null) {
                if (row != null && !row.active && row.outcome != null && engine.s.current?.n == order) {
                    ev += ended(engine, row, now, lat, lon)
                    close()
                    return ev
                }
                if (missing < MISSING_READS) missing++
                if (missing < MISSING_READS) return ev
                ev += giveUp(engine, now, lat, lon)
                close()
                return ev
            }
            missing = 0
            if (engine.s.current?.n != order) return ev
            if (row != null && row.active && row.events.any { it.atMs > 0 }) {
                ev += timeline(engine, row.events, now, lat, lon)
            } else {
                val st = open.stage
                if (st != null && st != stage) {
                    stage = st
                    ev += engine.advanceFromFeed(StageFeed.phaseOf(st), now, lat, lon)
                }
            }
            return ev
        }
        val first = feed.trips.firstOrNull { it.stage != null && it.id !in closed } ?: return ev
        val cur = engine.s.current
        if (cur != null && cur.n in released) return ev
        val row = feed.stages?.get(first.id)
        val timed = row != null && row.active && row.events.any { it.atMs > 0 }
        if (cur == null) {
            val firstAt = if (timed) row!!.events.first { it.atMs > 0 }.atMs else now
            val start = clamp(firstAt, engine.s.startedAtMs ?: now, now)
            ev += engine.startOrder(start).map(::fromFeed)
        }
        val o = engine.s.current ?: return ev
        trip = first.id
        order = o.n
        missing = 0
        if (timed) {
            stage = null
            appliedAtMs = null
            ev += timeline(engine, row!!.events, now, lat, lon)
        } else {
            stage = first.stage
            appliedAtMs = null
            ev += engine.advanceFromFeed(StageFeed.phaseOf(first.stage!!), now, lat, lon)
        }
        return ev
    }

    private fun timeline(engine: ShiftEngine, events: List<TripStages.Event>, now: Long, lat: Double?, lon: Double?): List<DayEvent> {
        val out = ArrayList<DayEvent>()
        val bound = engine.s.current?.n
        for (e in events) {
            if (e.atMs <= 0) continue
            val cur = stage
            if (cur != null && e.stage.ordinal <= cur.ordinal) continue
            val o = engine.s.current ?: break
            if (o.n != bound) break
            val at = clamp(e.atMs, appliedAtMs ?: o.startedAtMs, now)
            val fresh = now - at <= FRESH_MS
            out += engine.stageFromFeed(StageFeed.phaseOf(e.stage), at, if (fresh) lat else null, if (fresh) lon else null)
            stage = e.stage
            appliedAtMs = at
        }
        return out
    }

    private fun ended(engine: ShiftEngine, row: TripStages.Trip, now: Long, lat: Double?, lon: Double?): List<DayEvent> {
        val out = ArrayList<DayEvent>()
        out += timeline(engine, row.events, now, lat, lon)
        val o = engine.s.current
        if (o == null || o.n != order) return out
        val end = clamp(row.endedAtMs ?: now, appliedAtMs ?: o.startedAtMs, now)
        when (row.outcome) {
            TripStages.Outcome.CANCELLED ->
                if (engine.s.phase == OrderPhase.TO_RESTAURANT || engine.s.phase == OrderPhase.AT_RESTAURANT) out += engine.cancelOrder(end).map(::fromFeed)
                else released += o.n
            TripStages.Outcome.DELIVERED, TripStages.Outcome.DELIVERED_UNVERIFIED, TripStages.Outcome.UNCONFIRMED -> out += engine.deliverFromFeed(end)
            null -> Unit
        }
        return out
    }

    /** İkinci eksik okuma: teslim, iptal ya da sipariş elde kalır (bırakılır). */
    private fun giveUp(engine: ShiftEngine, now: Long, lat: Double?, lon: Double?): List<DayEvent> {
        val o = engine.s.current ?: return emptyList()
        if (o.n != order) return emptyList()
        return when (stage) {
            TripStages.Stage.TO_DROPOFF, TripStages.Stage.AT_DROPOFF -> engine.advanceFromFeed(OrderPhase.AT_CUSTOMER, now, lat, lon, deliver = true)
            TripStages.Stage.TO_PICKUP, TripStages.Stage.AT_PICKUP -> engine.cancelOrder(now).map(::fromFeed)
            else -> {
                released += o.n
                emptyList()
            }
        }
    }

    private fun fromFeed(e: DayEvent) = e.copy(data = e.data + ("source" to ShiftEngine.FEED_SOURCE))

    private fun clamp(v: Long, floor: Long, now: Long): Long = v.coerceIn(minOf(floor, now), now)

    private fun close() {
        trip?.let {
            closed += it
            while (closed.size > KEEP_CLOSED) closed.removeAt(0)
        }
        trip = null; order = null; stage = null; missing = 0; appliedAtMs = null
    }

    fun dayChanged() {
        close()
        released.clear()
    }

    fun toJson(): JSONObject {
        val j = JSONObject().put("v", 1)
        trip?.let { j.put("trip", it) }
        order?.let { j.put("order", it) }
        stage?.let { j.put("stage", it.key) }
        appliedAtMs?.let { j.put("at", it) }
        if (missing > 0) j.put("missing", missing)
        j.put("closed", JSONArray(closed))
        if (released.isNotEmpty()) j.put("released", JSONArray(released))
        return j
    }

    fun restore(j: JSONObject) {
        trip = if (j.has("trip") && !j.isNull("trip")) j.getString("trip") else null
        order = if (j.has("order") && !j.isNull("order")) j.getInt("order") else null
        stage = TripStages.Stage.of(j.optString("stage", ""))
        appliedAtMs = if (j.has("at") && !j.isNull("at")) j.getLong("at") else null
        missing = j.optInt("missing", 0)
        closed.clear()
        j.optJSONArray("closed")?.let { a -> for (i in 0 until a.length()) closed += a.getString(i) }
        released.clear()
        j.optJSONArray("released")?.let { a -> for (i in 0 until a.length()) released += a.getInt(i) }
        if (trip == null || order == null) {
            trip = null; order = null; stage = null; appliedAtMs = null; missing = 0
        }
    }

    private companion object {
        const val MISSING_READS = 2
        const val FRESH_MS = 30_000L
        const val KEEP_CLOSED = 20
    }
}
