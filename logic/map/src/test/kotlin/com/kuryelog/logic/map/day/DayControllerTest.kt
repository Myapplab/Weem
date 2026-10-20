package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.day.StageFixtures.NOW0
import com.kuryelog.logic.map.day.StageFixtures.TRIP_A
import com.kuryelog.logic.map.day.StageFixtures.feed
import com.kuryelog.logic.map.day.StageFixtures.trip
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `home_day_controller_test.dart` 4. test (1; A6.2b, MAP.md 11.7, 11.14, 11.16) — UYARLANIR: gün açık; okuma yok → sipariş yok; `to_pickup` → `toRestaurant`; `at_pickup`
 * → `atRestaurant` + toast "Restorana vardın — bekleme başladı" (M-11-1: " (KuryePlus)" eki kalktı); `courier.follow.v1` `stage = at_pickup`, restoran adı yok.
 * Ayar kısmı (eski `followCourierStages: false`) düşer: WEEM'de ayar yok (MAP.md 14.2). Sahte aşama okuması ([FollowerFeed]) ve sahte tercih deposu.
 */
class DayControllerTest {
    @Test
    fun `ekran okuması aşamaları siparişi ilerletir, okuma yoksa hiçbir şey değişmez, toast ve kalıcı durum`() {
        val prefs = MemoryMapPrefs()
        val c = DayController(prefs) { Lang.TR }
        c.startDay(NOW0)
        c.onStages(null, NOW0, null) // okuma yok
        assertNull(c.view.state.current)
        c.onStages(feed(trip(TRIP_A, Stage.TO_PICKUP)), NOW0 + 1_000, null)
        assertEquals(OrderPhase.TO_RESTAURANT, c.view.state.phase)
        val step = c.onStages(feed(trip(TRIP_A, Stage.AT_PICKUP)), NOW0 + 2_000, null)
        assertEquals(OrderPhase.AT_RESTAURANT, c.view.state.phase)
        assertTrue(step.toasts.any { it.text == "Restorana vardın — bekleme başladı" })
        val follow = prefs.getString("courier.follow.v1")!!
        assertEquals("at_pickup", JSONObject(follow).getString("stage"))
        assertFalse(follow.contains("Izmir"))
    }
}
