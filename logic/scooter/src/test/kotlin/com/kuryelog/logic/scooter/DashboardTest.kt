package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/** NT `DashboardTest.java` (S2; 14 çağrı, 14 çalışma). Segway-style dashboard frame: freshness, session max speed and the field values from 2026-10-07. */
class DashboardTest : NinebotSuite() {
    private fun read(key: String): NinebotMetrics.Read {
        for (r in NinebotMetrics.vehicle()) if (r.key == key) return r
        throw AssertionError(key)
    }

    @Test fun dashboardAssertions() {
        val m = NinebotMetrics(); val dash = NinebotDashboard()
        check(dash.snapshot(m.values(), 1000).speedKmh == null, "empty frame has no speed")
        val pack = NinebotMetrics.battery(0x07)
        // Raw bytes from the 2026-10-07 field log (session 14).
        m.accept(read("scooter_speed_kmh"), byteArrayOf(0x55, 0x00), 1000)
        m.accept(pack[0], byteArrayOf(0xb3.toByte(), 0x14), 1000)
        m.accept(pack[1], byteArrayOf(0x3c, 0x01), 1000)
        m.accept(read("battery_percent"), byteArrayOf(0x5e, 0x00), 1000)
        m.accept(read("remaining_range_km"), byteArrayOf(0xe6.toByte(), 0x0e), 1000)
        m.accept(read("dashboard_trip_km"), byteArrayOf(0x02, 0x00), 1000)
        m.accept(read("dashboard_ride_seconds"), byteArrayOf(0x58, 0x00), 1000)
        m.accept(read("vehicle_temperature_c"), byteArrayOf(0xdc.toByte(), 0x00), 1000)
        val s = dash.snapshot(m.values(), 1500)
        check(NinebotDashboard.speed(s.speedKmh) == "8.5", "speed shown with one decimal")
        check(NinebotDashboard.oneDecimal(s.voltageV) == "53.0" && NinebotDashboard.oneDecimal(s.currentA) == "3.2", "voltage and current tiles")
        check(NinebotDashboard.watts(s.powerW) == "167", "power is V×A in whole watts")
        check(NinebotDashboard.whole(s.batteryPct) == "94" && NinebotDashboard.oneDecimal(s.rangeKm) == "38.1", "range bar matches official 38.1 km / 94%")
        check(NinebotDashboard.oneDecimal(s.tripKm) == "0.2", "this ride matches official 0.2 KM")
        check(NinebotDashboard.ride(s.rideSec).contentEquals(arrayOf("1", "dk", "28", "sn")), "ride duration matches official 1 min 28 s")
        check(NinebotDashboard.whole(s.tempC) == "22", "vehicle temperature 22 °C")
        // Max speed is a session maximum and survives slower readings.
        m.accept(read("scooter_speed_kmh"), byteArrayOf(0xae.toByte(), 0x00), 2000); dash.snapshot(m.values(), 2000)
        m.accept(read("scooter_speed_kmh"), byteArrayOf(0x37, 0x00), 3000); val later = dash.snapshot(m.values(), 3000)
        check(NinebotDashboard.speed(later.speedKmh) == "5.5" && NinebotDashboard.whole(later.maxSpeedKmh) == "17", "max speed keeps session peak")
        // Stale fast values blank out instead of freezing on the last reading.
        val stale = dash.snapshot(m.values(), 3000 + NinebotMetrics.FAST_FRESH_MS + 1)
        check(stale.speedKmh == null && stale.powerW == null && NinebotDashboard.speed(stale.speedKmh) == NinebotDashboard.DASH, "stale speed/power shown as dash")
        check(NinebotDashboard.watts(-24.4) == "-24" && NinebotDashboard.watts(-0.3) == "0", "negative watts keep sign, no -0")
        check(NinebotDashboard.ride(42.0).contentEquals(arrayOf("42", "sn")) && NinebotDashboard.ride(3725.0).contentEquals(arrayOf("1", "sa", "2", "dk")), "ride formats under a minute and over an hour")
        check(NinebotDashboard.gauge(15.0) == 0.5f && NinebotDashboard.gauge(99.0) == 1f && NinebotDashboard.gauge(null) == 0f, "gauge fraction clamps")
        // External pack percent and odometer reach the snapshot for the floating panel choices.
        val ext = NinebotMetrics(); ext.accept(NinebotMetrics.battery(6)[2], byteArrayOf(58, 0), 1000); ext.accept(read("odometer_km"), byteArrayOf(0xd3.toByte(), 0xaa.toByte(), 0, 0), 1000)
        val e = NinebotDashboard().snapshot(ext.values(), 1500); check(NinebotDashboard.whole(e.extBatteryPct) == "58" && NinebotDashboard.oneDecimal(e.odometerKm) == "4373.1", "external pack % and odometer")
        assertEquals("dashboard assertions passed; no physical scooter was used", 14, count)
    }
}
