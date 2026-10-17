package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * R1 (Ekim planı F1.4; screenshot 16 Oct 22:30): two Wolt orders for the same customer. Wolt shows one customer card
 * "2 dropoffs to" + name + "Orders #072, #387" and a delivery timeline with both "Pickup from" rows ticked. Rawstats'
 * badge said "Wolt · Restoranda bekleniyor" and "Wolt · Müşteriye gidiliyor" at once: the second order stayed at its
 * restaurant although it was picked up.
 *
 * Causes: "2 dropoffs to" was no customer-card header (the timeline's "Pickup from" made the frame a restaurant screen),
 * and Wolt's 3-character order numbers never key customers (K10), so the card reached only the newest order.
 * The 3-character numbers now match orders inside this one card only; the K10 key rule does not change.
 *
 * The user's export of that day did not come (SORU-7): the frames follow the screenshot's line layout, in two OCR orders.
 * Names, addresses, restaurants and numbers are made up.
 */
class WoltSameCustomerR1Test {
    private val wolt = "com.wolt.courierapp"
    private fun obs(at: Long, vararg text: String, actions: List<String> = emptyList()) =
        CourierParser.parse(wolt, text.toList(), actions, at, "accessibility")

    private fun waiting(at: Long) = obs(at, "Baku Central", "Delivery demand", "Medium")
    private fun offer(at: Long, restaurant: String, fee: String) = obs(at, "Decline", "Ready in 4 min", fee,
        "2 stops (3.1 km) • 10–18 min", restaurant, "Test küç. 3, Baku, AZ1000", "Customer drop-off", "Test küçəsi 5, Bakı", "Accept",
        actions = listOf("Decline", "Accept"))
    private fun pickup(at: Long, restaurant: String, id: String) = obs(at, "Pickup from", restaurant, "Test küç. 3, AZ1000 Baku",
        "Collect order", id, "Test M.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        actions = listOf("Collect"))
    /** The two-order customer card: the timeline's ticks after (A) or before (B) the restaurant line. */
    private fun card(at: Long, tickFirst: Boolean = false, slider: Boolean = true): CourierObservation {
        fun row(r: String) = if (tickFirst) listOf("✓", "Pickup from", r) else listOf("Pickup from", r, "✓")
        val text = listOf("2 dropoffs to", "Test M.", "Test küçəsi 5, Bakı", "Orders #072, #387", "Address details", "Blok A",
            "Delivery timeline") + row("Test Doner") + row("Test Pizza") + listOf("Difficulties with the delivery?", "Send a message") +
            (if (slider) listOf("Order delivered!") else emptyList())
        return obs(at, *text.toTypedArray(), actions = if (slider) listOf("Send a message", "Order delivered!") else listOf("Send a message"))
    }

    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(text: String, at: Long) { events += core.click("com.wolt.courierapp", text, at) }
    }

    /** Order 1 (#072) from Test Doner, then order 2 (#387) from Test Pizza, both for Test M. */
    private fun twoOrdersPickedUp(): Run {
        val r = Run(DeliveryCore().also { it.start() })
        r.see(waiting(0)); r.see(waiting(1000))
        r.see(offer(10_000, "Test Doner", "₼3.10")); r.tap("Accept", 12_000)
        r.see(pickup(13_000, "TEST DONER", "#072")); r.see(pickup(300_000, "TEST DONER", "#072"))
        r.see(offer(310_000, "Test Pizza", "₼2.40")); r.tap("Accept", 312_000)
        r.see(pickup(313_000, "TEST PIZZA", "#387")); r.see(pickup(600_000, "TEST PIZZA", "#387"))
        assertEquals(2, r.core.trips.size)
        return r
    }
    private fun open(r: Run) = r.core.trips.values.filter { it.active }

    @Test fun twoDropoffsHeaderIsTheCustomerCard() {
        for (tickFirst in listOf(false, true)) {
            val o = card(900_000, tickFirst)
            assertEquals(ScreenStage.DROPOFF, o.stage)
            assertEquals("Test M.", o.customer.name)
            assertEquals("Test küçəsi 5, Bakı", o.customer.address)
            assertTrue(o.orderIds.containsAll(listOf("#072", "#387")))
            assertTrue(DeliveryCore.keyIds(o).isEmpty()) // K10 unchanged: 3 characters key nobody
        }
        assertEquals(ScreenStage.DROPOFF, card(900_000, slider = false).stage)
    }

    @Test fun bothOrdersGoToTheCustomerAfterTheTwoOrderCard() {
        for (tickFirst in listOf(false, true)) {
            val r = twoOrdersPickedUp()
            r.see(card(900_000, tickFirst)); r.see(card(901_000, tickFirst))
            val trips = open(r)
            assertEquals(2, trips.size)
            for (t in trips) {
                assertTrue("${t.id} ${t.phase}", t.atCustomer)
                assertNotNull(t.pickups.single().pickedAt)
                assertEquals("Test M.", t.customers[t.currentCustomer]!!.details.name)
            }
            assertEquals(setOf("TEST DONER", "TEST PIZZA"), trips.map { it.pickups.single().restaurant }.toSet())
            // Neither order is closed as "the next order is at the customer" (K34): they are one handover.
            assertTrue(r.events.none { it.type == "delivered_inferred" })
        }
    }

    @Test fun cardWithoutTheSliderAlsoMovesBoth() {
        val r = twoOrdersPickedUp()
        r.see(card(900_000, slider = false))
        assertTrue(open(r).all { it.atCustomer && it.pickups.single().pickedAt != null })
    }

    @Test fun sliderThenWaitingDeliversBothOrders() {
        val r = twoOrdersPickedUp()
        r.see(card(900_000)); r.see(card(901_000))
        r.see(waiting(904_000)); r.see(waiting(906_000))
        assertTrue(r.core.trips.values.all { it.completedAt != null })
        assertEquals(2, r.events.count { it.type == "delivered" })
        assertTrue(r.events.none { it.type == "end_unconfirmed" || it.type == "delivered_inferred" })
    }

    @Test fun anOrderNotOnTheCardStaysWhereItIs() {
        val r = Run(DeliveryCore().also { it.start() })
        r.see(waiting(0)); r.see(waiting(1000))
        r.see(offer(10_000, "Test Doner", "₼3.10")); r.tap("Accept", 12_000)
        r.see(pickup(13_000, "TEST DONER", "#555"))
        r.see(offer(310_000, "Test Pizza", "₼2.40")); r.tap("Accept", 312_000)
        r.see(pickup(313_000, "TEST PIZZA", "#387"))
        r.see(card(900_000))
        val first = r.core.trips.values.first()
        assertFalse(first.atCustomer) // #555 is not on the "Orders #072, #387" card
        assertNull(first.pickups.single().pickedAt)
    }
}
