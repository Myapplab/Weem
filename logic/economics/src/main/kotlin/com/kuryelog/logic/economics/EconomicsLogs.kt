package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsLogs.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Rows of the SPEC 2.1.0 §17.2 economics logs (`kuryelog_logs_v2.schema.json`) and the v2 feed objects of §17.1
 * (`feed_v2.schema.json`). Pure Kotlin: no Android imports, only org.json methods that Android has.
 *
 * Privacy (SPEC §18.1): no row holds a customer name, phone, address, note, apartment, floor, door code, check id,
 * raw screen text or a coordinate. Places become coarse zones (Z1–Z4, "unknown"), read only from a Plus Code or
 * DMS coordinate printed on the card ([OfferGeo]), never geocoded. Penalty reasons are rebuilt from parsed values,
 * never copied from the screen.
 */
object EconomicsLogs {
    const val OFFERS = "economics-offers.jsonl"
    const val LEGS = "economics-legs.jsonl"
    const val IDLE = "economics-idle.jsonl"
    const val PENALTIES = "economics-penalties.jsonl"
    const val WAITS = "economics-waits.jsonl"
    /** Files new in 2.1.0; every one is in [BackupFiles.logNames]. */
    val NEW_FILES = listOf(OFFERS, LEGS, IDLE, PENALTIES)
    const val SPEC_VERSION = "2.1.0"
    /** SPEC §13 freshness: an offer last seen longer ago is not visible now. */
    const val FRESH_MS = 5_000L
    /** Riding at least this far during a free period counts as repositioning (DESIGN, not verified on the phone). */
    const val REPOSITION_KM = 0.5
    /** SPEC §17.2 `countsAsDecline` kinds (Bolt optional orders never count). */
    val DECLINE_KINDS = setOf("declined_tap_observed", "declined_banner_observed", "timeout_inferred", "disappeared_unknown")
    private val FEE_BASES = setOf("net_to_courier", "gross_before_park", "unknown")
    private val BAKU: TimeZone = TimeZone.getTimeZone("Asia/Baku")

    private fun n(v: Any?): Any = v ?: JSONObject.NULL

    /** Minute of the Baku day (fractional), as `ref.hour_block` expects. */
    fun minuteOfDay(at: Long): Double {
        val c = Calendar.getInstance(BAKU).apply { timeInMillis = at }
        return c.get(Calendar.HOUR_OF_DAY) * 60.0 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60.0
    }
    fun hourBlock(at: Long): String = OfferGeo.hourBlock(minuteOfDay(at))
    /** Python-compatible rounding (HALF_EVEN). */
    fun round(x: Double, digits: Int = 3): Double = BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toDouble()

    /** "21:46" on a Baku screen at [at] → the next such instant (today, or tomorrow if it already passed). */
    fun clockToMs(clock: String?, at: Long): Long? {
        val m = Regex("^([0-9]{1,2}):([0-9]{2})$").find(clock?.trim() ?: return null) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) return null
        val c = Calendar.getInstance(BAKU).apply {
            timeInMillis = at; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return if (c.timeInMillis < at - 60_000L) c.timeInMillis + 86_400_000L else c.timeInMillis
    }

    // ------------------------------------------------------------------ offers

    /** Zone of a place printed on the card (Plus Code or DMS only; a street name is never looked up). */
    fun zoneOfText(text: String?, ref: OfferGeo.LatLon? = null): String {
        if (text.isNullOrBlank()) return OfferGeo.UNKNOWN
        val p = OfferGeo.fromCardText(text, ref?.lat, ref?.lon) ?: return OfferGeo.UNKNOWN
        return OfferGeo.coarseZone(p.lat, p.lon)
    }
    private fun ref(o: OfferState): OfferGeo.LatLon? { val la = o.lat; val lo = o.lon; return if (la != null && lo != null) OfferGeo.LatLon(la, lo) else null }
    fun pickupZone(o: OfferState): String = zoneOfText(o.data.restaurantAddress, ref(o))
    fun dropoffZone(o: OfferState): String = zoneOfText(o.data.customerAddress, ref(o))

    /** Bolt's priced button ("22 min, 4,49 ₼"): its minutes are the total; Wolt shows a range instead. */
    private fun boltTotal(d: CourierObservation): Int? = if (Platforms.of(d.pkg) == "bolt" && d.minLo != null && d.minLo == d.minHi) d.minHi else null

    /** What the card showed (feed v2 `shown`, also the `shown` object of `offerRecord`). */
    fun shown(d: CourierObservation): JSONObject = JSONObject()
        .put("minLo", n(d.minLo)).put("minHi", n(d.minHi)).put("km", n(d.km)).put("kmScope", "unknown")
        .put("stops", n(d.stops)).put("readyInMin", n(d.readyIn)).put("approachMin", n(d.approachMin)).put("deliveryMin", n(d.deliveryMin))
        .put("totalMin", n(boltTotal(d))).put("multiplier", n(d.multipliers.singleOrNull())).put("boostPct", n(d.bonusPct))
        .put("demandLabel", n(d.demand))

    /** `documented_upper_bound` (first seen + the documented window, an UNVERIFIED prior); KuryePlus reads no screen timer. */
    fun expiresAt(o: OfferState): Long? = DeliveryCore.offerWindowMs(o.pkg)?.let { o.firstAt + it }
    fun expirySource(o: OfferState): String = if (expiresAt(o) != null) "documented_upper_bound" else "unknown"

    /**
     * SPEC §17.2 mapping of a core event to `decisionKind`. Offers pending at a process restart and not seen since
     * ([restartedAtMs]) end as `recording_stopped` unless a tap or banner was observed. Null: not a decision.
     */
    fun decisionKind(type: String, reason: String?, o: OfferState, restartedAtMs: Long = Long.MIN_VALUE): String? {
        if (type == "accepted") return "accepted_observed"
        if (type != "rejected" && type != "offer_unconfirmed") return null
        val unseenSinceRestart = o.lastSeenAt < restartedAtMs
        return when (reason) {
            "courier_declined_confirmed", "courier_declined" -> "declined_tap_observed"
            "courier_declined_banner" -> "declined_banner_observed"
            "manual_shift_stop", "restart" -> "recording_stopped"
            else -> if (unseenSinceRestart) "recording_stopped" else when (reason) {
                "offer_to_idle_map" -> if (visibleUntilExpiry(o)) "timeout_inferred" else "disappeared_unknown"
                "offer_not_seen" -> "disappeared_unknown"
                "expired_inferred" -> "timeout_inferred"
                "replaced_without_idle" -> "replaced_by_new_offer"
                else -> if (type == "rejected" && reason?.contains("declin") == true) "declined_tap_observed" else "disappeared_unknown"
            }
        }
    }
    /** Still on screen at `expiresAtMs − 2 s` (SPEC §17.2 `timeout_inferred`). */
    private fun visibleUntilExpiry(o: OfferState): Boolean = expiresAt(o)?.let { o.lastSeenAt >= it - DeliveryCore.EXPIRY_MARGIN_MS } == true
    fun countsAsDecline(kind: String, optional: Boolean): Boolean = !optional && kind in DECLINE_KINDS

