package com.kuryelog.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * Gölge mod fikstürü üreticisi (WEEM A3.9): saha karelerini Rawstats @ 6388c04'ün saf sınıflarından (CourierParser, DeliveryCore,
 * OnlineSessions, BoostLog, PlatformHistory, PlatformTotals, CashPending, RecordJson) `OrderTracker.onScreen` sırasıyla geçirir
 * (OrderTracker.kt:300-381, 396-470, 511-572, 807-828) ve gün klasörlerini yazar. Android'e bağlı kısımlar yok: konum (null),
 * Geocoder (sonuç yok), YolGo, hava, ekonomi kaydedicisi (yalnız economics-exposure), bildirimler, balon/kart pencereleri. Saat = kare anı.
 * Gün başına yeni süreç (vardiya ilk karede başlar, son kareden 1 ms sonra biter).
 */
class Tracker(val out: File) {
    val core = DeliveryCore()
    private val sessions = OnlineSessions()
    private val boosts = BoostLog()
    private val snapshotHashes = HashMap<String, Int>()
    private val exposureAt = HashMap<String, Long>()
    private var clock = 0L
    private var lastPersist = 0L
    private val tz = TimeZone.getDefault()
    var pendingLast: Pair<Double, Long>? = null

    fun append(name: String, line: String, day: String? = null) {
        val d = File(out, day ?: Store.day(clock)); d.mkdirs(); File(d, name).appendText(line + "\n")
    }

    fun start(now: Long) { clock = now; core.start(); persist(true) }
    fun stop(now: Long) {
        clock = now
        sessions.stop().forEach { append("online-sessions.jsonl", OnlineSessions.json(it).put("t", Store.ts(it.endAt)).toString()) }
        val events = core.stop(now)
        process(events); persist(true)
    }

    fun onScreen(pkg: String, texts: List<String>, actions: List<String>, now: Long, source: String) {
        clock = now
        platformTotals(PlatformTotals.woltDaily(pkg, texts) + PlatformTotals.boltDays(pkg, texts) + cashTotals(pkg, texts), now)
        if (platformHistory(pkg, texts, now, source)) return
        cashCollections(pkg, texts, now)
        cashPending(pkg, texts, now)
        val o = CourierParser.parse(pkg, texts, actions, now, source, true).let { CashPayment.attach(it, CashPayment.parse(pkg, texts, null)) }
        PlatformTotals.fromStats(o)?.let { platformTotals(listOf(it), now) }
        val closedSession = sessions.observe(o)
        val events = core.observe(o)
        (core.pending[pkg] ?: core.active(pkg)?.offer)?.takeIf { it.feeAt == now && it.feeOcrMs == null }?.let { it.feeOcrMs = 0 }
        events.forEach { e -> linkSession(e); val t = e.tripId?.let { core.trips[it] }; sessions.event(e, t?.customers?.size ?: 1, t?.deliveredInferredAt != null) }
        listOfNotNull(closedSession).forEach { append("online-sessions.jsonl", OnlineSessions.json(it).put("t", Store.ts(it.endAt)).toString()) }
        if (now - (exposureAt[pkg] ?: 0) >= 5000 || events.isNotEmpty()) {
            exposureAt[pkg] = now
            val hour = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Baku")).apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
            append("economics-exposure.jsonl", JSONObject().put("at", now).put("pkg", pkg).put("stage", o.stage.name).put("complete", o.complete).put("source", o.source)
                .put("demand", o.demand ?: "unknown").put("vehicleMode", "unknown").put("zone", "unknown").put("bakuHour", hour)
                .put("dispatchAvailabilityVerified", false).toString())
        }
        boosts.observe(o, Store.day(now), Store.locJson()).forEach { append(BoostLog.FILE, it.toString()) }
        core.pending[pkg]?.let { offer -> if (events.any { it.type == "offer" }) {
            offer.phoneBattery = 77
            boosts.atOffer(o, now).let { b -> offer.boostValue = b.value; offer.boostUnit = b.unit; offer.boostSource = b.source; offer.boostSeenAt = b.seenAt }
        } }
        process(events)
        if (o.stage == ScreenStage.IDLE || o.stage == ScreenStage.OFFLINE || o.multipliers.isNotEmpty()) {
            val j = JSONObject().put("pkg", pkg).put("stage", o.stage.name).put("demand", o.demand).put("bonus_pct", o.bonusPct)
                .put("bonus_from", o.bonusFrom).put("multipliers", JSONArray(o.multipliers)).put("offline_until", o.offlineUntil)
            val hash = j.toString().hashCode()
            if (snapshotHashes["platform:$pkg"] != hash) { snapshotHashes["platform:$pkg"] = hash; j.put("t", Store.ts(now)); append("platforms.jsonl", j.toString()) }
        }
        persist(events.isNotEmpty()); tick(now)
    }

