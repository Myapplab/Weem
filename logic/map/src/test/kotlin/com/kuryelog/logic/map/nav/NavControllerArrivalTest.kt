package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteLeg
import com.kuryelog.logic.map.route.RoutePoint
import com.kuryelog.logic.map.route.RouteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `arrival_pin_test.dart` (3; A6.2b, MAP.md 7.8, 7.15): varınca durak silinir, `arrived` ve `reached` aynı durak; 90 sn kart zamanlayıcısı iğneyi silmez;
 * iğne yürürken kalır, 250 m uzaklaşınca kalkar; kötü doğruluklu fix silmez; yeni rota ve rota silme iğneyi kaldırır, durak eklemek kaldırmaz.
 * Sahte konum akışı yerine [NavController.onFix], sanal saat (90 sn) yerine [FakeClock] + [NavController.tick]; boş graf; sürüş günlüğü yok.
 */
class NavControllerArrivalTest {
    private val start = GeoPoint(40.4000, 49.8000)
    private val door = GeoPoint(40.4027, 49.8000) // ~300 m kuzey
    private val clock = FakeClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()
    private val nav = NavController(engine, scope, clock).also { it.setGraph(null) }

    @After
    fun close() {
        engine.dispose()
        scope.cancel()
    }

    /** [p] noktasından kuzeye [m] metre. */
    private fun north(p: GeoPoint, m: Double) = GeoPoint(p.lat + m / 111320.0, p.lon)

    private fun emitAt(p: GeoPoint, acc: Double = 5.0) = nav.onFix(NavFix(clock.nowMs, p.lat, p.lon, acc, speedMps = 1.0, headingDeg = 0.0))

    private fun plan(dest: GeoPoint) = Plan(
        RideMode.BATTERY, listOf(0),
        listOf(
            RouteLeg(
                ride = listOf(RoutePoint(start.lat, start.lon, 0.0), RoutePoint(dest.lat, dest.lon, 0.0)), walk = emptyList(), rideM = 300.0, walkM = 0.0,
                timeS = 60.0, energyWh = 4.0, climbM = 0.0, wrongWayM = 0.0, cost = 1.0, streets = emptyList(), destination = dest,
            ),
        ),
    )

    /** Rota hazır, kurye kapıya varıyor. */
    private fun arrive(): Stop {
        val stop = Stop(door, "Müşteri")
        nav.debugSetState(NavState(stops = listOf(stop), plans = mapOf(RideMode.BATTERY to plan(door))))
        emitAt(north(door, -10.0)) // kapıya 10 m
        return stop
    }

    @Test
    fun `Regresyon varınca hedef iğnesi kalır, 90 sn kart zamanlayıcısı onu silmez`() {
        val stop = arrive()
        var v = nav.state.value
        // 1.3.0: durak silinip plan temizleniyordu → haritada hiçbir iğne kalmıyordu.
        assertTrue(v.stops.isEmpty())
        assertFalse(v.hasRoute)
        assertSame(stop, v.arrived)
        assertSame(stop, v.reached)
        clock.advance(91_000)
        nav.tick()
        v = nav.state.value
        assertNull("kapı notu kartı 90 sn sonra kapanır", v.arrived)
        assertSame("iğne kart kapansa da kalır", stop, v.reached)
        nav.dismissArrived()
        assertSame("kartı kapatmak iğneyi silmez", stop, nav.state.value.reached)
    }

    @Test
    fun `iğne yürürken kalır, 250 m uzaklaşınca kalkar, kötü doğruluklu fix silmez`() {
        val stop = arrive()
        assertSame(stop, nav.state.value.reached)
        emitAt(north(door, 120.0))
        assertSame("bina/giriş aranırken kalır", stop, nav.state.value.reached)
        emitAt(north(door, 400.0), acc = 80.0)
        assertSame("GPS sıçraması (80 m doğruluk) iğneyi silmez", stop, nav.state.value.reached)
        emitAt(north(door, NavState.REACHED_KEEP_M + 30))
        assertNull(nav.state.value.reached)
    }

    @Test
    fun `yeni rota ve rota silme iğneyi kaldırır, durak eklemek kaldırmaz`() {
        val stop = arrive()
        nav.addStop(north(door, 500.0), label = "Ek")
        assertSame(stop, nav.state.value.reached)
        nav.goTo(north(door, 900.0), label = "Yeni müşteri")
        assertNull(nav.state.value.reached)
        arrive()
        assertNotNull(nav.state.value.reached)
        nav.clearRoute()
        assertNull(nav.state.value.reached)
        assertEquals(NavState.REACHED_KEEP_M, 250.0, 0.0)
    }
}
