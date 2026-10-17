package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.RouteStatus

// İskelet (A3.2): Rawstats `YolGoNav.pickupOf/preferred` ve `ScreenTrail` (YolGoNav.kt:24, 68-108 @ 6388c04). WEEM'de YolGo yük okuyucusu yok:
// hedef `RouteStatus.Target` (NavPort.route, ORDERS.md 4.9). Kayıt dizgileri (`yolgo_venue`, `nav_yolgo`, `gps_*_yolgo_target`) korunur. Gövdeler A3.5'te.
object NavBinding {
    const val TRAIL_MAX_GAP_MS = 2 * 60_000L
    /** Hedefin (kurye uygulamasının "Navigate" isteği) hangi siparişin hangi alışına ait olduğu; yok = bağlanmaz. */
    fun pickupOf(target: RouteStatus.Target, trips: Collection<TripRecord>, trail: ScreenTrail, now: Long): Pair<TripRecord, Int>? = TODO("A3.5")
    fun preferred(yolgo: VenueArrival.Venue?, geocode: VenueArrival.Venue?): VenueArrival.Venue? = TODO("A3.5")
}

/** E8: the courier apps' recent screen stages, only their changes, in memory: which order's card was on screen when. */
class ScreenTrail(private val keepMs: Long = 30 * 60_000L) {
    data class Entry(val at: Long, val pkg: String, val stage: ScreenStage, val tripId: String?)
    fun record(at: Long, pkg: String, stage: ScreenStage, tripId: String?): Unit = TODO("A3.5")
    /** The stage [pkg] showed at [time]: its last change at or before it. */
    fun at(pkg: String, time: Long): Entry? = TODO("A3.5")
    fun clear(): Unit = TODO("A3.5")
}
