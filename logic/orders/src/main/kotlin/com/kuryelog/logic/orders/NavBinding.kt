package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.RouteStatus

// Rawstats YolGoNav.kt:22-24, 68-108 @ 6388c04 (`pickupOf`, `preferred`, `ScreenTrail`), satır satır. WEEM (ORDERS.md 4.9): YolGo yük
// okuyucusu (`parse`, URI, imza) yok; hedef `RouteStatus.Target` (`NavPort.route`, A2.5 `RouteStatus.fromNavFeed` aynı alanları okur).
// Kayıt dizgileri (`yolgo_venue`, `nav_yolgo`, `gps_*_yolgo_target`) korunur.
/**
 * E8 (Faz 3): the rule that ties the active navigation target (the coordinate a courier app's "navigate" request opened) to a
 * Wolt pickup, so D9 takes the restaurant's coordinate from the target before the address geocode.
 *
 * A navigation request made while Wolt showed an order's pickup screen is that order's restaurant; one made on a customer
 * screen is not (stacked orders: the next order's restaurant is open meanwhile). Without the screen (process restarted) only
 * the navigator's own restaurant guess counts, and only when one pickup is open.
 */
object NavBinding {
    /** The courier app's screen before the request: at most this old, else it says nothing. */
    const val TRAIL_MAX_GAP_MS = 2 * 60_000L

    /**
     * The open Wolt pickup [target] was requested for, as (order, pickup index), or null. Only a courier app's navigation request
     * (origin `intent`) of the order's platform (or of an unknown sender), made between the start of that pickup's leg and its
     * Collect. The screen at the request decides; without one, the navigator's own restaurant guess with a single candidate.
     */
    fun pickupOf(target: RouteStatus.Target, trips: Collection<TripRecord>, trail: ScreenTrail, now: Long): Pair<TripRecord, Int>? {
        if (target.origin != NavOrigin.INTENT || now - target.requestedAtMs !in -60_000L..RouteStatus.TARGET_MAX_AGE_MS) return null
        val candidates = trips.filter { it.active && it.pkg in CourierParser.WOLT && (target.sourcePackage == null || target.sourcePackage == it.pkg) }
            .mapNotNull { t ->
                val i = t.pickups.indices.firstOrNull { i -> target.requestedAtMs in VenueArrival.from(t, i)..(t.pickups[i].pickedAt ?: Long.MAX_VALUE) }
                i?.let { t to it }
            }
        if (candidates.isEmpty()) return null
        val seen = candidates.map { it.first.pkg }.distinct().mapNotNull { pkg ->
            trail.at(pkg, target.requestedAtMs)?.takeIf { target.requestedAtMs - it.at <= TRAIL_MAX_GAP_MS }
        }
        if (seen.any { it.stage == ScreenStage.DROPOFF }) return null
        val onScreen = seen.filter { it.stage == ScreenStage.PICKUP }
        if (onScreen.isNotEmpty()) {
            val strong = candidates.filter { (t, _) -> onScreen.any { it.pkg == t.pkg && (it.tripId == null || it.tripId == t.id) } }
            return strong.singleOrNull()
        }
        return if (target.kind == NavKind.RESTAURANT) candidates.singleOrNull() else null
    }

    /** D9's restaurant coordinate: the navigation target first, the address geocode after it. */
    fun preferred(yolgo: VenueArrival.Venue?, geocode: VenueArrival.Venue?): VenueArrival.Venue? = yolgo ?: geocode
}

/** E8: the courier apps' recent screen stages, only their changes, in memory: which order's card was on screen when. */
class ScreenTrail(private val keepMs: Long = 30 * 60_000L) {
    data class Entry(val at: Long, val pkg: String, val stage: ScreenStage, val tripId: String?)
    private val entries = ArrayDeque<Entry>()

    @Synchronized fun record(at: Long, pkg: String, stage: ScreenStage, tripId: String?) {
        val last = entries.lastOrNull { it.pkg == pkg }
        if (last != null && (at < last.at || last.stage == stage && last.tripId == tripId)) return
        entries.addLast(Entry(at, pkg, stage, tripId))
        while (entries.isNotEmpty() && at - entries.first().at > keepMs) entries.removeFirst()
    }

    /** The stage [pkg] showed at [time]: its last change at or before it. */
    @Synchronized fun at(pkg: String, time: Long): Entry? = entries.lastOrNull { it.pkg == pkg && it.at <= time }

    @Synchronized fun clear() = entries.clear()
}
