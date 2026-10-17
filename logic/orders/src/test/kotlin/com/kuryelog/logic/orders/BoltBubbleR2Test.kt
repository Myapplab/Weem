package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
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

    @Test fun restaurantBubbleCountsDownFromTheBlueBubble() {
        val c = DeliveryCore().also { it.start() }
        c.observe(obs(bolt, 60_000, "4 min", *panel))
        val p = c.active(bolt)!!.pickups.single()
        assertEquals(Readiness.State(Readiness.Kind.COUNTDOWN, 4), Readiness.of(p, 60_000))
        c.observe(obs(bolt, 120_000, "3 min", *panel)) // the latest reading corrects it
        assertEquals(Readiness.State(Readiness.Kind.COUNTDOWN, 3), Readiness.of(c.active(bolt)!!.pickups.single(), 120_000))
    }
}
