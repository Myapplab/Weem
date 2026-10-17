package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * F7.2 (Ekim planı madde 12, SORU-11/16): the customer card becomes a thin bubble like the restaurant's. Left: the four
 * facts a courier needs, Building · Floor · Flat · Door code ("Blok A · 9. kat · D 1 · Kod 21"), only those known, else the
 * customer's name. Right: only minutes, the platform's drop-off countdown (K29). Notes below. A vertical swipe folds/unfolds
 * the notes, a horizontal one removes the card. Synthetic values.
 */
class CustomerBubbleF72Test {
    private val tl: (String, String) -> String = { tr, _ -> tr }
    private val az: (String, String) -> String = { _, a -> a }

    @Test fun lineWithAllFour() {
        val d = DeliveryDetails(building = "A", floor = "9", apartment = "1", doorCode = "21")
        assertEquals("Blok A · 9. kat · D 1 · Kod 21", CustomerBubble.line(d, "Test M.", tl))
        assertEquals("Blok A · 9-cu mərtəbə · M 1 · Kod 21", CustomerBubble.line(d, "Test M.", az))
    }

    @Test fun buildingAsWrittenAndMissingFieldsLeftOut() {
        assertEquals("Blok 197 · Kod 4512", CustomerBubble.line(DeliveryDetails(building = "197", doorCode = "4512"), null, tl))
        assertEquals("Test tikili 5 · 3. kat", CustomerBubble.line(DeliveryDetails(building = "Test tikili 5", floor = "3"), null, tl))
        assertEquals("2. kat · Oda 16", CustomerBubble.line(DeliveryDetails(floor = "2", apartment = "Oda 16"), null, tl))
        assertEquals("Test M.", CustomerBubble.line(DeliveryDetails(), "Test M.", tl)) // none known: the name
        assertEquals("Müşteri", CustomerBubble.line(DeliveryDetails(), null, tl))
    }

    @Test fun minutesOnlyFromThePlatformDeadline() {
        val now = 1_000_000L
        assertEquals("8 dk", CustomerBubble.minutes(now + 7 * 60_000 + 1, now, tl))
        assertEquals("0 dk", CustomerBubble.minutes(now, now, tl))
        assertEquals("-4 dk", CustomerBubble.minutes(now - 4 * 60_000, now, tl)) // late, as Wolt shows "Drop off in -4 min"
        assertEquals("8 dəq", CustomerBubble.minutes(now + 7 * 60_000 + 1, now, az))
        assertNull(CustomerBubble.minutes(null, now, tl)) // unknown: the circle stays empty
    }

    @Test fun ordersLabel() {
        assertNull(CustomerBubble.orders(1, tl))
        assertEquals("2 sipariş", CustomerBubble.orders(2, tl))
        assertEquals("2 sifariş", CustomerBubble.orders(2, az))
    }

    @Test fun notesInTheCustomersWords() {
        val d = DeliveryDetails(checkId = true, addressDetails = "rabociy lift", note = "Zəng etməyin", instructions = "Zəng etməyin")
        assertEquals(listOf("Kimlik kontrolü gerekli", "Adres detayı: rabociy lift", "Not: Zəng etməyin"), CustomerBubble.notes(d, tl))
        assertTrue(CustomerBubble.notes(DeliveryDetails(), tl).isEmpty())
    }

    @Test fun swipeDecision() {
        val w = 1000; val d = 3f
        assertEquals(CustomerBubble.Swipe.DISMISS, CustomerBubble.swipe(-260f, 40f, w, d))
        assertEquals(CustomerBubble.Swipe.DISMISS, CustomerBubble.swipe(300f, -20f, w, d))
        assertEquals(CustomerBubble.Swipe.NONE, CustomerBubble.swipe(200f, 10f, w, d)) // < 25 % of the width
        assertEquals(CustomerBubble.Swipe.TOGGLE, CustomerBubble.swipe(10f, -150f, w, d)) // > 48 dp up
        assertEquals(CustomerBubble.Swipe.TOGGLE, CustomerBubble.swipe(-20f, 150f, w, d))
        assertEquals(CustomerBubble.Swipe.NONE, CustomerBubble.swipe(5f, 100f, w, d)) // < 48 dp
        assertEquals(CustomerBubble.Swipe.NONE, CustomerBubble.swipe(0f, 0f, w, d)) // a tap
    }

    @Test fun sizes() {
        assertEquals(56, CustomerBubble.BUBBLE_DP)
        assertEquals(0.25, CustomerBubble.NOTES_MAX_SHARE, 0.0)
        assertEquals(RestaurantBubble.WIDTH_KEY, CustomerBubble.WIDTH_KEY) // same width setting as the restaurant bubble
    }
}
