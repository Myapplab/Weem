package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats VenueArrival.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * D9 (saha 11–12 Oct 2026: the restaurant wait was measured on 1 of 23 orders, a Bolt "arrived" status; Wolt shows no
 * arrival on screen, YolGo saw arrival and departure at the same ms). Arrival at a Wolt restaurant from the courier's own GPS:
 * a fix at most [RADIUS_M] from the restaurant below [STOP_KMH], and the courier still within [RADIUS_M] [DWELL_MS] later — or
 * at the pickup, which is made at the restaurant. The departure stays Wolt's "Collect" (the first customer frame after it,
 * [PickupRecord.pickedAt]). Pure: [DeliveryCore.venueArrived] records the arrival.
 *
 * The restaurant's coordinate: E8's navigation target from YolGo (Faz 3, [SOURCE_YOLGO], [YolGoNav.pickupOf]), else the Android
 * geocode of the pickup address ([SOURCE_GEOCODE]). A street-only address ("Mammad Afandiyev, AZ1001 Baku") may land far from
 * the door, and a navigation target may be the wrong place too, so at the pickup the courier's own last fix is the fallback
 * ([SOURCE_PICKUP_POINT]). Fixes worse than [MAX_ACCURACY_M] say
 * nothing; a fix farther than [RADIUS_M] or at riding speed ends a stay (walking in at 5 km/h does not). Fixes live in memory
 * only ([KEEP_MS]).
 */
class VenueArrival {
    /** One GPS fix: [at] on the wall clock of the screen frames, [speedKmh] null when the fix has none (not moving). */
    data class Fix(val at: Long, val lat: Double, val lon: Double, val accuracyM: Double?, val speedKmh: Double?)
    data class Venue(val lat: Double, val lon: Double, val source: String)

    private val fixes = ArrayDeque<Fix>()

    fun add(f: Fix) {
        if (!f.lat.isFinite() || !f.lon.isFinite() || fixes.isNotEmpty() && f.at <= fixes.last().at) return
        fixes.addLast(f)
        while (f.at - fixes.first().at > KEEP_MS) fixes.removeFirst()
    }

    fun clear() = fixes.clear()

    private fun usable(f: Fix) = f.accuracyM == null || f.accuracyM <= MAX_ACCURACY_M

    /**
     * Start of the first stay at ([lat], [lon]) between [from] and [to]: the first fix within [RADIUS_M] below [STOP_KMH], when
     * the following fixes stay within [RADIUS_M], below riding speed ([RIDE_KMH]: a red light passed is no stay; walking in
     * is), for [DWELL_MS]. [atPickup]: [to] is the pickup, so a stay that lasts until the last fix before it (GPS lost inside
     * the restaurant) lasts until [to].
     */
    fun stayStart(lat: Double, lon: Double, from: Long, to: Long, atPickup: Boolean): Long? {
        var start: Long? = null
        for (f in fixes) {
            if (f.at < from) continue
            if (f.at > to) break
            if (!usable(f)) continue
            if (metres(f.lat, f.lon, lat, lon) > RADIUS_M || f.speedKmh != null && f.speedKmh >= RIDE_KMH) { start = null; continue }
            if (start == null) { if (f.speedKmh == null || f.speedKmh < STOP_KMH) start = f.at else continue }
            if (f.at - start >= DWELL_MS) return start
        }
        return start?.takeIf { atPickup && to - it >= DWELL_MS }
    }

    /** M18: the first fix in [from]..[to] at most [ArrivalDistance.RESTAURANT_M] from ([lat], [lon]) (accuracy allowance) below
     *  [ArrivalDistance.SLOW_KMH]: the arrival itself, no dwell needed. */
    fun nearAt(lat: Double, lon: Double, from: Long, to: Long, limitM: Double = ArrivalDistance.RESTAURANT_M): Long? = fixes.firstOrNull { f ->
        f.at in from..to && ArrivalDistance.slow(f.speedKmh) &&
            ArrivalDistance.near(ArrivalDistance.Source(metres(f.lat, f.lon, lat, lon), f.at, ArrivalDistance.GPS, f.accuracyM), limitM)
    }?.at

