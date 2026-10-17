package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats DeliveryCore.kt @ 6388c04, satır satır (ORDERS.md 1–3); farklar ORDERS.md "WEEM" satırlarında.

enum class TripPhase { TO_RESTAURANT, WAITING, TO_CUSTOMER, AT_CUSTOMER, FINISHED, END_UNCONFIRMED }
data class OfferState(val id: String, val pkg: String, val firstAt: Long, var data: CourierObservation, var phoneBattery: Int? = null,
    var lat: Double? = null, var lon: Double? = null, var gpsAccuracyM: Double? = null, var gpsAgeMs: Long? = null,
    var scooterBatteryPct: Double? = null, var scooterObservedAt: Long? = null, var scooterSource: String? = null) {
    /** K27 boost of this offer ([BoostLog.atOffer]): value/unit null with a source = seen without boost; null source = older record. */
    var boostValue: Double? = null; var boostUnit: String? = null; var boostSource: String? = null; var boostSeenAt: Long? = null
    /** K28 weather at offer time from YolGo's feed; null = not observed (no YolGo, no grant, or older than 90 min). */
    var weather: OfferWeather? = null
    /** Last frame that showed this offer card. */
    var lastSeenAt: Long = firstAt
    /** Online session in which the offer was shown. */
    var sessionId: String? = null
    /** The courier tapped "Decline" while this offer was on screen. */
    var declineClickAt: Long? = null
    /** Bolt "optional" order: its decline or timeout does not count toward penalties (SPEC §9.2, §17.2). */
    var optional: Boolean = false
    /** Where each card field was read ("accessibility", "ocr"); an OCR read never replaces an accessibility value (N11, K7). */
    val fieldSource = HashMap<String, String>()
    /** D6 timing: the screenshot that first had the fee ([feeAt]), how long after the card's previous frame ([feeGapMs], null
     *  when it was the first or a late frame) and from that screenshot to the record ([feeOcrMs]); the app's notification
     *  just before the card ([notifiedAt]). Null = not observed (older records). */
    var feeAt: Long? = null; var feeGapMs: Long? = null; var feeOcrMs: Long? = null; var notifiedAt: Long? = null
    /** E15: the frame that last gave the card's "Pickup N min" ([CourierObservation.readyIn]); memory only (after a restart
     *  [Readiness.fromOffer] uses [lastSeenAt]). */
    var readyInAt: Long? = if (data.readyIn != null) firstAt else null
    private val firstSource = data.source
    fun enrich(o: CourierObservation) {
        val a = data
        val previous = lastSeenAt
        if (a.gross == null && o.gross != null && feeAt == null) { feeAt = o.at; feeGapMs = (o.at - previous).takeIf { it > 0 } }
        lastSeenAt = maxOf(lastSeenAt, o.at)
        fun <T> pick(field: String, new: T?, old: T?): T? {
            if (new == null) return old
            if (old != null && o.source == "ocr" && (fieldSource[field] ?: firstSource) != "ocr") return old
            fieldSource[field] = o.source
            return new
        }
        val readyIn = pick("readyIn", o.readyIn, a.readyIn)
        if (o.readyIn != null && readyIn == o.readyIn && fieldSource["readyIn"] == o.source) readyInAt = o.at
        data = o.copy(gross = pick("gross", o.gross, a.gross), km = pick("km", o.km, a.km), minLo = pick("minLo", o.minLo, a.minLo),
            minHi = pick("minHi", o.minHi, a.minHi), stops = pick("stops", o.stops, a.stops), quantity = pick("quantity", o.quantity, a.quantity),
            restaurant = pick("restaurant", o.restaurant, a.restaurant), restaurantAddress = pick("restaurantAddress", o.restaurantAddress, a.restaurantAddress),
            customerAddress = pick("customerAddress", o.customerAddress, a.customerAddress), readyIn = readyIn,
            orderIds = pick("orderIds", o.orderIds.ifEmpty { null }, a.orderIds.ifEmpty { null }) ?: emptyList(),
            approachMin = pick("approachMin", o.approachMin, a.approachMin), deliveryMin = pick("deliveryMin", o.deliveryMin, a.deliveryMin),
            offerCash = o.offerCash || a.offerCash, changeNeeded = o.changeNeeded ?: a.changeNeeded)
    }
    /** K37: fills only the fields still null from an older frame of this card (an OCR result that came back late). */
    fun backfill(o: CourierObservation) {
        val a = data
        if (a.gross == null && o.gross != null && feeAt == null) feeAt = o.at
        if (a.readyIn == null && o.readyIn != null) readyInAt = o.at
        fun <T> fill(field: String, new: T?, old: T?): T? {
            if (old != null || new == null) return old
            fieldSource[field] = o.source
            return new
        }
        data = a.copy(gross = fill("gross", o.gross, a.gross), km = fill("km", o.km, a.km), minLo = fill("minLo", o.minLo, a.minLo),
            minHi = fill("minHi", o.minHi, a.minHi), stops = fill("stops", o.stops, a.stops), quantity = fill("quantity", o.quantity, a.quantity),
            restaurant = fill("restaurant", o.restaurant, a.restaurant), restaurantAddress = fill("restaurantAddress", o.restaurantAddress, a.restaurantAddress),
            customerAddress = fill("customerAddress", o.customerAddress, a.customerAddress), readyIn = fill("readyIn", o.readyIn, a.readyIn),
            approachMin = fill("approachMin", o.approachMin, a.approachMin), deliveryMin = fill("deliveryMin", o.deliveryMin, a.deliveryMin),
            offerCash = a.offerCash || o.offerCash, changeNeeded = a.changeNeeded ?: o.changeNeeded)
    }
    /** The same card read again: OCR jitter in names and wrapped addresses, gross ±0.011, km ±0.15 (K7). */
    fun same(o: CourierObservation): Boolean {
        val a = data
        return OfferMatch.similar(a.restaurant, o.restaurant) &&
            (a.gross == null || o.gross == null || kotlin.math.abs(a.gross!! - o.gross!!) < 0.011) &&
            (a.km == null || o.km == null || kotlin.math.abs(a.km!! - o.km!!) < 0.151) &&
            OfferMatch.similar(a.customerAddress, o.customerAddress)
    }
}
data class PickupRecord(var restaurant: String?, var address: String?, var readyAt: Long? = null,
    var arrivedAt: Long? = null, var pickedAt: Long? = null, var arrivalSource: String? = null, var readyObservedAt: Long? = null) {
    /** Restaurant's own note (where to find it), order code and items for the restaurant card. */
    var note: String? = null; var code: String? = null; var items: List<String> = emptyList()
    /** Latest "ready in N min" seen on screen or in a notification; readyAt keeps the first one. */
    var readyHintAt: Long? = null; var preparingAt: Long? = null; var reminderAt: Long? = null
}
data class CustomerRecord(val id: String, val details: DeliveryDetails = DeliveryDetails(), var arrivedAt: Long? = null,
    var deliveredAt: Long? = null, var lat: Double? = null, var lon: Double? = null, var coordinateSource: String? = null,
    var reminderAt: Long? = null, var detailsAt: Long = 0, var deadlineClock: Int? = null) {
    /** K29: the platform's delivery deadline as a clock time (from "Drop off in N min"); null when never shown. */
    var deadlineAt: Long? = null
    /** M19: how [arrivedAt] was found when not from the platform's screen: `gps_near` or a navigation distance (`nav_*`). */
    var arrivalSource: String? = null
}
data class TripRecord(val id: String, val pkg: String, val offer: OfferState?, val acceptedAt: Long,
    var phase: TripPhase = TripPhase.TO_RESTAURANT, val pickups: MutableList<PickupRecord> = mutableListOf(),
    val customers: LinkedHashMap<String, CustomerRecord> = linkedMapOf(), var currentCustomer: String? = null,
    var expectedOrders: Int = 1, var completedAt: Long? = null, var distanceM: Double = 0.0,
    var usedWh: Double = 0.0, var recoveredWh: Double = 0.0, var energyCoveredMs: Long = 0,
    var energyShared: Boolean = false, var tip: Double = 0.0, var commissionPct: Double = 0.0,
    var priceIsNet: Boolean = false, var pickupIntentAt: Long? = null, var finishIntentAt: Long? = null,
    var latest: CourierObservation? = null, var missedOffer: Boolean = false,
    /** Customer whose delivery slider was on screen in the most recent drop-off frame. */
    var deliverSeenFor: String? = null, var deliverSeenAt: Long? = null
) {
    /** R1: Wolt's 3-character order numbers ("#072") seen on this order's screens. In memory only; they key no customer (K10). */
    val woltShortIds = linkedSetOf<String>()
    /** R1: the open orders handed over with this one on one "N dropoffs to" card (trip ids, in memory only). */
    val sameDropoff = linkedSetOf<String>()
    /** Online session in which the order was accepted. */
    var sessionId: String? = null
    /** Customer whose call button the courier tapped, and when (memory only, for the dialer number). */
    var callFor: String? = null; var callAt: Long? = null
    /** K34: closed (phase END_UNCONFIRMED) at this moment because order [deliveredInferredBy] reached the customer stage: an
     *  unverified delivery (`delivered_inferred`), never a `delivered` signal. The real handover time was not observed. */
    var deliveredInferredAt: Long? = null; var deliveredInferredBy: String? = null; var deliveredInferredReason: String? = null
    /** An inferred delivery that the courier has not confirmed by hand since. */
    val deliveredInferred get() = deliveredInferredAt != null && phase == TripPhase.END_UNCONFIRMED
    /** K33: Wolt's spinner was seen on the delivery slider of customer [deliverSpinnerFor] at this moment, after that slider.
     *  Extra evidence only (the spinner often passes too fast to be read): it never completes or closes an order by itself. */
    var deliverSpinnerAt: Long? = null; var deliverSpinnerFor: String? = null
    /** M22 (16 Oct): a handover sign on the card of customer [handoverHintFor] without the slider's label: `spinner` (K33's
     *  indicator on the slider row, the label replaced by it, as on Wolt's cash steps screen). Memory only; lets the next Wolt
     *  order's restaurant screen close this order like a seen slider does (D3(b)). */
    var handoverHintFor: String? = null; var handoverHintAt: Long? = null; var handoverHintKind: String? = null
    /** The spinner followed the slider of the customer the order is at now (cleared with that slider, kept as history). */
    val spinnerOnSlider get() = deliverSpinnerFor != null && deliverSpinnerFor == deliverSeenFor && deliverSeenFor == currentCustomer
    /** K31 payment: `cash` (source `wolt_cash_screen`, [cashAmount] from the checkbox line) or `card_inferred` (steps screen seen
     *  without a cash step). Null = not observed; [paymentSourceOrNotObserved] gives the flag for readers. */
    var paymentMethod: String? = null; var paymentSource: String? = null; var cashAmount: Double? = null; var paymentAt: Long? = null
    /** M16: "No change needed" seen (true) or a change amount (false, [cashChangeAzn]); the first moment the cash checkbox was
     *  seen ticked (accessibility only; null = not seen). */
    var cashNoChange: Boolean? = null; var cashChangeAzn: Double? = null; var cashCheckedAt: Long? = null
    val paymentSourceOrNotObserved get() = paymentSource ?: "not_observed"
    /** Who set [tip]: `user_cash_input` (K32 answer), `user_manual` (tip editor); null on older records. */
    var tipSource: String? = null
    /** D3: a platform list showed this order as delivered at [verifiedAt]: `wolt_earnings_list`, `bolt_deliveries_list` or
     *  `wolt_cash_collection`. Turns an unverified (inferred) delivery into a verified one; null = not matched (yet). */
    var verifiedBy: String? = null; var verifiedAt: Long? = null
    /** D3(d): Wolt Finances "Cash collection" row of this cash order: its clock (start of the minute, the handover time) and amount. */
    var cashCollectedAt: Long? = null; var cashCollectedAzn: Double? = null
    /** D15: Wolt earnings list "Extra waiting time" below this order's row: paid on top of the fee, part of [earnings] and [net]. */
    var extraWaitAzn: Double = 0.0
    /** D10: time the scooter's external pack was known but not read during the order: its energy is not in [usedWh]
     *  (only the internal pack's power was measured, which the external pack feeds). Coverage = [energyCoveredMs] of the order. */
    var energyPartialMs: Long = 0
    /** D15: the platform list's "Tip" below this order's row (in-app tip). Shown on the card; not added to [tip] or [net] (the
     *  courier may have typed the same tip by hand). */
    var platformTipAzn: Double? = null
    val active get() = phase != TripPhase.FINISHED && phase != TripPhase.END_UNCONFIRMED
    /** D3(c): delivered, observed or unverified (K34 inferred): counted in the day's deliveries, net and averages; an
     *  unverified one keeps its label until a platform list confirms it ([verifiedBy]). */
    val countsAsDelivered get() = completedAt != null || deliveredInferred
    /** On the way to or at a customer ("Müşteriye gidiliyor" / "Müşteriye varıldı"). */
    val atCustomer get() = active && (phase == TripPhase.TO_CUSTOMER || phase == TripPhase.AT_CUSTOMER)
    val actualMinutes get() = completedAt?.let { (it - acceptedAt) / 60000.0 }
    val gross get() = offer?.data?.gross
    /** D15: what the platform paid for the order: the offer fee plus the extra waiting fee of its list row. */
    val earnings get() = gross?.let { it + extraWaitAzn }
    /** The offer price is always gross (Wolt "Gross earnings", Bolt "All Deliveries" show the same amounts).
     *  priceIsNet is kept only for old records and no longer skips the commission. */
    val net get() = earnings?.let { it * (1.0 - commissionPct / 100.0) + tip }
    val waitMinutes get() = pickups.sumOf { p -> if (p.arrivedAt != null && p.pickedAt != null) (p.pickedAt!! - p.arrivedAt!!).coerceAtLeast(0) / 60000.0 else 0.0 }
    /** After the food was ready: the platform's "Ready in N min" time, else the moment it was seen ready. */
    val lateMinutes get() = pickups.sumOf { p -> val ready = p.readyAt ?: p.readyObservedAt
        if (ready != null && p.pickedAt != null && p.arrivedAt != null) (p.pickedAt!! - maxOf(ready, p.arrivedAt!!)).coerceAtLeast(0) / 60000.0 else 0.0 }
}
data class CoreEvent(val type: String, val pkg: String, val at: Long, val offerId: String? = null, val tripId: String? = null, val reason: String? = null)
/** How a presented offer ended ([type]/[reason] of its core event), kept for the decline window. */
data class OfferOutcome(val offerId: String, val pkg: String, val at: Long, val type: String, val reason: String?, val optional: Boolean = false) {
    val countsAsDecline get() = DeliveryCore.countsAsDecline(type, reason, optional)
    val explicitDecline get() = DeliveryCore.explicitDecline(type, reason, optional)
}
/** Declines of one platform in the window before [at]: SPEC §17.2 `countsAsDecline` and, separately, taps plus banners only. */
data class DeclineWindow(val at: Long, val countsAsDecline: Int, val explicit: Int, val windowMs: Long)

