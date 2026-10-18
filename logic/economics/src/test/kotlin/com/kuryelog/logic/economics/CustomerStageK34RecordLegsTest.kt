package com.kuryelog.logic.economics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats CustomerStageK34RecordTest.kt @ 6388c04 — ekonomi payı (1 test, `legsRowIsNeverADelivery`); öbürleri logic/orders'ta (ORDERS.md 15.1).

/**
 * K34: an order closed because the next one reached the customer stage is an unverified delivery (`delivered_inferred`).
 * It keeps its own flag in the trip record (orders.jsonl / courier.db), its own orders.jsonl event type, and an
 * economics-legs row that is never a delivery (tripLegs v1 has no such outcome: "unknown", not calibrated on).
 * Labels as in [CustomerStageK34Test]; names and addresses synthetic.
 * `KL_ROWS_OUT=<dir>` dumps the legs rows for `docs/ekonomi-v2/araclar/validate_rows.py`.
 */
class CustomerStageK34RecordLegsTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)
    private fun woltOffer(at: Long) = obs(wolt, at, "Decline", "Ready in 1 min", "₼3.94", "2 stops (4.2 km) • 11–19 min", "Test Restoran",
        "Test küç. 1, Baku, AZ1025", "Customer drop-off", "40°22'00.0\"N 49°50'00.0\"E, Baku", "Accept", actions = listOf("Decline", "Accept"))
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Test tarif", "Translate",
        "Collect order", "#014", "Test A.", "ORDER DETAILS", "1 x Test", "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders",
        actions = listOf("Collect", "Cancel this delivery"))
    private fun woltCard(at: Long) = obs(wolt, at, "Dropoff to", "Customer A", "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details", "Order details",
        "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!", actions = listOf("Send a message", "Order delivered!"))
    private fun boltOffer(at: Long) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltCard(at: Long) = obs(bolt, at, "Drop off in -4 min", "Test C. #UWG2Q", "Items (3)", "Address", "Test 5, Bakı", "Instructions",
        "Meet at the door", "Call", "Chat", "Delivery issues?", "Delivered", "Slide to confirm")

    private class Day(val core: DeliveryCore, val wolt: TripRecord, val bolt: TripRecord, val inferredEvent: CoreEvent, val rows: List<EconomicsRecorder.Row>)
    /** A Wolt order at its customer, then a Bolt order at its customer; every core event goes through the economics recorder. */
    private fun day(): Day {
        val c = DeliveryCore(); c.start(); val rec = EconomicsRecorder(); val rows = ArrayList<EconomicsRecorder.Row>(); val events = ArrayList<CoreEvent>()
        fun see(o: CourierObservation) {
            val before = c.pending.values.associateBy { it.id }; val e = c.observe(o); events += e
            rows += rec.frame(o, e, before, c, emptyList(), OfferGeo.UNKNOWN) { "unknown" }
        }
        fun tap(pkg: String, text: String, at: Long) {
            val before = c.pending.values.associateBy { it.id }; val e = c.click(pkg, text, at); events += e
            rows += rec.events(at, e, before, c, emptyList(), { "unknown" })
        }
        see(woltOffer(10_000)); tap(wolt, "Accept", 14_000); see(woltPickup(15_000)); see(woltCard(600_000)); see(woltCard(601_000))
        val w = c.active(wolt)!!
        see(boltOffer(610_000)); tap(bolt, "22 min, 4,49 ₼", 612_000); see(obs(bolt, 613_000, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99"))
        see(boltCard(1_200_000))
        return Day(c, w, c.active(bolt)!!, events.single { it.type == "delivered_inferred" }, rows)
    }
    private fun dump(rows: List<JSONObject>) {
        val dir = System.getenv("KL_ROWS_OUT") ?: return
        val f = java.io.File(dir).apply { mkdirs() }.resolve("CustomerStageK34RecordTest.jsonl")
        rows.forEach { f.appendText(JSONObject().put("def", "tripLegs").put("row", it).toString() + "\n") }
    }

    @Test fun legsRowIsNeverADelivery() {
        val d = day()
        val legs = d.rows.filter { it.file == EconomicsLogs.LEGS }.map { it.json }
        val row = legs.single()
        dump(legs)
        assertEquals(d.wolt.id, row.getString("tripId")); assertEquals("unknown", row.getString("outcome"))
        assertTrue(row.isNull("deliveredAtMs")); assertTrue(row.isNull("totalTaskMin")); assertTrue(row.getBoolean("excludeFromCalibration"))
        // A real delivery keeps outcome "delivered" and stays in the calibration.
        val c = DeliveryCore(); c.start()
        c.observe(woltCard(1000)); c.observe(obs(wolt, 2000, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00"))
        c.observe(obs(wolt, 3100, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00"))
        val real = EconomicsLogs.tripLegs(c.trips.values.single(), emptyMap(), c.trips.values, "delivery_slider_then_waiting")
        dump(listOf(real))
        assertEquals("delivered", real.getString("outcome")); assertFalse(real.getBoolean("excludeFromCalibration"))
    }
}