    /** The courier's last usable fix in [from]..[to], at most [PICKUP_FIX_MAX_AGE_MS] old at [to] (an older one may be another place). */
    fun lastFix(from: Long, to: Long): Fix? = fixes.lastOrNull { it.at in from..to && to - it.at <= PICKUP_FIX_MAX_AGE_MS && usable(it) }

    /**
     * Every fix: the open pickup of each Wolt order gets its arrival. M18/M20 first: a fresh navigation distance ([nav]: YolGo or
     * Google Maps, for this pickup) or the GPS distance to the restaurant at most 5 m while slow — that moment, replacing an earlier
     * dwell arrival. Else D9: the start of a stay at the restaurant coordinate.
     */
    fun live(core: DeliveryCore, venue: (TripRecord, Int) -> Venue?, now: Long,
             nav: (TripRecord, Int) -> ArrivalDistance.Source? = { _, _ -> null }): List<CoreEvent> {
        val out = ArrayList<CoreEvent>()
        for (t in core.trips.values.toList()) {
            if (!t.active || t.pkg !in CourierParser.WOLT) continue
            val i = t.pickups.indexOfLast { it.pickedAt == null }
            if (i < 0) continue
            val p = t.pickups[i]
            val dwelt = p.arrivedAt != null && p.arrivalSource in DWELL_SOURCES
            if (p.arrivedAt != null && !dwelt) continue
            val from = from(t, i)
            val n = nav(t, i)?.takeIf { it.kind != ArrivalDistance.GPS && now - it.at in 0..ArrivalDistance.FRESH_MS && it.at >= from &&
                ArrivalDistance.near(it, ArrivalDistance.RESTAURANT_M) && ArrivalDistance.slow(fixes.lastOrNull()?.speedKmh) }
            val v = venue(t, i)
            val near = n?.let { it.at to it.kind } ?: v?.let { x -> nearAt(x.lat, x.lon, from, now)?.let { it to nearSource(x) } }
            if (near != null) { out += core.venueArrived(t.id, i, near.first, near.second, now, replace = dwelt); continue }
            if (dwelt || v == null) continue
            val at = stayStart(v.lat, v.lon, from, now, atPickup = false) ?: continue
            out += core.venueArrived(t.id, i, at, v.source, now)
        }
        return out
    }

    /**
     * M19/M20: every fix, each order on its way to a customer: a fresh navigation distance ([nav]) or the GPS distance to the
     * customer's coordinate ([coord]) at most 10 m while slow is the arrival at that customer. Only fixes after the leg began count.
     */
    fun customers(core: DeliveryCore, coord: (TripRecord, CustomerRecord) -> Venue?, now: Long,
                  nav: (TripRecord) -> ArrivalDistance.Source? = { null }): List<CoreEvent> {
        val out = ArrayList<CoreEvent>()
        for (t in core.trips.values.toList()) {
            if (!t.active || t.phase != TripPhase.TO_CUSTOMER) continue
            val c = t.customers[t.currentCustomer ?: continue]?.takeIf { it.arrivedAt == null } ?: continue
            val from = (listOfNotNull(t.pickups.lastOrNull()?.pickedAt) + t.customers.values.mapNotNull { it.deliveredAt }).maxOrNull() ?: t.acceptedAt
            val n = nav(t)?.takeIf { it.kind != ArrivalDistance.GPS && now - it.at in 0..ArrivalDistance.FRESH_MS && it.at >= from &&
                ArrivalDistance.near(it, ArrivalDistance.CUSTOMER_M) && ArrivalDistance.slow(fixes.lastOrNull()?.speedKmh) }
            val near = n?.let { it.at to it.kind }
                ?: coord(t, c)?.let { v -> nearAt(v.lat, v.lon, from, now, ArrivalDistance.CUSTOMER_M)?.let { it to SOURCE_CUSTOMER_NEAR } } ?: continue
            out += core.customerArrived(t.id, near.first, near.second, now)
        }
        return out
    }

