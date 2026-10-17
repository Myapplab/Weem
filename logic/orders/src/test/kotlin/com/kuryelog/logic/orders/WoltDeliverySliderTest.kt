package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
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

    @Test fun aBriefWaitingFrameAfterTheSliderIsNotYetADelivery() {
        val c = DeliveryCore(); c.start(); c.observe(drop(1000))
        c.observe(drop(1500)); c.observe(waiting(2000)); c.observe(waiting(2500))
        assertNull(c.active(pkg)!!.completedAt)
        assertEquals(1, c.trips.size)
    }
    @Test fun swipedSliderThenStableWaitingMapIsADelivery() {
        // A swipe sends no click event: slider on screen, then the waiting map for over a second.
        val c = DeliveryCore(); c.start(); c.observe(drop(1000)); c.observe(waiting(2000))
        val events = c.observe(waiting(3100))
        assertEquals("delivered", events.single().type); assertEquals("delivery_slider_then_waiting", events.single().reason)
        assertNull(c.active(pkg)); assertEquals(TripPhase.FINISHED, c.trips.values.single().phase)
    }
    @Test fun confirmedSliderThenStableDemandMapCompletesOnceAndNewOfferIsIndependent() {
        val c = DeliveryCore(); c.start(); c.observe(drop(1000))
        c.click(pkg, "Order delivered!", 1500)
        assertTrue(c.observe(waiting(2000)).isEmpty())
        val events = c.observe(waiting(2500))
        assertEquals("delivered", events.single().type)
        assertNull(c.active(pkg)); assertEquals(1, c.trips.size)
        assertTrue(c.observe(waiting(3000)).isEmpty())
        c.observe(CourierParser.parse(pkg, listOf("Accept", "Decline", "₼3.00"), listOf("Accept", "Decline"), 4000))
        assertEquals(1, c.pending.size); assertEquals(1, c.trips.size)
    }
    @Test fun incompleteWaitingFrameCannotConfirmDelivery() {
        val c = DeliveryCore(); c.start(); c.observe(drop(1000)); c.click(pkg, "Order delivered!", 1500)
        c.observe(waiting(2000, false)); c.observe(waiting(2500, false))
        assertNull(c.active(pkg)!!.completedAt)
    }
    @Test fun waitingMapLongAfterTheSliderDoesNotConfirmWoltDelivery() {
        val c = DeliveryCore(); c.start(); c.observe(drop(1000))
        val late = 1000 + DeliveryCore.SLIDER_TO_WAITING_MS + 60_000
        c.observe(waiting(late)); c.observe(waiting(late + 2000)); assertNull(c.active(pkg)!!.completedAt)
    }
    @Test fun offlineWithFreshIntentIsNotSuccessfulWoltDelivery() {
        val c = DeliveryCore(); c.start(); c.observe(drop(1000)); c.click(pkg, "Order delivered!", 1500)
        val offline = CourierParser.parse(pkg, listOf("Unable to go online", "Go online", "Delivery demand High"), emptyList(), 2000)
        assertEquals(ScreenStage.OFFLINE, offline.stage)
        c.observe(offline); c.observe(offline.copy(at = 2500))
        assertNull(c.trips.values.single().completedAt)
        assertEquals(TripPhase.END_UNCONFIRMED, c.trips.values.single().phase)
    }
}
