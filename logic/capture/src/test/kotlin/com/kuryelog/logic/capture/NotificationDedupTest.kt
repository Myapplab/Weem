package com.kuryelog.logic.capture

import com.kuryelog.logic.core.LogStore
import java.io.File
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** R2 (CAPTURE.md 4.1, 6.2, 8): no Rawstats test; characterisation of `NotifService` @ 6388c04. */
class NotificationDedupTest {
    private val wolt = "com.wolt.courierapp"
    private val targets = setOf(wolt)

    @Test fun navigationAppsComeFirstAndOthersAreIgnored() {
        val d = NotificationDedup()
        assertEquals(NotificationDedup.Route.NAVIGATION, d.route("com.google.android.apps.maps", targets))
        // A navigation app in the watched list is still only a navigation app: never written.
        assertEquals(NotificationDedup.Route.NAVIGATION, d.route("com.waze", targets + "com.waze"))
        assertEquals(NotificationDedup.Route.WATCHED, d.route(wolt, targets))
        assertEquals(NotificationDedup.Route.IGNORE, d.route("com.whatsapp", targets))
    }

    @Test fun aRefreshedNotificationIsWrittenOnce() {
        val d = NotificationDedup()
        assertTrue(d.posted("k", "New order", "Burger House", null, null, true))
        assertFalse(d.posted("k", "New order", "Burger House", null, null, true))
        assertTrue(d.posted("k", "New order", "Burger House · 2.40 AZN", null, null, true))
        assertTrue(d.posted("k2", "New order", "Burger House · 2.40 AZN", null, null, true))   // another key
    }

    @Test fun removedForgetsTheKeyEvenWithoutPermission() {
        val d = NotificationDedup()
        assertTrue(d.posted("k", "a", null, null, null, true))
        assertFalse(d.removed("k", canRecord = false))
        assertTrue(d.posted("k", "a", null, null, null, true))
        assertTrue(d.removed("k", canRecord = true))
    }

    @Test fun withoutPermissionNothingIsWrittenAndTheMemoryIsCleared() {
        val d = NotificationDedup()
        assertTrue(d.posted("k", "a", null, null, null, true))
        assertFalse(d.posted("k", "b", null, null, null, false))
        assertTrue(d.posted("k", "a", null, null, null, true))   // memory was cleared
    }

    @Test fun ordersGetTheDistinctFieldsOnePerLine() {
        assertEquals("New order\nBurger House", NotificationDedup.forOrders("New order", "Burger House", "Burger House", null))
    }

    @Test fun rowsRedactPhonesAndSkipNullFields() {
        val log = LogStore(File("build/tmp/notif-rows"), zone = { ZoneId.of("Asia/Baku") })
        val p = CaptureRows.posted(log, 1_000, 900, wolt, "k", false, "orders", "Call +994 50 123 45 67", null, null, null)
        assertEquals("posted", p.getString("ev"))
        assertEquals("Call [tel]", p.getString("title"))
        assertFalse(p.has("text")); assertFalse(p.has("big")); assertFalse(p.has("loc"))
        assertEquals(log.ts(900), p.getString("posted"))
        val r = CaptureRows.removed(log, 1_000, wolt, "k")
        assertEquals(setOf("t", "ev", "pkg", "key"), r.keySet())
    }
}