/** Pure state machine: independent platform queues and explicit, observable transitions. */
class DeliveryCore {
    var running = false
        private set
    val pending = linkedMapOf<String, OfferState>()
    val trips = linkedMapOf<String, TripRecord>()
    private val idleSince = HashMap<String, Long>()
    /** M22 (16 Oct): first frame of Wolt's waiting map since the platform's last task frame (offer, restaurant, customer). Unlike
     *  [idleSince] a frame without evidence (UNKNOWN: map labels only, the notification shade, a half-read tree) does not clear it,
     *  so the waiting map closes the platform's open orders even when such reads come between its frames. */
    private val waitingSince = HashMap<String, Long>()
    private val declineAt = HashMap<String, Long>()
    private val declineDialogAt = HashMap<String, Long>()
    private val bannerCount = HashMap<String, Pair<Int, Long>>()
    private val lastMeaningfulAt = HashMap<String, Long>()
    /** K34: when the customer card of an order closed as an inferred delivery was last seen after the close (split screen,
     *  the courier back on it to swipe), by trip id. Memory only: after a restart its last own frame ([TripRecord.latest]) counts. */
    private val lateSeen = HashMap<String, Long>()
    private var sequence = 0L
    /** Resolved offers of the last hours, for the decline window; kept across shift stop/start (platform counters are). */
    private val outcomes = ArrayList<OfferOutcome>()
    val offerOutcomes: List<OfferOutcome> get() = outcomes
    /** Platforms whose offline-for-declines screen was already reported for the current offline period. */
    private val penaltyShown = HashSet<String>()
    private val penalties = HashMap<String, DeclineWindow>()
    /** Both decline counts at the moment of the last `penalty_offline` event per platform (for the penalties log). */
    val penaltyCounts: Map<String, DeclineWindow> get() = penalties
    fun start() { running = true; idleSince.clear(); waitingSince.clear(); declineAt.clear(); declineDialogAt.clear(); bannerCount.clear(); lastMeaningfulAt.clear(); penaltyShown.clear() }
    fun stop(at: Long): List<CoreEvent> {
        running = false; idleSince.clear(); waitingSince.clear(); declineAt.clear()
        val before = pending.values.associateBy { it.id }
        val out = pending.values.map { CoreEvent("offer_unconfirmed", it.pkg, at, it.id, reason = "manual_shift_stop") }.toMutableList()
        pending.clear()
        trips.values.filter { it.active }.forEach { it.phase = TripPhase.END_UNCONFIRMED; out.add(CoreEvent("end_unconfirmed", it.pkg, at, tripId = it.id, reason = "manual_shift_stop")) }
        remember(out, before)
        return out
    }
    /** Re-seeds the decline window, e.g. from the offers log after a process restart. */
    fun restoreOutcome(r: OfferOutcome) { if (outcomes.none { it.offerId == r.offerId }) { outcomes.add(r); outcomes.sortBy { it.at } } }
    /** Offers of [pkg] resolved in the [DECLINE_WINDOW_MS] before [at]. */
    fun recentDeclines(pkg: String, at: Long): DeclineWindow {
        val recent = outcomes.filter { it.pkg == pkg && at - it.at in 0..DECLINE_WINDOW_MS }
        return DeclineWindow(at, recent.count { it.countsAsDecline }, recent.count { it.explicitDecline }, DECLINE_WINDOW_MS)
    }
    private fun remember(events: List<CoreEvent>, before: Map<String, OfferState>) {
        for (e in events) {
            val offer = e.offerId?.let { before[it] } ?: continue
            if (e.type !in setOf("rejected", "offer_unconfirmed", "accepted")) continue
            outcomes.add(OfferOutcome(offer.id, offer.pkg, e.at, e.type, e.reason, offer.optional))
        }
        val latest = outcomes.lastOrNull()?.at ?: return
        outcomes.removeAll { latest - it.at > 2 * DECLINE_WINDOW_MS }
    }
    /** Wolt "set offline until 21:46 for declining too many deliveries": one event per offline period, with the window count (K18). */
    private fun penalty(o: CourierObservation, out: MutableList<CoreEvent>) {
        if (o.stage != ScreenStage.OFFLINE) { if (o.stage != ScreenStage.UNKNOWN) penaltyShown.remove(o.pkg); return }
        if (!o.complete || o.pkg in penaltyShown || !offlineForDeclines(o.raw)) return
        penaltyShown.add(o.pkg)
        val w = recentDeclines(o.pkg, o.at)
        penalties[o.pkg] = w
        out.add(CoreEvent("penalty_offline", o.pkg, o.at, reason = "declines_in_window=${w.countsAsDecline}"))
    }
    fun active(pkg: String) = trips.values.lastOrNull { it.pkg == pkg && it.active }
    /** The open order of [pkg] whose task screen was seen last (the newest one on a tie): the order a tap on that screen, or
     *  the waiting map right after it, belongs to. Equal to [active] while the platform has one open order (K34). */
    fun shown(pkg: String): TripRecord? {
        var best: TripRecord? = null
        for (t in trips.values) if (t.pkg == pkg && t.active && (best == null || seenAt(t) >= seenAt(best))) best = t
        return best
    }
    private fun seenAt(t: TripRecord) = t.latest?.at ?: t.acceptedAt
    private fun lastSeen(t: TripRecord) = maxOf(seenAt(t), lateSeen[t.id] ?: Long.MIN_VALUE)
    /** The order of [pkg], open or closed, whose task screen was seen last (the newest one on a tie). */
    private fun lastShown(pkg: String): TripRecord? {
        var best: TripRecord? = null
        for (t in trips.values) if (t.pkg == pkg && (best == null || lastSeen(t) >= lastSeen(best))) best = t
        return best
    }
    /** K34: the order closed as an inferred delivery whose customer card is the platform's last task screen, if any. */
    private fun closedOrderShown(pkg: String): TripRecord? = lastShown(pkg)?.takeIf { it.deliveredInferred }
    /** Customer whose call button was tapped within [CALL_TO_DIALER_MS] and is not delivered yet. */
    fun callTarget(now: Long): Pair<TripRecord, CustomerRecord>? = trips.values
        .filter { it.active && it.callAt != null && now - it.callAt!! in 0..CALL_TO_DIALER_MS }
        .maxByOrNull { it.callAt!! }
        ?.let { t -> t.customers[t.callFor]?.takeIf { it.deliveredAt == null }?.let { t to it } }
    fun observe(o: CourierObservation): List<CoreEvent> {
        if (!running) return emptyList()
        // OCR is asynchronous. An older frame must never resurrect an accepted offer.
        if (o.at < (lastMeaningfulAt[o.pkg] ?: Long.MIN_VALUE)) { backfillLate(o); return emptyList() }
        if (o.stage != ScreenStage.UNKNOWN) lastMeaningfulAt[o.pkg] = o.at
        val out = ArrayList<CoreEvent>()
        val before = pending.values.associateBy { it.id }
        react(o, out)
        spinner(o, out)
        payment(o, out)
        remember(out, before)
        penalty(o, out)
        return out
    }
    /** K37: an offer frame older than the last frame (an OCR result returns after the next accessibility reads) changes no
     *  state, but the same card's fields it read fill the ones still missing on the pending offer, or on the offer of the trip
     *  accepted after it. Never overwrites a value ([OfferState.backfill]); never resurrects an offer. */
    private fun backfillLate(o: CourierObservation) {
        if (o.stage != ScreenStage.OFFER) return
        pending[o.pkg]?.takeIf { o.at >= it.firstAt - LATE_OFFER_FRAME_MS && it.same(o) }?.let { it.backfill(o); return }
        val t = active(o.pkg) ?: return
        val offer = t.offer ?: return
        if (o.at <= t.acceptedAt && o.at >= offer.firstAt - LATE_OFFER_FRAME_MS && offer.same(o)) offer.backfill(o)
    }
    private fun react(o: CourierObservation, out: MutableList<CoreEvent>) {
        if (o.declineDialog) declineDialogAt[o.pkg] = o.at
        o.declinedCount?.let { n -> declineBanner(o, n, out) }
        if (o.stage == ScreenStage.OFFER || o.stage == ScreenStage.PICKUP || o.stage == ScreenStage.DROPOFF) waitingSince.remove(o.pkg)
        val current = active(o.pkg)
        // K34: the card of an order already closed as an inferred delivery (a late OCR frame, the card still on screen in
        // split screen, or the courier back on it to swipe) neither opens an order, nor takes a pending offer, nor closes the
        // order at the customer now; else two cards would close each other. Its own completion signal still counts.
        if (o.stage == ScreenStage.DROPOFF) closedOrderOfCard(o, current)?.let { u -> idleSince.remove(o.pkg); expireStale(o, out); lateCard(u, o, out); return }
        if (o.delivered && current == null) return
        val ids = keyIds(o)
        if (current==null && pending[o.pkg]==null && o.stage in setOf(ScreenStage.PICKUP,ScreenStage.DROPOFF)) {
            val finished=trips.values.lastOrNull { it.pkg==o.pkg && it.completedAt!=null && o.at-it.completedAt!! in 0..30000 }
            val repeatedId=finished!=null && ids.isNotEmpty() && ids.all { it in finished.customers }
            val repeatedAddress=finished!=null && ids.isEmpty() && o.customerAddress!=null && finished.customers.values.any { it.details.address==o.customerAddress }
            if (repeatedId || repeatedAddress) return
        }
        if (o.stage == ScreenStage.OFFER) {
            idleSince.remove(o.pkg)
            val old = pending[o.pkg]
            if (old != null && old.same(o)) { old.enrich(o); if (isOptional(o)) old.optional = true }
            else {
                if (old != null) out.add(if (old.declineClickAt != null) CoreEvent("rejected", o.pkg, o.at, old.id, reason = "courier_declined")
                    else CoreEvent("offer_unconfirmed", o.pkg, o.at, old.id, reason = "replaced_without_idle"))
                val next = OfferState("offer-${o.at}-${++sequence}", o.pkg, o.at, o).also { it.optional = isOptional(o); if (o.gross != null) it.feeAt = o.at }
                pending[o.pkg] = next
                out.add(CoreEvent("offer", o.pkg, o.at, next.id))
            }
        } else if (o.stage == ScreenStage.PICKUP || o.stage == ScreenStage.DROPOFF) {
            idleSince.remove(o.pkg)
            val waiting = pending[o.pkg]
            // A frame of the delivery already in progress is not the acceptance of a newer
            // offer shown on top of it; the courier may have declined that one.
            val useOffer = waiting != null && waiting.declineClickAt == null && (current == null || !belongsTo(o, current))
            if (!useOffer) expireStale(o, out)
            val offer = if (useOffer) pending.remove(o.pkg) else null
            // With two open orders of one platform, a customer card that names a customer of the older one is that order's
            // frame, not the newest order's (K34: otherwise the newest one would be moved to the customer stage).
            var trip = if (offer == null && o.stage == ScreenStage.DROPOFF) ownerOf(o, current) else current
            // D3(b): past its delivery slider, an order does not get a second restaurant: the platform moved on to a new order
            // whose offer card was missed (it opens below, and the order before it is closed by [nextAtRestaurant]).
            if (o.stage == ScreenStage.PICKUP && offer == null && trip != null && trip.atCustomer && pastSlider(trip) && otherRestaurant(o, trip)) trip = null
            if (offer != null || trip == null) {
                // Without a restaurant, customer or order code a frame cannot open a delivery.
                if (!substantive(o)) { if (offer != null) pending[o.pkg] = offer; return }
                bannerCount.remove(o.pkg)
                trip = TripRecord("trip-${o.at}-${++sequence}", o.pkg, offer, o.at, missedOffer = offer == null)
                trips[trip.id] = trip
                out.add(CoreEvent("accepted", o.pkg, o.at, offer?.id, trip.id, if (offer == null) "active_screen_offer_missed" else "active_screen"))
                // D25: Wolt's offer card said "Cash order" (11 Oct King Doner, Bonbon): the order is cash from its acceptance;
                // the steps screen later only refreshes the amount.
                if (offer?.data?.offerCash == true && trip.paymentMethod == null) {
                    trip.paymentMethod = "cash"; trip.paymentSource = "wolt_offer_card"; trip.paymentAt = o.at
                    offer.data.changeNeeded?.let { trip.cashChangeAzn = it; trip.cashNoChange = it == 0.0 }
                    out.add(CoreEvent("payment_seen", trip.pkg, o.at, tripId = trip.id, reason = "cash_offer_card"))
                }
            }
            trip.latest = o
            trip.woltShortIds += shortIds(o)
            trip.expectedOrders = maxOf(trip.expectedOrders, o.quantity ?: 1, ids.size)
            if (o.stage == ScreenStage.PICKUP) {
                var p = trip.pickups.lastOrNull()
                if (p == null || p.pickedAt != null && o.restaurant != null && p.restaurant != o.restaurant) {
                    p = PickupRecord(o.restaurant ?: offer?.data?.restaurant, o.restaurantAddress ?: offer?.data?.restaurantAddress)
                    trip.pickups.add(p)
                    trip.phase = TripPhase.TO_RESTAURANT
                    // E15: the offer card's "Pickup N min" starts the first restaurant's ready counter (this frame's own label corrects it).
                    if (trip.pickups.size == 1) Readiness.fromOffer(trip.offer)?.let { p.readyHintAt = it }
                }
                o.restaurant?.let { p.restaurant = it }; o.restaurantAddress?.let { p.address = it }
                nextAtRestaurant(trip, o.at, out)
                // Bolt's "Arrive in N min" is the courier's arrival target, not the food's ready time.
                o.readyIn?.let { if (p.readyAt == null) p.readyAt = o.at + it * 60000L; p.readyHintAt = o.at + it * 60000L }
                if (o.ready && p.readyObservedAt == null) p.readyObservedAt = o.at
                if (o.preparing && p.preparingAt == null) p.preparingAt = o.at
                o.restaurantNote?.let { p.note = it }; o.pickupCode?.let { p.code = it }; if (o.items.isNotEmpty()) p.items = o.items
                if (o.arrived && p.arrivedAt == null) {
                    p.arrivedAt = o.at; p.arrivalSource = "platform_status"; trip.phase = TripPhase.WAITING
                    out.add(CoreEvent("restaurant_arrived", o.pkg, o.at, tripId = trip.id))
                }
                ids.forEach { id -> trip.customers.getOrPut(id) { CustomerRecord(id) } }
            } else {
                trip.pickups.lastOrNull()?.takeIf { it.pickedAt == null }?.let { p ->
                    p.pickedAt = o.at; out.add(CoreEvent("picked_up", o.pkg, o.at, tripId = trip.id))
                }
                if (trip.phase == TripPhase.TO_RESTAURANT || trip.phase == TripPhase.WAITING) trip.phase = TripPhase.TO_CUSTOMER
                // Without an order ID, an OCR variant of a name already seen ("Gulnar" / "Gulnar Niftaliyevc")
                // is the same customer, not a new one.
                val id = ids.firstOrNull() ?: o.customer.name?.let { n -> CustomerMatch.find(trip.customers.values, n, o.customer.address)?.id ?: "name:${CourierParser.norm(n)}" }
                    ?: trip.currentCustomer ?: "customer-1"
                // Replace a placeholder with the platform ID without losing captured details.
                if (id != "customer-1" && trip.customers.size == 1 && trip.customers.containsKey("customer-1")) {
                    val previous = trip.customers.remove("customer-1")!!
                    trip.customers[id] = previous.copy(id = id)
                }
                val customer = trip.customers.getOrPut(id) { CustomerRecord(id) }
                val previous = trip.currentCustomer?.takeIf { it != id }?.let { trip.customers[it] }
                if (previous != null && previous.deliveredAt == null) {
                    // A swipe produces no click event; the slider followed by the next
                    // customer's card is what the courier sees after a delivery.
                    val why = when {
                        trip.finishIntentAt != null && o.at - trip.finishIntentAt!! < 30000 -> "next_customer_after_confirm"
                        trip.deliverSeenFor == previous.id -> "next_customer_after_delivery_slider"
                        else -> null
                    }
                    if (why != null) {
                        previous.deliveredAt = o.at; trip.finishIntentAt = null
                        out.add(CoreEvent("customer_delivered", o.pkg, o.at, tripId = trip.id, reason = why))
                    }
                }
                if (trip.currentCustomer != id) trip.phase = TripPhase.TO_CUSTOMER
                trip.currentCustomer = id
                customer.details.merge(o.customer); customer.detailsAt = o.at
                customer.deadlineClock = o.dropoffClock
                o.dropoffClock?.let { customer.deadlineAt = DeliveryDeadline.merge(customer.deadlineAt, DeliveryDeadline.fromClock(o.at, it)) }
                if (o.deliverControl) { trip.deliverSeenFor = id; trip.deliverSeenAt = o.at }
                else if (trip.deliverSeenFor != id) { trip.deliverSeenFor = null; trip.deliverSeenAt = null }
                if (o.deliverSpinner && o.pkg in CourierParser.WOLT) { trip.handoverHintFor = id; trip.handoverHintAt = o.at; trip.handoverHintKind = "spinner" }
                // M16: Wolt delivers nothing before the cash checkbox is ticked: the tick is a handover sign too.
                else if (o.cashChecked == true && o.pkg in CourierParser.WOLT) { trip.handoverHintFor = id; trip.handoverHintAt = o.at; trip.handoverHintKind = "cash_checked" }
                if (o.arrived && customer.arrivedAt == null) { customer.arrivedAt = o.at; trip.phase = TripPhase.AT_CUSTOMER }
                sameCard(trip, o, id, out)
                if (o.delivered) completeCustomerOrTrip(trip, o.at, out, "platform_completed")
                if (trip.atCustomer && !mayBeClosedCard(trip)) oneAtCustomer(trip, o.at, out)
            }
        } else if (o.complete && (o.stage == ScreenStage.IDLE || o.stage == ScreenStage.OFFLINE)) {
            val since = idleSince.getOrPut(o.pkg) { o.at }
            if (o.stage == ScreenStage.IDLE) waitingSince.getOrPut(o.pkg) { o.at }
            // Two stable reads prevent transient panel closure/loading from being counted as a rejection.
            if (o.at - since >= 250L) {
                pending.remove(o.pkg)?.let { out.add(offerLeft(it, since, o.at)) }
                // K34: the platform's last task screen was the card of an order closed as an inferred delivery; its own deliver
                // tap or slider followed by the waiting screen is that order's real delivery (as it was before the close).
                val closed = closedOrderShown(o.pkg)
                closed?.let { u ->
                    if (o.stage == ScreenStage.IDLE && u.finishIntentAt != null && o.at - u.finishIntentAt!! < 30000) finishInferred(u, o.at, out, "confirmed_delivery_to_idle")
                    else if (o.stage == ScreenStage.IDLE && u.deliverSeenFor != null && u.deliverSeenFor == u.currentCustomer &&
                        o.at - (u.deliverSeenAt ?: Long.MIN_VALUE) in 0..SLIDER_TO_WAITING_MS && o.at - since >= 1000L) finishInferred(u, o.at, out, "delivery_slider_then_waiting")
                }
                // The order whose card was on screen last, not simply the newest one (K34: a slider then the waiting map). When
                // that card was a closed order's, neither the tap nor the slider of an open order was the last thing seen.
                shown(o.pkg)?.let { trip ->
                    val sliderThenWaiting = closed == null && o.stage == ScreenStage.IDLE && trip.latest?.stage == ScreenStage.DROPOFF &&
                        trip.deliverSeenFor == trip.currentCustomer && o.at - (trip.deliverSeenAt ?: Long.MIN_VALUE) in 0..SLIDER_TO_WAITING_MS && o.at - since >= 1000L
                    if (closed == null && o.stage == ScreenStage.IDLE && trip.finishIntentAt != null && o.at - trip.finishIntentAt!! < 30000) completeCustomerOrTrip(trip, o.at, out, "confirmed_delivery_to_idle")
                    else if (sliderThenWaiting) completeOnWaiting(trip, o.at, out)
                    else if (o.stage == ScreenStage.OFFLINE) { trip.phase = TripPhase.END_UNCONFIRMED; out.add(CoreEvent("end_unconfirmed", o.pkg, o.at, tripId = trip.id, reason = "offline_without_completion")) }
                }
            }
            // Courier's rule: while Wolt shows its waiting map there is no Wolt task left. M22: timed from the first waiting frame
            // after the last task frame, so reads without evidence between its frames (they reset [idleSince]) do not keep it open.
            if (o.stage == ScreenStage.IDLE && o.pkg in CourierParser.WOLT && o.at - (waitingSince[o.pkg] ?: since) >= WAITING_MAP_CLOSES_MS)
                trips.values.filter { it.pkg == o.pkg && it.active }.forEach { t ->
                    // Its slider was the last thing seen of it (the stable-frame rule above may not have run between such reads).
                    if (pastSlider(t) && o.at - (t.deliverSeenAt ?: Long.MIN_VALUE) in 0..SLIDER_TO_WAITING_MS) completeOnWaiting(t, o.at, out)
                    if (!t.active) return@forEach
                    if (t.customers.values.any { it.deliveredAt != null }) finish(t, o.at, out, "waiting_map_after_delivery")
                    // M22: picked up and at the customer stage, then no task left: handed over without an observed signal, like K34.
                    else if (t.atCustomer) inferDelivered(t, o.at, null, "waiting_map_at_customer", out)
                    else {
                        t.phase = TripPhase.END_UNCONFIRMED
                        out.add(CoreEvent("end_unconfirmed", o.pkg, o.at, tripId = t.id, reason = if (t.pickups.none { it.pickedAt != null }) "waiting_map_before_pickup" else "waiting_map_without_delivery"))
                    }
                }
        } else idleSince.remove(o.pkg)
        if (o.stage == ScreenStage.IDLE || o.stage == ScreenStage.OFFLINE || o.stage == ScreenStage.UNKNOWN) expireStale(o, out)
    }
    /** The offer left the screen for a stable waiting/offline frame first seen at [goneAt] (K17): a tap is a decline;
     *  without one, a card that stayed until the documented window (− 2 s) timed out; otherwise today's rejection. */
    private fun offerLeft(p: OfferState, goneAt: Long, at: Long): CoreEvent {
        if (p.declineClickAt != null) return CoreEvent("rejected", p.pkg, at, p.id, reason = "courier_declined")
        val window = offerWindowMs(p.pkg)
        if (window != null && goneAt >= p.firstAt + window - EXPIRY_MARGIN_MS) return CoreEvent("offer_unconfirmed", p.pkg, at, p.id, reason = "expired_inferred")
        return CoreEvent("rejected", p.pkg, at, p.id, reason = "offer_to_idle_map")
    }
    /** "N deliveries declined": a higher N than before means another decline. Shown over any screen. */
    private fun declineBanner(o: CourierObservation, n: Int, out: MutableList<CoreEvent>) {
        val last = bannerCount[o.pkg]?.takeIf { o.at - it.second in 0..BANNER_RESET_MS }
        bannerCount[o.pkg] = n to o.at
        if (o.stage == ScreenStage.OFFER) return
        val clicked = declineAt[o.pkg]?.let { o.at - it in 0..BANNER_RESET_MS } == true
        if (if (last == null) !clicked else n <= last.first) return
        pending.remove(o.pkg)?.let { out.add(CoreEvent("rejected", o.pkg, o.at, it.id, reason = "courier_declined_banner")) }
    }
    /** An offer that has not been on screen for minutes is gone. */
    private fun expireStale(o: CourierObservation, out: MutableList<CoreEvent>) {
        val p = pending[o.pkg] ?: return
        if (o.at - p.lastSeenAt < if (active(o.pkg) != null) OFFER_STALE_ON_TASK_MS else OFFER_STALE_MS) return
        pending.remove(o.pkg)
        out.add(if (p.declineClickAt != null) CoreEvent("rejected", o.pkg, o.at, p.id, reason = "courier_declined")
            else CoreEvent("offer_unconfirmed", o.pkg, o.at, p.id, reason = "offer_not_seen"))
    }
    private fun substantive(o: CourierObservation) = o.restaurant != null || keyIds(o).isNotEmpty() || o.customer.name != null ||
        o.customer.address != null || o.customerAddress != null || o.deliverControl || o.pickupCode != null
    /** Nothing on the frame names a different restaurant, customer or order than the trip's. */
    private fun belongsTo(o: CourierObservation, t: TripRecord): Boolean {
        val ids = keyIds(o)
        if (ids.isNotEmpty() && t.customers.isNotEmpty()) return ids.any { it in t.customers }
        o.restaurant?.let { r -> if (t.pickups.isNotEmpty()) return t.pickups.any { it.restaurant != null && CourierParser.norm(it.restaurant!!) == CourierParser.norm(r) } }
        o.customer.name?.let { n -> if (t.customers.isNotEmpty()) return t.customers.values.any { it.details.name != null && CourierParser.norm(it.details.name!!) == CourierParser.norm(n) } }
        return true
    }
    /** The frame names one of [t]'s customers: an order id that keys customers (4+ characters), else the customer's name
     *  (OCR variants included, [CustomerMatch.sameName]). A frame without either names nobody. */
    private fun names(o: CourierObservation, t: TripRecord): Boolean {
        val ids = keyIds(o)
        if (ids.isNotEmpty()) return ids.any { it in t.customers }
        val n = o.customer.name ?: return false
        return t.customers.values.any { CustomerMatch.sameName(it.details.name, n) }
    }
    /** [current] (the newest open order of the platform), unless the customer card names a customer of another open order of it. */
    private fun ownerOf(o: CourierObservation, current: TripRecord?): TripRecord? {
        if (current == null || names(o, current)) return current
        return trips.values.lastOrNull { it.pkg == o.pkg && it.active && it !== current && names(o, it) } ?: current
    }
    /**
     * K34: the order already closed as an inferred delivery whose customer card this DROPOFF frame is, or null.
     * - Named: the frame names a customer of an inferred delivery of the platform (the latest such) and none of the open
     *   order's. An order of that platform accepted after the close owns the card: Wolt cards carry names only ("Elvin
     *   M."), so a later customer may share the name, and ignoring it would leave that order before its customer for good.
     * - Not named (verifier, 8 Oct): with no open order of the platform, a card read while the closed order's card is the
     *   platform's last task screen, within [SLIDER_TO_WAITING_MS] of that or of the close, is that order's card unless it
     *   carries an order id the order does not have or names another order of the platform. An OCR variant of the name
     *   ("AIi Q." for "Ali Q."), a frame without the name line or a Bolt card without its #id would otherwise open a
     *   phantom order at the customer stage. With an offer on hold (no decline tap) the frame could be its acceptance;
     *   then, as for an open order ([belongsTo]), a name or restaurant of another order also makes it another order's
     *   card. A new order still opens from its offer or pickup screen.
     */
    private fun closedOrderOfCard(o: CourierObservation, current: TripRecord?): TripRecord? {
        trips.values.lastOrNull { it.pkg == o.pkg && it.deliveredInferred && names(o, it) }?.let { u ->
            if (current == null || !names(o, current) && current.acceptedAt <= u.deliveredInferredAt!!) return u
        }
        if (current != null) return null
        val u = closedOrderShown(o.pkg) ?: return null
        if (o.at - maxOf(lastSeen(u), u.deliveredInferredAt!!) > SLIDER_TO_WAITING_MS) return null
        val ids = keyIds(o)
        if (ids.isNotEmpty() && ids.none { it in u.customers } || trips.values.any { it !== u && it.pkg == o.pkg && names(o, it) }) return null
        return u.takeIf { pending[o.pkg]?.takeIf { p -> p.declineClickAt == null } == null || belongsTo(o, u) }
    }
    /**
     * A customer card of [u], closed as an inferred delivery: it stays closed (not moved back to the customer stage) and
     * the order at the customer now is left alone, but the card's slider and a completed status are [u]'s own signals.
     * When an order of the platform was accepted after the close, the card may be that order's: nothing is recorded.
     */
    private fun lateCard(u: TripRecord, o: CourierObservation, out: MutableList<CoreEvent>) {
        if (trips.values.any { it.pkg == u.pkg && it.acceptedAt > u.deliveredInferredAt!! }) return
        lateSeen[u.id] = o.at
        if (lateSeen.size > 200) lateSeen.keys.retainAll(trips.keys)
        val id = keyIds(o).firstOrNull { it in u.customers }
            ?: o.customer.name?.let { n -> u.customers.values.firstOrNull { CustomerMatch.sameName(it.details.name, n) }?.id } ?: u.currentCustomer
        if (o.deliverControl) { u.deliverSeenFor = id; u.deliverSeenAt = o.at }
        else if (u.deliverSeenFor != id) { u.deliverSeenFor = null; u.deliverSeenAt = null }
        if (o.delivered) finishInferred(u, o.at, out, "platform_completed")
    }
    /**
     * K34: [t] was first seen at a customer card (its pickup was not seen) while the card of an order of its platform closed
     * as an inferred delivery could still be on screen (within [SLIDER_TO_WAITING_MS]): the frame may have been that card
     * with an id or name OCR misread, so [t] closes no other order. Outside that window such an order closes the others
     * like any order reaching its customer (e.g. after a restart mid-delivery).
     */
    private fun mayBeClosedCard(t: TripRecord): Boolean = t.pickups.isEmpty() && trips.values.any { u ->
        u !== t && u.pkg == t.pkg && u.deliveredInferredAt != null && u.deliveredInferredBy != t.id &&
            t.acceptedAt - maxOf(lastSeen(u), u.deliveredInferredAt!!) <= SLIDER_TO_WAITING_MS
    }
    /**
     * A real completion signal of an order closed as an inferred delivery (K34): it ends as `delivered` like any other
     * order, at this moment. `deliveredInferredAt/By/Reason` stay as history, as after the courier's manual correction; the
     * economics recorder adds no second tripLegs row (that row was written at the close).
     */
    private fun finishInferred(u: TripRecord, at: Long, out: MutableList<CoreEvent>, why: String) {
        u.deliverSeenFor = null; u.deliverSeenAt = null; u.finishIntentAt = null
        finish(u, at, out, why)
    }
    /**
     * K34 (courier's rule, 8 Oct 2026): the courier carries one order at a time, so at most one order, of either platform, is
     * at the customer stage. [trip] is there now: every other open order still at its customer was handed over without an
     * observed delivery signal (e.g. the next task followed the slider before any stable waiting map). It ends as an
     * unverified delivery: phase END_UNCONFIRMED, [TripRecord.deliveredInferredAt], event `delivered_inferred` — never
     * `delivered`, no `completedAt`, no customer `deliveredAt`. A frame older than that order's own last task frame
     * (OCR is asynchronous) closes nothing.
     */
    private fun oneAtCustomer(trip: TripRecord, at: Long, out: MutableList<CoreEvent>) {
        for (u in trips.values) {
            if (u === trip || !u.atCustomer || (u.latest?.at ?: u.acceptedAt) > at || u.id in trip.sameDropoff) continue
            // K33: the slider's spinner upgrades the reason only; the order still ends unverified (`delivered_inferred`).
            val why = if (u.deliverSeenFor != null && u.deliverSeenFor == u.currentCustomer)
                (if (u.spinnerOnSlider) "next_order_at_customer_after_slider_spinner" else "next_order_at_customer_after_slider") else "next_order_at_customer"
            u.phase = TripPhase.END_UNCONFIRMED; u.finishIntentAt = null
            u.deliveredInferredAt = at; u.deliveredInferredBy = trip.id; u.deliveredInferredReason = why
            out.add(CoreEvent("delivered_inferred", u.pkg, at, tripId = u.id, reason = why))
        }
    }
    /**
     * R1 (16 Oct): a Wolt card for several orders of one customer ("2 dropoffs to", "Orders #072, #387") is the card of every
     * open order it lists: each one's restaurant is behind it and it goes to customer [id] together with [trip], as one
     * handover (no K34 close between them; a completion of [trip] completes them, [companionsDone]). Orders are matched by
     * Wolt's 3-character numbers inside this card only; they still key no customer (K10).
     */
    private fun sameCard(trip: TripRecord, o: CourierObservation, id: String, out: MutableList<CoreEvent>) {
        val short = shortIds(o); if (short.size < 2) return
        for (u in trips.values) {
            if (u === trip || u.pkg != trip.pkg || !u.active || u.woltShortIds.none { it in short }) continue
            trip.sameDropoff += u.id; u.sameDropoff += trip.id
            u.pickups.lastOrNull()?.takeIf { it.pickedAt == null }?.let { p ->
                p.pickedAt = o.at; out.add(CoreEvent("picked_up", u.pkg, o.at, tripId = u.id, reason = "same_dropoff_card"))
            }
            val c = u.customers.getOrPut(id) { CustomerRecord(id) }
            c.details.merge(o.customer); c.detailsAt = o.at
            if (u.phase == TripPhase.TO_RESTAURANT || u.phase == TripPhase.WAITING) u.phase = TripPhase.TO_CUSTOMER
            u.currentCustomer = id
            if (o.arrived && c.arrivedAt == null) { c.arrivedAt = o.at; u.phase = TripPhase.AT_CUSTOMER }
        }
    }
    /** R1: [trip]'s customer was handed over; the orders of the same "N dropoffs to" card were handed over with it. */
    private fun companionsDone(trip: TripRecord, at: Long, out: MutableList<CoreEvent>, why: String) {
        for (u in trip.sameDropoff.mapNotNull { trips[it] }) if (u.atCustomer) finish(u, at, out, why)
    }
    /** The delivery slider of the customer [t] is at now was on screen (Wolt 2.202.6 shows it on the customer card). */
    private fun pastSlider(t: TripRecord) = t.deliverSeenFor != null && t.deliverSeenFor == t.currentCustomer
    /** The restaurant screen names a restaurant that is none of [t]'s, OCR variants included ([OfferMatch.similar]), and no
     *  order id of it: a frame of another order, not [t]'s own pickup panel or timeline read again. */
    private fun otherRestaurant(o: CourierObservation, t: TripRecord): Boolean {
        val r = o.restaurant ?: return false
        val own = t.pickups.mapNotNull { it.restaurant } + listOfNotNull(t.offer?.data?.restaurant)
        return own.isNotEmpty() && own.none { OfferMatch.similar(it, r) } && keyIds(o).none { it in t.customers }
    }
    /**
     * D3(b) (saha 11–12 Oct): Wolt shows one task at a time, so its restaurant screen of order [trip] after the delivery slider
     * of another open Wolt order means that order was handed over. The swipe produces no event and only ~26% of the sliders
     * were followed by a stable waiting map (35 sliders: a new offer, the map, a power saving dialog instead); the next
     * order's customer stage ([oneAtCustomer]) came minutes later. The order ends as an unverified delivery like K34's:
     * `delivered_inferred`, reason `next_order_at_restaurant_after_slider`. Another platform's restaurant screen (split screen,
     * a Bolt order accepted on the way) proves nothing; a frame older than the order's own last task frame closes nothing.
     */
    private fun nextAtRestaurant(trip: TripRecord, at: Long, out: MutableList<CoreEvent>) {
        if (trip.pkg !in CourierParser.WOLT) return
        for (u in trips.values) {
            if (u === trip || u.pkg != trip.pkg || !u.atCustomer || !(pastSlider(u) || hinted(u)) || (u.latest?.at ?: u.acceptedAt) > at) continue
            val why = when {
                pastSlider(u) -> if (u.spinnerOnSlider) "next_order_at_restaurant_after_slider_spinner" else "next_order_at_restaurant_after_slider"
                else -> "next_order_at_restaurant_after_${u.handoverHintKind}"
            }
            inferDelivered(u, at, trip.id, why, out)
        }
    }
    /** M22: a handover sign (spinner without the label) on the card of the customer [t] is at now. */
    private fun hinted(t: TripRecord) = t.handoverHintFor != null && t.handoverHintFor == t.currentCustomer
    /** Closes open order [u] as an unverified delivery (K34): END_UNCONFIRMED, [TripRecord.deliveredInferredAt], `delivered_inferred`. */
    private fun inferDelivered(u: TripRecord, at: Long, by: String?, why: String, out: MutableList<CoreEvent>) {
        u.phase = TripPhase.END_UNCONFIRMED; u.finishIntentAt = null
        u.deliveredInferredAt = at; u.deliveredInferredBy = by; u.deliveredInferredReason = why
        out.add(CoreEvent("delivered_inferred", u.pkg, at, tripId = u.id, reason = why))
    }
    /**
     * D3(d): the Wolt Finances "Cash collection" row of cash order [id], clock [at] (start of its minute: the row has no
     * seconds), is the handover time. A delivered order keeps its time inside that minute, else it moves to the nearest
     * moment of it (King Doner 11 Oct: Rawstats 16:57, Finances 16:46; event `delivery_time_corrected`); an unverified or
     * still open order ends as `delivered` (reason `wolt_cash_collection`). The row also marks the order cash, fills an
     * unread cash amount (K31) and verifies the delivery ([TripRecord.verifiedBy]). Once per order.
     */
    fun cashCollected(id: String, at: Long, amount: Double?, now: Long): List<CoreEvent> {
        val t = trips[id]?.takeIf { it.cashCollectedAt == null } ?: return emptyList()
        val out = ArrayList<CoreEvent>()
        t.cashCollectedAt = at; t.cashCollectedAzn = amount
        t.verifiedBy = "wolt_cash_collection"; t.verifiedAt = now
        if (t.paymentMethod != "cash" || t.cashAmount == null && amount != null) {
            if (t.paymentMethod != "cash") { t.paymentMethod = "cash"; t.paymentSource = "wolt_cash_collection"; t.paymentAt = now }
            if (t.cashAmount == null) t.cashAmount = amount
            out.add(CoreEvent("payment_seen", t.pkg, now, tripId = t.id, reason = "cash_collection"))
        }
        fun inMinute(x: Long?) = (x ?: at).coerceIn(at, at + 59_999)
        val done = t.completedAt
        when {
            done != null -> {
                val fixed = inMinute(done)
                if (fixed != done) {
                    t.customers.values.filter { it.deliveredAt == done }.forEach { it.deliveredAt = fixed }
                    t.completedAt = fixed
                    out.add(CoreEvent("delivery_time_corrected", t.pkg, fixed, tripId = t.id, reason = "wolt_cash_collection"))
                }
            }
            t.deliveredInferred -> finishInferred(t, inMinute(t.deliveredInferredAt), out, "wolt_cash_collection")
            else -> { t.finishIntentAt = null; finish(t, inMinute(t.latest?.at), out, "wolt_cash_collection") }
        }
        return out
    }
    /** D3(c): a platform's earnings list showed order [id] as delivered (K36 match); an unverified delivery becomes verified. */
    fun listVerified(id: String, by: String, now: Long): Boolean {
        val t = trips[id]?.takeIf { it.verifiedBy == null } ?: return false
        t.verifiedBy = by; t.verifiedAt = now
        return true
    }
    /**
     * D9: the courier's GPS stayed at the restaurant of pickup [index] of order [id] since [at] ([VenueArrival]; [source] names
     * the restaurant's coordinate). A platform status or the courier's "Arrived" tap is never replaced, nor is an arrival after
     * the pickup taken. [replace]: a GPS arrival found before the pickup is corrected at the pickup (null [at] drops it). An order
     * still before that pickup moves to the restaurant stage (WAITING). The event's time is [now], the moment it was found.
     */
    fun venueArrived(id: String, index: Int, at: Long?, source: String?, now: Long, replace: Boolean = false): List<CoreEvent> {
        if (!running) return emptyList()
        val t = trips[id] ?: return emptyList()
        val p = t.pickups.getOrNull(index) ?: return emptyList()
        if (at != null && (p.pickedAt ?: Long.MAX_VALUE) < at) return emptyList()
        val corrected = p.arrivedAt != null
        if (corrected && !(replace && p.arrivalSource?.startsWith("gps") == true) || p.arrivedAt == at) return emptyList()
        p.arrivedAt = at; p.arrivalSource = at?.let { source }
        if (at != null && p.pickedAt == null && t.phase == TripPhase.TO_RESTAURANT && index == t.pickups.lastIndex) t.phase = TripPhase.WAITING
        return listOf(CoreEvent(if (corrected) "restaurant_arrival_corrected" else "restaurant_arrived", t.pkg, now, tripId = t.id,
            reason = at?.let { source } ?: "gps_venue_mismatch"))
    }
    /** M19: the courier reached the current customer of [id] at [at] (GPS or navigation distance, [source]); the platform's own
     *  "Arrived" seen before stays. */
    fun customerArrived(id: String, at: Long, source: String, now: Long): List<CoreEvent> {
        if (!running) return emptyList()
        val t = trips[id]?.takeIf { it.active && it.phase == TripPhase.TO_CUSTOMER } ?: return emptyList()
        val c = t.customers[t.currentCustomer ?: return emptyList()]?.takeIf { it.arrivedAt == null && it.deliveredAt == null } ?: return emptyList()
        c.arrivedAt = at; c.arrivalSource = source; t.phase = TripPhase.AT_CUSTOMER
        return listOf(CoreEvent("customer_arrived", t.pkg, now, tripId = t.id, reason = source))
    }
    /** "Ready" / "ready in N min" from a courier-app notification, for the restaurant card. */
    fun readinessNotice(pkg: String, text: String, at: Long) {
        val t = active(pkg)?.takeIf { it.phase == TripPhase.TO_RESTAURANT || it.phase == TripPhase.WAITING } ?: return
        val p = t.pickups.lastOrNull { it.pickedAt == null } ?: return
        val n = CourierParser.norm(text)
        Regex("ready in\\s*(\\d+)\\s*min").find(n)?.groupValues?.get(1)?.toIntOrNull()?.let { p.readyHintAt = at + it * 60000L; return }
        if (Regex("ready for pickup|order is ready|is ready|hazirdir").containsMatchIn(n) && p.readyObservedAt == null) p.readyObservedAt = at
    }
    fun click(pkg: String, text: String, at: Long): List<CoreEvent> {
        if (!running) return emptyList()
        val out = ArrayList<CoreEvent>()
        val p = pending[pkg]
        if (p != null && CourierParser.isDecline(text)) {
            val first = p.declineClickAt; val dialog = declineDialogAt[pkg]
            // Wolt: "Decline" on the offer opens "Decline offer?"; "Decline" there confirms it.
            if (first != null && dialog != null && dialog >= first && at - dialog in 0..DECLINE_DIALOG_MS) {
                pending.remove(pkg); out.add(CoreEvent("rejected", pkg, at, p.id, reason = "courier_declined_confirmed")); remember(out, mapOf(p.id to p))
            } else if (first == null) {
                p.declineClickAt = at
            }
        } else if (p != null && (CourierParser.norm(text) == "return to offer details" || CourierParser.isAccept(text))) {
            p.declineClickAt = null; declineDialogAt.remove(pkg)
        }
        if (CourierParser.isDecline(text)) declineAt[pkg] = at
        val genericConfirm=CourierParser.norm(text) in setOf("confirm", "complete", "finish", "done", "✓", "✔", "teslim et", "tehvil ver")
        // The card of an order closed as an inferred delivery is shown: a tap on it is that order's; only its deliver
        // intent counts (K34).
        closedOrderShown(pkg)?.let { u -> if (CourierParser.isDeliver(text) || genericConfirm) u.finishIntentAt = at; return out }
        // A tap belongs to the order whose screen is shown, e.g. "Arrived" on the older order's customer card (K34).
        val trip = shown(pkg) ?: return out
        if (CourierParser.isCollect(text)) trip.pickupIntentAt = at
        if (CourierParser.isCall(text) && (trip.phase == TripPhase.TO_CUSTOMER || trip.phase == TripPhase.AT_CUSTOMER) && trip.currentCustomer != null) {
            trip.callFor = trip.currentCustomer; trip.callAt = at
        }
        if (trip.latest?.stage==ScreenStage.DROPOFF && (CourierParser.isDeliver(text) || genericConfirm)) trip.finishIntentAt = at
        if (CourierParser.isArrive(text)) {
            if (trip.phase == TripPhase.TO_RESTAURANT) {
                trip.pickups.lastOrNull()?.let { it.arrivedAt = at; it.arrivalSource = "user_arrived_action" }; trip.phase = TripPhase.WAITING
            } else if (trip.phase == TripPhase.TO_CUSTOMER) {
                trip.customers[trip.currentCustomer]?.arrivedAt = at; trip.phase = TripPhase.AT_CUSTOMER
            }
        }
        return out
    }
    /**
     * K31: Wolt's delivery steps screen sets the payment of the order on screen (the last shown open Wolt order, else the only
     * open one). A cash step always wins (a later frame may only refresh the amount); `card_inferred` needs a complete frame
     * with the steps header and no cash step, and never replaces an observed cash. Emits `payment_seen` on every change.
     */
    private fun payment(o: CourierObservation, out: MutableList<CoreEvent>) {
        if (o.pkg !in CourierParser.WOLT || !(o.cashOrder || (o.cashStepsSeen && o.complete))) return
        // An offer card's "Cash order" belongs to the offer, never to the order already on the way (D25).
        if (o.stage == ScreenStage.OFFER) return
        val t = lastShown(o.pkg)?.takeIf { it.active } ?: trips.values.singleOrNull { it.pkg == o.pkg && it.active } ?: return
        if (o.cashOrder) {
            o.cashChangeAzn?.let { t.cashChangeAzn = it; t.cashNoChange = it == 0.0 }
            if (o.cashChecked == true && t.cashCheckedAt == null) {
                t.cashCheckedAt = o.at; out.add(CoreEvent("cash_checked_seen", t.pkg, o.at, tripId = t.id))
            }
            val amount = o.cashAmount ?: t.cashAmount
            if (t.paymentMethod == "cash" && t.cashAmount == amount) return
            t.paymentMethod = "cash"; t.paymentSource = if (amount == null && t.paymentSource == "wolt_offer_card") "wolt_offer_card" else "wolt_cash_screen"
            t.cashAmount = amount; t.paymentAt = o.at
            out.add(CoreEvent("payment_seen", t.pkg, o.at, tripId = t.id, reason = if (amount != null) "cash" else "cash_amount_unread"))
        } else {
            if (t.paymentMethod != null) return
            t.paymentMethod = "card_inferred"; t.paymentSource = "wolt_delivery_steps_no_cash"; t.paymentAt = o.at
            out.add(CoreEvent("payment_seen", t.pkg, o.at, tripId = t.id, reason = "card_inferred"))
        }
    }
    /**
     * K33: a Wolt frame with a progress indicator on the delivery slider, at most [SPINNER_AFTER_SLIDER_MS] after the slider of
     * the customer the last shown order (open, or closed as an inferred delivery) is at: recorded once per slider as evidence
     * (`delivery_spinner_seen`). It finishes nothing; the slider logic (waiting map, next customer) still decides.
     */
    private fun spinner(o: CourierObservation, out: MutableList<CoreEvent>) {
        if (!o.deliverSpinner || o.pkg !in CourierParser.WOLT) return
        val t = lastShown(o.pkg)?.takeIf { it.active || it.deliveredInferred } ?: return
        val c = t.deliverSeenFor?.takeIf { it == t.currentCustomer } ?: return
        if (o.at - (t.deliverSeenAt ?: return) !in 0..SPINNER_AFTER_SLIDER_MS || t.deliverSpinnerFor == c) return
        t.deliverSpinnerAt = o.at; t.deliverSpinnerFor = c
        out.add(CoreEvent("delivery_spinner_seen", t.pkg, o.at, tripId = t.id, reason = "after_delivery_slider"))
    }
    fun confirmDelivered(id: String, at: Long): List<CoreEvent> {
        val trip = trips[id] ?: return emptyList()
        val out = ArrayList<CoreEvent>(); finish(trip, at, out, "courier_manual_correction"); return out
    }
    /** The waiting map after a delivery slider: the platform has no task left for this trip. */
    private fun completeOnWaiting(trip: TripRecord, at: Long, out: MutableList<CoreEvent>) {
        val why = "delivery_slider_then_waiting"
        trip.deliverSeenFor = null; trip.deliverSeenAt = null
        companionsDone(trip, at, out, why)
        if (trip.expectedOrders <= 1 && trip.customers.size <= 1) { finish(trip, at, out, why); return }
        trip.customers[trip.currentCustomer]?.takeIf { it.deliveredAt == null }?.let {
            it.deliveredAt = at; out.add(CoreEvent("customer_delivered", trip.pkg, at, tripId = trip.id, reason = why))
        }
        if (trip.customers.values.all { it.deliveredAt != null } && trip.customers.size >= trip.expectedOrders) finish(trip, at, out, why)
    }
    private fun completeCustomerOrTrip(trip: TripRecord, at: Long, out: MutableList<CoreEvent>, why: String) {
        trip.finishIntentAt = null
        companionsDone(trip, at, out, why)
        if (trip.expectedOrders <= 1 && trip.customers.size <= 1) { finish(trip, at, out, why); return }
        val customer = trip.customers[trip.currentCustomer] ?: return
        if (customer.deliveredAt == null) {
            customer.deliveredAt = at
            out.add(CoreEvent("customer_delivered", trip.pkg, at, tripId = trip.id, reason = why))
        }
        // A bundle remains active until each known/expected customer's delivery
        // is confirmed. A repeated completion message cannot finish another one.
        if (trip.customers.values.count { it.deliveredAt != null } >= maxOf(trip.expectedOrders, trip.customers.size))
            finish(trip, at, out, why)
    }
    private fun finish(trip: TripRecord, at: Long, out: MutableList<CoreEvent>, why: String) {
        if (trip.completedAt != null) return
        trip.customers[trip.currentCustomer]?.let { if (it.deliveredAt == null) it.deliveredAt = at }
        trip.completedAt = at; trip.phase = TripPhase.FINISHED
        out.add(CoreEvent("delivered", trip.pkg, at, tripId = trip.id, reason = why))
    }
    /** Route distance belongs to one trip, never split or duplicated: the active trip whose task screen was seen last (K9). */
    fun addDistance(m: Double) {
        if (m <= 0) return
        trips.values.filter { it.active }.maxByOrNull { it.latest?.at ?: it.acceptedAt }?.let { it.distanceM += m }
    }
    /** Scooter energy gathered for open trips, moved into the record once at the trip's end ([closeEnergy]): the record, its
     *  courier.db row and its orders.jsonl snapshots do not change every second because of it (also while waiting). */
    private class EnergySum { var used = 0.0; var recovered = 0.0; var ms = 0L; var partialMs = 0L; var shared = false }
    private val energy = HashMap<String, EnergySum>()
    /** Merged Sürüş Paneli, one step of the scooter's 1 Hz samples: the energy goes where the distance goes (the active trip
     *  whose task screen was seen last), never split or duplicated, so Wh/km per order holds. [TripRecord.energyShared] marks a
     *  trip that got energy while another order was open too: the split then follows the screens, not a meter per order. */
    fun addEnergy(usedWh: Double, recoveredWh: Double, ms: Long, complete: Boolean = true) {
        if (ms <= 0 || !usedWh.isFinite() || !recoveredWh.isFinite() || usedWh < 0 || recoveredWh < 0) return
        val open = trips.values.filter { it.active }
        val t = open.maxByOrNull { it.latest?.at ?: it.acceptedAt } ?: return
        if (energy.size > open.size) energy.keys.retainAll { id -> open.any { it.id == id } } // trips gone without an end (import)
        val sum = energy.getOrPut(t.id) { EnergySum() }
        // D10: only both packs' power is the order's energy; an internal-only step adds measurement gap time, no Wh.
        if (complete) { sum.used += usedWh; sum.recovered += recoveredWh; sum.ms += ms } else sum.partialMs += ms
        if (open.size > 1) sum.shared = true
    }
    /** At the trip's end (delivered, inferred or unconfirmed): its gathered scooter energy joins the record, once. */
    fun closeEnergy(trip: TripRecord) {
        val sum = energy.remove(trip.id) ?: return
        trip.usedWh += sum.used; trip.recoveredWh += sum.recovered; trip.energyCoveredMs += sum.ms; trip.energyPartialMs += sum.partialMs
        if (sum.shared) trip.energyShared = true
    }
    companion object {
        const val SLIDER_TO_WAITING_MS = 30 * 60_000L
        /** K33: a spinner counts only this soon after the slider frame it belongs to. */
        const val SPINNER_AFTER_SLIDER_MS = 10_000L
        /** Wolt's waiting map this long (stable) closes any Wolt trip still open. */
        const val WAITING_MAP_CLOSES_MS = 3_000L
        const val OFFER_STALE_MS = 3 * 60_000L
        /** K37: a late offer frame taken this long before the offer's first frame still describes it. */
        const val LATE_OFFER_FRAME_MS = 2_000L
        /** A newer offer shown during a delivery may be accepted later; keep it longer. */
        const val OFFER_STALE_ON_TASK_MS = 20 * 60_000L
        const val DECLINE_DIALOG_MS = 30_000L
        const val CALL_TO_DIALER_MS = 60_000L
        const val BANNER_RESET_MS = 5 * 60_000L
        /** An offer still on screen this close to its documented window had timed out (SPEC §17.2 `timeout_inferred`). */
        const val EXPIRY_MARGIN_MS = 2_000L
        /** Window of the platform's decline counter: PRIOR `wolt_K_support.window_min` (SPEC Appendix A). */
        val DECLINE_WINDOW_MS: Long by lazy { OfferWindows.declineWindowMs } // WEEM: OfferWindows (ORDERS.md 2.13)
        /** Documented offer window, an UNVERIFIED upper bound (PRIOR `documented_window_ms`); null for other platforms. */
        fun offerWindowMs(pkg: String): Long? = OfferWindows.offerWindowMs(pkg) // WEEM: OfferWindows (ORDERS.md 2.13)
        /** Order ids that key a customer: 4+ characters ("#UWG2Q"); Wolt's short "#408" is kept on the observation only (K10). */
        fun keyIds(o: CourierObservation): List<String> = o.orderIds.filter { it.removePrefix("#").length >= 4 }
        /** R1: Wolt's short order numbers ("#072"), only for matching orders on one card ([sameCard]). */
        fun shortIds(o: CourierObservation): List<String> =
            if (o.pkg in CourierParser.WOLT) o.orderIds.filter { it.removePrefix("#").length == 3 } else emptyList()
        /** SPEC §17.2 `countsAsDecline`: taps, the banner, timeouts and unknown disappearances; never accepted, replaced,
         *  recording stopped or a Bolt optional order. */
        fun countsAsDecline(type: String, reason: String?, optional: Boolean = false): Boolean = !optional && when (type) {
            "rejected" -> true
            "offer_unconfirmed" -> reason == "expired_inferred" || reason == "offer_not_seen"
            else -> false
        }
        /** The courier's own decline tap or Wolt's "N deliveries declined" banner, the explicit part of [countsAsDecline]. */
        fun explicitDecline(type: String, reason: String?, optional: Boolean = false): Boolean = !optional && type == "rejected" &&
            reason in setOf("courier_declined", "courier_declined_confirmed", "courier_declined_banner")
        private val offlineForDeclinesText = Regex("(?:set offline|offline until).{0,120}declin")
        /** The ban message itself (one line, or wrapped onto the next); "declining too many can mean being set offline" is not one. */
        fun offlineForDeclines(lines: List<String>): Boolean = lines.indices.any { i ->
            offlineForDeclinesText.containsMatchIn(CourierParser.norm(lines[i] + " " + (lines.getOrNull(i + 1) ?: "")))
        }
        private val optionalLine = Regex("^optional(?: order| delivery)?$")
        /** Bolt marks orders outside the courier's radius "Optional" (SPEC §9.2; label not yet seen on the phone). */
        fun isOptional(o: CourierObservation): Boolean = o.pkg.contains("bolt", true) && o.raw.any { optionalLine.matches(CourierParser.norm(it)) }
    }
}

