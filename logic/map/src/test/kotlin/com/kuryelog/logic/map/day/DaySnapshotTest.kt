package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A10.4i (SORU-11-2 a, S35 #17): Receiver'ın günü Provider'a kısaltılmış gider; koordinat ve duruş yok, Bluetooth'a sığar; rapor adı denetlenir. */
class DaySnapshotTest {
    private val t0 = 1_792_000_000_000L

    private fun order(n: Int, delivered: Boolean) = OrderRec(n, t0 + n * 600_000L).apply {
        restArrivedAtMs = startedAtMs + 60_000; restLeftAtMs = startedAtMs + 120_000; restName = "Borani $n"
        restLat = 40.41; restLon = 49.86; custLat = 40.37; custLon = 49.89
        restTarget = GeoPoint(40.41, 49.86); customerTarget = GeoPoint(40.37, 49.89); distM = 2_100.0
        if (delivered) deliveredAtMs = startedAtMs + 400_000
    }

    private fun day(orders: Int) = DayState().apply {
        active = true; startedAtMs = t0; distM = 12_345.0; movingS = 3_600.0
        repeat(orders) { this.orders += order(it + 1, delivered = true) }
        current = order(orders + 1, delivered = false); this.orders += current!!; phase = OrderPhase.TO_CUSTOMER
        repeat(30) { stops += StopRec.fromJson(JSONObject().put("start", t0 + it).put("end", t0 + it + 90_000).put("lat", 40.4).put("lon", 49.8).put("kind", "light")) }
        delivered = orders
    }

    @Test fun keepsTheCardButNoStopsOrCoordinates() {
        val text = DaySnapshot.encode(DayView(day(5), t0 + 5_000), t0 + 10_000)
        assertFalse(text.contains("40.37")); assertFalse(text.contains("49.89")); assertFalse(text.contains("Target"))
        assertTrue(text.length <= DaySnapshot.MAX_CHARS)
        val v = DaySnapshot.decode(text)!!
        assertTrue(v.state.active); assertEquals(t0, v.state.startedAtMs); assertEquals(12_345.0, v.state.distM, 0.6)
        assertEquals(t0 + 5_000, v.lightSinceMs)
        assertEquals(6, v.state.orders.size); assertEquals(5, v.state.delivered)
        assertEquals(6, v.state.current!!.n); assertEquals(OrderPhase.TO_CUSTOMER, v.state.phase); assertEquals("Borani 6", v.state.current!!.restName)
        assertTrue(v.state.stops.isEmpty()); assertNull(v.state.current!!.custLat)
    }

    @Test fun aLongDayDropsTheOldestFinishedOrdersToFit() {
        val text = DaySnapshot.encode(DayView(day(60), null), t0)
        assertTrue(text.length <= DaySnapshot.MAX_CHARS)
        val v = DaySnapshot.decode(text)!!
        assertEquals(61, v.state.current!!.n)
        assertTrue(v.state.orders.size < 61); assertEquals(61, v.state.orders.last().n)
    }

    @Test fun badOrForeignTextIsIgnored() {
        for (bad in listOf(null, "", "{", "{\"schemaVersion\":2,\"state\":{}}", "{\"schemaVersion\":1}")) assertNull(bad, DaySnapshot.decode(bad))
    }

    @Test fun reportMessagesCarryOnlyDayReportFiles() {
        val report = day(2).toJson().toString(1)
        val msg = DaySnapshot.report("2026-10-22_0930.gun.json", report)!!
        val (name, body) = DaySnapshot.readReport(msg)!!
        assertEquals("2026-10-22_0930.gun.json", name)
        assertEquals(t0, DayState.fromJson(JSONObject(body)).startedAtMs)
        for (bad in listOf("../x.gun.json", "2026-10-22.jsonl", "2026-10-22_route_5.json", "2026-10-22_0930.gun.json/..")) {
            assertNull(bad, DaySnapshot.report(bad, report))
            assertNull(bad, DaySnapshot.readReport(JSONObject(msg).put("name", bad).toString()))
        }
        assertNull(DaySnapshot.report("2026-10-22_0930.gun.json", "{oops"))
        assertNull(DaySnapshot.readReport(JSONObject(msg).put("report", "{}").toString()))
    }
}
