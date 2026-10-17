package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * K33 (docs/KURYELOG_ISTEKLER.md): after Wolt's `Order delivered` slider is swiped, a spinner appears in the middle of the
 * slider, often too fast to be seen. It is extra evidence only: never a delivery by itself, never a confirmed delivery out
 * of a K34 inference (it may only upgrade the inference reason).
 *
 * Labels: Wolt customer card ("Dropoff to" … "Order delivered!"), offer E5, pickup E8 and waiting sheet E6 as in
 * CustomerStageK34Test / WoltDeliverySliderTest. No recorded frame shows the spinner: its node is modelled on the
 * accessibility class `android.widget.ProgressBar`; bounds are synthetic. Names and addresses are synthetic.
 */
class WoltSliderSpinnerK33Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) =
        CourierParser.parse(pkg, text.toList(), actions, at)
    private fun woltWaiting(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    private fun woltOffer(at: Long, fee: String = "₼3.94", restaurant: String = "Test Restoran") = obs(wolt, at, "Decline", "Ready in 1 min", fee,
        "2 stops (4.2 km) • 11–19 min", restaurant, "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept",
        actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long, restaurant: String = "TEST RESTORAN") = obs(wolt, at, "Pickup from", restaurant, "Test küç. 1, AZ1010 Baku", "Notes",
        "Test tarif", "Translate", "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?",
        "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long, name: String, slider: Boolean = true) = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS",
        "Address details", "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message",
        *(if (slider) arrayOf("Order delivered!") else emptyArray()), actions = listOf("Send a message") + (if (slider) listOf("Order delivered!") else emptyList()))
    /** The same card with the spinner flag the accessibility reader sets (OrderTracker.onScreen copies it in). */
    private fun spinning(o: CourierObservation) = o.copy(deliverSpinner = true)

    private class Run(val core: DeliveryCore) {
        val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) { events += core.observe(o) }
        fun tap(pkg: String, text: String, at: Long) { events += core.click(pkg, text, at) }
    }
    private fun run(): Run = Run(DeliveryCore().also { it.start() })
    private fun firstWoltOrderAtCustomer(r: Run) {
        r.see(woltWaiting(0)); r.see(woltWaiting(1000))
        r.see(woltOffer(10_000)); r.see(woltOffer(11_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(woltCard(600_000, "Customer A")); r.see(woltCard(601_000, "Customer A"))
    }
    private fun nextWoltOrder(r: Run) {
        r.see(woltOffer(602_000, fee = "₼2.91", restaurant = "Other Doner")); r.tap(wolt, "Accept", 605_000)
        r.see(woltPickup(606_000, restaurant = "OTHER DONER")); r.see(woltCard(1_200_000, "Customer B"))
    }

    // ---- core ----

    @Test fun theSpinnerAloneNeverDeliversAnOrder() {
        val r = run(); firstWoltOrderAtCustomer(r); val t = r.core.active(wolt)!!
        r.see(spinning(woltCard(601_400, "Customer A", slider = false)))
        val e = r.events.single { it.type == "delivery_spinner_seen" }
        assertEquals(t.id, e.tripId); assertEquals("after_delivery_slider", e.reason); assertEquals(601_400L, t.deliverSpinnerAt)
        assertTrue(r.events.none { it.type == "delivered" || it.type == "customer_delivered" || it.type == "delivered_inferred" })
        assertTrue(t.active); assertNull(t.completedAt); assertNull(t.customers.values.single().deliveredAt)
    }
    @Test fun aSpinnerWithoutASliderOrTooLateOrFromBoltIsIgnored() {
        val r = run(); r.see(woltWaiting(0)); r.see(woltOffer(10_000)); r.tap(wolt, "Accept", 14_000); r.see(woltPickup(15_000))
        r.see(spinning(woltPickup(16_000)))
        r.see(woltCard(600_000, "Customer A", slider = false)); r.see(spinning(woltCard(601_000, "Customer A", slider = false)))
        assertTrue(r.events.none { it.type == "delivery_spinner_seen" })
        r.see(woltCard(602_000, "Customer A")); r.see(spinning(woltCard(602_000 + DeliveryCore.SPINNER_AFTER_SLIDER_MS + 1, "Customer A", slider = false)))
        assertTrue(r.events.none { it.type == "delivery_spinner_seen" })
        val b = run(); b.see(spinning(obs(bolt, 1000, "Drop off in -4 min", "Test C. #UWG2Q", "Delivered", "Slide to confirm")))
        assertTrue(b.events.none { it.type == "delivery_spinner_seen" })
    }
    @Test fun oneEvidenceEventPerSlider() {
        val r = run(); firstWoltOrderAtCustomer(r)
        r.see(spinning(woltCard(601_300, "Customer A"))); r.see(spinning(woltCard(601_600, "Customer A", slider = false)))
        assertEquals(1, r.events.count { it.type == "delivery_spinner_seen" })
    }
    @Test fun sliderSpinnerThenWaitingMapIsTheSameDeliveryAsBefore() {
        val r = run(); firstWoltOrderAtCustomer(r); val t = r.core.active(wolt)!!
        r.see(spinning(woltCard(601_400, "Customer A", slider = false)))
        r.see(woltWaiting(602_000)); r.see(woltWaiting(603_100))
        val d = r.events.single { it.type == "delivered" }
        assertEquals("delivery_slider_then_waiting", d.reason); assertEquals(TripPhase.FINISHED, t.phase)
        assertEquals(601_400L, t.deliverSpinnerAt)
    }
    @Test fun k34InferenceGetsTheSpinnerReasonButStaysUnverified() {
        val r = run(); firstWoltOrderAtCustomer(r); val first = r.core.active(wolt)!!
        r.see(spinning(woltCard(601_400, "Customer A", slider = false)))
        nextWoltOrder(r)
        val e = r.events.single { it.type == "delivered_inferred" }
        // D3(b): the next order's restaurant screen closes it (before its customer card would).
        assertEquals(first.id, e.tripId); assertEquals("next_order_at_restaurant_after_slider_spinner", e.reason)
        assertEquals(TripPhase.END_UNCONFIRMED, first.phase); assertNull(first.completedAt)
        assertTrue(r.events.none { it.type == "delivered" && it.tripId == first.id })
        assertEquals("next_order_at_restaurant_after_slider_spinner", first.deliveredInferredReason)
    }
    @Test fun k34InferenceWithoutSpinnerKeepsItsReason() {
        val r = run(); firstWoltOrderAtCustomer(r); nextWoltOrder(r)
        assertEquals("next_order_at_restaurant_after_slider", r.events.single { it.type == "delivered_inferred" }.reason)
    }
    @Test fun spinnerFieldsAreWrittenOnlyWhenSeenAndReadBack() {
        val r = run(); firstWoltOrderAtCustomer(r); val t = r.core.active(wolt)!!
        val before = RecordJson.trip(t)
        assertFalse(before.has("deliver_spinner_at")); assertFalse(before.has("deliver_spinner_for"))
        assertNull(RecordJson.trip(before).deliverSpinnerAt)
        r.see(spinning(woltCard(601_400, "Customer A", slider = false)))
        val j = RecordJson.trip(t)
        assertEquals(601_400L, j.getLong("deliver_spinner_at"))
        val back = RecordJson.trip(j)
        assertEquals(601_400L, back.deliverSpinnerAt); assertEquals(t.deliverSpinnerFor, back.deliverSpinnerFor); assertTrue(back.spinnerOnSlider)
    }
}
