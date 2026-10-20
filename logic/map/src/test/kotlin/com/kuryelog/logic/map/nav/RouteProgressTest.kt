package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.nav.NavFixtures.T0
import com.kuryelog.logic.map.nav.NavFixtures.fix
import com.kuryelog.logic.map.nav.NavFixtures.plan
import com.kuryelog.logic.map.nav.NavFixtures.planWalk
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.core.RouteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `nav_feed_test.dart` "kalan rota" (2) + `remaining_km_e12_test.dart` ölçüm kuralları (4; metin/gösterge kısmı 3 test `:app` `MapMetricsTest`'te;
 * A6.2b, MAP.md 7.9, 7.15): ölçüm = etkin bacağın sürüş çizgisine izdüşüm; kalan = çizgide kalan + yürüme; yalnız doğruluğu ≤50 m fix'ler ölçülür.
 */
class RouteProgressTest {
    @Test
    fun `kalan sürüş ve yürüme ile izdüşümden sonraki dönüşler`() {
        val start = RouteProgress.measure(plan(), fix(0.0))!!
        assertEquals(1040.0, start.remainingM, 1.0)
        assertEquals(2, start.remainingTurns)
        assertTrue(start.onRoute)
        assertEquals(T0, start.measuredAtMs)
        val mid = RouteProgress.measure(plan(), fix(500.0, eastM = 8.0))!!
        assertEquals(540.0, mid.remainingM, 1.0)
        assertEquals(1, mid.remainingTurns)
        assertEquals(0, RouteProgress.measure(plan(), fix(800.0))!!.remainingTurns)
        assertFalse(RouteProgress.measure(plan(), fix(500.0, eastM = 120.0))!!.onRoute)
    }

    @Test
    fun `rota ya da konum yoksa null`() {
        assertNull(RouteProgress.measure(null, fix(0.0)))
        assertNull(RouteProgress.measure(plan(), null))
        assertNull(RouteProgress.measure(com.kuryelog.logic.map.route.Plan(com.kuryelog.logic.map.route.RideMode.TIME, emptyList(), emptyList()), fix(0.0)))
    }

    @Test
    fun `aynı fix görünür kalan km ile yayındaki kalan sürüş artı yürüme aynıdır`() {
        val p = planWalk()
        val tracker = RouteProgressTracker()
        tracker.setPlan(p)
        val gate = RouteStatusGate()
        val published = ArrayList<RouteStatus>()
        val target = RouteStatusBuilder.target(Stop(GeoPoint(40.4, 49.85), "Hedef"), T0)
        var previous: Double? = null
        for (m in listOf(0.0, 300.0, 500.0, 800.0)) {
            val f = fix(m, atMs = T0 + m.toLong() * 1000)
            tracker.onFix(f)
            val shown = tracker.measure!!
            assertEquals(RouteProgress.measure(p, f)!!.remainingM, shown.remainingM, 0.0)
            if (gate.due(target, shown, f.atMs)) {
                gate.sent(target, shown, f.atMs)
                published += RouteStatusBuilder.status(target, shown, f.atMs)
            }
            assertEquals("yayın = gösterge", shown.remainingM, published.last().route!!.remainingM, 0.0)
            previous?.let { assertTrue(shown.remainingM < it) }
            previous = shown.remainingM
        }
        assertEquals("kalan 0,2 km (800 m'de 240 m)", 240.0, tracker.measure!!.remainingM, 1.0)
    }

    @Test
    fun `20 m yayın eşiğinin altında da her fix ölçümde tazelenir`() {
        val p = planWalk()
        val tracker = RouteProgressTracker()
        tracker.setPlan(p)
        val gate = RouteStatusGate()
        val target = RouteStatusBuilder.target(Stop(GeoPoint(40.4, 49.85), "Hedef"), T0)
        tracker.onFix(fix(480.0))
        gate.sent(target, tracker.measure, T0)
        val first = tracker.measure!!.remainingM
        tracker.onFix(fix(495.0, atMs = T0 + 1000))
        assertEquals("ölçüm 15 m ilerler", first - 15, tracker.measure!!.remainingM, 1.0)
        assertFalse("E8 yayın eşiği göstergeyi yavaşlatmaz, kendisi 20 m bekler", gate.due(target, tracker.measure, T0 + 1000))
    }

    @Test
    fun `rota dışına çıkınca onRoute düşer, geri gelince döner, rota silinince ölçüm biter`() {
        val tracker = RouteProgressTracker()
        tracker.setPlan(planWalk())
        tracker.onFix(fix(500.0))
        assertTrue(tracker.measure!!.onRoute)
        tracker.onFix(fix(500.0, eastM = 120.0, atMs = T0 + 1000))
        assertFalse(tracker.measure!!.onRoute)
        tracker.onFix(fix(510.0, atMs = T0 + 2000))
        assertTrue(tracker.measure!!.onRoute)
        tracker.setPlan(null)
        assertNull(tracker.measure)
    }

    @Test
    fun `rota ya da fix yoksa ölçüm yok, yeni bacak son fix ile yeniden ölçülür, kaba konum yok sayılır`() {
        val tracker = RouteProgressTracker()
        tracker.onFix(fix(500.0))
        assertNull("rota yok", tracker.measure)
        tracker.setPlan(planWalk())
        assertNotNull("açılışta son konum ölçülür", tracker.measure)
        tracker.onFix(fix(500.0, acc = 80.0, atMs = T0 + 1000))
        assertEquals("doğruluğu >50 m fix ölçmez", T0, tracker.measure!!.measuredAtMs)
        val before = tracker.measure!!.remainingM
        tracker.setPlan(planWalk(walkM = 240.0))
        assertEquals("yeni bacak son fix'le yeniden ölçülür", before + 200, tracker.measure!!.remainingM, 1.0)
    }
}