    /** Açık restoran balonu (sipariş, alım sırası); müşteri kartı (E16) konumsuz açılmaz. */
    private var reminder: Pair<String, Int>? = null
    /** OrderTracker.tick (574-641) konumsuz: E14 restoran balonu varış görülünce açılır (ilk açılışta `reminderAt`), kapanış kuralları. */
    private fun tick(now: Long) {
        if (!core.running) { reminder = null; return }
        for (t in core.trips.values.filter { it.active && (it.phase == TripPhase.TO_RESTAURANT || it.phase == TripPhase.WAITING) }) {
            val i = t.pickups.indexOfLast { it.pickedAt == null }; if (i < 0) continue
            val p = t.pickups[i]
            if (reminder == t.id to i) continue
            if (!RestaurantBubble.visible(false, null, p.arrivedAt != null, false)) continue
            if (p.reminderAt == null) p.reminderAt = now
            reminder = t.id to i
            persist(true)
        }
        reminder?.let { (id, i) ->
            val t = core.trips[id]; val p = t?.pickups?.getOrNull(i)
            if (t == null || p == null || !t.active || p.pickedAt != null || (t.phase != TripPhase.TO_RESTAURANT && t.phase != TripPhase.WAITING)) reminder = null
            else if (!RestaurantBubble.visible(true, null, p.arrivedAt != null, false)) reminder = null
        }
        persist(false)
    }

