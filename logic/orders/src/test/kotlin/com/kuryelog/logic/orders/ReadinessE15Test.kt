package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * E15 (Faz 4): the restaurant's ready counter. Wolt 2.202.6 "Pickup N min" (offer card and the map on the way) counts down from
 * its first reading and is corrected by every new one; when time runs out it says "şimdi" (not "hazır"); "Ready" says "hazır".
 * The offer card's label starts the counter. Shown in the restaurant notification (refreshed as it counts) and the E14 bubble.
 * Wolt texts from the field frames; restaurant names and addresses are made up.
 */
class ReadinessE15Test {
    private val wolt = "com.wolt.courierapp"
    private val min = 60_000L
    private val tr = { a: String, _: String -> a }
    private val az = { _: String, b: String -> b }
    private val t0 = 1_791_295_200_000L

    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private fun offer(at: Long, label: String) = obs(at, "Decline", label, "AZN 2.61", "Expected earnings for the full delivery",
        "Delivery from", "Test Döner", "Route distance", "2 km", "Estimated", "9 - 17 min", "Accept", "Test küç. 1, AZ1000 Baku")
    private fun pickup(at: Long, vararg label: String) = obs(at, *label, "Pickup from", "Test Döner", "Test küç. 1, AZ1000 Baku")

    @Test fun statesAndTheirTexts() {
        fun p(f: PickupRecord.() -> Unit) = PickupRecord("Test Döner", null).apply(f)
        assertEquals(Readiness.State(Readiness.Kind.COUNTDOWN, 4), Readiness.of(p { readyHintAt = t0 + 3 * min + 10_000 }, t0))
        assertEquals(Readiness.State(Readiness.Kind.NOW), Readiness.of(p { readyHintAt = t0 }, t0))
        assertEquals(Readiness.State(Readiness.Kind.NOW), Readiness.of(p { readyHintAt = t0 - 5 * min }, t0))
        // "Ready" wins over any countdown; a preparing screen without a time says so; nothing seen is unknown.
        assertEquals(Readiness.State(Readiness.Kind.READY), Readiness.of(p { readyHintAt = t0 + 9 * min; readyObservedAt = t0 }, t0))
        assertEquals(Readiness.State(Readiness.Kind.PREPARING), Readiness.of(p { preparingAt = t0 }, t0))
        assertEquals(Readiness.State(Readiness.Kind.UNKNOWN), Readiness.of(p { }, t0))
        // The first platform estimate counts when no later reading exists.
        assertEquals(Readiness.State(Readiness.Kind.COUNTDOWN, 2), Readiness.of(p { readyAt = t0 + 2 * min }, t0))

        val cd = Readiness.State(Readiness.Kind.COUNTDOWN, 4)
        assertEquals("4 dk sonra hazır", Readiness.headline(cd, tr)); assertEquals("4 dəq sonra hazır", Readiness.headline(cd, az))
        assertEquals("4 dk", Readiness.short(cd, tr)); assertEquals("4 dəq", Readiness.short(cd, az))
        val now = Readiness.State(Readiness.Kind.NOW)
        assertEquals("Şimdi (süre doldu)", Readiness.headline(now, tr)); assertEquals("İndi (vaxt bitdi)", Readiness.headline(now, az))
        assertEquals("şimdi", Readiness.short(now, tr)); assertEquals("indi", Readiness.short(now, az))
        val ready = Readiness.State(Readiness.Kind.READY)
        assertEquals("✓ HAZIR", Readiness.headline(ready, tr)); assertEquals("hazır", Readiness.short(ready, tr))
        // Time up never reads as ready.
        assertFalse(Readiness.headline(now, tr).lowercase().contains("hazır"))
        assertFalse(Readiness.short(now, tr).contains("hazır"))
    }

    @Test fun offerCardStartsTheCounterAndEveryReadingCorrectsIt() {
        val c = DeliveryCore().also { it.start() }
        c.observe(offer(t0, "Pickup 5 min")); c.observe(offer(t0 + 2_000, "Pickup 5 min"))
        // First restaurant screen: the map label was not read; the offer card's reading (last at t0 + 2 s) runs on.
        c.observe(pickup(t0 + 6_000))
        val p = c.active(wolt)!!.pickups.single()
        assertEquals(t0 + 2_000 + 5 * min, p.readyHintAt)
        assertEquals(Readiness.State(Readiness.Kind.COUNTDOWN, 4), Readiness.of(p, t0 + 90_000))
        // A new reading on the way corrects it (Wolt counted faster than the clock).
        c.observe(pickup(t0 + 2 * min, "Pickup 2 min"))
        assertEquals(t0 + 4 * min, p.readyHintAt)
        assertEquals(Readiness.State(Readiness.Kind.COUNTDOWN, 2), Readiness.of(p, t0 + 2 * min + 1_000))
        // Time up: "now", not "ready" (the map label says "now" too).
        assertEquals(Readiness.State(Readiness.Kind.NOW), Readiness.of(p, t0 + 4 * min))
        c.observe(pickup(t0 + 4 * min + 30_000, "now"))
        assertEquals(Readiness.State(Readiness.Kind.NOW), Readiness.of(p, t0 + 4 * min + 31_000))
        // "Ready" on the map: ready.
        c.observe(pickup(t0 + 6 * min, "Ready"))
        assertEquals(Readiness.State(Readiness.Kind.READY), Readiness.of(p, t0 + 6 * min + 1_000))
    }

    @Test fun anOrderWithoutTheOfferCardStartsAtItsFirstLabel() {
        val c = DeliveryCore().also { it.start() }
        c.observe(pickup(t0, "Pickup 6 min"))
        assertEquals(t0 + 6 * min, c.active(wolt)!!.pickups.single().readyHintAt)
        val none = DeliveryCore().also { it.start() }
        none.observe(pickup(t0))
        assertNull(none.active(wolt)!!.pickups.single().readyHintAt)
    }

    @Test fun offerWithoutALabelStartsNothing() {
        assertNull(Readiness.fromOffer(null))
        val o = OfferState("o", wolt, t0, offer(t0, "Accept"))
        assertNull(o.data.readyIn); assertNull(Readiness.fromOffer(o))
    }

    // ------------------------------------------------------------------ wiring (source checks; notification and card are telefonda denenmedi)
    // WEEM (A3.7): `OrderTracker.kt` → `OrderFlow.kt`, `DeliveryReminder.notify(ctx, next, update = true)` → `env.notifyReminder(next,
    // update = true)` (Android `:orders` `Orders` → `DeliveryReminder.notify`). The card and list (`cardAndListShowIt`) are A3.8.
    @Test fun restaurantNotificationUsesTheCounterAndFollowsIt() {
        val tracker = RepoSource.text("logic/orders/src/main/kotlin/com/kuryelog/logic/orders/OrderFlow.kt")
        val fn = tracker.substringAfter("private fun readiness(").substringBefore("\n    private fun ")
        assertTrue(fn.contains("Readiness.headline(Readiness.of("))
        assertFalse(tracker.contains("Hazır olmalı (tahmin)"))
        // As it counts down the notification is posted again silently (same id, alert once).
        assertTrue(tracker.contains("notifyReminder(next, update = true)") && tracker.contains("env.notifyReminder(r, update)"))
        assertTrue(RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/Orders.kt").contains("DeliveryReminder.notify(ctx, reminder, update)"))
        val reminder = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/DeliveryReminder.kt")
        assertTrue(reminder.contains("setOnlyAlertOnce(update)"))
    }

}
