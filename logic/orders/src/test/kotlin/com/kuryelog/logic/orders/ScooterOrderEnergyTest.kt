package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CourierParser
import org.junit.Assert.*
import org.junit.Test

/**
 * RT `ScooterOrderEnergyTest.kt` (7 test; SCOOTER.md 13.2): her sipariş kaydı çalışıldığı sürenin scooter enerjisini alır (kullanılan/geri
 * giren Wh, ölçülen süre), GPS mesafesi gibi 1 Hz örneklerden adım adım dolar ve kartında görünür. Sentetik değerler.
 * `:orders` `:scooter`'ı tanımaz (modül grafiği): adım hesabı (`ScooterSamples.step`: ilk iki test ve D10'un adım payı) `logic/scooter`
 * `ScooterSamplesTest`'te; burada adımlar `DeliveryCore.addEnergy`'ye değer olarak verilir (`ScooterBridge.steps` → `OrderFlow.scooterEnergy`).
 */
class ScooterOrderEnergyTest {
    private val wolt = "com.wolt.partner"; private val bolt = "com.bolt.food.courier"
    private fun pickup(pkg: String = bolt, at: Long = 2000) = CourierParser.parse(pkg, listOf("Order is being prepared", "Arrive in 11 min",
        "Xengeland", "Mehdi Hüseyn 99", "Orders to pick up:", "2", "#Q3FYM", "Items (0)", "#8P6D2"), emptyList(), at, complete = true)

    @Test fun energyGoesWhereTheDistanceGoesAndIsCountedOnce() {
        val c = DeliveryCore(); c.start(); c.observe(pickup())
        val first = c.active(bolt)!!
        c.addEnergy(10.0, 0.5, 60_000)
        // A second platform's order seen later takes the next steps, like the distance; nothing is split or duplicated.
        c.observe(pickup(wolt, 2_100)); val second = c.active(wolt)!!
        c.addEnergy(4.0, 0.0, 30_000); c.addDistance(800.0)
        assertEquals(0.0, first.usedWh, 0.0) // gathered, not yet in the records: they do not change every second
        c.closeEnergy(first); c.closeEnergy(second)
        assertEquals(10.0, first.usedWh, 1e-12); assertEquals(0.5, first.recoveredWh, 1e-12); assertEquals(60_000L, first.energyCoveredMs)
        assertEquals(4.0, second.usedWh, 1e-12); assertEquals(30_000L, second.energyCoveredMs); assertEquals(800.0, second.distanceM, 1e-9)
        assertTrue(second.energyShared); assertFalse(first.energyShared)
        c.closeEnergy(first) // a later event of the same trip (manual correction) adds nothing twice
        assertEquals(14.0, c.trips.values.sumOf { it.usedWh }, 1e-12)
    }

    @Test fun closedOrdersAndBrokenStepsGetNothing() {
        val c = DeliveryCore(); c.start()
        c.addEnergy(5.0, 0.0, 1_000) // no order open: the energy belongs to no order (the idle rows have it)
        c.observe(pickup()); val t = c.active(bolt)!!
        for ((used, back, ms) in listOf(Triple(Double.NaN, 0.0, 1_000L), Triple(-1.0, 0.0, 1_000L), Triple(1.0, 0.0, 0L), Triple(1.0, Double.POSITIVE_INFINITY, 1_000L)))
            c.addEnergy(used, back, ms)
        c.closeEnergy(t)
        assertEquals(0.0, t.usedWh, 0.0); assertEquals(0L, t.energyCoveredMs)
        c.confirmDelivered(t.id, 9_000)
        c.addEnergy(3.0, 0.0, 1_000); c.closeEnergy(t)
        assertEquals(0.0, t.usedWh, 0.0)
    }

    @Test fun recordKeepsTheEnergy() {
        val c = DeliveryCore(); c.start(); c.observe(pickup())
        val t = c.active(bolt)!!; c.addEnergy(12.345, 0.67, 600_000); c.closeEnergy(t); t.energyShared = true
        val back = RecordJson.trip(RecordJson.trip(t))
        assertEquals(12.345, back.usedWh, 1e-12); assertEquals(0.67, back.recoveredWh, 1e-12)
        assertEquals(600_000L, back.energyCoveredMs); assertTrue(back.energyShared)
    }