/** Same customer seen again without an order ID: OCR misreads a letter or drops the surname. */
object CustomerMatch {
    private fun tokens(s: String) = CourierParser.norm(s).split(" ").filter { it.isNotEmpty() }
    internal fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val cur = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = cur }
        }
        return d[b.length]
    }
    private fun tokenEq(a: String, b: String) = a == b || (minOf(a.length, b.length) >= 5 && distance(a, b) <= 1)
    fun sameName(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val x = tokens(a); val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return false
        val (short, long) = if (x.size <= y.size) x to y else y to x
        // Every word of the shorter name appears in the longer one, the first word included.
        return tokenEq(short[0], long[0]) && short.all { s -> long.any { tokenEq(s, it) } }
    }
    /** An undelivered customer of the trip with the same name. A different address keeps them apart. */
    fun find(customers: Collection<CustomerRecord>, name: String, address: String?): CustomerRecord? =
        customers.firstOrNull { c -> c.deliveredAt == null && sameName(c.details.name, name) &&
            (address == null || c.details.address == null || !looksLikeAddress(c.details.address!!) || !looksLikeAddress(address) ||
                CourierParser.norm(c.details.address!!) == CourierParser.norm(address)) }
    /** A street line has a digit; a surname moved to the address line by OCR has none. */
    fun looksLikeAddress(s: String) = s.any(Char::isDigit)
    /** For records saved before the fix: OCR variants of one customer shown once (fullest name, real address). */
    fun distinct(customers: Collection<CustomerRecord>): List<CustomerRecord> {
        val out = ArrayList<CustomerRecord>()
        for (c in customers) {
            val i = out.indexOfFirst { o -> o.details.name != null && sameName(o.details.name, c.details.name) &&
                (o.details.address == null || c.details.address == null || !looksLikeAddress(o.details.address!!) || !looksLikeAddress(c.details.address!!) ||
                    CourierParser.norm(o.details.address!!) == CourierParser.norm(c.details.address!!)) }
            if (i < 0) { out.add(c); continue }
            val o = out[i]
            val d = o.details.copy().also { it.merge(c.details) }
            d.name = listOfNotNull(o.details.name, c.details.name).maxByOrNull { tokens(it).size } ?: d.name
            d.address = listOfNotNull(o.details.address, c.details.address).firstOrNull(::looksLikeAddress) ?: d.address
            out[i] = o.copy(details = d, deliveredAt = o.deliveredAt ?: c.deliveredAt, arrivedAt = o.arrivedAt ?: c.arrivedAt)
        }
        return out
    }
}

/** One offer card read twice (accessibility and OCR, or two OCR crops): similar, not equal, text (K7). */
object OfferMatch {
    /** OCR confuses 1/l/i/| and 0/o; punctuation and line wraps ("Test 2," + "Bakı") carry no identity. */
    private fun tokens(s: String) = CourierParser.norm(s).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        .map { when (it) { '1', 'i', '|' -> 'l'; '0' -> 'o'; else -> it } }.joinToString("")
        .split(" ").filter { it.isNotEmpty() }
    private fun tokenEq(a: String, b: String) = a == b || (minOf(a.length, b.length) >= 5 && CustomerMatch.distance(a, b) <= 1)
    /** Unknown (or only punctuation) on either side is no contradiction. Otherwise every word of the shorter text appears in the longer one,
     *  the first word included (a wrapped or truncated line), like [CustomerMatch.sameName]. */
    fun similar(a: String?, b: String?): Boolean {
        if (a == null || b == null) return true
        val x = tokens(a); val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return true
        val (short, long) = if (x.size <= y.size) x to y else y to x
        return tokenEq(short[0], long[0]) && short.all { t -> long.any { tokenEq(t, it) } }
    }
}
