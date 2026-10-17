package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 21 (16 Ekim, dördüncü tur): Wolt ana ekranındaki boost bazen yanlış ya da geç; "Earn extra → Coming up later"
 * listesi açılınca oradaki dilimler esas alınır, ana ekranla çelişirse liste kazanır, kaynak ayrı yazılır. Tarihli satırlar
 * ("17/10") kendi gününe yazılır. Satır düzeni ekli Earn extra görüntüsünden.
 */
class BoostListM21Test {
    private val wolt = "com.wolt.courierapp"
    private val baku = TimeZone.getTimeZone("Asia/Baku")
    private fun ms(day: String, clock: String) = PlatformHistory.toMs(day, clock, baku)
    private fun obs(at: Long, vararg text: String) = CourierParser.parse(wolt, text.toList(), emptyList(), at, "ocr")
    private val list = arrayOf("Earn extra", "Coming up later", "Today", "Hər sifariş üçün əlavə 15%", "18:00-19:00",
        "17/10", "Hər sifariş üçün əlavə 20%", "15:00-17:00", "17/10", "Hər sifariş üçün əlavə 10%", "17:00-18:00",
        "18/10", "Hər sifariş üçün əlavə 45%", "16:00-17:00")
    private val home = arrayOf("Delivery demand", "Low", "NOW", "EXPECTED DEMAND", "Now · Boost", "Hər sifariş üçün əlavə 40%", "18:00-19:00",
        "Quick links", "Go offline")

    @Test fun earnExtraRowsKeepTheirDates() {
        val o = obs(ms("2026-10-16", "17:12"), *list)
        assertEquals(listOf(null, "17/10", "17/10", "18/10"), o.boostSlots.map { it.date })
        assertEquals(listOf(15, 20, 10, 45), o.boostSlots.map { it.pct })
        assertTrue(obs(ms("2026-10-16", "18:10"), *home).boostSlots.all { it.date == null })
    }

    @Test fun theListWinsOverTheHomeScreen() {
        val b = BoostLog()
        val rows = b.observe(obs(ms("2026-10-16", "17:12"), *list), "2026-10-16", null)
        val today = rows.single { it.optInt("value") == 15 }
        assertEquals("earn_extra", today.getString("plan_source")); assertEquals("2026-10-16", today.getString("slot_day"))
        assertEquals("2026-10-17", rows.first { it.optInt("value") == 20 }.getString("slot_day"))
        val homeRows = b.observe(obs(ms("2026-10-16", "18:10"), *home), "2026-10-16", null)
        assertEquals("home_screen", homeRows.single { it.optString("kind") == "plan" }.getString("plan_source"))
        val at = ms("2026-10-16", "18:30")
        assertEquals(15, b.planAt(wolt, at)!!.pct)
        val offer = b.atOffer(obs(at, "Decline", "AZN 2.61", "Expected earnings for the full delivery", "Accept"), at)
        assertEquals(15.0, offer.value!!, 1e-9); assertEquals(BoostLog.SOURCE_PLAN_LIST, offer.source)
    }

    @Test fun withoutTheListTheHomeScreenPlanStays() {
        val b = BoostLog()
        b.observe(obs(ms("2026-10-16", "18:10"), *home), "2026-10-16", null)
        val at = ms("2026-10-16", "18:30")
        assertEquals(40, b.planAt(wolt, at)!!.pct)
        assertEquals(BoostLog.SOURCE_PLAN, b.atOffer(obs(at, "Decline", "AZN 2.61", "Expected earnings for the full delivery", "Accept"), at).source)
    }

    @Test fun datedRowsBelongToTheirOwnDay() {
        val b = BoostLog()
        b.observe(obs(ms("2026-10-16", "17:12"), *list), "2026-10-16", null)
        assertNull("17/10'un 15:00-17:00'si bugüne yazılmaz", b.planAt(wolt, ms("2026-10-16", "16:00")))
        assertEquals(20, b.planAt(wolt, ms("2026-10-17", "16:00"))!!.pct)
        assertEquals(45, b.planAt(wolt, ms("2026-10-18", "16:30"))!!.pct)
    }
}