    /** `offerRecord` v1, written once when the offer resolves; null for other platforms or non-decision events. */
    fun offerRecord(o: OfferState, e: CoreEvent, feeBasis: String, duringTripId: String?, restartedAtMs: Long = Long.MIN_VALUE,
                    verdictShadow: JSONObject? = null): JSONObject? {
        val platform = Platforms.of(o.pkg) ?: return null
        val kind = decisionKind(e.type, e.reason, o, restartedAtMs) ?: return null
        val exp = expiresAt(o)
        return JSONObject().put("schemaVersion", 1).put("offerKey", o.id).put("pkg", o.pkg).put("platform", platform)
            .put("firstSeenAtMs", o.firstAt).put("lastSeenAtMs", maxOf(o.lastSeenAt, o.firstAt)).put("decisionKind", kind)
            .put("decisionAtMs", maxOf(e.at, 0L)).put("duringTripId", n(duringTripId)).put("feeAzn", n(o.data.gross))
            .put("feeBasis", if (feeBasis in FEE_BASES) feeBasis else "unknown").put("shown", shown(o.data))
            .put("pickupZone", pickupZone(o)).put("dropoffZone", dropoffZone(o)).put("optional", o.optional)
            .put("expiresAtMs", n(exp)).put("expirySource", if (exp != null) "documented_upper_bound" else "unknown")
            .put("countsAsDecline", countsAsDecline(kind, o.optional)).put("verdictShadow", verdictShadow ?: JSONObject.NULL)
            .also { r -> o.weather?.let { r.put("weather", weather(it)) } }
    }
    /** SPEC `$defs/weather` from YolGo's Open-Meteo summary (K28): ambient °C, gust m/s, rain when known. */
    fun weather(w: OfferWeather): JSONObject = JSONObject().put("ambientC", n(w.tempC)).put("gustMs", n(w.gustKmh?.let { round(it / 3.6, 2) }))
        .put("rain", when { w.rainMm != null -> w.rainMm!! > 0.0; w.code != null -> w.code in 51..99; else -> JSONObject.NULL })
        .put("source", "open_meteo")

    // ------------------------------------------------------------------ feed v2 (SPEC §17.1)

    /** The `v2` object of one feed offer; null for a platform the schema does not know. */
    fun feedOfferV2(o: OfferState, now: Long, duringTripId: String?): JSONObject? {
        val platform = Platforms.of(o.pkg) ?: return null
        val d = o.data
        val exp = expiresAt(o)
        val feeSource = when (o.fieldSource["gross"] ?: d.source) { "ocr" -> "ocr"; "notification" -> "notification"; else -> "accessibility" }
        val confidence = when { d.gross == null -> "low"; feeSource == "accessibility" && d.complete -> "high"; else -> "medium" }
        return JSONObject().put("offerKey", o.id).put("platform", platform).put("firstSeenAtMs", o.firstAt)
            .put("lastSeenAtMs", maxOf(o.lastSeenAt, o.firstAt)).put("visibleNow", now - o.lastSeenAt <= FRESH_MS)
            .put("expiresAtMs", n(exp)).put("expirySource", if (exp != null) "documented_upper_bound" else "unknown")
            .put("shown", shown(d)).put("pickupZone", pickupZone(o)).put("dropoffZone", dropoffZone(o))
            // Bolt's card shows no drop-off (K8): the reader must not assume one.
            .put("dropoffKnown", d.customerAddress != null).put("optional", o.optional).put("duringTripId", n(duringTripId))
            .put("parse", JSONObject().put("feeSource", feeSource).put("confidence", confidence))
    }

    // WEEM: `platformOf` / `stage` → `Platforms.of` / `Platforms.stage` (`:orders`, ORDERS.md 13.3).

    /** Feed v2 `courierState`. Unknown values stay null/"unknown"; nothing here is a customer detail. */
    fun courierState(appsOnline: Collection<String>, trips: Collection<TripRecord>, woltDeclinesInWindow: Int, windowMin: Double,
                     woltOfflineUntilMs: Long?, woltOfflineReason: String?, woltTriggerCounts: List<Int>, boltAcceptanceRate: Double?,
                     hereZone: String, speedKmh: Double?, repositioning: Boolean): JSONObject {
        val active = JSONArray()
        trips.filter { it.active }.sortedBy { it.acceptedAt }.takeLast(3).forEach { t ->
            val c = t.customers[t.currentCustomer]
            active.put(JSONObject().put("tripId", t.id).put("platformPackage", t.pkg).put("stage", Platforms.stage(t.phase) ?: "to_pickup")
                .put("remainingMinEstimate", JSONObject.NULL).put("dropZone", OfferGeo.coarseZone(c?.lat, c?.lon)))
        }
        val reason = woltOfflineReason?.takeIf { it in setOf("declines", "scheduled", "cash_limit", "unknown") }
        return JSONObject().put("appsOnline", JSONArray(appsOnline.distinct().sorted())).put("activeTrips", active)
            .put("wolt", JSONObject().put("declinesInWindow", maxOf(0, woltDeclinesInWindow)).put("windowMin", windowMin)
                .put("offlineUntilMs", n(woltOfflineUntilMs)).put("offlineReason", n(reason))
                .put("penaltyHistory", JSONObject().put("triggerCounts", JSONArray(woltTriggerCounts.filter { it >= 1 }))))
            .put("bolt", JSONObject().put("acceptanceRate", n(boltAcceptanceRate))
                .put("acceptanceRateSource", if (boltAcceptanceRate != null) "screen" else "unknown"))
            // The scooter's battery is not read by KuryePlus yet.
            .put("scooter", JSONObject().put("batteryPct", JSONObject.NULL).put("observedAtMs", JSONObject.NULL).put("source", "unknown"))
            .put("hereZone", if (hereZone in setOf("Z1", "Z2", "Z3", "Z4")) hereZone else OfferGeo.UNKNOWN)
            .put("speedKmh", n(speedKmh?.takeIf { it.isFinite() && it >= 0 }?.let { round(it, 1) })).put("repositioning", repositioning)
    }
    /** A schema-valid `courierState` that knows nothing (feed readers get this when the caller passes none). */
    fun unknownCourierState(): JSONObject = courierState(emptyList(), emptyList(), 0, DeliveryCore.DECLINE_WINDOW_MS / 60_000.0,
        null, null, emptyList(), null, OfferGeo.UNKNOWN, null, false)

