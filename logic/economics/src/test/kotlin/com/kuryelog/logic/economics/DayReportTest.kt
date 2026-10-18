package com.kuryelog.logic.economics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

/** A4.2: net (S8), enerji maliyeti, ölçülmemiş enerji, günlük hedef (N8). Yapay tutarlar. */
class DayReportTest {
    private val wolt = "com.wolt.courierapp"
    private fun tl(tr: String, @Suppress("UNUSED_PARAMETER") az: String) = tr
    private fun azl(@Suppress("UNUSED_PARAMETER") tr: String, az: String) = az

    private fun trip(id: String, gross: Double?, commission: Double, tip: Double = 0.0, usedWh: Double = 0.0, coveredMs: Long = 0, partialMs: Long = 0,
                     delivered: Boolean = true, extraWait: Double = 0.0): TripRecord {
        val obs = CourierObservation(wolt, 1_000, ScreenStage.OFFER, true, "test", emptyList(), emptyList(), gross = gross, restaurant = "R")
        return TripRecord(id, wolt, OfferState("o-$id", wolt, 1_000, obs), 1_000,
            if (delivered) TripPhase.FINISHED else TripPhase.TO_RESTAURANT, mutableListOf(PickupRecord("R", null)),
            completedAt = if (delivered) 2_000 else null, commissionPct = commission, tip = tip, usedWh = usedWh, energyCoveredMs = coveredMs)
            .also { it.energyPartialMs = partialMs; it.extraWaitAzn = extraWait }
    }

    private fun idle(spell: String, wh: Double?) = JSONObject().put("kind", "energy_segment").put("segment", "idle").put("spellId", spell)
        .put("energy", JSONObject().put("wh", wh ?: JSONObject.NULL))
    private fun leg(trip: String, wh: Double) = JSONObject().put("kind", "energy_segment").put("segment", "to_dropoff").put("tripId", trip)
        .put("energy", JSONObject().put("wh", wh))
    private fun day(vararg t: TripRecord) = OrderHistory.Day("2026-10-18", t.toList())
    private val priced = NetSettings.Prices(0.10, 80.0, null)

    @Test fun netIsGrossPlusTipsMinusCommissionMinusEnergyCost() {
        // 10 ₼ brüt %10 komisyon + 1 ₼ bahşiş; 20 ₼ brüt %10 + 2 ₼ bahşiş. 600 Wh sipariş + 150 Wh boşta × 0.10 ₼/kWh ÷ 0.80.
        val d = day(trip("a", 10.0, 10.0, tip = 1.0, usedWh = 400.0, coveredMs = 600_000), trip("b", 20.0, 10.0, tip = 2.0, usedWh = 200.0, coveredMs = 600_000))
        val r = DayReport.build(d, listOf(idle("s1", 150.0)), priced)
        assertEquals(30.0, r.gross, 1e-9); assertEquals(3.0, r.tips, 1e-9); assertEquals(3.0, r.commission, 1e-9)
        assertEquals(30.0, r.netBeforeEnergy, 1e-9)
        assertEquals(d.net, r.netBeforeEnergy, 1e-9)
        assertEquals(750.0, r.energyWh, 1e-9); assertEquals(600.0, r.orderWh, 1e-9); assertEquals(150.0, r.idleWh, 1e-9)
        assertEquals(EnergyState.COSTED, r.energyState)
        assertEquals(750.0 * 0.10 / 1000.0 / 0.80, r.energyCost, 1e-9)   // 0.09375 ₼
        assertEquals(30.0 - 0.09375, r.net, 1e-9)
        assertEquals(2, r.measuredTrips); assertEquals(2, r.deliveredTrips)
    }

    @Test fun extraWaitingFeeCountsAsGrossAndCommissionAppliesToIt() {
        val d = day(trip("a", 10.0, 20.0, extraWait = 2.0))
        val r = DayReport.build(d, emptyList(), priced)
        assertEquals(12.0, r.gross, 1e-9); assertEquals(2.4, r.commission, 1e-9)
        assertEquals(d.net, r.netBeforeEnergy, 1e-9)
    }

