package com.kuryelog.logic.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Plan 4.3 arayüzleri (A2.5): üreticisi olmayan arayüz veri uydurmaz; modül dinleyicileri birbirinden korunur; bildirim numarası tablodan. */
class PortsTest {
    @Test fun noneImplementationsSayThereIsNoData() = runBlocking {
        assertTrue(OrderEvents.NONE.offers.value.isEmpty())
        assertEquals(TripStages.EMPTY, OrderEvents.NONE.stages.value)
        assertNull(OrderEvents.NONE.day.value)
        assertFalse(OrderEvents.NONE.busy())
        assertNull(ScooterBridge.NONE.energy(1L, 2L))
        assertNull(ScooterBridge.NONE.batteryPct())
        assertNull(ScooterBridge.NONE.live.value)
        assertEquals(0, ScooterBridge.NONE.steps.replayCache.size) // adım üretmez (SCOOTER.md 12.2)
        assertNull(NavPort.NONE.route.value)
        assertNull(WeatherPort.NONE.current())
    }

    @Test fun anOpenTripMakesTheShiftBusy() {
        val stages = kotlinx.coroutines.flow.MutableStateFlow(TripStages.EMPTY)
        val events = object : OrderEvents {
            override val offers = OrderEvents.NONE.offers
            override val stages = stages
            override val day = OrderEvents.NONE.day
        }
        assertFalse(events.busy())
        stages.value = TripStages(listOf(TripStages.Trip("t", "com.wolt.courierapp", true, TripStages.Stage.AT_DROPOFF, null, null, emptyList())))
        assertTrue(events.busy())
    }

    @Test fun listenersAreIsolatedAndNamed() {
        val failures = mutableListOf<String>()
        val l = ShiftListeners { FailureSink { source, _ -> failures += source } }
        val calls = mutableListOf<String>()
        val broken = object : ShiftMachine.Hooks {
            override fun onNewShift(nowMs: Long, reason: String) = error("orders broke")
            override fun busy() = error("busy broke")
        }
        l.add("orders", broken)
        l.add("scooter", object : ShiftMachine.Hooks {
            override fun onNewShift(nowMs: Long, reason: String) { calls += "scooter:$reason" }
            override fun odometer() = ShiftDistance.Odo(1.0, 2.0, 3L)
            override fun busy() = true
        })
        l.onNewShift(1L, "manual")
        assertEquals(listOf("scooter:manual"), calls)
        assertTrue(l.busy())
        assertEquals(ShiftDistance.Odo(1.0, 2.0, 3L), l.odometer())
        assertEquals(listOf("shift_start/orders", "busy/orders"), failures)
        l.remove(broken)
        l.onNewShift(2L, "home_departure")
        assertEquals(listOf("scooter:manual", "scooter:home_departure"), calls)
        assertEquals(2, failures.size)
        assertFalse(ShiftListeners { FailureSink { _, _ -> } }.busy())
    }

    @Test fun notificationsUseTheTable() {
        Notify(NotificationIds.WEATHER, "duzyol_weather", "Hava", "18°", timeoutMs = NotifyPolicy.TIMEOUT_MS)
        Notify(NotificationIds.cashTip("t1"), "cash_tips", "Bahşiş", "?", timeoutMs = 15_000)
        assertThrows(IllegalArgumentException::class.java) { Notify(4000, "cash_tips", "x", "y") }
        assertThrows(IllegalArgumentException::class.java) { Notify(7400, "yolgo_recording", "x", "y") }
    }
}
