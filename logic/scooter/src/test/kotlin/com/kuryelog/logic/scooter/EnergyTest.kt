package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/** NT `EnergyTest.java` (S4, R14; 20 çağrı, 20 çalışma). Energy block for the KuryePlus merge: keys, sign, gap handling and the external-pack flags. Synthetic samples only. */
class EnergyTest : NinebotSuite() {
    private fun s(t: Long, w: Double, extA: Double?) = ScooterEnergy.Sample(
        1000000 + t, t, 80.0, 52.0, -w / 52.0, if (extA == null) null else 60.0, if (extA == null) null else 44.0, extA, w, 25.0, 18.0, 4373.0 + t / 3600000.0 * 18,
    )

    @Test fun energyBlockAssertions() {
        val e = ScooterEnergy()
        val empty = e.block(0, Long.MAX_VALUE)
        check(empty["source"] == "not_measured" && empty["wh"] == null && false == empty["power_verified"], "no samples: not_measured, nulls")
        check(ArrayList(empty.keys) == listOf("source", "power_verified", "internal", "external", "external_attached", "external_feeding", "vxa_w_avg", "wh", "esc_w_avg", "temp_c", "scooter_speed_kmh_avg", "odometer_km_start", "odometer_km_end"), "keys match KuryePlus EnergySegments.energy()")
        check(ArrayList((empty["internal"] as Map<*, *>).keys) == listOf("pct_start", "pct_end", "v_avg", "a_avg"), "pack keys match")
        // One hour at a steady -360 W (discharge) is 360 Wh of consumption.
        for (t in 0L..3600000L step 1000) e.add(s(t, -360.0, null))
        val hour = e.block(1000000, 1000000 + 3600000)
        check(abs(hour["wh"] as Double - 360) < 1e-6 && abs(hour["vxa_w_avg"] as Double - 360) < 1e-6, "360 W for an hour = 360 Wh, consumption positive")
        check(hour["source"] == "surus_paneli" && hour["external_attached"] == null && hour["external_feeding"] == null, "no external data: flags stay null")
        check(abs(hour["odometer_km_end"] as Double - hour["odometer_km_start"] as Double - 18) < 1e-6, "odometer start/end")
        // A 30 s Bluetooth gap is not integrated; a window that is mostly gap reports null instead of a partial figure.
        val g = ScooterEnergy(); for (t in 0L..10000L step 1000) g.add(s(t, -100.0, null)); for (t in 40000L..50000L step 1000) g.add(s(t, -100.0, null))
        check(g.block(1000000, 1050000)["wh"] == null, "window mostly gap: wh null")
        check(abs(g.block(1000000, 1010000)["wh"] as Double - 100.0 * 10 / 3600) < 1e-9, "covered part integrates")
        // A link that connected 20 s into the window (or dropped 20 s before its end) has no start (end) battery or odometer:
        // never the first value it happened to see. Within EDGE_MS the nearest value counts.
        val late = ScooterEnergy(); for (t in 20000L..60000L step 1000) late.add(s(t, -100.0, null)); val lb = late.block(1000000, 1060000)
        check((lb["internal"] as Map<*, *>)["pct_start"] == null && lb["odometer_km_start"] == null, "late link: no start values")
        check((lb["internal"] as Map<*, *>)["pct_end"] != null && lb["odometer_km_end"] != null && lb["wh"] == null, "late link: end values kept, partial energy null")
        val eb = late.block(1020000, 1080000); check((eb["internal"] as Map<*, *>)["pct_end"] == null && lb["odometer_km_end"] != null && eb["odometer_km_end"] == null, "early drop: no end values")
        val near = late.block(1010000, 1060000); check((near["internal"] as Map<*, *>)["pct_start"] != null && near["odometer_km_start"] != null, "first sample 10 s in: start values kept")
        // External pack discharging: attached and feeding.
        val x = ScooterEnergy(); for (t in 0L..5000L step 1000) x.add(s(t, -500.0, -4.0)); val ext = x.block(1000000, 1005000)
        check(true == ext["external_attached"] && true == ext["external_feeding"] && abs((ext["external"] as Map<*, *>)["a_avg"] as Double + 4) < 1e-9, "external pack feeding detected")
        val idle = ScooterEnergy(); for (t in 0L..5000L step 1000) idle.add(s(t, -10.0, 0.0)); check(false == idle.block(1000000, 1005000)["external_feeding"], "attached but idle external pack is not feeding")
        // Samples out of order or empty are ignored; the buffer is bounded.
        val before = x.size; x.add(s(2000, -1.0, null)); x.add(ScooterEnergy.Sample(0, 99999999, null, null, null, null, null, null, null, null, null, null)); check(x.size == before, "stale/empty samples ignored")
        val big = ScooterEnergy(); for (t in 0L until ScooterEnergy.CAPACITY + 50L) big.add(s(t * 1000, -1.0, null)); check(big.size == ScooterEnergy.CAPACITY, "bounded history")
        // From live register values (field bytes 2026-10-07): internal pack and speed.
        val m = NinebotMetrics(); m.accept(NinebotMetrics.voltage(7), byteArrayOf(0x8b.toByte(), 0x14), 500); m.accept(NinebotMetrics.battery(7)[1], byteArrayOf(0xec.toByte(), 0xff.toByte()), 500)
        val live = ScooterEnergy.sample(m.values(), 7, 600, 6); check(abs(live.intV!! - 52.59) < 1e-9 && abs(live.powerW!! - 52.59 * -0.20) < 1e-9 && live.extV == null, "sample from register values")
        // D10 (13 Ekim): external pack known but not read (only the internal pack, being fed: +104 W): no negative Wh, a coverage
        // gap; with both packs read again the steps count.
        val q = ScooterEnergy()
        for (t in 0L..60000L step 1000) q.add(ScooterEnergy.Sample(2000000 + t, t, 70.0, 52.0, 2.0, null, null, null, 104.0, 25.0, 18.0, 4400.0, true))
        val gap = q.block(2000000, 2060000)
        check("surus_paneli" == gap["source"] && gap["wh"] == null && gap["vxa_w_avg"] == null, "internal pack alone while the external one is known: no Wh, not negative")
        for (t in 61000L..300000L step 1000) q.add(ScooterEnergy.Sample(2000000 + t, t, 70.0, 52.0, -4.0, 60.0, 44.0, -3.0, -340.0, 25.0, 18.0, 4400.0, true))
        val wh = q.block(2061000, 2300000)["wh"] as Double?
        check(wh != null && abs(wh - 340 * 239000 / 3600000.0) < 1e-9, "both packs read: consumption counted, positive: $wh")
        check(q.block(2000000, 2120000)["wh"] == null, "half the window on one pack only: under 80% coverage, null")
        assertEquals("energy block assertions passed; synthetic samples only", 20, count)
    }
}
