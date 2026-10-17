package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (DeliveryCore.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
enum class TripPhase { TO_RESTAURANT, WAITING, TO_CUSTOMER, AT_CUSTOMER, FINISHED, END_UNCONFIRMED }

data class OfferState(val id: String, val pkg: String, val firstAt: Long, var data: CourierObservation, var phoneBattery: Int? = null,
    var lat: Double? = null, var lon: Double? = null, var gpsAccuracyM: Double? = null, var gpsAgeMs: Long? = null,
    var scooterBatteryPct: Double? = null, var scooterObservedAt: Long? = null, var scooterSource: String? = null) {
    var boostValue: Double? = null
    var boostUnit: String? = null
    var boostSource: String? = null
    var boostSeenAt: Long? = null
    var weather: OfferWeather? = null
    var lastSeenAt: Long
        get() = TODO("A3.5")
        set(_) = TODO("A3.5")
    var sessionId: String? = null
    var declineClickAt: Long? = null
    var optional: Boolean = false
    val fieldSource = HashMap<String, String>()
    var feeAt: Long? = null
    var feeGapMs: Long? = null
    var feeOcrMs: Long? = null
    var notifiedAt: Long? = null
    var readyInAt: Long?
        get() = TODO("A3.5")
        set(_) = TODO("A3.5")
    fun enrich(o: CourierObservation): Unit = TODO("A3.5")
    fun backfill(o: CourierObservation): Unit = TODO("A3.5")
    fun same(o: CourierObservation): Boolean = TODO("A3.5")
}

data class PickupRecord(var restaurant: String?, var address: String?, var readyAt: Long? = null,
    var arrivedAt: Long? = null, var pickedAt: Long? = null, var arrivalSource: String? = null, var readyObservedAt: Long? = null) {
    var note: String? = null
    var code: String? = null
    var items: List<String> = emptyList()
    var readyHintAt: Long? = null
    var preparingAt: Long? = null
    var reminderAt: Long? = null
}

data class CustomerRecord(val id: String, val details: DeliveryDetails = DeliveryDetails(), var arrivedAt: Long? = null,
    var deliveredAt: Long? = null, var lat: Double? = null, var lon: Double? = null, var coordinateSource: String? = null,
    var reminderAt: Long? = null, var detailsAt: Long = 0, var deadlineClock: Int? = null) {
    var deadlineAt: Long? = null
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
    val woltShortIds = linkedSetOf<String>()
    val sameDropoff = linkedSetOf<String>()
    var sessionId: String? = null
    var callFor: String? = null
    var callAt: Long? = null
    var deliveredInferredAt: Long? = null
    var deliveredInferredBy: String? = null
    var deliveredInferredReason: String? = null
    val deliveredInferred: Boolean get() = TODO("A3.5")
    var deliverSpinnerAt: Long? = null
    var deliverSpinnerFor: String? = null
    var handoverHintFor: String? = null
    var handoverHintAt: Long? = null
    var handoverHintKind: String? = null
    val spinnerOnSlider: Boolean get() = TODO("A3.5")
    var paymentMethod: String? = null
    var paymentSource: String? = null
    var cashAmount: Double? = null
    var paymentAt: Long? = null
    var cashNoChange: Boolean? = null
    var cashChangeAzn: Double? = null
    var cashCheckedAt: Long? = null
    val paymentSourceOrNotObserved: String get() = TODO("A3.5")
    var tipSource: String? = null
    var verifiedBy: String? = null
    var verifiedAt: Long? = null
    var cashCollectedAt: Long? = null
    var cashCollectedAzn: Double? = null
    var extraWaitAzn: Double = 0.0
    var energyPartialMs: Long = 0
    var platformTipAzn: Double? = null
    val active: Boolean get() = TODO("A3.5")
    val countsAsDelivered: Boolean get() = TODO("A3.5")
    val atCustomer: Boolean get() = TODO("A3.5")
    val actualMinutes: Double? get() = TODO("A3.5")
    val gross: Double? get() = TODO("A3.5")
    val earnings: Double? get() = TODO("A3.5")
    val net: Double? get() = TODO("A3.5")
    val waitMinutes: Double get() = TODO("A3.5")
    val lateMinutes: Double get() = TODO("A3.5")
}

data class CoreEvent(val type: String, val pkg: String, val at: Long, val offerId: String? = null, val tripId: String? = null, val reason: String? = null)

data class OfferOutcome(val offerId: String, val pkg: String, val at: Long, val type: String, val reason: String?, val optional: Boolean = false) {
    val countsAsDecline: Boolean get() = TODO("A3.5")
    val explicitDecline: Boolean get() = TODO("A3.5")
}

data class DeclineWindow(val at: Long, val countsAsDecline: Int, val explicit: Int, val windowMs: Long)

class DeliveryCore {
    var running: Boolean
        get() = TODO("A3.5")
        set(_) = TODO("A3.5")
    val pending = linkedMapOf<String, OfferState>()
    val trips = linkedMapOf<String, TripRecord>()
    val offerOutcomes: List<OfferOutcome> get() = TODO("A3.5")
    val penaltyCounts: Map<String, DeclineWindow> get() = TODO("A3.5")
    fun start(): Unit = TODO("A3.5")
    fun stop(at: Long): List<CoreEvent> = TODO("A3.5")
    fun restoreOutcome(r: OfferOutcome): Unit = TODO("A3.5")
    fun recentDeclines(pkg: String, at: Long): DeclineWindow = TODO("A3.5")
    fun active(pkg: String): TripRecord? = TODO("A3.5")
    fun shown(pkg: String): TripRecord? = TODO("A3.5")
    fun callTarget(now: Long): Pair<TripRecord, CustomerRecord>? = TODO("A3.5")
    fun observe(o: CourierObservation): List<CoreEvent> = TODO("A3.5")
    fun cashCollected(id: String, at: Long, amount: Double?, now: Long): List<CoreEvent> = TODO("A3.5")
    fun listVerified(id: String, by: String, now: Long): Boolean = TODO("A3.5")
    fun venueArrived(id: String, index: Int, at: Long?, source: String?, now: Long, replace: Boolean = false): List<CoreEvent> = TODO("A3.5")
    fun customerArrived(id: String, at: Long, source: String, now: Long): List<CoreEvent> = TODO("A3.5")
    fun readinessNotice(pkg: String, text: String, at: Long): Unit = TODO("A3.5")
    fun click(pkg: String, text: String, at: Long): List<CoreEvent> = TODO("A3.5")
    fun confirmDelivered(id: String, at: Long): List<CoreEvent> = TODO("A3.5")
    fun addDistance(m: Double): Unit = TODO("A3.5")
    fun addEnergy(usedWh: Double, recoveredWh: Double, ms: Long, complete: Boolean = true): Unit = TODO("A3.5")
    fun closeEnergy(trip: TripRecord): Unit = TODO("A3.5")
    companion object {
        const val SLIDER_TO_WAITING_MS = 30 * 60_000L
        const val SPINNER_AFTER_SLIDER_MS = 10_000L
        const val WAITING_MAP_CLOSES_MS = 3_000L
        const val OFFER_STALE_MS = 3 * 60_000L
        const val LATE_OFFER_FRAME_MS = 2_000L
        const val OFFER_STALE_ON_TASK_MS = 20 * 60_000L
        const val DECLINE_DIALOG_MS = 30_000L
        const val CALL_TO_DIALER_MS = 60_000L
        const val BANNER_RESET_MS = 5 * 60_000L
        const val EXPIRY_MARGIN_MS = 2_000L
        val DECLINE_WINDOW_MS: Long get() = TODO("A3.5")
        fun offerWindowMs(pkg: String): Long? = TODO("A3.5")
        fun keyIds(o: CourierObservation): List<String> = TODO("A3.5")
        fun shortIds(o: CourierObservation): List<String> = TODO("A3.5")
        fun countsAsDecline(type: String, reason: String?, optional: Boolean = false): Boolean = TODO("A3.5")
        fun explicitDecline(type: String, reason: String?, optional: Boolean = false): Boolean = TODO("A3.5")
        fun offlineForDeclines(lines: List<String>): Boolean = TODO("A3.5")
        fun isOptional(o: CourierObservation): Boolean = TODO("A3.5")
    }
}

object CustomerMatch {
    fun find(customers: Collection<CustomerRecord>, name: String, address: String?): CustomerRecord? = TODO("A3.5")
    fun distinct(customers: Collection<CustomerRecord>): List<CustomerRecord> = TODO("A3.5")
}

object OfferMatch {
    fun similar(a: String?, b: String?): Boolean = TODO("A3.5")
}

