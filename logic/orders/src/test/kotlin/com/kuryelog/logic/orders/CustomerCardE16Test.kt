package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * E16 (Faz 4): the customer card opens on whichever comes first: 300 m to the address in a straight line, 300 m left on the
 * route, or the last 2 turns. Route distance and turns come from the navigation status (WEEM: `RouteStatus`, plan 4.4; Rawstats: YolGo's `nav/v1/current`, E8); the old
 * trigger (1,000 m / 700 m from the Google Maps/Waze notification, NavDistance) is gone. Default size 15 % smaller, adjustable.
 * Tested with made-up payloads (uydurma koordinatlar); WEEM reads the same payload text through `RouteStatus.fromNavFeed`.
 */
class CustomerCardE16Test {
    private val now = 1_791_295_300_000L
    private val collected = now - 6 * 60_000L

    private fun payload(kind: String = "customer", requested: Long = collected + 30_000, remainingM: Double? = 900.0, turns: Int? = 3,
                        measured: Long = now - 2_000): RouteStatus = RouteStatus.fromNavFeed("""{"schemaVersion":1,"source":"yolgo","generatedAtMs":${now - 1_000},
        "updatedAtMs":${now - 1_000},"target":{"lat":40.4012,"lon":49.8521,"kind":"$kind","origin":"intent","requestedAtMs":$requested,
        "sourcePackage":"com.wolt.courierapp"},"route":${if (remainingM == null) "null" else """{"remainingM":$remainingM,"remainingTurns":${turns ?: "null"},"measuredAtMs":$measured,"onRoute":true}"""}}""")!!

    @Test fun whicheverComesFirst() {
        val r = CustomerCard.routeFor(payload(remainingM = 280.0, turns = 4), collected, now)!!
        assertEquals(CustomerCard.Why.ROUTE, CustomerCard.trigger(600.0, r))
        val turns = CustomerCard.routeFor(payload(remainingM = 900.0, turns = 2), collected, now)!!
        assertEquals(CustomerCard.Why.TURNS, CustomerCard.trigger(600.0, turns))
        val far = CustomerCard.routeFor(payload(remainingM = 900.0, turns = 3), collected, now)!!
        assertEquals(CustomerCard.Why.STRAIGHT, CustomerCard.trigger(290.0, far))
        assertNull(CustomerCard.trigger(400.0, far))
        // Without a route only the straight line counts.
        assertEquals(CustomerCard.Why.STRAIGHT, CustomerCard.trigger(300.0, null))
        assertNull(CustomerCard.trigger(301.0, null)); assertNull(CustomerCard.trigger(null, null))
        // Unknown turns never trigger by themselves.
        assertNull(CustomerCard.trigger(null, CustomerCard.routeFor(payload(remainingM = 900.0, turns = null), collected, now)))
    }

    @Test fun onlyAFreshRouteToThisCustomerCounts() {
        assertNull(CustomerCard.routeFor(payload(measured = now - 40_000), collected, now))      // older than 30 s
        assertNull(CustomerCard.routeFor(payload(kind = "restaurant"), collected, now))           // YolGo's guess: a restaurant
        assertNull(CustomerCard.routeFor(payload(requested = collected - 5 * 60_000), collected, now)) // asked before Collect: the restaurant's route
        assertNotNull(CustomerCard.routeFor(payload(requested = collected - 30_000), collected, now))  // Navigate tapped just before Collect
        assertNotNull(CustomerCard.routeFor(payload(kind = "unknown"), collected, now))
        assertNull(CustomerCard.routeFor(payload(remainingM = null), collected, now))
        assertNull(CustomerCard.routeFor(null, collected, now))
        // Second customer of a bundle: a target asked before the first handover is the first customer's.
        val firstHandedOver = collected + 10 * 60_000
        val later = now + 10 * 60_000
        assertNull(CustomerCard.routeFor(payload(requested = collected + 60_000, measured = later - 2_000), firstHandedOver, later))
        assertNotNull(CustomerCard.routeFor(payload(requested = firstHandedOver + 20_000, measured = later - 2_000), firstHandedOver, later))
    }

    @Test fun straightLineNeedsAFreshAccurateFix() {
        val lat = 40.4012; val lon = 49.8521
        assertEquals(250.0, CustomerCard.straight(now - 3_000, lat + 250 / 111_195.08, lon, 10.0, lat, lon, now)!!, 0.5)
        assertNull(CustomerCard.straight(now - 30_000, lat, lon, 10.0, lat, lon, now))
        assertNull(CustomerCard.straight(now - 3_000, lat, lon, 80.0, lat, lon, now))
        assertNull(CustomerCard.straight(now - 3_000, lat, lon, 10.0, null, null, now))
    }

    @Test fun reasonTexts() {
        val tr = { a: String, _: String -> a }; val az = { _: String, b: String -> b }
        val r = RouteStatus.Route(280.0, 2, now)
        assertEquals("Adrese yaklaşık 290 m (kuş uçuşu)", CustomerCard.reason(CustomerCard.Why.STRAIGHT, 290.4, r, tr))
        assertEquals("Rotada yaklaşık 280 m kaldı (WEEM)", CustomerCard.reason(CustomerCard.Why.ROUTE, null, r, tr))
        assertEquals("Son 2 dönüş (WEEM, 280 m)", CustomerCard.reason(CustomerCard.Why.TURNS, null, r, tr))
        assertEquals("Son 2 dönmə (WEEM, 280 m)", CustomerCard.reason(CustomerCard.Why.TURNS, null, r, az))
        assertEquals("Kalan: 1.2 km · 3 dönüş (WEEM)", CustomerCard.live(RouteStatus.Route(1234.0, 3, now), tr))
        assertEquals("Qalıb: 280 m (WEEM)", CustomerCard.live(RouteStatus.Route(280.0, null, now), az))
    }

    @Test fun cardIsFifteenPercentSmallerByDefault() {
        assertEquals(85, CustomerCard.DEFAULT_SIZE_PCT)
        assertEquals(listOf(70, 85, 100, 115), CustomerCard.SIZE_CHOICES)
        assertEquals(0.34, CustomerCard.heightShare(85, expanded = false), 1e-9)   // was 0.40 of the screen
        assertEquals(0.527, CustomerCard.heightShare(85, expanded = true), 1e-9)   // was 0.62
        assertEquals(0.40, CustomerCard.heightShare(100, expanded = false), 1e-9)
        assertEquals(85, CustomerCard.sizePct(13)); assertEquals(115, CustomerCard.sizePct(115))
    }

    // ------------------------------------------------------------------ wiring (source checks; the card is telefonda denenmedi)
    private fun main(): File = listOf("src/main", "app/src/main", "kuryelog/app/src/main").map(::File).firstOrNull { it.isDirectory }
        ?: error("KuryeLog kaynak klasörü bulunamadı: ${File(".").absolutePath}")
    private fun src(name: String) = File(main(), "java/com/kuryelog/app/$name").readText()

}