    /**
     * Pickup [index] of order [tripId] was just collected: without an arrival, the stay before it at the restaurant's coordinate,
     * else at the courier's own position at the pickup. A restaurant coordinate farther than [VENUE_MISMATCH_M] from that
     * position was the wrong place (a geocoded street), so it is not used, and an arrival found with it before is corrected.
     */
    fun collected(core: DeliveryCore, tripId: String, index: Int, venue: (TripRecord, Int) -> Venue?, now: Long): List<CoreEvent> {
        val t = core.trips[tripId]?.takeIf { it.pkg in CourierParser.WOLT } ?: return emptyList()
        val p = t.pickups.getOrNull(index) ?: return emptyList()
        val picked = p.pickedAt ?: return emptyList()
        if (p.arrivedAt != null && p.arrivalSource !in COORDINATE_SOURCES) return emptyList()
        val from = from(t, index)
        val here = lastFix(from, picked)
        val v = venue(t, index)?.takeIf { here == null || metres(it.lat, it.lon, here.lat, here.lon) <= VENUE_MISMATCH_M }
        if (p.arrivedAt != null && v != null) return emptyList()
        val found = v?.let { stayStart(it.lat, it.lon, from, picked, atPickup = true)?.let { at -> at to it.source } }
            ?: here?.let { h -> stayStart(h.lat, h.lon, from, picked, atPickup = true)?.let { at -> at to SOURCE_PICKUP_POINT } }
        return core.venueArrived(t.id, index, found?.first, found?.second, now, replace = true)
    }

    companion object {
        const val RADIUS_M = 60.0
        const val STOP_KMH = 3.0
        const val RIDE_KMH = 10.0
        const val DWELL_MS = 20_000L
        const val MAX_ACCURACY_M = 60.0
        const val PICKUP_FIX_MAX_AGE_MS = 5 * 60_000L
        /** The courier parks within [RADIUS_M] of the door and the last fix may be inside: a right coordinate is closer than this. */
        const val VENUE_MISMATCH_M = 150.0
        const val KEEP_MS = 90 * 60_000L
        const val SOURCE_GEOCODE = "gps_dwell_geocode"
        /** E8: the restaurant coordinate is YolGo's navigation target (the request Wolt's pickup screen sent to YolGo). */
        const val SOURCE_YOLGO = "gps_dwell_yolgo_target"
        /** Arrivals found at a restaurant coordinate that was not the courier's own position: checked again at the pickup. */
        val COORDINATE_SOURCES = setOf(SOURCE_GEOCODE, SOURCE_YOLGO, "gps_near_geocode", "gps_near_yolgo_target")
        const val SOURCE_PICKUP_POINT = "gps_dwell_pickup_point"
        /** M18: at most 5 m from the restaurant coordinate (accuracy allowance) below 10 km/h: the geocode's or YolGo's. */
        const val SOURCE_NEAR_GEOCODE = "gps_near_geocode"
        const val SOURCE_NEAR_YOLGO = "gps_near_yolgo_target"
        fun nearSource(v: Venue) = if (v.source == SOURCE_YOLGO) SOURCE_NEAR_YOLGO else SOURCE_NEAR_GEOCODE
        /** M19: at most 10 m from the customer's coordinate (accuracy allowance) below 10 km/h. */
        const val SOURCE_CUSTOMER_NEAR = "gps_near"
        /** D9 arrivals a later M18 arrival replaces. */
        val DWELL_SOURCES = setOf(SOURCE_GEOCODE, SOURCE_YOLGO, SOURCE_PICKUP_POINT)

        /** A pickup's window starts at the order's first task frame (a stacked order's, after the earlier handover), or the previous pickup. */
        fun from(t: TripRecord, i: Int): Long = if (i <= 0) t.acceptedAt else t.pickups[i - 1].pickedAt ?: t.acceptedAt

        /** Great-circle distance in metres. */
        fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
            val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
            return 2 * 6_371_008.8 * Math.asin(Math.min(1.0, Math.sqrt(a)))
        }
    }
}
