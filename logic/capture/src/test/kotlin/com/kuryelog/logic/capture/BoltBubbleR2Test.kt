package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * R2 (Ekim planı F1.5; user 16 Oct): on Bolt Food's restaurant screen the map's blue bubble "4 min" is the restaurant's
 * remaining preparation time (on Bolt blue is always the restaurant, green the customer). It was never read, so Bolt's
 * restaurant bubble (E15) stayed "?" / "Hazırlanıyor". OCR reads the bubble as one line ("4 min") or two ("4", "min"),
 * before or after the panel. "Arrive in 11 min" (the courier's arrival target) and "~8 min" are other minutes.
 * Layout of CourierFlowTest's Bolt pickup; restaurant and street synthetic.
 */
class BoltBubbleR2Test {
    private val bolt = "com.bolt.food.courier"; private val wolt = "com.wolt.courierapp"
    private fun obs(pkg: String, at: Long, vararg text: String) = CourierParser.parse(pkg, text.toList(), emptyList(), at, "accessibility")
    private val panel = arrayOf("Order is being prepared", "Arrive in 11 min", "Test Kafe", "Test küç. 9", "Orders to pick up:", "2", "#Q3FYM", "Items (0)", "#8P6D2")

    @Test fun bubbleOnOneLineBeforeThePanel() {
        val o = obs(bolt, 1000, "4 min", *panel)
        assertEquals(ScreenStage.PICKUP, o.stage)
        assertEquals(4, o.readyIn)
        assertEquals(11, o.pickupClock) // the arrival target stays what it was
    }

    @Test fun bubbleOnTwoLinesAfterThePanel() {
        assertEquals(4, obs(bolt, 1000, *panel, "4", "min").readyIn)
        assertEquals(7, obs(bolt, 1000, *panel, "7min").readyIn)
    }

    @Test fun arrivedScreenBubble() {
        assertEquals(6, obs(bolt, 1000, "Arrived", "Order is being prepared", "Test Kafe", "Test küç. 9", "6 min").readyIn)
    }

    @Test fun otherMinutesAreNoBubble() {
        assertNull(obs(bolt, 1000, *panel).readyIn)
        assertNull(obs(bolt, 1000, "Order is being prepared", "Arrive in 11 min", "~8 min", "Test Kafe").readyIn)
        assertNull(obs(bolt, 1000, "Order is being prepared", "Arrive in 11 min", "Test Kafe", "Orders to pick up:", "2", "min ago").readyIn)
        // Wolt keeps its own "Pickup N min" rule (D22); a bare "4 min" there is the status-bar chip.
        assertNull(obs(wolt, 1000, "Pickup from", "TEST KAFE", "Test küç. 9", "4 min", "Collect order", "#014", "Collect").readyIn)
    }

}
