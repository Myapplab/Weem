package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * Screen labels transcribed from the courier's Wolt / Bolt Food screenshots (October 2026).
 * Customer names, addresses and numbers are synthetic.
 */
class DeliveryRecognitionTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList()) = CourierParser.parse(pkg, text.toList(), actions, at)

    private fun woltCard(at: Long, name: String = "Test Customer", id: String = "#408") = obs(wolt, at,
        "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details", "Address details",
        "Block B ( TEST evleri) (Domofon kodu: 68🔔)", "Notes", "BLOCK B\n(Domofon kodu: 68🔔)\nMertebe 6...", "Translate",
        "Order details", "From", "Punkap", id, "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))
    private fun woltWaiting(at: Long) = obs(wolt, at, "Expect a task soon", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 50% from 08:00")
    private fun woltHot(at: Long) = obs(wolt, at, "Orders coming in hot!", "8 live hotspots", "Hər sifariş üçün əlavə 50% from 08:00")
    private fun woltOffline(at: Long) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 50% from 08:00", "Go online")
    private fun woltOffer(at: Long) = obs(wolt, at, "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Test Doner", "Test 1, Baku", "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup 3 min", "Pickup from", "Punkap", "Test 3, Baku", actions = listOf("Collect orders"))

    private fun boltCard(at: Long, id: String = "#UWG2Q", slider: String = "Delivered") = obs(bolt, at,
        "Drop off in -4 min", "Test C. $id", "Items (3)", "Address", "Test 5, Bakı", "Instructions", "Meet at the door",
        "Call", "Chat", "Delivery issues?", slider, "Slide to confirm")
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")
    private fun boltOffline(at: Long) = obs(bolt, at, "1.5x", "1.45x", "BADAMDAR", "Go online")

    // ---- Teslim: kaydırıcı → bekleme ekranı ----

    @Test fun woltCustomerCardIsDropoffWithDeliverySlider() {
        val o = woltCard(1000)
        assertEquals(ScreenStage.DROPOFF, o.stage); assertTrue(o.deliverControl); assertFalse(o.delivered)
    }
    @Test fun supportStatsAndSettingsPagesAreNotOrderScreens() {
        val pages = listOf(
            obs(wolt, 1, "Recent orders", "Today", "Punkap", "Test T, #408", "20:09"),
            obs(wolt, 1, "App help", "I haven't received orders in a while, why?", "An accepted order went missing", "Questions about the app"),
            obs(wolt, 1, "Previous task", "No previous completed task found."),
            obs(wolt, 1, "Your stats", "TODAY", "WEEK", "MONTH", "CUSTOM", "Deliveries completed", "4", "Earnings (estimate)", "AZN 16.79"),
            obs(wolt, 1, "Feedback", "Before you start, please let us know how happy you are currently with your partnership with Wolt.", "Unhappy", "Very happy", "Submit", "Skip feedback"),
            obs(wolt, 1, "Set offline time", "Offline time", "00:24", "Confirm offline time (tomorrow at 00:24)", "Go offline now"),
            obs(bolt, 1, "All Deliveries", "Kofteci Test", "10/04 18:22", "#UWG2Q", "3,21 ₼", "Address"),
            obs(bolt, 1, "Select an issue", "I'm not getting any orders", "My account has been blocked", "Something else"),
            obs(bolt, 1, "Earnings Schedule", "Microdistrict", "1.1–1.65x"))
        for (p in pages) assertEquals(p.raw.first(), ScreenStage.UNKNOWN, p.stage)
    }
    @Test fun woltWaitingPanelWithQuickLinksIsStillWaiting() {
        val o = obs(wolt, 1, "Baku Central", "Delivery demand", "Medium", "EXPECTED DEMAND", "Quick links", "Previous task", "Scheduled offline", "Support", "Go offline")
        assertEquals(ScreenStage.IDLE, o.stage)
    }
    // ---- Çevrimdışı ----

    @Test fun bothOfflineScreensAreOffline() {
        assertEquals(ScreenStage.OFFLINE, woltOffline(1).stage)
        assertEquals(ScreenStage.OFFLINE, boltOffline(1).stage)
    }

    // ---- Not ve telefon ----

    @Test fun emptyFieldsAreFilledFromTheCustomerNote() {
        val d = woltCard(1000).customer
        assertEquals("Test Customer", d.name)
        assertEquals("Block B ( TEST evleri) (Domofon kodu: 68🔔)", d.addressDetails)
        assertEquals("68", d.doorCode); assertEquals("B", d.building); assertEquals("6", d.floor)
        assertTrue(d.noteTruncated)
        assertEquals("BLOCK B\n(Domofon kodu: 68🔔)\nMertebe 6...", d.note)
        assertTrue(d.display().contains("Not kısaltılmış"))
    }
    @Test fun platformFieldWinsOverTheNote() {
        val o = obs(bolt, 1, "Drop off in 2 min", "Test C. #ABCD1", "Address", "Test 5", "Floor", "3", "Additional note", "mertebe 7, menzil 21, domofon 4411")
        assertEquals("3", o.customer.floor); assertEquals("21", o.customer.apartment); assertEquals("4411", o.customer.doorCode)
    }
    @Test fun azerbaijaniNoteWordsAreRecognised() {
        val f = NoteFields.extract("2-ci podyezd, 9-cu mərtəbə, mənzil 54, qapı kodu 54#1234")
        assertEquals("2", f.entrance); assertEquals("9", f.floor); assertEquals("54", f.apartment); assertEquals("54#1234", f.doorCode)
        val r = NoteFields.extract("Корпус 2, подъезд 3, этаж 5, кв. 17, домофон 17")
        assertEquals("2", r.building); assertEquals("3", r.entrance); assertEquals("5", r.floor); assertEquals("17", r.apartment); assertEquals("17", r.doorCode)
    }
    @Test fun phoneNumbersInNotesAreFound() {
        assertEquals(listOf("+994 55 123 45 67"), NoteFields.phones("zəng edin +994 55 123 45 67"))
        assertEquals(listOf("+994 50 765 43 21"), NoteFields.phones("Nomre: 050-765-43-21"))
        assertEquals(listOf("+994 70 111 22 33"), NoteFields.phones("(070) 111 22 33"))
        assertEquals(listOf("+994 12 444 55 66"), NoteFields.phones("ev: 0124445566"))
        assertEquals(listOf("+994 99 888 77 66"), NoteFields.phones("998887766 zeng edin"))
        val f = NoteFields.extract("Mertebe 6, domofon 68, tel 055 123 45 67")
        assertEquals("6", f.floor); assertEquals("68", f.doorCode); assertEquals(listOf("+994 55 123 45 67"), f.phones)
    }
    @Test fun ordinaryNumbersAreNotPhones() {
        for (s in listOf("Domofon kodu: 68", "#1444890483", "Payout ID 7325284466", "Mertebe 6, menzil 124", "4,49 ₼", "Test küçəsi 39k", "20:09"))
            assertEquals(s, emptyList<String>(), NoteFields.phones(s))
    }
    @Test fun truncatedNoteDoesNotReplaceTheFullOne() {
        val d = DeliveryDetails(note = "BLOCK B (Domofon kodu: 68) Mertebe 6 menzil 24")
        d.merge(DeliveryDetails(note = "BLOCK B (Domofon kodu: 68) Mertebe 6..."))
        assertEquals("BLOCK B (Domofon kodu: 68) Mertebe 6 menzil 24", d.note)
    }

    // ---- Navigasyon mesafesi ----

    @Test fun routeDistanceComesOnlyFromATripSummary() {
        // E16: only the trip summary line is read now (the customer card no longer opens from the notification's distance).
        assertEquals("8 min · 2,1 km · ETA 20:41", NavDistance.summary(listOf("Təbriz küçəsi", "8 min · 2,1 km · ETA 20:41")))
        assertEquals("3 min (900 m)", NavDistance.summary(listOf("3 min (900 m)")))
        assertEquals("4 dəq · 1.2 km", NavDistance.summary(listOf("4 dəq · 1.2 km")))
        assertNull(NavDistance.summary(listOf("150 m", "Turn left onto Test St")))
        assertNull(NavDistance.summary(listOf("In 300 m turn right")))
    }
}
