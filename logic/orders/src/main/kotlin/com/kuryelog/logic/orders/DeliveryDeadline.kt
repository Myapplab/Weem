package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (DeliveryDeadline.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object DeliveryDeadline {
    const val PLATFORM = "platform_dropoff_clock"
    const val OFFER_ESTIMATE = "offer_upper_estimate"
    const val MOVE_MS = 2 * 60_000L
    data class Deadline(val at: Long, val source: String)
    data class Margins(val deadline: Deadline, val atPickupMin: Double?, val atDeliveryMin: Double?)
    fun fromClock(at: Long, minutes: Int): Long = TODO("A3.5")
    fun merge(old: Long?, new: Long): Long = TODO("A3.5")
    fun deadline(t: TripRecord): Deadline? = TODO("A3.5")
    fun margins(t: TripRecord): Margins? = TODO("A3.5")
}

