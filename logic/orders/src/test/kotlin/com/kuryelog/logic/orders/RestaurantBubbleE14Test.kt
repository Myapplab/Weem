package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * E14 (Faz 4): the restaurant bubble. A horizontal bubble (90 % of the screen width by default, size adjustable) with the
 * restaurant's name on the left and the E15 ready state in a circle on the right. It appears 50 m from the restaurant in a
 * straight line (or at the arrival the platform or the D9 GPS stay saw) and stays until Collect or until the courier is 150 m
 * away. It replaces the old trigger (1,000 m on the route / 700 m straight, OrderTracker ROUTE_TRIGGER_M / STRAIGHT_TRIGGER_M).
 * Synthetic positions (no real place).
 */
class RestaurantBubbleE14Test {
    private val venue = VenueArrival.Venue(40.4000, 49.8500, VenueArrival.SOURCE_GEOCODE)
    private val now = 1_791_295_200_000L
    /** [north] metres north of the venue (1° latitude = 111 195 m). */
    private fun at(north: Double) = venue.lat + north / 111_195.08

    @Test fun showsAt50mStaysUntil150m() {
        var shown = false
        for ((d, want) in listOf(300.0 to false, 120.0 to false, 51.0 to false, 49.0 to true, 100.0 to true, 149.0 to true, 151.0 to false, 60.0 to false, 45.0 to true)) {
            shown = RestaurantBubble.visible(shown, d, arrived = false, collected = false)
            assertEquals("at $d m", want, shown)
        }
    }

    @Test fun collectClosesItAndArrivalOpensIt() {
        assertFalse(RestaurantBubble.visible(true, 10.0, arrived = true, collected = true))
        // Arrival seen (platform "Arrived" or the D9 GPS stay): at the restaurant, whatever the (maybe wrong) geocode says.
        assertTrue(RestaurantBubble.visible(false, null, arrived = true, collected = false))
        assertTrue(RestaurantBubble.visible(false, 400.0, arrived = true, collected = false))
        // No coordinate or no fresh fix: nothing new opens, an open bubble stays.
        assertFalse(RestaurantBubble.visible(false, null, arrived = false, collected = false))
        assertTrue(RestaurantBubble.visible(true, null, arrived = false, collected = false))
    }

    @Test fun onlyAFreshAccurateFixMeasures() {
        assertEquals(40.0, RestaurantBubble.distance(now - 2_000, at(40.0), venue.lon, 8.0, venue, now)!!, 0.5)
        assertNull(RestaurantBubble.distance(now - 20_000, at(40.0), venue.lon, 8.0, venue, now))   // older than 15 s
        assertNull(RestaurantBubble.distance(now - 2_000, at(40.0), venue.lon, 60.0, venue, now))   // worse than 40 m
        assertNull(RestaurantBubble.distance(now - 2_000, at(40.0), venue.lon, null, venue, now))   // accuracy unknown
        assertNull(RestaurantBubble.distance(now - 2_000, at(40.0), venue.lon, 8.0, null, now))     // no restaurant coordinate
    }

    @Test fun widthIsNinetyPercentByDefaultAndAdjustable() {
        assertEquals(90, RestaurantBubble.DEFAULT_WIDTH_PCT)
        assertEquals(972, RestaurantBubble.widthPx(1080, RestaurantBubble.DEFAULT_WIDTH_PCT))
        assertEquals(listOf(70, 80, 90, 100), RestaurantBubble.WIDTH_CHOICES)
        // Text and height follow the width (90 % = scale 1).
        assertEquals(1.0f, RestaurantBubble.scale(90), 1e-6f)
        assertEquals(70f / 90f, RestaurantBubble.scale(70), 1e-6f)
        // A stored value outside the choices falls back to the default.
        assertEquals(90, RestaurantBubble.widthPct(55)); assertEquals(100, RestaurantBubble.widthPct(100))
    }

    // ------------------------------------------------------------------ wiring (source checks; the overlay is telefonda denenmedi)
    // WEEM (A3.7): `OrderTracker.kt` → `logic/orders` `OrderFlow.kt`, `DeliveryReminder.kt` → Android `:orders`. The width setting
    // (`RestaurantBubble.WIDTH_KEY` in Ayarlar → Ekran) is checked when the settings screen is written (A3.8).
    private fun flow() = RepoSource.text("logic/orders/src/main/kotlin/com/kuryelog/logic/orders/OrderFlow.kt")

    @Test fun oldRestaurantTriggerIsReplaced() {
        val tracker = flow()
        val loop = tracker.substringAfter("// E14: restaurant bubble").substringBefore("val r = reminder")
        assertTrue(loop.contains("RestaurantBubble.visible("))
        assertFalse(loop.contains("ROUTE_TRIGGER_M")); assertFalse(loop.contains("STRAIGHT_TRIGGER_M")); assertFalse(loop.contains("travelEta"))
        assertFalse(tracker.contains("Restorana rotada yaklaşık"))
        // The open bubble closes at 150 m too, and a bubble the courier closed stays closed for that pickup.
        assertTrue(tracker.substringAfter("val r = reminder").contains("RestaurantBubble.visible(true,"))
        assertTrue(tracker.contains("bubbleDismissed"))
    }

    @Test fun overlayShowsTheBubbleWithNameAndReadyCircle() {
        val ui = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/DeliveryReminder.kt")
        assertTrue(ui.contains("private fun showBubble("))
        assertTrue(ui.contains("Readiness.short("))
        assertTrue(ui.contains("RestaurantBubble.widthPx("))
        assertTrue(ui.contains("GradientDrawable.OVAL"))
        assertTrue(ui.contains("RestaurantBubble.WIDTH_KEY"))
    }

}
