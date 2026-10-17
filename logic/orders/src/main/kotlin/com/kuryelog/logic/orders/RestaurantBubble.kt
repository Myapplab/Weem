package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats RestaurantBubble.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * E14 (Faz 4): the restaurant bubble's rule. It opens [SHOW_M] from the restaurant in a straight line, or when the arrival was
 * seen (the platform's "Arrived", the D9 GPS stay), and closes at Collect or once the courier is more than [HIDE_M] away. This
 * replaced the old restaurant trigger (1,000 m on the navigation app's route, 700 m straight, the app's ≤3 min). The restaurant's
 * coordinate is D9's ([YolGoNav.preferred]: YolGo's navigation target, else the address geocode); only a fresh fix of known
 * accuracy measures. Size: [DEFAULT_WIDTH_PCT] of the screen width, chosen from [WIDTH_CHOICES] under [WIDTH_KEY]. Pure.
 */
object RestaurantBubble {
    const val SHOW_M = 50.0
    const val HIDE_M = 150.0
    const val FIX_MAX_AGE_MS = 15_000L
    const val FIX_MAX_ACCURACY_M = 40.0
    const val WIDTH_KEY = "restaurantBubbleWidthPct"
    const val DEFAULT_WIDTH_PCT = 90
    val WIDTH_CHOICES = listOf(70, 80, 90, 100)

    /** Straight-line metres from the fix to [venue], or null without a venue or a fix that is fresh ([FIX_MAX_AGE_MS]) and
     *  accurate ([FIX_MAX_ACCURACY_M]); [fixAt] and [now] on the wall clock. */
    fun distance(fixAt: Long, lat: Double, lon: Double, accuracyM: Double?, venue: VenueArrival.Venue?, now: Long): Double? {
        if (venue == null || accuracyM == null || accuracyM > FIX_MAX_ACCURACY_M || now - fixAt !in 0..FIX_MAX_AGE_MS) return null
        return VenueArrival.metres(lat, lon, venue.lat, venue.lon)
    }

    /** Whether the bubble of an open pickup is on screen after this check; [shown]: it is now. Without a distance nothing new
     *  opens and an open bubble stays. */
    fun visible(shown: Boolean, distanceM: Double?, arrived: Boolean, collected: Boolean): Boolean = when {
        collected -> false
        arrived -> true
        distanceM == null -> shown
        shown -> distanceM <= HIDE_M
        else -> distanceM <= SHOW_M
    }

    /** The stored width choice, or the default for a value that is not one of [WIDTH_CHOICES]. */
    fun widthPct(stored: Int): Int = stored.takeIf { it in WIDTH_CHOICES } ?: DEFAULT_WIDTH_PCT
    fun widthPx(screenPx: Int, pct: Int): Int = Math.round(screenPx * pct / 100f)
    /** Text and height follow the width: 1 at the default width. */
    fun scale(pct: Int): Float = pct / DEFAULT_WIDTH_PCT.toFloat()
}
