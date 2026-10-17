package com.kuryelog.logic.capture

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
    // ---- node rule (pure) ----

    @Test fun onlyTheProgressBarClassIsASpinner() {
        assertTrue(SliderSpinner.isSpinnerClass("android.widget.ProgressBar"))
        assertTrue(SliderSpinner.isSpinnerClass("ProgressBar"))
        for (c in listOf(null, "", "android.widget.TextView", "android.view.View", "android.widget.SeekBar")) assertFalse(c.toString(), SliderSpinner.isSpinnerClass(c))
    }
    @Test fun aRoundIndicatorOnTheSliderRowCountsALinearBarDoesNot() {
        val label = SliderSpinner.Box(300, 2100, 780, 2160)
        val onRow = SliderSpinner.Box(510, 2095, 570, 2155)
        val farAbove = SliderSpinner.Box(510, 900, 570, 960)
        val linear = SliderSpinner.Box(0, 2120, 1080, 2130)
        assertTrue(SliderSpinner.onSlider(listOf(onRow), listOf(label)))
        assertFalse(SliderSpinner.onSlider(listOf(farAbove), listOf(label)))
        assertFalse(SliderSpinner.onSlider(listOf(linear), listOf(label)))
        assertFalse(SliderSpinner.onSlider(emptyList(), listOf(label)))
        assertFalse(SliderSpinner.onSlider(listOf(SliderSpinner.Box(5, 5, 5, 5)), emptyList()))
        // The spinner may replace the label: then only the core's time window decides.
        assertTrue(SliderSpinner.onSlider(listOf(onRow), emptyList()))
    }

}
