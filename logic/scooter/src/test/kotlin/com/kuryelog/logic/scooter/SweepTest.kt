package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/** NT `SweepTest.java` (S8, S2; 21 çağrı, 24 çalışma). Pure checks for the opt-in register sweep range and the derived voltage×current power. */
class SweepTest : NinebotSuite() {
    /** As in the original: counts without `check`, only [IllegalArgumentException] passes. */
    private fun rejects(r: () -> Unit, label: String) {
        try {
            r()
        } catch (e: IllegalArgumentException) {
            count++
            return
        }
        throw AssertionError(label)
    }

    @Test fun sweepAndPowerAssertions() {
        // Discovery sweep: inclusive int16 raw reads over one board, flagged so unanswered registers never tear down the session.
        val spec = NinebotMetrics.Sweep(0x16, 0x00, 0xc0)
        val reads = NinebotMetrics.sweep(spec)
        check(reads.size == 0xc1, "sweep covers inclusive range")
        val first = reads[0]; val mid = reads[0x6a]
        check(first.board == 0x16 && first.index == 0 && first.size == 2 && first.sweep && first.key == "sweep_16_00", "sweep read shape")
        check(mid.index == 0x6a && mid.key == "sweep_16_6a", "sweep key names the register")
        check(NinebotMetrics.decode(mid, byteArrayOf(0xd2.toByte(), 0x04)) == 1234.0, "sweep decodes little-endian int16")
        check(NinebotMetrics.decode(mid, byteArrayOf(0x00, 0x80.toByte())) == -32768.0, "sweep decodes signed negative")
        check(!NinebotMetrics.vehicle()[0].sweep, "mapped reads are not sweep reads")
        rejects({ NinebotMetrics.Sweep(0x16, 0xc0, 0x00) }, "reversed sweep range rejected")
        rejects({ NinebotMetrics.Sweep(0x16, 0, 300) }, "out-of-byte sweep register rejected")
        rejects({ NinebotMetrics.Sweep(-1, 0, 1) }, "invalid sweep board rejected")

        // Derived dashboard power = freshest same-pack voltage × current, sign preserved, stale inputs ignored.
        val volt = NinebotMetrics.voltage(7); val curr = NinebotMetrics.battery(7)[1]
        val m = NinebotMetrics()
        m.accept(volt, byteArrayOf(0x90.toByte(), 0x14), 1000) // 0x1490=5264 -> 52.64 V
        m.accept(curr, byteArrayOf(0xec.toByte(), 0xff.toByte()), 1000) // 0xffec=-20 -> -0.20 A
        val power = NinebotMetrics.powerW(m.values(), 1000, 5000)
        check(power != null && abs(power - (52.64 * -0.20)) < 1e-9, "power equals voltage times current with sign")
        check(NinebotMetrics.powerW(m.values(), 9000, 5000) == null, "stale voltage and current produce no power")
        val only = NinebotMetrics(); only.accept(volt, byteArrayOf(0x90.toByte(), 0x14), 1000)
        check(NinebotMetrics.powerW(only.values(), 1000, 5000) == null, "voltage without current produces no power")
        val mixed = NinebotMetrics(); mixed.accept(volt, byteArrayOf(0x90.toByte(), 0x14), 1000); mixed.accept(NinebotMetrics.battery(0x22)[1], byteArrayOf(0xec.toByte(), 0xff.toByte()), 1000)
        check(NinebotMetrics.powerW(mixed.values(), 1000, 5000) == null, "voltage and current from different packs are not multiplied")

        // Candidate dashboard registers: speed 0x57 (×0.1 km/h), trip 0x68 (×0.1 km), ride 0x6A (seconds) — the last two per field log 2026-10-07.
        val vehicle = NinebotMetrics.vehicle()
        val speedR = vehicle[6]; val tripR = vehicle[7]; val rideR = vehicle[8]
        check(speedR.board == 0x16 && speedR.index == 0x57 && speedR.size == 2 && speedR.key == "scooter_speed_kmh" && !speedR.sweep, "speed candidate shape")
        check(tripR.index == 0x68 && tripR.key == "dashboard_trip_km", "trip candidate shape")
        check(rideR.index == 0x6a && rideR.key == "dashboard_ride_seconds" && rideR.unit == "s", "ride time candidate shape")
        check(NinebotMetrics.decode(speedR, byteArrayOf(0xeb.toByte(), 0x00)) == 23.5, "speed register scales to km/h")
        // Field 2026-10-07: raw 0x0002 while the official app showed "0.2 KM"; raw 0x004c=76 after ~73 s of riding.
        check(abs(NinebotMetrics.decode(tripR, byteArrayOf(0x02, 0x00))!! - 0.2) < 1e-9, "trip register is 0.1 km per step (official 0.2 km)")
        check(abs(NinebotMetrics.decode(tripR, byteArrayOf(0xe8.toByte(), 0x03))!! - 100.0) < 1e-9, "trip register scales to km")
        check(NinebotMetrics.decode(rideR, byteArrayOf(0x4c, 0x00)) == 76.0, "ride register reads seconds")
        check(NinebotMetrics.decode(speedR, byteArrayOf(0xff.toByte(), 0x7f)) == null, "speed above max rejected")

        // freshValue feeds the top-level dashboard summary from a candidate register only while fresh.
        val dash = NinebotMetrics(); dash.accept(speedR, byteArrayOf(0xeb.toByte(), 0x00), 2000)
        check(NinebotMetrics.freshValue(dash.values(), "scooter_speed_kmh", 2000, 5000) == 23.5, "freshValue returns the speed candidate")
        check(NinebotMetrics.freshValue(dash.values(), "scooter_speed_kmh", 9000, 5000) == null, "stale speed is not reported")
        check(NinebotMetrics.freshValue(dash.values(), "dashboard_power_w", 2000, 5000) == null, "missing key returns null")
        assertEquals("sweep and power assertions passed; no physical scooter was used", 24, count)
    }
}
