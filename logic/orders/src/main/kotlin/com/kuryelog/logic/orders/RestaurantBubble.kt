package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (RestaurantBubble.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.6'da.
object RestaurantBubble {
    const val SHOW_M = 50.0
    const val HIDE_M = 150.0
    const val FIX_MAX_AGE_MS = 15_000L
    const val FIX_MAX_ACCURACY_M = 40.0
    const val WIDTH_KEY = "restaurantBubbleWidthPct"
    const val DEFAULT_WIDTH_PCT = 90
    val WIDTH_CHOICES: List<Int> get() = TODO("A3.6")
    fun distance(fixAt: Long, lat: Double, lon: Double, accuracyM: Double?, venue: VenueArrival.Venue?, now: Long): Double? = TODO("A3.6")
    fun visible(shown: Boolean, distanceM: Double?, arrived: Boolean, collected: Boolean): Boolean = TODO("A3.6")
    fun widthPct(stored: Int): Int = TODO("A3.6")
    fun widthPx(screenPx: Int, pct: Int): Int = TODO("A3.6")
    fun scale(pct: Int): Float = TODO("A3.6")
}

