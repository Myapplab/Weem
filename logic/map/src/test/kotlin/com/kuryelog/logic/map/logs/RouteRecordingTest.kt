package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.nav.ReplanReason
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteLeg
import com.kuryelog.logic.map.route.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** Rota kaydı bağı (MAP.md 12.5, 12.13 "Yeni"): yeni rota öncekini `new_route` ile bitirir, `route_end` olayı, dosya yalnız ayar açık ve ≥ 2 iz noktasında. */
class RouteRecordingTest {
    private val lat0 = 40.40
    private val lon0 = 49.85
    private fun lat(n: Double) = lat0 + n / 111320
    private fun lon(e: Double) = lon0 + e / (111320 * cos(lat0 * Math.PI / 180))

    private fun plan(): Plan {
        val ride = (0..10).map { RoutePoint(lat(it * 50.0), lon0, 0.0) }
        val leg = RouteLeg(ride, emptyList(), 500.0, 0.0, 100.0, 1.0, 0.0, 0.0, 1.0, emptyList(), GeoPoint(lat(500.0), lon0))
        return Plan(RideMode.TIME, listOf(0), listOf(leg))
    }

    private class Rig(val enabled: Boolean = true) {
        var now = 1_000L
        val events = ArrayList<Pair<String, Map<String, Any?>>>()
        val files = ArrayList<Pair<Long, String>>()
        val speeds = ArrayList<Pair<Double?, Double?>>()
        val rec = RouteRecording({ now }, { enabled }, { n, d -> events += n to d }, { s, j -> files += s to j }, { a, m -> speeds += a to m })
    }

    private fun drive(rig: Rig, from: Int = 1, to: Int = 30) {
        for (i in from..to) {
            rig.now = 1_000L + i * 1000
            rig.rec.fix(NavFix(rig.now, lat(i * 5.0), lon0, 5.0, speedMps = 5.0, headingDeg = 0.0), 0.0)
        }
    }

    @Test
    fun `yeni rota öncekini new_route ile bitirir ve dosya yazar`() {
        val rig = Rig()
        rig.rec.planned(ReplanReason.ROUTE, plan(), 1_000)
        drive(rig)
        rig.rec.planned(ReplanReason.ROUTE, plan(), rig.now)
        assertEquals("route_end", rig.events.single().first)
        assertEquals("new_route", rig.events.single().second["reason"])
        assertEquals(0, rig.events.single().second["dev"])
        assertTrue((rig.events.single().second["m"] as Long) > 100)
        assertEquals(1_000L, rig.files.single().first)
        assertTrue(rig.files.single().second.contains("duzyol-route-v1"))
        assertTrue(rig.rec.active)
    }

    @Test
    fun `ayar kapalıyken ya da iz kısaysa dosya yok ama olay var, yeniden hesap aynı kaydı sürdürür`() {
        val off = Rig(enabled = false)
        off.rec.planned(ReplanReason.ROUTE, plan(), 1_000); drive(off); off.rec.finish("arrive", off.now)
        assertTrue(off.files.isEmpty()); assertEquals("arrive", off.events.single().second["reason"])
        val short = Rig()
        short.rec.planned(ReplanReason.ROUTE, plan(), 1_000)
        short.rec.fix(NavFix(2_000, lat(5.0), lon0, 5.0, 5.0, 0.0), 0.0)
        short.rec.finish("clear", 3_000)
        assertTrue(short.files.isEmpty()); assertEquals(1, short.events.size)
        val re = Rig()
        re.rec.planned(ReplanReason.ROUTE, plan(), 1_000); drive(re, 1, 5)
        re.rec.planned(ReplanReason.REROUTE, plan(), re.now)
        assertTrue(re.events.isEmpty())
        drive(re, 6, 30); re.rec.finish("arrive", re.now)
        assertTrue(re.files.single().second.contains("\"reroutes\":[{"))
        assertNull(Rig().also { it.rec.finish("clear", 1) }.files.firstOrNull())
    }

    @Test
    fun `plansız bacak kayıt açmaz, ortalama hız ekrana akar ve bitince sıfırlanır`() {
        val rig = Rig()
        rig.rec.planned(ReplanReason.ROUTE, Plan(RideMode.TIME, emptyList(), emptyList()), 1_000)
        assertTrue(!rig.rec.active)
        rig.rec.planned(ReplanReason.ROUTE, plan(), 1_000); drive(rig)
        assertTrue(rig.speeds.last().first!! > 10)
        rig.rec.finish("arrive", rig.now)
        assertEquals(null to null, rig.speeds.last())
    }
}
