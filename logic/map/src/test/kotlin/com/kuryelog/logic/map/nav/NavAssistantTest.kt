package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.day.DayController
import com.kuryelog.logic.map.day.DayStep
import com.kuryelog.logic.map.day.FollowerFeed
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.StageFixtures.TRIP_A
import com.kuryelog.logic.map.day.StageFixtures.trip
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.input.DestinationResolver
import com.kuryelog.logic.map.input.GeoConfidence
import com.kuryelog.logic.map.input.GeocodeHit
import com.kuryelog.logic.map.input.Geocoder
import com.kuryelog.logic.map.net.FakeHttp
import com.kuryelog.logic.map.net.HttpResponse
import com.kuryelog.logic.map.route.RouteEngine
import com.kuryelog.logic.map.tasks.DailyTask
import com.kuryelog.logic.map.tasks.TaskController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `nav_assistant_test.dart` (1; döngüyle 2: restoran=true/false; A6.2b, MAP.md 7.15, 16.3): restoran=true: konuma yakın dış hedef (<60 m) restoran olduğu için kabul
 * edilir, sipariş açmaz; aşamalar yalnız sipariş akışından (`to_pickup` → `toRestaurant`, `at_dropoff` → `atCustomer`, boş → teslim 1); teslimde yakındaki görev uyarısı (Y20).
 * restoran=false: hata, durak yok. Riverpod sağlayıcıları yerine üç saf denetleyici ([NavController], [DayController], [TaskController]) test kodunda bağlanır
 * (çalışma zamanı dikişi `DayRuntime`/`TaskRuntime`: teslim olayı → `TaskController.deliveryFinished`); `OrderEvents.stages` sahte [FollowerFeed] ile beslenir.
 */
class NavAssistantTest {
    private val here = GeoPoint(40.4, 49.8)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RouteEngine()

    private class PlaceGeocoder(private val restaurant: Boolean) : Geocoder {
        override suspend fun search(query: String, near: GeoPoint?) =
            GeocodeHit(GeoPoint(40.4, 49.8), if (restaurant) "Restoran" else "Mənzil", GeoConfidence.HIGH, "test", restaurant = restaurant)
    }

    @After
    fun close() {
        engine.dispose()
        scope.cancel()
    }

    @Test
    fun `yakın dış hedef ve gerçek akış restoran true ve false`() = runBlocking {
        for (restaurant in listOf(true, false)) {
            val clock = FakeClock()
            val nav = NavController(engine, scope, clock, DestinationResolver(FakeHttp { HttpResponse(500, "{}") }, PlaceGeocoder(restaurant)))
            nav.setGraph(null)
            val day = DayController(MemoryMapPrefs(), { Lang.TR })
            val tasks = TaskController(MemoryMapPrefs(), clock) { true }
            day.startDay(clock.nowMs)
            nav.setOrder(dayActive = true, currentOrder = null)
            nav.setOrigin(here)
            nav.onFix(NavFix(clock.nowMs, here.lat, here.lon, 5.0, speedMps = 4.0, headingDeg = 90.0))
            nav.onExternalText("Restoran, Bakı", "intent")
            if (!restaurant) {
                assertNull(day.view.state.current)
                assertNotNull(nav.state.value.error)
                assertTrue(nav.state.value.stops.isEmpty())
                continue
            }
            // Adres paylaşımı sipariş açmaz; sipariş ve aşamaları yalnız ekran okumasından gelir.
            assertNull(day.view.state.current)
            assertEquals(OrderPhase.NONE, day.view.state.phase)
            day.onFix(com.kuryelog.logic.map.day.DayFix(clock.nowMs, here.lat, here.lon, 4.0), clock.nowMs)
            assertNull(day.view.state.current)
            fun stages(vararg trips: com.kuryelog.logic.map.day.FollowerTrip): DayStep {
                clock.advance(5_000)
                return day.onStages(FollowerFeed(trips.toList()), clock.nowMs, here)
            }
            stages(trip(TRIP_A, Stage.TO_PICKUP))
            assertEquals(OrderPhase.TO_RESTAURANT, day.view.state.phase)
            tasks.add(DailyTask("bank", "Banka", "Filial", here, clock.nowMs))
            stages(trip(TRIP_A, Stage.AT_DROPOFF))
            assertEquals(OrderPhase.AT_CUSTOMER, day.view.state.phase)
            stages(); val last = stages()
            assertEquals(1, day.view.state.delivered)
            assertNull(day.view.state.current)
            if (last.events.any { it.type == "delivered" }) tasks.deliveryFinished(here, 5.0, clock.nowMs)
            assertEquals("bank", tasks.alerts.single().id)
        }
    }
}
