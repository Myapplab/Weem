package com.kuryelog.logic.orders

import com.kuryelog.logic.core.TripStages

// Rawstats TripStages.kt:13-78 @ 6388c04, satır satır. WEEM (ORDERS.md 13.2): JSON yerine `logic/core` `TripStages` tipi üretilir
// (`OrderEvents.stages`); `attach` (feed v2'ye ekleme) yok (S7). Sınırlar `TripStages.MAX_ACTIVE/MAX_ENDED/ENDED_KEEP_MS` (aynı değerler).
/**
 * E8 (Faz 3): the stage timeline of each open order and of each order ended in the last [TripStages.ENDED_KEEP_MS]. Each stage
 * carries the moment it happened in the record, so a reader that missed a read still applies the stages in their real order and
 * at their real times (D17: a 7 s poll saw a Wolt order jump from "to the restaurant" to "to the customer" and logged the arrival
 * and the departure at the same ms). An ended order says how it ended. Only ids, the platform package, stage names and times:
 * no customer data. Pure.
 */
object OrderStages {
    /** How an ended order ended: `delivered`, `delivered_unverified` (K34/D3 inferred), `cancelled` (closed before any pickup,
     *  D7) or `unconfirmed` (picked up, the handover not seen); null while the order is open. */
    fun outcome(t: TripRecord): TripStages.Outcome? = when {
        t.active -> null
        t.phase == TripPhase.FINISHED -> TripStages.Outcome.DELIVERED
        t.deliveredInferred -> TripStages.Outcome.DELIVERED_UNVERIFIED
        t.pickups.none { it.pickedAt != null } -> TripStages.Outcome.CANCELLED
        else -> TripStages.Outcome.UNCONFIRMED
    }

    /** When an ended order ended: the record's own time, else [closedAt] (the close event the recorder saw); null if unknown. */
    fun endedAt(t: TripRecord, closedAt: Long?): Long? = when {
        t.active -> null
        t.phase == TripPhase.FINISHED -> t.completedAt ?: closedAt
        t.deliveredInferred -> t.deliveredInferredAt ?: closedAt
        else -> closedAt
    }

    /** The order's stages in order, each at the moment it began; a stage whose time was not observed is left out. */
    fun events(t: TripRecord): List<TripStages.Event> {
        val out = ArrayList<TripStages.Event>()
        fun add(stage: TripStages.Stage, at: Long?, pickup: Int? = null, source: String? = null) {
            if (at != null) out.add(TripStages.Event(stage, at, pickup, source))
        }
        add(TripStages.Stage.TO_PICKUP, t.acceptedAt, pickup = 0)
        for ((i, p) in t.pickups.withIndex()) {
            add(TripStages.Stage.AT_PICKUP, p.arrivedAt, pickup = i, source = if (p.arrivalSource?.startsWith("gps") == true) "gps" else "screen")
            val picked = p.pickedAt ?: return out
            if (i < t.pickups.lastIndex) add(TripStages.Stage.TO_PICKUP, picked, pickup = i + 1) else add(TripStages.Stage.TO_DROPOFF, picked)
        }
        // Customers in handover order (as the trip legs); the last handover is the order's end ([endedAt]).
        val customers = t.customers.values.sortedWith(compareBy({ it.deliveredAt ?: Long.MAX_VALUE }, { it.arrivedAt ?: Long.MAX_VALUE }))
        for ((k, c) in customers.withIndex()) {
            add(TripStages.Stage.AT_DROPOFF, c.arrivedAt)
            val done = c.deliveredAt ?: break
            if (k < customers.lastIndex) add(TripStages.Stage.TO_DROPOFF, done)
        }
        return out
    }

    fun trip(t: TripRecord, closedAt: Long?): TripStages.Trip = TripStages.Trip(t.id, t.pkg, t.active,
        if (t.active) TripStages.Stage.of(Platforms.stage(t.phase)) ?: TripStages.Stage.TO_PICKUP else null,
        outcome(t), endedAt(t, closedAt), events(t))

    /** The `stages` object: the open orders (as `courierState.activeTrips`) and the orders ended in the last [TripStages.ENDED_KEEP_MS]. */
    fun of(trips: Collection<TripRecord>, closedAt: (String) -> Long?, now: Long): TripStages {
        val open = trips.filter { it.active }.sortedBy { it.acceptedAt }.takeLast(TripStages.MAX_ACTIVE)
        val ended = trips.filter { !it.active }
            .mapNotNull { t -> endedAt(t, closedAt(t.id))?.takeIf { now - it in 0..TripStages.ENDED_KEEP_MS }?.let { t to it } }
            .sortedBy { it.second }.takeLast(TripStages.MAX_ENDED).map { it.first }
        return TripStages((ended + open).map { trip(it, closedAt(it.id)) })
    }
}
