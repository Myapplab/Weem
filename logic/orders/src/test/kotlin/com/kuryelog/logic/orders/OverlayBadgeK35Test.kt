package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * K35: the overlay badge shows only the platform and the status ("Wolt · Müşteriye gidiliyor"), no restaurant or customer
 * name, so no long line wraps and leaves empty width. The short line is still KuryePlus's own line for the parser.
 */
class OverlayBadgeK35Test {
    private val tr = { t: String, _: String -> t }
    private val az = { _: String, a: String -> a }

    @Test fun statusLineHasNoName() {
        assertEquals("Wolt · Müşteriye gidiliyor", OverlayText.tripStatus("Wolt", TripPhase.TO_CUSTOMER, tr))
        assertEquals("Bolt Food · Restorana gedilir", OverlayText.tripStatus("Bolt Food", TripPhase.TO_RESTAURANT, az))
        // An offer without a name falls back to the word "Teklif", never a restaurant.
        assertEquals("Wolt · 3.94₼ · net 3.15₼ · Teklif", OverlayText.offer("Wolt", 3.94, null, net = 3.15, tl = tr))
    }

    @Test fun shortStatusLinesAreOwnLinesInBothLanguagesAndOcrForms() {
        val lines = ArrayList<String>()
        for (tl in listOf(tr, az)) for (platform in listOf("Wolt", "Bolt Food", "courierapp")) for (p in TripPhase.values())
            lines += OverlayText.tripStatus(platform, p, tl)
        assertEquals(emptyList<String>(), lines.filterNot(OwnLines::isOwn))
        for (l in listOf("Wolt • Müşteriye gidiliyor", "Wolt Müşteriye gidiliyor", "Bolt Food - Müştəriyə gedilir", "Wolt·Restoranda bekleniyor"))
            assertTrue(l, OwnLines.isOwn(l))
    }

    @Test fun courierScreenLinesAreNotTakenForTheBadge() {
        for (l in listOf("Order delivered", "Müşteriye gidiliyor", "Arrived at customer", "Wolt", "Delivery completed", "Teslim edildi"))
            assertFalse(l, OwnLines.isOwn(l))
    }

    // ------------------------------------------------------------------ wiring (source check; the badge is telefonda denenmedi)
    // WEEM (A3.7): the badge window moved from `WatchService` to Android `:orders` `OrdersHost` (CaptureHost). The home's names
    // (`names = true`) are checked with the orders tab (A3.8).
    @Test fun overlayUsesTheNamelessLines() {
        val host = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/OrdersHost.kt")
        assertTrue(host.contains("badgeLines(names = false)"))
        assertTrue(host.contains("WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT"))
        assertTrue(RepoSource.text("logic/orders/src/main/kotlin/com/kuryelog/logic/orders/OverlayText.kt").contains("else tripStatus(platform(it.pkg), it.phase, tl)"))
    }
}