    // ------------------------------------------------------------------ waits v2

    /** When the courier became available on the platform before the first visible waiting frame. */
    data class Since(val at: Long, val source: String)

    /**
     * `economics-waits.jsonl` schemaVersion 2: one row per segment of the spell (all v1 keys with v1 meaning, times of the
     * segment). Segments end with `context_changed` except the last, which carries the spell's own end; only the last can
     * be an observed offer. [zoneAt], [boostDuring] and [kmDuring] read the courier's GPS zone, the platform's boost and the
     * distance ridden for an interval.
     */
    fun waitRows(e: WaitObservation.Episode, since: Since, zoneAt: (Long) -> String, boostDuring: (Long, Long) -> Boolean?,
                 kmDuring: (Long, Long) -> Double?): List<JSONObject> {
        val segs = e.segments.ifEmpty { listOf(WaitObservation.Segment(e.startAt, e.endAt, e.demand, e.bakuHour, "unknown")) }
        return segs.mapIndexed { i, s ->
            val last = i == segs.lastIndex
            val start = maxOf(0L, s.startAt); val end = maxOf(start, s.endAt)
            val km = kmDuring(start, end)
            JSONObject().put("schemaVersion", 2).put("pkg", e.pkg).put("startAtMs", start).put("endAtMs", end)
                .put("durationMinutes", (end - start) / 60000.0).put("eventObserved", last && e.eventObserved)
                .put("cause", e.cause).put("endReason", if (last) e.endReason else "context_changed")
                .put("demand", s.demand ?: "unknown").put("vehicleMode", e.vehicleMode).put("zone", e.zone)
                .put("bakuHour", s.bakuHour.coerceIn(-1, 23)).put("dispatchAvailabilityVerified", false)
                .put("spellId", e.spellId).put("segmentIndex", i)
                .put("availableSinceMs", maxOf(0L, minOf(since.at, e.startAt))).put("availableSinceSource", since.source)
                .put("coarseZone", zoneAt(start)).put("hourBlock", hourBlock(start)).put("boostActive", n(boostDuring(start, end)))
                .put("otherAppState", s.otherAppState).put("repositioning", km != null && km >= REPOSITION_KM).put("visibleMs", end - start)
        }
    }

    // ------------------------------------------------------------------ trip legs

