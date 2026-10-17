package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats DeliveryDeadline.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * K29: how much time was left to deliver (a) when the order was picked up and (b) when it was delivered (negative = late).
 * The deadline is Bolt's own "Drop off in N min" turned into a clock time ([CustomerRecord.deadlineAt], source
 * [PLATFORM]); without it (Wolt shows no customer deadline) the offer's upper estimate ("11–19 min" → accepted offer + 19 min,
 * source [OFFER_ESTIMATE]) is used and labelled as such. Nothing known → null, never a guess.
 */
object DeliveryDeadline {
    const val PLATFORM = "platform_dropoff_clock"
    const val OFFER_ESTIMATE = "offer_upper_estimate"
    /** A later frame moves the stored deadline only when the platform changed it, not for a countdown's rounding. */
    const val MOVE_MS = 2 * 60_000L

    data class Deadline(val at: Long, val source: String)
    data class Margins(val deadline: Deadline, val atPickupMin: Double?, val atDeliveryMin: Double?)

    /** The deadline clock time of a "Drop off in [minutes] min" frame seen at [at]. */
    fun fromClock(at: Long, minutes: Int): Long = at + minutes * 60_000L

    /** Keeps [old] unless the new reading differs by more than [MOVE_MS]. */
    fun merge(old: Long?, new: Long): Long = if (old == null || kotlin.math.abs(new - old) > MOVE_MS) new else old

    fun deadline(t: TripRecord): Deadline? {
        t.customers.values.mapNotNull { it.deadlineAt }.maxOrNull()?.let { return Deadline(it, PLATFORM) }
        val o = t.offer ?: return null
        val hi = o.data.minHi ?: return null
        return Deadline(o.firstAt + hi * 60_000L, OFFER_ESTIMATE)
    }

    fun margins(t: TripRecord): Margins? {
        val d = deadline(t) ?: return null
        val picked = t.pickups.mapNotNull { it.pickedAt }.maxOrNull()
        val delivered = t.completedAt ?: t.customers.values.mapNotNull { it.deliveredAt }.maxOrNull()
        return Margins(d, picked?.let { (d.at - it) / 60_000.0 }, delivered?.let { (d.at - it) / 60_000.0 })
    }
}
