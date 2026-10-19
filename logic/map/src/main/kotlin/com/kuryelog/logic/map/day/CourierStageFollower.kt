package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages
import com.kuryelog.logic.core.TripStages.Stage
import org.json.JSONArray
import org.json.JSONObject

/** Açık yolculuk (feed `activeTrips`): [stage] bilinmeyen aşamada null. */
data class FollowerTrip(val id: String, val platform: String, val stage: Stage?)

/**
 * Aşama izleyicinin girdisi (MAP.md 11.7): [trips] açık yolculuklar (sıralı); [stages] Rawstats'ın gerçek saatli aşama satırları (yolculuk kimliğine göre),
 * yoksa null (eski kural: iki eksik okuma).
 */
data class FollowerFeed(val trips: List<FollowerTrip>, val stages: Map<String, TripStages.Trip>? = null)

/** `TripStages` (`:core`) → [FollowerFeed]; okuma yoksa (`OrderEvents` bağlı değil) null. */
object StageFeed {
    fun from(stages: TripStages?): FollowerFeed? {
        if (stages == null) return null
        val open = stages.trips.filter { it.active }.map { FollowerTrip(it.tripId, it.platformPackage, it.stage) }
        val lines = LinkedHashMap<String, TripStages.Trip>()
        for (t in stages.trips) lines.putIfAbsent(t.tripId, t.copy(events = t.events.filter { it.atMs > 0 }))
        return FollowerFeed(open, lines)
    }

    /** `to_pickup` → toRestaurant, `at_pickup` → atRestaurant, `to_dropoff` → toCustomer, `at_dropoff` → atCustomer. */
    fun phaseOf(stage: Stage): OrderPhase = when (stage) {
        Stage.TO_PICKUP -> OrderPhase.TO_RESTAURANT
        Stage.AT_PICKUP -> OrderPhase.AT_RESTAURANT
        Stage.TO_DROPOFF -> OrderPhase.TO_CUSTOMER
        Stage.AT_DROPOFF -> OrderPhase.AT_CUSTOMER
    }
}

/**
 * Ekran okumasının aşamalarını gün motoruna işler (Dart `CourierStageFollower`, MAP.md 11.7): elle basmadan sipariş açar/ilerletir/teslim eder; geri yön uygulanmaz;
 * teslim için iki eksik okuma (ya da bitiş saatli `stages`); gün kapalıyken izlenmez. Kalıcı durum `{"v":1,"trip","order","stage","at"?,"closed":[]}`.
 */
class CourierStageFollower(private val missingReads: Int = 2, private val keepClosed: Int = 20) {
    private var trip: String? = null
    private var order: Int? = null
    private var stage: Stage? = null
    private var missing = 0

    /** Son uygulanan zaman çizelgesi aşamasının saati (ms). */
    private var at: Long? = null
    private val closed = ArrayList<String>()
    private val released = HashSet<Int>()

    val linkedTrip: String? get() = trip
    val linkedOrder: Int? get() = order

    /** İlk eksik okumadan sonra kısa yeniden okuma istenir. */
    val awaitingSecondRead: Boolean get() = trip != null && missing > 0 && missing < missingReads

    /** [feed] null = okuma yok (hiçbir şey değişmez). */
    fun sync(engine: ShiftEngine, feed: FollowerFeed?, atMs: Long, lat: Double? = null, lon: Double? = null): List<DayEvent> {
        val s = engine.s
        if (feed == null || !s.active) return emptyList()
        val open = listOfNotNull(s.current?.n) + s.queuedOrders.map { it.n }
        if (trip != null && (order == null || order !in open)) close()

        val tr = trip
        if (tr != null) {
            val t = feed.trips.firstOrNull { it.id == tr }
            val line = feed.stages?.get(tr)
            if (t == null) {
                if (line != null && !line.active && line.outcome != null && s.current?.n == order) {
                    val ev = ended(engine, line, atMs, lat, lon)
                    close()
                    return ev
                }
                if (missing < missingReads) missing++
                if (missing < missingReads || s.current?.n != order) return emptyList()
                val picked = stage == Stage.TO_DROPOFF || stage == Stage.AT_DROPOFF
                val ev: List<DayEvent> = if (picked) {
                    engine.advanceFromFeed(OrderPhase.AT_CUSTOMER, atMs, lat, lon, deliver = true)
                } else if (s.phase == OrderPhase.TO_RESTAURANT || s.phase == OrderPhase.AT_RESTAURANT) {
                    engine.cancelOrder(atMs).map { it.copy(data = it.data + ("source" to ShiftEngine.FEED_SOURCE)) }
                } else {
                    // Ekran okumasında alıştan önce bitti ama WEEM'de alındı: elle durum kalır, bu sipariş başka yolculuğa bağlanmaz.
                    order?.let { released += it }
                    emptyList()
                }
                close()
                return ev
            }
            missing = 0
            if (s.current?.n != order) return emptyList()
            if (timed(line)) return timeline(engine, line!!, atMs, lat, lon)
            val st = t.stage
            if (st == null || st == stage) return emptyList()
            stage = st
            return engine.advanceFromFeed(StageFeed.phaseOf(st), atMs, lat, lon)
        }

        val next = feed.trips.firstOrNull { it.stage != null && it.id !in closed } ?: return emptyList()
        val line = feed.stages?.get(next.id)
        val isTimed = timed(line)
        val ev = ArrayList<DayEvent>()
        if (s.current == null) {
            val start = if (isTimed) clamp(line!!.events.first().atMs, s.startedAtMs ?: 0L, atMs) else atMs
            ev += engine.startOrder(start).map { it.copy(data = it.data + ("source" to ShiftEngine.FEED_SOURCE)) }
        } else if (s.current!!.n in released) {
            return emptyList()
        }
        val o = s.current ?: return ev
        trip = next.id; order = o.n; missing = 0; at = null
        if (isTimed) {
            stage = null
            ev += timeline(engine, line!!, atMs, lat, lon)
        } else {
            stage = next.stage
            ev += engine.advanceFromFeed(StageFeed.phaseOf(next.stage!!), atMs, lat, lon)
        }
        return ev
    }