    /** Boundaries of a trip whose GPS distance is sampled: accepted, each pickup's arrival and collection, each customer's arrival and delivery. */
    fun legBoundaries(t: TripRecord): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        out.add("accepted" to t.acceptedAt)
        t.pickups.forEachIndexed { i, p -> p.arrivedAt?.let { out.add("arrive:$i" to it) }; p.pickedAt?.let { out.add("pick:$i" to it) } }
        t.customers.values.forEach { c -> c.arrivedAt?.let { out.add("carrive:${c.id}" to it) }; c.deliveredAt?.let { out.add("deliver:${c.id}" to it) } }
        t.completedAt?.let { out.add("completed" to it) }
        return out
    }

    /** SPEC §5 platform minutes M of the accepted offer and their source. */
    fun shownMinutes(d: CourierObservation?): Pair<Double?, String> {
        if (d == null) return null to "missing"
        if (Platforms.of(d.pkg) == "wolt") {
            val lo = d.minLo; val hi = d.minHi
            return when { lo != null && hi != null -> (lo + hi) / 2.0 to "wolt_range_midpoint"; lo != null -> lo.toDouble() to "wolt_range_midpoint"; else -> null to "missing" }
        }
        val a = d.approachMin; val b = d.deliveryMin
        if (a != null && b != null) return (a + b).toDouble() to "bolt_eta_sum"
        return boltTotal(d)?.let { it.toDouble() to "bolt_button_total" } ?: (null to "missing")
    }

    /**
     * `tripLegs` v1 for a trip that ended (delivered, or closed without confirmation → outcome "unknown"; a K34 inferred
     * delivery is one of the latter, with `excludeFromCalibration` true).
     * [samples] maps [legBoundaries] keys to the trip's GPS metres at that moment; a leg without both samples, or a trip
     * without any GPS distance, has `km` null. [why] is the reason of the `delivered` event.
     */
    fun tripLegs(t: TripRecord, samples: Map<String, Double>, others: Collection<TripRecord>, why: String? = null,
                 endOf: (TripRecord) -> Long = { x -> x.completedAt ?: if (x.active) Long.MAX_VALUE else (x.latest?.at ?: x.acceptedAt) }): JSONObject {
        val legs = JSONArray()
        val gps = t.distanceM > 0
        fun km(a: String, b: String?): Any {
            if (!gps || b == null) return JSONObject.NULL
            val x = if (a == "accepted") samples[a] ?: 0.0 else samples[a] ?: return JSONObject.NULL
            val y = samples[b] ?: return JSONObject.NULL
            return if (y >= x) round((y - x) / 1000.0) else JSONObject.NULL
        }
        fun leg(kind: String, a: String, at: Long, b: String?, bt: Long?, source: String) {
            legs.put(JSONObject().put("kind", kind).put("startAtMs", at).put("endAtMs", n(bt)).put("km", km(a, b))
                .put("wh", JSONObject.NULL).put("source", source))
        }
        var prev: Pair<String, Long>? = "accepted" to t.acceptedAt
        for ((i, p) in t.pickups.withIndex()) {
            val from = prev ?: break
            val arrive = p.arrivedAt; val pick = p.pickedAt
            // D9: a Wolt arrival comes from the courier's GPS stay at the restaurant, not from a screen.
            val arriveSource = if (p.arrivalSource?.startsWith("gps") == true) "gps" else "screen_stage"
            prev = when {
                arrive != null -> {
                    leg("approach", from.first, from.second, "arrive:$i", arrive, arriveSource)
                    if (pick != null) { leg("wait_at_venue", "arrive:$i", arrive, "pick:$i", pick, arriveSource); "pick:$i" to pick } else null
                }
                pick != null -> { leg("approach", from.first, from.second, "pick:$i", pick, "inferred"); "pick:$i" to pick }
                else -> { leg("approach", from.first, from.second, null, null, "inferred"); null }
            }
        }
        val manual = why == "courier_manual_correction"
        val customers = t.customers.values.sortedWith(compareBy({ it.deliveredAt ?: Long.MAX_VALUE }, { it.arrivedAt ?: Long.MAX_VALUE }))
        if (prev != null) for (c in customers) {
            val from = prev ?: break
            val arrive = c.arrivedAt; val done = c.deliveredAt
            val doneSource = if (manual && done == t.completedAt) "manual" else "screen_stage"
            prev = when {
                arrive != null -> {
                    leg("to_dropoff", from.first, from.second, "carrive:${c.id}", arrive, "screen_stage")
                    if (done != null) { leg("handoff", "carrive:${c.id}", arrive, "deliver:${c.id}", done, doneSource); "deliver:${c.id}" to done } else null
                }
                done != null -> { leg("to_dropoff", from.first, from.second, "deliver:${c.id}", done, if (manual && done == t.completedAt) "manual" else "inferred"); "deliver:${c.id}" to done }
                else -> { leg("to_dropoff", from.first, from.second, null, null, "inferred"); null }
            }
        }
        val delivered = t.phase == TripPhase.FINISHED && t.completedAt != null
        // Another task open at the same time (any platform): its time is not this task's own (SPEC 5 calibration).
        val end = endOf(t)
        val stacked = others.firstOrNull { o -> o.id != t.id && o.acceptedAt < end && endOf(o) > t.acceptedAt }
        val (m, mSource) = shownMinutes(t.offer?.data)
        return JSONObject().put("schemaVersion", 1).put("tripId", t.id).put("offerKey", n(t.offer?.id)).put("pkg", t.pkg)
            .put("acceptedAtMs", t.acceptedAt).put("pickedUpAtMs", n(t.pickups.mapNotNull { it.pickedAt }.maxOrNull()))
            .put("deliveredAtMs", n(if (delivered) t.completedAt else null))
            .put("totalTaskMin", n(if (delivered) round((t.completedAt!! - t.acceptedAt) / 60000.0, 4) else null))
            .put("shownM", n(m)).put("shownMSource", mSource).put("readyInMin", n(t.offer?.data?.readyIn)).put("legs", legs)
            .put("stackedWith", n(stacked?.id)).put("outcome", if (delivered) "delivered" else "unknown")
            // K34 inferred delivery: tripLegs v1 has no outcome for it, so "unknown" (no delivery time) and never calibrated on;
            // orders.jsonl (`delivered_inferred`, record `delivered_inferred_at`) tells it apart by tripId.
            .put("excludeFromCalibration", manual || t.deliveredInferredAt != null)
    }

    // ------------------------------------------------------------------ penalties

    /** `penaltyObservation` v1 with every optional field present (null when not observed). */
    fun penaltyRow(platform: String, kind: String, at: Long, offlineUntilMs: Long? = null, countInWindow: Int? = null,
                   reason: String? = null, declinedCountShown: Int? = null): JSONObject = JSONObject()
        .put("schemaVersion", 1).put("platform", platform).put("kind", kind).put("observedAtMs", maxOf(0L, at))
        .put("offlineUntilMs", n(offlineUntilMs)).put("countInWindowAtTrigger", n(countInWindow?.coerceAtLeast(0)))
        .put("maxCountWithoutTriggerSoFar", JSONObject.NULL).put("acceptanceRate", JSONObject.NULL)
        .put("rawReasonRedacted", n(reason?.take(200))).put("declinedCountShown", n(declinedCountShown?.coerceAtLeast(0)))

    /** Performance page values (WP5 `stats*` fields) of one screen visit. */
    data class Performance(val pkg: String, val at: Long, val acceptanceRate: Double?, val completionRate: Double?, val tasksDone: Int?,
                           val earningsAzn: Double?, val rating: Double?, val periodLabel: String?)
    fun performanceRow(p: Performance): JSONObject? {
        val platform = Platforms.of(p.pkg) ?: return null
        return JSONObject().put("schemaVersion", 1).put("platform", platform).put("kind", "performance_snapshot").put("observedAtMs", maxOf(0L, p.at))
            .put("acceptanceRate", n(p.acceptanceRate)).put("completionRate", n(p.completionRate)).put("tasksDone", n(p.tasksDone?.coerceAtLeast(0)))
            .put("earningsAzn", n(p.earningsAzn)).put("rating", n(p.rating)).put("periodLabel", n(p.periodLabel?.take(40)))
    }

    private val quickLink = Regex("^[^\\p{L}\\p{N}]*scheduled offline[^\\p{L}\\p{N}]*$")
    private val scheduledState = Regex("(?<![\\p{L}\\p{N}_])(?:scheduled|planned) (?:offline|break)(?![\\p{L}\\p{N}_])|planlasdirilmis (?:oflayn|offline|fasile)|planli (?:cevrimdisi|mola)")
    private val stateContext = Regex("(?<![\\p{L}\\p{N}_])(?:until|you|you're|youre|are|qeder|kadar)(?![\\p{L}\\p{N}_])|[0-9]{1,2}:[0-9]{2}")
    /**
     * A scheduled-offline STATE on an offline screen ("You're on scheduled offline until 18:00"), not Wolt's "Scheduled offline"
     * quick link that is on every home frame (DESIGN wording, not yet seen on the phone).
     */
    fun scheduledOffline(o: CourierObservation): Boolean = o.stage == ScreenStage.OFFLINE && o.complete && o.raw.any { line ->
        val n = CourierParser.norm(line)
        !quickLink.matches(n) && scheduledState.containsMatchIn(n) && stateContext.containsMatchIn(n)
    }
}

