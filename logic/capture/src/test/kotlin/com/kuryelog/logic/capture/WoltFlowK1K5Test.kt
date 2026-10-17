package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * K1–K5 (docs/KURYELOG_ISTEKLER.md): labels transcribed from the courier's Wolt / Bolt Food
 * screenshots of 6 Oct 2026. Names, addresses, codes and items are synthetic.
 */
class WoltFlowK1K5Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, at, source)

    private val chart = arrayOf("NOW", "09:00", "15:00", "21:00", "03:00", "EXPECTED DEMAND", "EARN EXTRA")
    private val promo = arrayOf("Starts in 4 hours", "Boost", "Hər sifariş üçün əlavə 20%", "18:00-20:00")
    private val links = arrayOf("Quick links", "Previous task", "Scheduled offline", "Support")
    private fun online(at: Long, vararg extra: String) = obs(wolt, at, "Baku Central", "Delivery demand", "Low", *chart, *promo, *links, *extra, "Go offline")
    private fun offline(at: Long, label: String = "Go online", source: String = "accessibility") = obs(wolt, at, "Delivery demand", "Low", *chart, *promo, *links, label, source = source)
    /** E6: collapsed waiting sheet, no slider visible. */
    private fun collapsed(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 20% from 18:00")
    private fun feedback(at: Long, banner: Boolean = true) = obs(wolt, at, *(if (banner) arrayOf("1 deliveries declined", "Quick reminder, declining too many can mean being set offline!") else emptyArray()),
        "Feedback", "App functionality", "How were the time estimates?", "Venues", "How was your collaboration with the venue?",
        "Wolt support", "Was everything OK with Wolt support?", "Add a comment", "Submit feedback")
    private fun offer(at: Long, price: String = "₼3.94", restaurant: String = "Test Aptek") = obs(wolt, at, "Decline", "Ready in 1 min", price, "2 stops (4.2 km) • 11–19 min",
        restaurant, "Test street, Baku, AZ1025", "Customer drop-off", "40°23'50\"N 49°49'34\"E, Baku", "Accept", actions = listOf("Decline", "Accept"))
    private fun declineSheet(at: Long) = obs(wolt, at, "Ready in 7 min", "Decline offer?",
        "You'll lose potential earnings and it might take some time to find another offer in your delivery area.", "Decline", "Return to offer details",
        actions = listOf("Decline", "Return to offer details"))
    private fun pickup(at: Long, restaurant: String = "TEST HOUSE") = obs(wolt, at, "Pickup from", restaurant, "Test Street 1, AZ1010 Baku", "Notes",
        "Test prospekti kəsişməsi, mağazanın yanı", "Translate", "Collect order", "#014", "Test R.", "ORDER DETAILS", "1 x Test Künəfə 1 nəfərlik",
        "Collect", "Difficulties with the delivery?", "Cancel this delivery", "Collect orders", actions = listOf("Collect", "Cancel this delivery"))

    // ---- K1: çevrimiçi / çevrimdışı yalnız pozitif kanıtla ----

    @Test fun demandMapAloneIsNoAvailabilityEvidence() {
        assertNull(collapsed(1000).availability)
        assertEquals("online", online(1000).availability)
        assertEquals("offline", offline(1000).availability)
    }
    @Test fun ocrSliderVariantsAreRead() {
        assertEquals(ScreenStage.OFFLINE, offline(1000, label = "> Go online", source = "ocr").stage)
        assertEquals(ScreenStage.OFFLINE, offline(1000, label = "Go online!", source = "ocr").stage)
        assertEquals("online", online(1000).availability)
    }
    @Test fun waitingSheetWithToolbarIconIsNotTheSideMenu() {
        assertEquals(ScreenStage.IDLE, online(1000, "Stats").stage)
        assertEquals(ScreenStage.UNKNOWN, obs(wolt, 1000, "Delivery settings", "Insights", "Finances", "Stats", "Earn extra", "Help center").stage)
    }

    // ---- K4: müşteri kartı ----

    @Test fun woltBuildingNameAndColumnOrderedRow() {
        val d = obs(wolt, 1000, "Dropoff to", "Test Customer", "Yasamal, Bakı", "INFO", "ITEMS", "Address details", "Building name",
            "Test tikili, Test küçəsi 5 c", "Entrance", "Menzil33", "Floor", "9", "Apartment", "33", "Order details", "From", "Test Corner", "#932",
            "Order delivered!", actions = listOf("Order delivered!")).customer
        assertEquals("Test tikili, Test küçəsi 5 c", d.building)
        // F7.1 (Ekim planı V3): "Menzil33" in the Entrance field is the flat, not an entrance.
        assertNull(d.entrance); assertEquals("9", d.floor); assertEquals("33", d.apartment)
    }
    @Test fun ocrRowOrderEntranceFloorApartment() {
        val d = obs(wolt, 1000, "Dropoff to", "Test Customer", "Yasamal, Bakı", "Address details", "Building name", "Test tikili 5",
            "Entrance", "Floor", "Apartment", "2", "9", "33", "Order details", "Order delivered!", source = "ocr").customer
        assertEquals("2", d.entrance); assertEquals("9", d.floor); assertEquals("33", d.apartment)
    }

}