    private fun timed(line: TripStages.Trip?): Boolean = line != null && line.active && line.events.isNotEmpty()

    private fun clamp(atMs: Long, floorMs: Long, now: Long): Long = atMs.coerceIn(minOf(floorMs, now), now)

    /** Bağlı siparişe son uygulanandan ileri olan aşamaları kendi saatleriyle uygular. */
    private fun timeline(e: ShiftEngine, line: TripStages.Trip, now: Long, lat: Double?, lon: Double?): List<DayEvent> {
        val o = e.s.current ?: return emptyList()
        val out = ArrayList<DayEvent>()
        for (x in line.events) {
            val applied = stage
            if (applied != null && x.stage.ordinal <= applied.ordinal) continue
            val atX = clamp(x.atMs, at ?: o.startedAtMs, now)
            val fresh = now - atX <= FRESH_STAGE_MS
            out += e.stageFromFeed(StageFeed.phaseOf(x.stage), atX, if (fresh) lat else null, if (fresh) lon else null)
            stage = x.stage
            at = atX
            if (e.s.current !== o) break
        }
        return out
    }

    /** Bağlı yolculuk bitti ve sonucunu ve saatini söylüyor: kalan aşamalar, sonra teslim ya da iptal. */
    private fun ended(e: ShiftEngine, line: TripStages.Trip, now: Long, lat: Double?, lon: Double?): List<DayEvent> {
        val o = e.s.current!!
        val out = ArrayList<DayEvent>(timeline(e, line, now, lat, lon))
        if (e.s.current !== o) return out
        val end = clamp(line.endedAtMs ?: now, at ?: o.startedAtMs, now)
        if (line.outcome == TripStages.Outcome.CANCELLED) {
            if (e.s.phase == OrderPhase.TO_RESTAURANT || e.s.phase == OrderPhase.AT_RESTAURANT) {
                out += e.cancelOrder(end).map { it.copy(data = it.data + ("source" to ShiftEngine.FEED_SOURCE)) }
            } else {
                released += o.n // Alıştan önce bitti, WEEM'de alındı: elle durum kalır
            }
        } else {
            out += e.deliverFromFeed(end)
        }
        return out
    }

    /** Gün başlarken/biterken: bağ kapanır (süren yolculuk yeni günde sipariş açmaz). */
    fun dayChanged() {
        close()
        released.clear()
    }

    private fun close() {
        val t = trip
        if (t != null && t !in closed) {
            closed.add(t)
            if (closed.size > keepClosed) closed.subList(0, closed.size - keepClosed).clear()
        }
        trip = null; order = null; stage = null; missing = 0; at = null
    }

    /** Kalıcı durum: yalnız yolculuk kimlikleri, sipariş numarası ve aşama adı. Adres/müşteri bilgisi yok. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        trip?.let { put("trip", it) }
        order?.let { put("order", it) }
        stage?.let { put("stage", it.key) }
        at?.let { put("at", it) }
        if (missing > 0) put("missing", missing)
        put("closed", JSONArray(closed))
        if (released.isNotEmpty()) put("released", JSONArray(released.sorted()))
    }

    fun restore(j: JSONObject) {
        trip = j.opt("trip") as? String
        order = (j.opt("order") as? Number)?.toInt()
        stage = Stage.of(j.opt("stage") as? String)
        missing = (j.opt("missing") as? Number)?.toInt() ?: 0
        at = (j.opt("at") as? Number)?.toLong()?.takeIf { it > 0 }
        closed.clear()
        (j.opt("closed") as? JSONArray)?.let { a -> for (i in 0 until a.length()) (a.opt(i) as? String)?.let { closed.add(it) } }
        released.clear()
        (j.opt("released") as? JSONArray)?.let { a -> for (i in 0 until a.length()) (a.opt(i) as? Number)?.let { released.add(it.toInt()) } }
        if (trip == null || order == null) { trip = null; order = null; stage = null; missing = 0; at = null }
    }

    companion object {
        /** Konum yalnız bu kadar taze bir aşamaya yazılır (geç okunan aşamada şimdiki konum o anın konumu değildir). */
        const val FRESH_STAGE_MS = 30_000L
    }
}