/** Change points of a value over time (zone, boost), oldest dropped after [keepMs]. */
class Timeline<T>(private val keepMs: Long = 12 * 3_600_000L) {
    private val at = ArrayList<Long>()
    private val values = ArrayList<T>()
    fun record(t: Long, v: T) {
        var i = at.size
        while (i > 0 && at[i - 1] > t) i--
        if (i > 0 && values[i - 1] == v) return
        at.add(i, t); values.add(i, v)
        while (at.size > 2 && at[1] < at[at.size - 1] - keepMs) { at.removeAt(0); values.removeAt(0) }
    }
    fun at(t: Long): T? { var i = at.size - 1; while (i >= 0 && at[i] > t) i--; return if (i >= 0) values[i] else null }
    /** Values in force at any moment of [s]..[e]. */
    fun during(s: Long, e: Long): List<T> = listOfNotNull(at(s)) + at.indices.filter { at[it] in (s + 1)..e }.map { values[it] }
}

/** Cumulative GPS metres over time in 5 s buckets, for distance ridden in an interval. */
class Odometer(private val keepMs: Long = 12 * 3_600_000L) {
    private var times = LongArray(256); private var totals = DoubleArray(256); private var size = 0
    private var base = 0.0
    var total = 0.0
        private set
    fun add(t: Long, m: Double) {
        if (!(m > 0) || !m.isFinite()) return
        total += m
        if (size > 0 && t >= times[size - 1] && t - times[size - 1] < 5_000L) { totals[size - 1] = total; return }
        if (size == times.size) { times = times.copyOf(size * 2); totals = totals.copyOf(size * 2) }
        times[size] = maxOf(t, if (size > 0) times[size - 1] else t); totals[size] = total; size++
        var drop = 0
        while (drop < size - 1 && times[drop] < times[size - 1] - keepMs) drop++
        if (drop > 0) { base = totals[drop - 1]; times.copyInto(times, 0, drop, size); totals.copyInto(totals, 0, drop, size); size -= drop }
    }
    /** Metres ridden up to [t]. */
    fun at(t: Long): Double { var i = size - 1; while (i >= 0 && times[i] > t) i--; return if (i >= 0) totals[i] else base }
    /** Kilometres between [s] and [e]; null before any GPS distance was recorded. */
    fun km(s: Long, e: Long): Double? = if (size == 0 && total == 0.0) null else EconomicsLogs.round(maxOf(0.0, at(e) - at(s)) / 1000.0)
}

/**
 * Stateful writer of the 2.1.0 economics logs around [DeliveryCore], [WaitObservation] and [OnlineSessions]
 * (OrderTracker feeds it every frame, click, GPS step and shift stop). Pure Kotlin, JVM-tested.
 */
class EconomicsRecorder {
    data class Row(val file: String, val json: JSONObject, val day: String? = null)

    /** Shadow-mode verdict of an offer for its row (SPEC §19, WP8); null until an evaluator is set. Never shown. */
    var verdictShadow: (OfferState) -> JSONObject? = { null }
    /** Pending offers last seen before this instant were restored from the previous process (SPEC §17.2 `recording_stopped`). */
    var restartedAtMs: Long = Long.MIN_VALUE
        private set
    private val zones = Timeline<String>()
    private val boost = HashMap<String, Timeline<Boolean>>()
    val odometer = Odometer()
    /** Per package, ordered (time, source or null = not available from then on). */
    private val marks = HashMap<String, ArrayList<Pair<Long, String?>>>()
    private val markedSessions = HashSet<String>()
    private val duringTrip = LinkedHashMap<String, String?>()
    private val legSamples = HashMap<String, HashMap<String, Double>>()
    private val legsWritten = LinkedHashSet<String>()
    /** When trips closed without a completion time ended (end_unconfirmed), for `stackedWith`. */
    private val tripEnds = LinkedHashMap<String, Long>()
    private fun endOf(t: TripRecord): Long = t.completedAt ?: tripEnds[t.id] ?: if (t.active) Long.MAX_VALUE else (t.latest?.at ?: t.acceptedAt)

    // ---- availability (waits v2 availableSince)
    private fun mark(pkg: String, at: Long, source: String?) {
        val l = marks.getOrPut(pkg) { ArrayList() }
        var i = l.size
        while (i > 0 && l[i - 1].first > at) i--
        l.add(i, at to source)
        while (l.size > 64) l.removeAt(0)
    }
    /**
     * The availability event before a spell that began at [startAt]. The core confirms a delivery or a disappearance on the
     * second stable waiting frame, so an event up to [CONFIRM_LAG_MS] after the first waiting frame also explains the spell.
     */
    fun since(pkg: String, startAt: Long): EconomicsLogs.Since {
        val l = marks[pkg].orEmpty()
        val prior = l.lastOrNull { it.first <= startAt }
        val ps = prior?.second
        if (prior != null && ps != null) return EconomicsLogs.Since(prior.first, ps)
        val lagged = l.firstOrNull { it.first > startAt && it.first - startAt <= CONFIRM_LAG_MS }?.second
        return EconomicsLogs.Since(startAt, lagged ?: "first_idle_frame")
    }
    fun duringTripId(offerId: String): String? = duringTrip[offerId]

    fun distance(at: Long, m: Double) { odometer.add(at, m) }
    fun zoneAt(at: Long): String = zones.at(at) ?: OfferGeo.UNKNOWN

    /** One parsed frame after `DeliveryCore.observe` ([before]: pending offers before it) and `OnlineSessions.observe`. */
    fun frame(o: CourierObservation, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore,
              online: List<OnlineSessions.Session>, zone: String, feeBasis: (String) -> String): List<Row> {
        zones.record(o.at, zone)
        if (o.stage == ScreenStage.IDLE && o.complete && o.source != "notification") boost.getOrPut(o.pkg) { Timeline() }.record(o.at, o.bonusPct != null)
        for (s in online) if (s.startObserved && markedSessions.add(s.id)) mark(s.pkg, s.startAt, "online_start")
        if (o.stage == ScreenStage.OFFLINE && o.complete) mark(o.pkg, o.at, null)
        val ban = o.stage == ScreenStage.OFFLINE && DeliveryCore.offlineForDeclines(o.raw)
        return events(o.at, events, before, core, online, feeBasis, o, ban) + penalties.frame(o)
    }