    private val historyKeys = HashMap<String, MutableSet<String>>()
    private val historyTaken = HashMap<String, MutableSet<String>>()
    private val historyWritten = HashMap<String, MutableList<JSONObject>>()
    private var historyHash = 0
    private fun historyDay(day: String) {
        if (historyKeys.containsKey(day)) return
        val f = File(File(out, day), PlatformHistory.FILE)
        val (k, t, w) = if (f.exists()) f.useLines { PlatformHistory.loaded(it) } else Triple(HashSet<String>(), HashSet<String>(), ArrayList<JSONObject>())
        historyKeys[day] = k; historyTaken[day] = t; historyWritten[day] = w
    }
    private fun platformHistory(pkg: String, texts: List<String>, now: Long, source: String): Boolean {
        val rows = PlatformHistory.parse(pkg, texts, now, tz) ?: return false
        val hash = (pkg + texts.joinToString("\n")).hashCode()
        if (hash == historyHash) return true
        historyHash = hash
        val out = PlatformHistory.records(rows, now, source, pkg, tz,
            keys = { d -> historyDay(d); historyKeys.getValue(d) }, taken = { d -> historyDay(d); historyTaken.getValue(d) },
            tripsOn = { d -> core.trips.values.filter { Store.day(OrderHistory.listTime(it, OrderTracker_platform(it.pkg))) == d || Store.day(it.acceptedAt) == d } },
            written = { d -> historyDay(d); historyWritten.getValue(d) })
        for ((day, j) in out) append(PlatformHistory.FILE, j.toString(), day)
        var liveChanged = false
        for (r in rows) if (r.extraWait != null || r.tip != null) {
            val day = r.day ?: PlatformHistory.dayOf(now, tz)
            historyDay(day)
            val id = PlatformHistory.owner(r, day, historyWritten.getValue(day)) ?: continue
            val t = core.trips[id] ?: continue
            var changed = false
            r.extraWait?.let { if (t.extraWaitAzn != it) { t.extraWaitAzn = it; changed = true } }
            r.tip?.let { if (t.platformTipAzn != it) { t.platformTipAzn = it; changed = true } }
            if (changed) liveChanged = true
        }
        val by = if (pkg in CourierParser.WOLT) "wolt_earnings_list" else "bolt_deliveries_list"
        for ((_, j) in out) j.optJSONObject("match")?.optString("trip_id", "")?.takeIf { it.isNotEmpty() }?.let { id -> core.listVerified(id, by, now) }
        if (out.isNotEmpty() || liveChanged) persist(true)
        return true
    }
    private val totalsWritten = HashMap<String, PlatformTotals.Total>()
    private val cashSeen = HashMap<String, LinkedHashSet<PlatformHistory.CashRow>>()
    private fun cashTotals(pkg: String, texts: List<String>): List<PlatformTotals.Total> =
        PlatformHistory.cashRows(pkg, texts).onEach { cashSeen.getOrPut(it.day) { LinkedHashSet() }.add(it) }.map { it.day }.distinct()
            .flatMap { PlatformTotals.woltCash(cashSeen.getValue(it).toList()) }
    private fun platformTotals(totals: List<PlatformTotals.Total>, now: Long) {
        for (t in totals) {
            val k = PlatformTotals.key(t)
            if (totalsWritten[k] == t) continue
            totalsWritten[k] = t
            append(PlatformTotals.FILE, PlatformTotals.record(t, now).toString(), PlatformTotals.dayOf(t.period) ?: Store.day(now))
        }
    }
    private var cashHash = 0
    private fun cashCollections(pkg: String, texts: List<String>, now: Long) {
        val rows = PlatformHistory.cashRows(pkg, texts).takeIf { it.isNotEmpty() } ?: return
        val hash = texts.joinToString("\n").hashCode()
        if (hash == cashHash) return
        cashHash = hash
        val events = ArrayList<CoreEvent>()
        for (r in rows) {
            val at = PlatformHistory.toMs(r.day, r.clock, tz)
            if (at > now + 60_000) continue
            val t = PlatformHistory.cashMatch(r, at, core.trips.values) ?: continue
            events += core.cashCollected(t.id, at, r.amount, now)
        }
        if (events.isEmpty()) return
        process(events); persist(true)
    }
    private val offloadsWritten = HashSet<String>()
    private fun cashPending(pkg: String, texts: List<String>, now: Long) {
        CashPending.pending(pkg, texts)?.let { v ->
            val last = pendingWritten
            if (CashPending.shouldWrite(last, v, now)) { append(CashPending.FILE, CashPending.pendingRow(v, now).toString()); pendingWritten = v to now }
            pendingLast = v to now
        }
        for (r in PlatformHistory.offloadRows(pkg, texts)) {
            if (offloadsWritten.add("${r.day} ${r.clock} ${r.amount}")) append(CashPending.FILE, CashPending.offloadRow(r, now).toString(), r.day)
        }
    }
    private var pendingWritten: Pair<Double, Long>? = null
    fun cashPendingShift(kind: String, shiftStartMs: Long, now: Long) {
        val last = pendingLast
        val day = Store.day(shiftStartMs)
        append(CashPending.FILE, CashPending.shiftRow(kind, shiftStartMs, last?.first, last?.second, now).toString(), day)
        if (kind != CashPending.SHIFT_END) return
        for (t in core.trips.values.filter { it.acceptedAt >= shiftStartMs }) append(CashPending.FILE, CashPending.orderSnapshot(t.id, shiftStartMs, last?.first, now).toString(), day)
    }
    private fun linkSession(e: CoreEvent) {
        val id = sessions.currentId(e.pkg)
        when (e.type) {
            "offer" -> { core.pending[e.pkg]?.takeIf { it.id == e.offerId && it.sessionId == null }?.let { it.sessionId = id } }
            "accepted" -> { e.tripId?.let { core.trips[it] }?.takeIf { it.sessionId == null }?.let { t -> t.sessionId = t.offer?.sessionId ?: id } }
        }
    }
    private fun process(events: List<CoreEvent>) {
        for (e in events) {
            val trip = e.tripId?.let { core.trips[it] }
            if (e.type == "accepted" && trip != null) { trip.commissionPct = 0.0; trip.priceIsNet = false }
            if (trip != null && e.type in setOf("delivered", "delivered_inferred", "end_unconfirmed")) core.closeEnergy(trip)
            val j = JSONObject().put("t", Store.ts(e.at)).put("type", e.type).put("pkg", e.pkg).put("offer_id", e.offerId).put("id", e.tripId).put("reason", e.reason)
                .put("session_id", trip?.sessionId ?: core.pending[e.pkg]?.takeIf { it.id == e.offerId }?.sessionId ?: sessions.currentId(e.pkg))
            trip?.let { j.put("record", RecordJson.trip(it)); j.put("gross", it.gross); j.put("net", it.net); j.put("total_min", it.actualMinutes); j.put("wait_min", it.waitMinutes); j.put("km", it.distanceM / 1000); j.put("restaurant", it.pickups.firstOrNull()?.restaurant) }
            core.pending[e.pkg]?.takeIf { it.id == e.offerId }?.let { j.put("offer", RecordJson.offer(it)) }
            j.put("phone_battery_pct", 77)
            append("orders.jsonl", j.toString())
        }
    }
    private fun persist(force: Boolean) {
        val now = clock; if (!force && now - lastPersist < 1500) return
        lastPersist = now
        for (t in core.trips.values) {
            val j = RecordJson.trip(t)
            val portable = JSONObject(j.toString()); portable.remove("latest")
            val hash = portable.toString().hashCode()
            if (snapshotHashes[t.id] != hash && (force || now % 10000 < 2000)) {
                snapshotHashes[t.id] = hash
                append("orders.jsonl", JSONObject().put("t", Store.ts(now)).put("type", "record_snapshot").put("record", j).toString())
            }
        }
    }
}

