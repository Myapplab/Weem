package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/** NT `TripMeterTest.java` (S4; 19 çağrı, 19 çalışma). Per-trip totals for the small panel and the future KuryePlus order view. Synthetic samples only. */
class TripMeterTest : NinebotSuite() {
    private fun s(t: Long, speed: Double, w: Double, ip: Double, ep: Double?) =
        ScooterEnergy.Sample(5000 + t, t, ip, 52.0, w / 52.0, ep, if (ep == null) null else 44.0, if (ep == null) null else -2.0, w, 25.0, speed, null)

    @Test fun tripMeterAssertions() {
        val m = TripMeter(); val empty = m.stats()
        check(empty.wh == null && empty.avgMovingKmh == null && empty.pctUsed == null && empty.powerW == null, "nothing measured: nulls, not zeros")
        m.reset("order-1", 5000)
        // 10 min at 18 km/h and -400 W, then 5 min standing at -10 W.
        for (t in 0L..600000L step 1000) m.add(s(t, 18.0, -400.0, 80 - t / 600000.0 * 2, 60.0 - t / 600000.0))
        for (t in 601000L..900000L step 1000) m.add(s(t, 0.0, -10.0, 78.0, 59.0))
        val st = m.stats()
        check(st.label == "order-1", "label kept")
        check(abs(st.avgMovingKmh!! - 18) < 1e-9, "standing time excluded from the average speed")
        check(abs(st.km - 3.0) < 1e-9 && st.movingMs == 600000L, "10 min at 18 km/h = 3 km")
        val expected = 400 * 600000 / 3600000.0 + (400 + 10) / 2.0 * 1000 / 3600000.0 + 10 * 299000 / 3600000.0
        check(abs(st.wh!! - expected) < 1e-6, "Wh consumption positive incl. standby: ${st.wh}")
        check(abs(st.pctUsed!! - 3.0) < 1e-9, "internal 2 + external 1 percent points used")
        check(st.powerW == -10.0, "latest power, panel sign")
        // A Bluetooth gap adds nothing; reset clears totals.
        m.add(s(960000, 18.0, -400.0, 78.0, 59.0)); check(abs(m.stats().wh!! - expected) < 1e-6 && m.stats().movingMs == 600000L, "60 s gap not integrated")
        m.reset("order-2", 99); val fresh = m.stats(); check(fresh.wh == null && fresh.km == 0.0 && fresh.label == "order-2" && fresh.startedWallMs == 99L, "reset clears totals")
        m.add(s(970000, 10.0, -100.0, 77.0, null)); check(abs(m.stats().pctUsed!!) < 1e-9, "without external pack only internal percent counts")
        // KuryePlus delivery: only the meter of the delivered order ends (a later order keeps counting), and it ends without a label.
        val d = TripMeter(); d.reset("order-3", 1000); check(!d.end("order-2", 2000) && d.stats().label == "order-3", "another order's delivery keeps this meter")
        check(!d.end("", 2000) && !d.end(null, 2000), "no label ends nothing")
        check(d.end("order-3", 3000) && d.stats().label.isEmpty() && d.stats().startedWallMs == 3000L && d.stats().wh == null, "delivered order: meter counts on without its label")
        // D10 (13 Ekim): never negative. External pack known but not read: the internal pack alone is being fed (+W); that time is
        // counted apart, no energy is added and the percent used is unknown (the internal pack rises).
        val f = TripMeter(); f.reset("order-4", 0)
        for (t in 0L..60000L step 1000) f.add(ScooterEnergy.Sample(t, t, 70.0 + t / 60000.0, 52.0, 2.0, null, null, null, 104.0, 25.0, 18.0, null, true))
        val fed = f.stats()
        check(fed.wh == null && fed.inWh == null && fed.partialMs == 60000L && fed.coveredMs == 0L, "internal pack alone while an external pack is known: not counted, coverage kept")
        check(fed.pctUsed == null, "percent used unknown without the external pack's percent (internal rose 70→71)")
        // Both packs read: a charger step is energy in, never negative consumption.
        for (t in 61000L..120000L step 1000) f.add(ScooterEnergy.Sample(t, t, 71.0, 52.0, -6.0, 60.0, 44.0, -2.0, -400.0, 25.0, 18.0, null, true))
        for (t in 121000L..180000L step 1000) f.add(ScooterEnergy.Sample(t, t, 71.5, 52.0, 3.0, 60.2, 44.0, 0.5, 180.0, 25.0, 0.0, null, true))
        val both = f.stats()
        // The step from the last internal-only sample to the first both-pack sample is still partial (61 s in all).
        check(both.coveredMs == 119000L && both.partialMs == 61000L, "coverage: covered ${both.coveredMs} partial ${both.partialMs}")
        check(abs(both.wh!! - (400 * 59000 / 3600000.0 + (400 - 180) / 2.0 * 1000 / 3600000.0)) < 1e-9, "consumption from the both-pack steps: ${both.wh}")
        check(abs(both.inWh!! - 180 * 59000 / 3600000.0) < 1e-9, "charger inflow on its own: ${both.inWh}")
        check(both.pctUsed != null && both.pctUsed >= 0, "percent used never negative: ${both.pctUsed}")
        assertEquals("trip meter assertions passed; synthetic samples only", 19, count)
    }
}