    @Test fun unmeasuredEnergyIsLeftOutAndSaidSo() {
        val r = DayReport.build(day(trip("a", 10.0, 0.0)), emptyList(), priced)
        assertEquals(EnergyState.NOT_MEASURED, r.energyState)
        assertEquals(0.0, r.energyCost, 0.0); assertEquals(r.netBeforeEnergy, r.net, 0.0)
        val lines = DayReportCard.card(r, today = false, ::tl).blocks.filterIsInstance<Block.Line>().map { it.text }
        assertTrue(lines.any { it.contains("Enerji ölçülmedi") && it.contains("hesaba katılmadı") })
        assertTrue(DayReportCard.energyNote(r, ::azl).contains("ölçülmədi"))
    }

    @Test fun noPricePrintsMeasuredWhAndSkipsCost() {
        val d = day(trip("a", 10.0, 0.0, usedWh = 300.0, coveredMs = 60_000))
        val r = DayReport.build(d, emptyList(), NetSettings.Prices(null, 90.0, null))
        assertEquals(EnergyState.NO_PRICE, r.energyState); assertEquals(0.0, r.energyCost, 0.0); assertEquals(300.0, r.energyWh, 1e-9)
        assertTrue(DayReportCard.energyNote(r, ::tl).contains("kWh fiyatı girilmediği"))
    }

    @Test fun orderWithMostlyUnreadExternalPackIsNotMeasured() {
        // D10: harici batarya okunmayan süre ölçülen sürenin %25'inden fazlaysa sipariş ölçülmemiş sayılır (Wh toplama girmez).
        val a = trip("a", 10.0, 0.0, usedWh = 100.0, coveredMs = 60_000, partialMs = 30_000)
        val b = trip("b", 10.0, 0.0, usedWh = 200.0, coveredMs = 60_000, partialMs = 10_000)
        val r = DayReport.build(day(a, b), emptyList(), priced)
        assertEquals(200.0, r.energyWh, 1e-9); assertEquals(1, r.measuredTrips); assertEquals(2, r.deliveredTrips)
        assertTrue(DayReportCard.energyNote(r, ::tl).contains("1/2 siparişte ölçüldü"))
    }

    @Test fun idleRowsCountOncePerSpellAndNeverNegativeOrNull() {
        val rows = listOf(idle("s1", 50.0), idle("s1", 60.0), idle("s2", null), idle("s3", -30.0), leg("t1", 999.0))
        assertEquals(60.0, DayReport.idleWh(rows), 1e-9)   // son s1 satırı; null, şarj (negatif) ve bacak satırı sayılmaz
    }

    @Test fun undeliveredAndUnpricedTripsStayOut() {
        val d = day(trip("a", 10.0, 0.0, tip = 1.0), trip("open", 50.0, 0.0, delivered = false), trip("np", null, 0.0, tip = 5.0))
        val r = DayReport.build(d, emptyList(), priced)
        assertEquals(10.0, r.gross, 1e-9); assertEquals(1.0, r.tips, 1e-9); assertEquals(1, r.unpriced)
        assertEquals(d.net, r.netBeforeEnergy, 1e-9)
        assertTrue(DayReportCard.card(r, today = true, ::tl).blocks.filterIsInstance<Block.Line>().any { it.text.contains("1 sipariş brüt ve net") })
    }

    @Test fun missingCommissionMakesNetGrossPlusTipsAndSaysSo() {
        val r = DayReport.build(day(trip("a", 10.0, 0.0, tip = 1.0)), emptyList(), priced)
        assertTrue(r.commissionMissing); assertEquals(0.0, r.commission, 0.0); assertEquals(11.0, r.net, 1e-9)
        assertTrue(DayReportCard.card(r, false, ::tl).blocks.filterIsInstance<Block.Line>().any { it.text.contains("Filo komisyonu girilmemiş") })
        val withC = DayReport.build(day(trip("a", 10.0, 15.0, tip = 1.0)), emptyList(), priced)
        assertFalse(withC.commissionMissing)
    }

