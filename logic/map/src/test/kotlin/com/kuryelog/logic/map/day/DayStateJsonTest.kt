package com.kuryelog.logic.map.day

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `yolgo_rules_test.dart` "eski sipariş JSON teslimat sırasında geriye sarılmaz" (1; A6.2b, MAP.md 11.5, 11.16): `restArrived` = `restLeft`, `customerDeparted` yok →
 * `toCustomer` okunur ve gidiş-dönüşte geri sarılmaz.
 */
class DayStateJsonTest {
    @Test
    fun `eski sipariş JSON teslimat sırasında geriye sarılmaz`() {
        val now = DayFixtures.ms("2026-10-02T12:00:00Z")
        val order = OrderRec.fromJson(JSONObject().put("n", 1).put("start", now).put("restArrived", now).put("restLeft", now))
        assertEquals(OrderPhase.TO_CUSTOMER, order.phase)
        val s = DayState().also { it.active = true; it.current = order; it.phase = order.phase }
        assertEquals(OrderPhase.TO_CUSTOMER, DayState.fromJson(s.toJson()).current!!.phase)
    }
}