    /**
     * Core events of a frame, click, manual correction or shift stop: offer rows on resolution, legs rows when a trip ends,
     * offline-for-declines rows, then the courier-level idle spell.
     */
    fun events(at: Long, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore, online: List<OnlineSessions.Session>,
               feeBasis: (String) -> String, o: CourierObservation? = null, offlineBan: Boolean = false, idleUpdate: Boolean = true): List<Row> {
        val out = ArrayList<Row>()
        val ended = ArrayList<Pair<TripRecord, String?>>()
        for (e in events) when (e.type) {
            "offer" -> {
                e.offerId?.let { id -> duringTrip[id] = core.trips.values.lastOrNull { it.active }?.id; while (duringTrip.size > 200) duringTrip.remove(duringTrip.keys.first()) }
                idle.offer(e.pkg); mark(e.pkg, e.at, null)
            }
            "accepted" -> {
                mark(e.pkg, e.at, null)
                val trip = e.tripId?.let { core.trips[it] }
                val offer = e.offerId?.let { before[it] ?: trip?.offer?.takeIf { x -> x.id == it } }
                if (offer != null) offerRow(offer, e, core, feeBasis)?.let(out::add)
            }
            "rejected", "offer_unconfirmed" -> {
                val offer = e.offerId?.let { before[it] } ?: continue
                val row = offerRow(offer, e, core, feeBasis) ?: continue
                out.add(row)
                when (row.json.optString("decisionKind")) {
                    "declined_tap_observed", "declined_banner_observed" -> mark(e.pkg, e.at, "decline_observed")
                    "timeout_inferred" -> mark(e.pkg, e.at, "timeout_inferred")
                    else -> mark(e.pkg, e.at, null)
                }
            }
            "delivered" -> {
                mark(e.pkg, e.at, "delivered_event")
                // A later manual correction of a trip that already ended (or ended before this process) adds no second row;
                // nor does the real delivery signal of a K34 inferred delivery: its row was written at the close.
                e.tripId?.let { core.trips[it] }?.takeIf { e.reason != "courier_manual_correction" && it.deliveredInferredAt == null || it.id in legSamples }
                    ?.let { ended.add(it to e.reason) }
            }
            // K34 `delivered_inferred`: the handover was not observed, so no availability source either.
            "end_unconfirmed", "delivered_inferred" -> {
                mark(e.pkg, e.at, null)
                e.tripId?.let { id -> tripEnds[id] = e.at; while (tripEnds.size > 500) tripEnds.remove(tripEnds.keys.first()) }
                e.tripId?.let { core.trips[it] }?.let { ended.add(it to e.reason) }
            }
            "penalty_offline" -> {
                mark(e.pkg, e.at, null)
                penalties.offlineForDeclines(e, o, core.penaltyCounts[e.pkg]?.countsAsDecline)?.let(out::add)
            }
        }
        sampleLegs(core.trips.values.filter { it.active } + ended.map { it.first }, at)
        for ((t, why) in ended) {
            if (!legsWritten.add(t.id)) continue
            while (legsWritten.size > 500) legsWritten.remove(legsWritten.first())
            out.add(Row(EconomicsLogs.LEGS, EconomicsLogs.tripLegs(t, legSamples.remove(t.id).orEmpty(), core.trips.values, why) { endOf(it) }))
        }
        legSamples.keys.retainAll(core.trips.keys)
        if (idleUpdate) {
            val reason = when {
                events.any { it.type == "accepted" } -> "offer_accepted"
                events.any { it.type == "penalty_offline" } || offlineBan -> "penalty_offline"
                online.isEmpty() -> "all_offline"
                else -> "gap"
            }
            idle.update(at, online.map { it.pkg }.toSet(), core.trips.values.any { it.active }, reason)?.let { out.add(idleRow(it)) }
        }
        return out
    }

    /** Like a wait spell, an idle spell is filed under the Baku day it started (VM5), also when it ends after midnight. */
    private fun idleRow(j: JSONObject) = Row(EconomicsLogs.IDLE, j, WaitObservation.bakuDay(j.getLong("startAtMs")))

    private fun offerRow(o: OfferState, e: CoreEvent, core: DeliveryCore, feeBasis: (String) -> String): Row? {
        val during = if (duringTrip.containsKey(o.id)) duringTrip.remove(o.id)
            else core.trips.values.lastOrNull { it.active && it.acceptedAt <= o.firstAt && it.offer?.id != o.id }?.id
        return EconomicsLogs.offerRecord(o, e, feeBasis(o.pkg), during, restartedAtMs, verdictShadow(o))?.let { Row(EconomicsLogs.OFFERS, it) }
    }

    /** A boundary is sampled when it is new; one passed before this process (a restored trip) stays unknown. */
    private fun sampleLegs(trips: Collection<TripRecord>, at: Long) {
        for (t in trips) {
            val s = legSamples.getOrPut(t.id) { HashMap() }
            for ((k, bt) in EconomicsLogs.legBoundaries(t)) if (k !in s) {
                if (k == "accepted") s[k] = 0.0 else if (bt >= at - SAMPLE_LAG_MS) s[k] = t.distanceM
            }
        }
    }

    /** Closed wait spells → waits v2 rows in the Baku day file of the spell's start (VM5). */
    fun waits(episodes: List<WaitObservation.Episode>): List<Row> = episodes.flatMap { e ->
        EconomicsLogs.waitRows(e, since(e.pkg, e.startAt), { zoneAt(it) }, { s, end -> boostDuring(e.pkg, s, end) }, { s, end -> odometer.km(s, end) })
            .map { Row(EconomicsLogs.WAITS, it, e.fileDay) }
    }
    private fun boostDuring(pkg: String, s: Long, e: Long): Boolean? {
        val v = boost[pkg]?.during(s, e).orEmpty()
        return if (v.isEmpty()) null else v.any { it }
    }

    /** Manual shift stop: the idle spell ends as `shift_end`, performance visits are written, then the stop's core events. */
    fun stop(at: Long, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore, feeBasis: (String) -> String): List<Row> {
        val out = ArrayList<Row>()
        idle.close(at, "shift_end")?.let { out.add(idleRow(it)) }
        out.addAll(penalties.flush())
        out.addAll(events(at, events, before, core, emptyList(), feeBasis, idleUpdate = false))
        marks.clear(); markedSessions.clear()
        return out
    }

