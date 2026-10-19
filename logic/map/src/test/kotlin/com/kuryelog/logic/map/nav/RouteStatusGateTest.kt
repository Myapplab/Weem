package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFixtures.T0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `nav_feed_test.dart` hedef (2), yük (1), yayın kapısı (1) testleri (A6.2b, MAP.md 7.9, 7.15). Uyarlama: yük JSON'u (`source: "yolgo"`,
 * `generatedAtMs`, `onRoute`) yerine `logic/core` [RouteStatus] (`NavPort.route`) alanları denetlenir; `onRoute` iç alandır, `RouteStatus.Route`'ta yoktur.
 */
class RouteStatusGateTest {
    private val t0 = T0

    @Test
    fun `aktif durak tür YolGo tahmini kaynak istek saati ve gönderen koordinat 6 ondalık`() {
        val stop = Stop(GeoPoint(40.4091234567, 49.8673121), "R", restaurant = true, origin = NavOrigin.INTENT, requestedAtMs = t0, sourcePackage = "com.wolt.courierapp")
        val t = RouteStatusBuilder.target(stop, seenAtMs = t0 + 60_000)!!
        assertEquals(RouteStatus.Target(40.409123, 49.867312, NavKind.RESTAURANT, NavOrigin.INTENT, t0, "com.wolt.courierapp"), t)
        assertEquals(NavKind.CUSTOMER, RouteStatusBuilder.target(Stop(GeoPoint(40.4, 49.8), "M", restaurant = false), seenAtMs = t0)!!.kind)
        val unknown = RouteStatusBuilder.target(Stop(GeoPoint(40.4, 49.8), "X"), seenAtMs = t0)!!
        // istek saati bilinmiyorsa hedefin ilk görüldüğü an
        assertEquals(listOf(NavKind.UNKNOWN, NavOrigin.MAP, t0, null), listOf(unknown.kind, unknown.origin, unknown.requestedAtMs, unknown.sourcePackage))
    }

    @Test
    fun `rota yoksa ya da ev rotasıysa hedef yok (ev koordinatı gönderilmez)`() {
        assertNull(RouteStatusBuilder.target(null, seenAtMs = t0))
        assertNull(RouteStatusBuilder.target(Stop(GeoPoint(40.4, 49.8), "Ev", restaurant = false, purpose = StopPurpose.HOME), seenAtMs = t0))
    }

    @Test
    fun `yük v1 RouteStatus alanları, 4000 karakter`() {
        val target = RouteStatusBuilder.target(
            Stop(GeoPoint(40.409123, 49.867312), "R", restaurant = true, origin = NavOrigin.INTENT, requestedAtMs = t0, sourcePackage = "com.wolt.courierapp"), t0,
        )
        val measure = RouteProgress.measure(NavFixtures.plan(), NavFixtures.fix(0.0, atMs = t0 + 9_000))
        val status = RouteStatusBuilder.status(target, measure, updatedAtMs = t0 + 9_000)
        val raw = status.toJson().toString()
        assertTrue(raw.length <= RouteStatus.FEED_MAX_CHARS)
        val j = status.toJson()
        assertEquals(listOf("schemaVersion", "target", "route", "updatedAtMs"), j.keys().asSequence().toList())
        assertEquals(listOf("lat", "lon", "kind", "origin", "requestedAtMs", "sourcePackage"), j.getJSONObject("target").keys().asSequence().toList())
        assertEquals(listOf("remainingM", "remainingTurns", "measuredAtMs"), j.getJSONObject("route").keys().asSequence().toList())
        assertEquals(t0 + 9_000, j.getLong("updatedAtMs"))
        val none = RouteStatusBuilder.status(null, null, t0).toJson()
        assertTrue(none.isNull("target"))
        assertTrue(none.isNull("route"))
    }

    @Test
    fun `yayın hedef dönüş sayısı ya da 20 m değişince, rotadayken 10 sn de bir`() {
        val g = RouteStatusGate()
        val a = RouteStatusBuilder.target(Stop(GeoPoint(40.4, 49.8), "A", restaurant = true), seenAtMs = t0)
        val b = RouteStatusBuilder.target(Stop(GeoPoint(40.41, 49.8), "B", restaurant = true), seenAtMs = t0)
        fun r(m: Double, turns: Int = 2, on: Boolean = true) = RouteMeasure(m, turns, measuredAtMs = 1, onRoute = on)
        fun at(s: Int) = t0 + s * 1000L
        assertTrue(g.due(null, null, at(0))) // açılış: hedef yok da bir kez söylenir
        g.sent(null, null, at(0))
        assertFalse(g.due(null, null, at(60)))
        assertTrue(g.due(a, null, at(1)))
        g.sent(a, null, at(1))
        assertTrue(g.due(a, r(900.0), at(2))) // rota hesaplandı
        g.sent(a, r(900.0), at(2))
        assertFalse(g.due(a, r(885.0), at(3))) // 15 m
        assertTrue(g.due(a, r(880.0), at(4))) // 20 m
        assertTrue(g.due(a, r(899.0, turns = 1), at(4))) // dönüş geçildi
        assertFalse(g.due(a, r(895.0), at(11)))
        assertTrue(g.due(a, r(895.0), at(12))) // rotadayken 10 sn
        assertFalse(g.due(a, r(895.0, on = false), at(12))) // rota dışında heartbeat yok
        assertTrue(g.due(b, r(895.0), at(5))) // yeni hedef
        assertTrue(g.due(null, null, at(5))) // rota silindi
    }
}
