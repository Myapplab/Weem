package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/** K21/K24 regresyonları: OCR'ın aynı müşteriyi üç kez yazması ve arama düğmesinden gelen numara.
 *  Yapay adlar ve adresler; gerçek kayıt değil. */
class CustomerMatchK21Test {
    private val wolt = "com.wolt.courierapp"
    private fun o(at: Long, stage: ScreenStage, name: String? = null, address: String? = null, details: String? = null, deliver: Boolean = false) =
        CourierObservation(wolt, at, stage, true, "test", emptyList(), emptyList(), gross = 3.0, minLo = 2, minHi = 10, restaurant = "Test Bar",
            customer = DeliveryDetails(name = name, address = address, addressDetails = details), deliverControl = deliver)

    private fun started(): DeliveryCore = DeliveryCore().apply {
        start(); observe(o(1_000, ScreenStage.OFFER)); observe(o(5_000, ScreenStage.PICKUP))
    }

    @Test fun ocrVariantsOfOneCustomerStayOneCustomer() {
        val core = started()
        core.observe(o(60_000, ScreenStage.DROPOFF, "Testa Niyazova", "Test Street 12, Baku", "Mərkəzi qapı"))
        core.observe(o(61_000, ScreenStage.DROPOFF, "Testa Niyazovc", "Test Street 12, Baku", "Mərkəzi qapı"))
        core.observe(o(62_000, ScreenStage.DROPOFF, "Testa", "Niyazova", "Mərkəzi qapı", deliver = true))
        val trip = core.trips.values.single()
        assertEquals(1, trip.customers.size)
        val c = trip.customers.values.single()
        // The fuller name and the real street stay; a surname read on the address line does not replace them.
        assertEquals("Test Street 12, Baku", c.details.address)
        assertEquals(2, c.details.name!!.split(' ').size)
        assertNull(c.deliveredAt)
    }

    @Test fun differentCustomersOfABundleStayApart() {
        val core = started()
        core.observe(o(60_000, ScreenStage.DROPOFF, "Testa Niyazova", "Test Street 12, Baku"))
        core.observe(o(70_000, ScreenStage.DROPOFF, "Orxan Testov", "Other Avenue 3, Baku"))
        assertEquals(2, core.trips.values.single().customers.size)
        // Same first name, different street with a number: two people.
        assertNull(CustomerMatch.find(core.trips.values.single().customers.values, "Testa Niyazova", "Far Road 99, Baku"))
    }

    @Test fun storedTriplicatesAreShownOnce() {
        val a = CustomerRecord("name:testa niyazova", DeliveryDetails(name = "Testa Niyazova", address = "Test Street 12, Baku", addressDetails = "Mərkəzi qapı"))
        val b = CustomerRecord("name:testa niyazovc", DeliveryDetails(name = "Testa Niyazovc", address = "Test Street 12, Baku", addressDetails = "Mərkəzi qapı"))
        val c = CustomerRecord("name:testa", DeliveryDetails(name = "Testa", address = "Niyazova", addressDetails = "Mərkəzi qapı"))
        val d = CustomerRecord("name:orxan testov", DeliveryDetails(name = "Orxan Testov", address = "Other Avenue 3, Baku"))
        val shown = CustomerMatch.distinct(listOf(a, b, c, d))
        assertEquals(2, shown.size)
        assertEquals("Test Street 12, Baku", shown[0].details.address)
        assertEquals("Orxan Testov", shown[1].details.name)
    }

    @Test fun callButtonBindsTheDialerNumberToThatCustomer() {
        val core = started()
        core.observe(o(60_000, ScreenStage.DROPOFF, "Testa Niyazova", "Test Street 12, Baku"))
        assertNull(core.callTarget(61_000))
        core.click(wolt, "Call", 62_000)
        val (trip, customer) = core.callTarget(70_000)!!
        assertEquals(trip.currentCustomer, customer.id)
        assertNull(core.callTarget(62_000 + DeliveryCore.CALL_TO_DIALER_MS + 1))
        assertTrue(CourierParser.isCall("Zəng et")); assertTrue(CourierParser.isCall("Call"))
        assertFalse(CourierParser.isCall("Call support")); assertFalse(CourierParser.isCall("Contact support")); assertFalse(CourierParser.isCall("Call restaurant"))
    }

    @Test fun waitBeforeOfferCountsFromPreviousDeliveryOrSessionStart() {
        fun trip(id: String, offer: Long, accepted: Long, done: Long?, session: String? = null) =
            TripRecord(id, wolt, OfferState("o-$id", wolt, offer, o(offer, ScreenStage.OFFER)), accepted,
                if (done != null) TripPhase.FINISHED else TripPhase.TO_CUSTOMER, completedAt = done).apply { sessionId = session }
        val first = trip("t1", 600_000, 610_000, 1_400_000, session = "wolt-0")
        val second = trip("t2", 2_000_000, 2_010_000, 2_600_000, session = "wolt-0")
        assertEquals(10.0, OrderHistory.waitBeforeOffer(first, listOf(first, second))!!, 1e-9)
        assertEquals(10.0, OrderHistory.waitBeforeOffer(second, listOf(first, second))!!, 1e-9)
        // Offer while another order was still running: no waiting.
        val overlap = trip("t3", 1_200_000, 1_210_000, 1_900_000)
        assertEquals(0.0, OrderHistory.waitBeforeOffer(overlap, listOf(first, overlap))!!, 1e-9)
        assertNull(OrderHistory.waitBeforeOffer(trip("t4", 100, 200, 300), emptyList()))
        assertEquals(800_000 / 60000.0, OrderHistory.totalMinutes(first)!!, 1e-9)
    }
}
