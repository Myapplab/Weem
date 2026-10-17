package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (VenueArrival.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
class VenueArrival {
    data class Fix(val at: Long, val lat: Double, val lon: Double, val accuracyM: Double?, val speedKmh: Double?)
    data class Venue(val lat: Double, val lon: Double, val source: String)
    fun add(f: Fix): Unit = TODO("A3.5")
    fun clear(): Unit = TODO("A3.5")
    fun stayStart(lat: Double, lon: Double, from: Long, to: Long, atPickup: Boolean): Long? = TODO("A3.5")
    fun nearAt(lat: Double, lon: Double, from: Long, to: Long, limitM: Double = ArrivalDistance.RESTAURANT_M): Long? = TODO("A3.5")
    fun lastFix(from: Long, to: Long): Fix? = TODO("A3.5")
    fun live(core: DeliveryCore, venue: (TripRecord, Int) -> Venue?, now: Long,
             nav: (TripRecord, Int) -> ArrivalDistance.Source? = { _, _ -> null }): List<CoreEvent> = TODO("A3.5")
    fun customers(core: DeliveryCore, coord: (TripRecord, CustomerRecord) -> Venue?, now: Long,
                  nav: (TripRecord) -> ArrivalDistance.Source? = { null }): List<CoreEvent> = TODO("A3.5")
    fun collected(core: DeliveryCore, tripId: String, index: Int, venue: (TripRecord, Int) -> Venue?, now: Long): List<CoreEvent> = TODO("A3.5")
    companion object {
        const val RADIUS_M = 60.0
        const val STOP_KMH = 3.0
        const val RIDE_KMH = 10.0
        const val DWELL_MS = 20_000L
        const val MAX_ACCURACY_M = 60.0
        const val PICKUP_FIX_MAX_AGE_MS = 5 * 60_000L
        const val VENUE_MISMATCH_M = 150.0
        const val KEEP_MS = 90 * 60_000L
        const val SOURCE_GEOCODE = "gps_dwell_geocode"
        const val SOURCE_YOLGO = "gps_dwell_yolgo_target"
        val COORDINATE_SOURCES: Set<String> get() = TODO("A3.5")
        const val SOURCE_PICKUP_POINT = "gps_dwell_pickup_point"
        const val SOURCE_NEAR_GEOCODE = "gps_near_geocode"
        const val SOURCE_NEAR_YOLGO = "gps_near_yolgo_target"
        fun nearSource(v: Venue): String = TODO("A3.5")
        const val SOURCE_CUSTOMER_NEAR = "gps_near"
        val DWELL_SOURCES: Set<String> get() = TODO("A3.5")
        fun from(t: TripRecord, i: Int): Long = TODO("A3.5")
        fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = TODO("A3.5")
    }
}