    /**
     * Process restart: [kept] pending offers were restored into the core, [dropped] ones were not (other day, shift off).
     * Dropped offers get their row now (`recording_stopped`); kept ones end that way unless seen again.
     */
    fun restart(at: Long, kept: Collection<OfferState>, dropped: Collection<OfferState>, feeBasis: (String) -> String): List<Row> {
        (kept + dropped).maxOfOrNull { it.lastSeenAt }?.let { restartedAtMs = maxOf(restartedAtMs, it + 1) }
        return dropped.mapNotNull { o -> EconomicsLogs.offerRecord(o, CoreEvent("offer_unconfirmed", o.pkg, at, o.id, reason = "restart"),
            feeBasis(o.pkg), null, restartedAtMs, verdictShadow(o))?.let { Row(EconomicsLogs.OFFERS, it) } }
    }

    // ---- courier state for the v2 feed
    fun courierState(now: Long, core: DeliveryCore, appsOnline: Collection<String>, hereZone: String, speedKmh: Double?): JSONObject {
        val wolt = CourierParser.WOLT
        val declines = wolt.sumOf { core.recentDeclines(it, now).countsAsDecline }
        val off = penalties.offline.filterKeys { it in wolt }.values.filter { it.first != null && it.first!! > now }.maxByOrNull { it.first!! }
        return EconomicsLogs.courierState(appsOnline, core.trips.values, declines, DeliveryCore.DECLINE_WINDOW_MS / 60_000.0,
            off?.first, off?.second, penalties.triggerCounts, penalties.boltAcceptance, hereZone, speedKmh, idle.repositioning(now))
    }

    // ---- idle spells (economics-idle.jsonl)
    private val idle = IdleSpells()
    fun idleKey(): String = idle.key()
    fun encodeIdle(): String? = idle.encode()
    /** An idle spell persisted before a process death becomes a censored `gap` row ending at its last update. */
    fun restoreIdle(raw: String?): List<Row> = listOfNotNull(idle.restoredRow(raw)?.let { idleRow(it) })

