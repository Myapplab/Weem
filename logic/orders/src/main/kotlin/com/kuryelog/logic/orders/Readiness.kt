package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats Readiness.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * E15 (Faz 4): the restaurant's ready counter. Wolt 2.202.6's "Pickup N min" (offer card, the map on the way to the restaurant)
 * or a "ready in N min" notification counts down from its latest reading ([PickupRecord.readyHintAt], else the first platform
 * estimate [PickupRecord.readyAt]); when the time is up it says "now" (the label says "now" too, it does not mean ready);
 * "Ready" seen on screen or in a notification ([PickupRecord.readyObservedAt]) is ready. The status bar's "📍 N min" chip is
 * not a ready time (the parser never reads it). Pure: the restaurant notification (OrderTracker) and the E14 bubble show it.
 */
object Readiness {
    enum class Kind { READY, NOW, COUNTDOWN, PREPARING, UNKNOWN }
    /** [minutes]: whole minutes left, rounded up, for [Kind.COUNTDOWN] only. */
    data class State(val kind: Kind, val minutes: Int? = null)

    fun of(p: PickupRecord, now: Long): State {
        if (p.readyObservedAt != null) return State(Kind.READY)
        (p.readyHintAt ?: p.readyAt)?.let { at ->
            val m = Math.ceil((at - now) / 60000.0).toInt()
            return if (m > 0) State(Kind.COUNTDOWN, m) else State(Kind.NOW)
        }
        if (p.preparingAt != null) return State(Kind.PREPARING)
        return State(Kind.UNKNOWN)
    }

    /** First line of the restaurant notification (and of the restaurant card). */
    fun headline(s: State, tl: (String, String) -> String): String = when (s.kind) {
        Kind.READY -> tl("✓ HAZIR", "✓ HAZIRDIR")
        Kind.NOW -> tl("Şimdi (süre doldu)", "İndi (vaxt bitdi)")
        Kind.COUNTDOWN -> tl("${s.minutes} dk sonra hazır", "${s.minutes} dəq sonra hazır")
        Kind.PREPARING -> tl("Hazırlanıyor", "Hazırlanır")
        Kind.UNKNOWN -> tl("Hazır olup olmadığı bilinmiyor", "Hazır olub-olmadığı bilinmir")
    }

    /** The E14 bubble's circle: "4 dk", "şimdi", "hazır", "…" while preparing, "?" when nothing was seen. */
    fun short(s: State, tl: (String, String) -> String): String = when (s.kind) {
        Kind.READY -> tl("hazır", "hazır")
        Kind.NOW -> tl("şimdi", "indi")
        Kind.COUNTDOWN -> tl("${s.minutes} dk", "${s.minutes} dəq")
        Kind.PREPARING -> "…"
        Kind.UNKNOWN -> "?"
    }

    /** Start of the first pickup's counter from the offer card's "Pickup N min": its last reading ([OfferState.readyInAt]; after a
     *  restart, the card's last frame) plus N minutes; null when the card showed none. */
    fun fromOffer(offer: OfferState?): Long? = offer?.data?.readyIn?.let { (offer.readyInAt ?: offer.lastSeenAt) + it * 60_000L }
}
