package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.route.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.cos

/** YolGo `v140_features_test.dart` "rota kaydı" 2 test (A6.2b, MAP.md 12.5, 12.13): sapma iki fix sonra açılır, dönünce kapanır; yeniden hesap sapmayı kapatır; adres yazılmaz. */
class RouteRecorderTest {
    private val t0 = Instant.parse("2026-10-06T12:00:00Z").toEpochMilli()
    private val lat0 = 40.40
    private val lon0 = 49.85
    private fun lat(n: Double) = lat0 + n / 111320
    private fun lon(e: Double) = lon0 + e / (111320 * cos(lat0 * Math.PI / 180))
    private fun line(fromN: Double, toN: Double) = generateSequence(fromN) { it + 50 }.takeWhile { it <= toN }.map { RoutePoint(lat(it), lon0, 0.0) }.toList()

    @Test
    fun `sapma iki fix sonra açılır, dönünce kapanır, ortalama hız`() {
        val r = RouteRecorder(t0, "time", line(0.0, 1000.0), GeoPoint(lat(1000.0), lon0), plannedM = 1000.0, plannedS = 130.0)
        var t = t0
        for (i in 1..60) {
            t = t0 + i * 1000L
            val north = i * 5.0
            val east = if (i in 20 until 30) 80.0 else 0.0 // 10 sn rotadan 80 m sapma
            r.addFix(t, lat(north), lon(east), accuracy = 5.0, speedMps = 5.0, off = east)
        }
        assertEquals(1, r.deviations.size)
        assertNotNull(r.deviations.single().endMs)
        assertEquals(80.0, r.deviations.single().maxOffM, 0.0)
        val avg = r.avgKmh(t)!!
        assertTrue(avg > 17)
        val j = r.toJson(t, "arrive")
        assertEquals("duzyol-route-v1", j.getString("format"))
        assertEquals(1, j.getJSONArray("deviations").length())
        assertFalse("adres metni yazılmaz", j.toString().contains("label"))
    }

    @Test
    fun `yeniden hesaplama sapmayı kapatır ve yeni çizgi eklenir`() {
        val r = RouteRecorder(t0, "battery", line(0.0, 500.0), GeoPoint(lat(500.0), lon0), plannedM = 500.0, plannedS = 60.0)
        r.addFix(t0 + 1000, lat(5.0), lon(60.0), off = 60.0)
        r.addFix(t0 + 2000, lat(10.0), lon(60.0), off = 60.0)
        assertEquals(1, r.deviations.size)
        r.replanned(t0 + 3000, "reroute", line(0.0, 600.0))
        assertNotNull(r.deviations.single().endMs)
        assertEquals(2, r.plans.size)
    }
}