    private inner class IdleSpells {
        inner class Spell(val id: String, val start: Long, var last: Long, val zoneStart: String, val odoStart: Double) {
            val onlineSince = LinkedHashMap<String, Long>()
            val onlineMs = LinkedHashMap<String, Long>()
            val offers = LinkedHashMap<String, Int>()
        }
        var open: Spell? = null
        fun offer(pkg: String) { open?.let { it.offers[pkg] = (it.offers[pkg] ?: 0) + 1 } }
        fun key(): String = open?.let { "${it.id}:${it.onlineSince.keys}:${it.offers}" } ?: ""
        fun repositioning(now: Long): Boolean = open?.let { (odometer.at(now) - it.odoStart) / 1000.0 >= EconomicsLogs.REPOSITION_KM } == true
        /** Free = no active trip on any platform and at least one app online. */
        fun update(at: Long, online: Set<String>, busy: Boolean, reason: String): JSONObject? {
            var row: JSONObject? = null
            var v = open
            if (v != null && at - v.last > GAP_MS) { row = close(v.last, "gap"); v = null }
            if (v != null) {
                val t = maxOf(at, v.last)
                for (pkg in v.onlineSince.keys.toList()) if (pkg !in online) v.onlineMs[pkg] = (v.onlineMs[pkg] ?: 0L) + maxOf(0L, t - v.onlineSince.remove(pkg)!!)
                for (pkg in online) if (pkg !in v.onlineSince) { v.onlineSince[pkg] = t; v.onlineMs.putIfAbsent(pkg, 0L) }
                v.last = t
                if (busy || online.isEmpty()) return close(t, reason) ?: row
                return row
            }
            if (!busy && online.isNotEmpty()) {
                val s = Spell("idle-$at", at, at, zoneAt(at), odometer.at(at))
                for (pkg in online.sorted()) { s.onlineSince[pkg] = at; s.onlineMs[pkg] = 0L }
                open = s
            }
            return row
        }
        fun close(at: Long, endEvent: String): JSONObject? {
            val v = open ?: return null
            open = null
            val end = maxOf(at, v.start)
            for ((pkg, since) in v.onlineSince) v.onlineMs[pkg] = (v.onlineMs[pkg] ?: 0L) + maxOf(0L, end - since)
            val km = EconomicsLogs.round(maxOf(0.0, odometer.at(end) - v.odoStart) / 1000.0)
            return row(v.id, v.start, end, v.onlineMs, endEvent, v.offers, v.zoneStart, zoneAt(end), km)
        }
        private fun row(id: String, start: Long, end: Long, onlineMs: Map<String, Long>, endEvent: String, offers: Map<String, Int>,
                        zoneStart: String, zoneEnd: String, km: Double): JSONObject {
            val apps = JSONArray(); onlineMs.forEach { (pkg, ms) -> apps.put(JSONObject().put("pkg", pkg).put("onlineMs", maxOf(0L, ms))) }
            val seen = JSONObject(); offers.forEach { (pkg, n) -> seen.put(pkg, n) }
            return JSONObject().put("schemaVersion", 1).put("spellId", id).put("startAtMs", maxOf(0L, start)).put("endAtMs", maxOf(start, end))
                .put("appsOnline", apps).put("endEvent", endEvent).put("offersSeen", seen).put("zoneStart", zoneStart).put("zoneEnd", zoneEnd)
                .put("hourBlockStart", EconomicsLogs.hourBlock(start)).put("kmRidden", km).put("repositioning", km >= EconomicsLogs.REPOSITION_KM)
                .put("censored", endEvent != "offer_accepted")
        }
        fun encode(): String? = open?.let { v ->
            val online = JSONObject(); v.onlineSince.forEach { (k, t) -> online.put(k, t) }
            val ms = JSONObject(); v.onlineMs.forEach { (k, t) -> ms.put(k, t) }
            val offers = JSONObject(); v.offers.forEach { (k, c) -> offers.put(k, c) }
            JSONObject().put("id", v.id).put("start", v.start).put("last", v.last).put("zoneStart", v.zoneStart).put("zoneLast", zoneAt(v.last))
                .put("km", EconomicsLogs.round(maxOf(0.0, odometer.at(v.last) - v.odoStart) / 1000.0))
                .put("onlineSince", online).put("onlineMs", ms).put("offers", offers).toString()
        }
        fun restoredRow(raw: String?): JSONObject? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val j = JSONObject(raw)
                val start = j.getLong("start"); val last = maxOf(start, j.getLong("last"))
                require(start > 0)
                fun longs(o: JSONObject?): LinkedHashMap<String, Long> = LinkedHashMap<String, Long>().also { m ->
                    o?.keys()?.forEach { k -> m[k] = o.optLong(k) } }
                val ms = longs(j.optJSONObject("onlineMs"))
                longs(j.optJSONObject("onlineSince")).forEach { (k, t) -> ms[k] = (ms[k] ?: 0L) + maxOf(0L, last - t) }
                val offers = LinkedHashMap<String, Int>(); j.optJSONObject("offers")?.let { o -> o.keys().forEach { k -> offers[k] = maxOf(0, o.optInt(k)) } }
                val zone = { k: String -> j.optString(k).takeIf { it in setOf("Z1", "Z2", "Z3", "Z4") } ?: OfferGeo.UNKNOWN }
                row(j.optString("id").ifBlank { "idle-$start" }, start, last, ms, "gap", offers, zone("zoneStart"), zone("zoneLast"),
                    maxOf(0.0, j.optDouble("km", 0.0).takeIf { it.isFinite() } ?: 0.0))
            }.getOrNull()
        }
    }

    // ---- penalties (economics-penalties.jsonl)
    private val penalties = Penalties()

    private inner class Penalties {
        /** Per package: (offline until, reason) of the last offline-for-declines or scheduled-offline observation. */
        val offline = HashMap<String, Pair<Long?, String>>()
        val triggerCounts = ArrayList<Int>()
        var boltAcceptance: Double? = null
        private val scheduledShown = HashSet<String>()
        private val banner = HashMap<String, Pair<Int, Long>>()
        private val visits = HashMap<String, EconomicsLogs.Performance>()
        private val visitLast = HashMap<String, Long>()

        fun offlineForDeclines(e: CoreEvent, o: CourierObservation?, count: Int?): Row? {
            val platform = Platforms.of(e.pkg) ?: return null
            val clock = o?.offlineUntil
            val until = EconomicsLogs.clockToMs(clock, e.at)
            offline[e.pkg] = until to "declines"
            if (count != null && count >= 1 && platform == "wolt") { triggerCounts.add(count); while (triggerCounts.size > 50) triggerCounts.removeAt(0) }
            val reason = if (until != null) "set offline until $clock for declining too many deliveries" else "set offline for declining too many deliveries"
            return Row(EconomicsLogs.PENALTIES, EconomicsLogs.penaltyRow(platform, "offline_for_declines", e.at, until, count, reason))
        }

        fun frame(o: CourierObservation): List<Row> {
            val out = ArrayList<Row>()
            val platform = Platforms.of(o.pkg)
            // Performance page visit: written once, when the courier leaves it.
            val stats = o.statsEarningsAzn != null || o.statsTasksDone != null || o.statsCompletionRate != null ||
                o.statsAcceptanceRate != null || o.statsRating != null || o.statsPeriodLabel != null
            val last = visitLast[o.pkg]
            if (last != null && ((o.stage != ScreenStage.UNKNOWN && o.complete) || o.at - last > VISIT_GAP_MS)) out.addAll(endVisit(o.pkg))
            if (stats && platform != null) {
                val p = visits[o.pkg]
                visits[o.pkg] = EconomicsLogs.Performance(o.pkg, p?.at ?: o.at, o.statsAcceptanceRate ?: p?.acceptanceRate,
                    o.statsCompletionRate ?: p?.completionRate, o.statsTasksDone ?: p?.tasksDone, o.statsEarningsAzn ?: p?.earningsAzn,
                    o.statsRating ?: p?.rating, o.statsPeriodLabel ?: p?.periodLabel)
                visitLast[o.pkg] = o.at
                if (platform == "bolt") o.statsAcceptanceRate?.let { boltAcceptance = it }
            }
            if (platform == null) return out
            // Scheduled offline: once per offline period.
            if (o.stage != ScreenStage.OFFLINE && o.stage != ScreenStage.UNKNOWN) scheduledShown.remove(o.pkg)
            if (EconomicsLogs.scheduledOffline(o) && scheduledShown.add(o.pkg)) {
                val until = EconomicsLogs.clockToMs(o.offlineUntil, o.at)
                offline[o.pkg] = until to "scheduled"
                out.add(Row(EconomicsLogs.PENALTIES, EconomicsLogs.penaltyRow(platform, "scheduled_offline", o.at, until,
                    reason = if (until != null) "scheduled offline until ${o.offlineUntil}" else "scheduled offline")))
            }
            // "N deliveries declined": once per shown number (it stays on screen for many frames).
            val c = o.declinedCount
            if (c != null) {
                val seen = banner[o.pkg]
                banner[o.pkg] = c to o.at
                if (seen == null || seen.first != c || o.at - seen.second > DeliveryCore.BANNER_RESET_MS)
                    out.add(Row(EconomicsLogs.PENALTIES, EconomicsLogs.penaltyRow(platform, "declines_banner", o.at,
                        reason = "$c deliveries declined", declinedCountShown = c)))
            }
            return out
        }
        private fun endVisit(pkg: String): List<Row> {
            visitLast.remove(pkg)
            val p = visits.remove(pkg) ?: return emptyList()
            return listOfNotNull(EconomicsLogs.performanceRow(p)?.let { Row(EconomicsLogs.PENALTIES, it) })
        }
        fun flush(): List<Row> = visits.keys.toList().flatMap { endVisit(it) }.also { scheduledShown.clear(); banner.clear() }
    }

    companion object {
        /** No frame of any courier app for this long ends the idle spell as a censored `gap` (DESIGN). */
        const val GAP_MS = 30 * 60_000L
        /** A performance page left alone this long is a finished visit. */
        const val VISIT_GAP_MS = 5 * 60_000L
        /** A leg boundary older than this when first seen is not sampled (its GPS metres are not known). */
        const val SAMPLE_LAG_MS = 2 * 60_000L
        /** See [since]. */
        const val CONFIRM_LAG_MS = 5_000L
    }
}
