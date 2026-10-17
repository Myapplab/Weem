package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/** Visible UI labels only. Synthetic customers; no supplied personal data. */
class WoltDeliverySliderTest {
    private val pkg = "com.wolt.courierapp"
    private fun drop(at: Long, label: String = "Order delivered!", action: Boolean = true) = CourierParser.parse(pkg,
        listOf("Dropoff to", "Test Customer", "Test District", "Address details", "Delivery timeline", label),
        if (action) listOf(label) else emptyList(), at)
    private fun waiting(at: Long, complete: Boolean = true) = CourierParser.parse(pkg,
        listOf("Test Central", "Delivery demand", "High"), emptyList(), at, complete = complete)

    @Test fun sliderLabelIsPendingDeliveryAndNotAnotherOffer() {
        for (label in listOf("Order delivered!", "Order delivered")) {
            for (action in listOf(true, false)) {
                val o = drop(1000, label, action)
                assertEquals(ScreenStage.DROPOFF, o.stage)
                assertFalse(o.delivered)
                assertTrue(CourierParser.isDeliver(label))
            }
        }
    }
    @Test fun waitingContextDoesNotOverrideAnActiveDeliveryOrProveDispatchAvailability() {
        val o = CourierParser.parse(pkg, drop(1000).raw + listOf("Delivery demand", "High"), listOf("Order delivered!"), 1000)
        assertEquals(ScreenStage.DROPOFF, o.stage); assertFalse(o.delivered)
        assertEquals(ScreenStage.IDLE, waiting(2000).stage)
        assertEquals(ScreenStage.UNKNOWN, CourierParser.parse(pkg, listOf("High"), emptyList(), 3000).stage)
    }
    @Test fun bothKnownWoltPackagesUseTheSameWaitingAndSliderRules() {
        for (p in listOf("com.wolt.courierapp", "com.wolt.partner")) {
            assertEquals(ScreenStage.IDLE, waiting(1000).let { CourierParser.parse(p, it.raw, it.actions, it.at) }.stage)
            val o = drop(2000, "Order delivered", false).let { CourierParser.parse(p, it.raw, it.actions, it.at) }
            assertEquals(ScreenStage.DROPOFF, o.stage); assertFalse(o.delivered)
        }
    }
}