fun OrderTracker_platform(pkg: String) = if (pkg.contains("wolt", true)) "Wolt" else if (pkg.contains("bolt", true) || pkg.contains("mtakso", true)) "Bolt Food" else pkg.substringAfterLast('.')

fun main(args: Array<String>) {
    val frames = File(args[0]); val out = File(args[1]); out.deleteRecursively(); out.mkdirs()
    val stages = StringBuilder()
    val rows = GZIPInputStream(frames.inputStream()).bufferedReader().readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
    for ((day, fs) in rows.groupBy { it.getString("gun") }) {
        val tr = Tracker(out)
        fun at(o: JSONObject) = OffsetDateTime.parse(o.getString("t").replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")).toInstant().toEpochMilli()
        val first = at(fs.first()); val last = fs.maxOf { at(it) }
        tr.start(first); tr.cashPendingShift(CashPending.SHIFT_START, first, first)
        for (f in fs) {
            fun list(k: String) = f.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val o = CourierParser.parse(f.getString("pkg"), list("texts"), list("actions"), at(f), f.optString("src"))
            stages.append(o.stage.name).append('\n')
            tr.onScreen(f.getString("pkg"), list("texts"), list("actions"), at(f), f.optString("src"))
        }
        tr.cashPendingShift(CashPending.SHIFT_END, first, last + 1)
        tr.stop(last + 1)
    }
    File(out, "stages.txt").writeText(stages.toString())
}