    @Test fun cardLineShowsNetEnergyPerKmAndCoverage() {
        val tr = { a: String, _: String -> a }; val az = { _: String, b: String -> b }
        val c = DeliveryCore(); c.start(); c.observe(pickup(at = 0)); val t = c.active(bolt)!!
        assertNull(OrderHistory.energyLine(t, tr)) // nothing measured: no line, never "0 Wh"
        c.addEnergy(31.0, 1.0, 18 * 60_000L); c.addDistance(2_500.0); c.closeEnergy(t); c.confirmDelivered(t.id, 20 * 60_000L)
        // The G3 has no regenerative braking: the headline is what left the batteries, the inflow (external battery feeding
        // the internal one, or a charger) is named for what it is and never subtracted.
        // WEEM A10.4f: the 2 unread minutes are named ("veri eksik").
        assertEquals("Scooter enerjisi: 31.0 Wh · 12.4 Wh/km · bataryaya giren 1.0 Wh (harici batarya/şarj) · ölçülen 18/20 dk · veri eksik 2 dk", OrderHistory.energyLine(t, tr))
        t.energyShared = true; t.recoveredWh = 0.0; t.distanceM = 100.0
        assertEquals("Skuter enerjisi: 31.0 Wh · ölçülən 18/20 dəq · məlumat əskik 2 dəq · başqa sifarişlə eyni vaxtda", OrderHistory.energyLine(t, az))
    }

    /** D10 (13 Ekim): the order's energy is both packs' V×A only; an internal-only step (external pack known, not read) adds
     *  coverage gap time, never Wh; the figure is never negative and the coverage is written. */
    @Test fun d10OnlyBothPacksCountAndCoverageIsWritten() {
        val tr = { a: String, _: String -> a }
        val c = DeliveryCore(); c.start(); c.observe(pickup(at = 0)); val t = c.active(bolt)!!
        c.addEnergy(0.0, 0.0, 6 * 60_000L, complete = false) // the internal pack alone, being fed (`ScooterSamples.step(..., complete = false)`: 0 Wh)
        c.addEnergy(20.0, 0.0, 12 * 60_000L); c.addDistance(2_000.0); c.closeEnergy(t); c.confirmDelivered(t.id, 20 * 60_000L)
        assertEquals(20.0, t.usedWh, 1e-12); assertEquals(0.0, t.recoveredWh, 0.0); assertEquals(12 * 60_000L, t.energyCoveredMs); assertEquals(6 * 60_000L, t.energyPartialMs)
        // No Wh/km while part of the order was measured on one pack only.
        assertEquals("Scooter enerjisi: 20.0 Wh · ölçülen 12/20 dk · harici batarya okunmayan 6 dk · veri eksik 2 dk", OrderHistory.energyLine(t, tr))
        assertEquals(6 * 60_000L, RecordJson.trip(RecordJson.trip(t)).energyPartialMs)
        // Only internal-only steps: no Wh at all ("—"), never "0 Wh" or a negative figure.
        val c2 = DeliveryCore(); c2.start(); c2.observe(pickup(at = 0)); val u = c2.active(bolt)!!
        c2.addEnergy(0.0, 1.7, 8 * 60_000L, complete = false); c2.closeEnergy(u); c2.confirmDelivered(u.id, 10 * 60_000L)
        assertEquals("Scooter enerjisi: — · ölçülen 0/10 dk · harici batarya okunmayan 8 dk · veri eksik 2 dk", OrderHistory.energyLine(u, tr))
        // The average leaves out orders measured on one pack for over a fifth of the time.
        val a = OrderHistory.averages(listOf(t, u)) { "Bolt Food" }
        assertNull(a.energyWh)
        t.energyPartialMs = 2 * 60_000L
        assertEquals(20.0, OrderHistory.averages(listOf(t, u)) { "Bolt Food" }.energyWh!!, 1e-12)
    }
}