    @Test fun dailyGoalIsOnNetAndShowsRemainingOrReached() {
        val d = day(trip("a", 40.0, 0.0, usedWh = 1000.0, coveredMs = 60_000))   // maliyet 1 kWh × 0.10 / 0.80 = 0.125
        val p = NetSettings.Prices(0.10, 80.0, 100.0)
        val r = DayReport.build(d, emptyList(), p)
        assertEquals(40.0 - 0.125, r.net, 1e-9); assertEquals(100.0 - r.net, r.remaining!!, 1e-9)
        assertFalse(r.goalReached); assertEquals(40, r.goalPct)
        val line = (DayReportCard.goalLine(r, ::tl) as Block.Line).text
        assertTrue(line, line.contains("hedefe kalan 60.13₼")); assertTrue(line.contains("%40"))
        val done = DayReport.build(day(trip("a", 120.0, 0.0)), emptyList(), p)
        assertTrue(done.goalReached); assertEquals(0.0, done.remaining!!, 0.0)
        assertTrue((DayReportCard.goalLine(done, ::tl) as Block.Line).text.contains("hedefe ulaşıldı (+20.00₼)"))
        assertTrue((DayReportCard.goalLine(done, ::azl) as Block.Line).text.contains("hədəfə çatıldı"))
        assertNull(DayReportCard.goalLine(DayReport.build(d, emptyList(), priced), ::tl))
    }

    @Test fun settingsReadWithDefaultsAndValidation() {
        val p = KuryelogPrefs.Memory()
        val none = NetSettings.read(p)
        assertNull(none.kwhPrice); assertEquals(90.0, none.chargeEfficiencyPct, 0.0); assertNull(none.dailyGoal); assertNull(none.aznPerWh())
        p.put(mapOf(NetSettings.KWH_PRICE to 0.12f, NetSettings.CHARGE_EFFICIENCY to 85f, NetSettings.DAILY_GOAL to 120.5f))
        val s = NetSettings.read(p)
        assertEquals(0.12, s.kwhPrice!!, 0.0); assertEquals(85.0, s.chargeEfficiencyPct, 0.0); assertEquals(120.5, s.dailyGoal!!, 0.0)
        assertEquals(0.12 / 1000 / 0.85, s.aznPerWh()!!, 1e-12)
        p.put(mapOf(NetSettings.KWH_PRICE to -1f, NetSettings.CHARGE_EFFICIENCY to 20f, NetSettings.DAILY_GOAL to 0f))
        val bad = NetSettings.read(p)
        assertNull(bad.kwhPrice); assertEquals(90.0, bad.chargeEfficiencyPct, 0.0); assertNull(bad.dailyGoal)
        assertEquals(0.1, NetSettings.parse(" 0,1 ", 0.0, 5.0)!!, 0.0); assertNull(NetSettings.parse("abc", 0.0, 5.0)); assertNull(NetSettings.parse("9", 0.0, 5.0))
    }

    @Test fun notificationSummarisesNetAndGoal() {
        val r = DayReport.build(day(trip("a", 40.0, 10.0, tip = 2.0)), emptyList(), NetSettings.Prices(null, 90.0, 50.0))
        val (title, body) = DayReportCard.notification(r, ::tl)
        assertEquals("Gün sonu raporu · net 38.00₼", title)
        assertTrue(body.contains("Brüt 40.00 + bahşiş 2.00 − komisyon 4.00 − enerji 0.00"))
        assertTrue(body.contains("kWh fiyatı girilmedi") || body.contains("Enerji ölçülmedi"))
        assertTrue(body.contains("hedefe kalan 12.00₼"))
    }
}
