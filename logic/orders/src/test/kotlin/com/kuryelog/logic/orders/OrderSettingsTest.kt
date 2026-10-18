package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/** A4.2 (S20 (b)): Siparişler ayarları (komisyon yoksa net = brüt + bahşiş testi: `DayReportTest.missingCommissionMakesNetGrossPlusTipsAndSaysSo`). */
class OrderSettingsTest {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "ee.mtakso.driver"

    @Test fun commissionReadsPackageThenGlobalThenZero() {
        val p = KuryelogPrefs.Memory()
        assertEquals(0.0, OrderSettings.commissionPct(p, wolt), 0.0)
        p.put(mapOf("commission" to 5f)); assertEquals(5.0, OrderSettings.commissionPct(p, wolt), 0.0)
        p.put(mapOf("commission:$wolt" to 12.5f)); assertEquals(12.5, OrderSettings.commissionPct(p, wolt), 0.0); assertEquals(5.0, OrderSettings.commissionPct(p, bolt), 0.0)
    }

    @Test fun parseAcceptsCommaAndRejectsOutOfRange() {
        assertEquals(12.5, OrderSettings.parseCommission(" 12,5 ")!!, 0.0); assertEquals(0.0, OrderSettings.parseCommission("0")!!, 0.0)
        assertEquals(99.99, OrderSettings.parseCommission("99.99")!!, 1e-9)
        assertNull(OrderSettings.parseCommission("100")); assertNull(OrderSettings.parseCommission("-1")); assertNull(OrderSettings.parseCommission("")); assertNull(OrderSettings.parseCommission("NaN"))
    }

    @Test fun writeGoesToEveryPackageOfThePlatform() {
        val p = KuryelogPrefs.Memory()
        p.put(OrderSettings.commissionWrite(listOf("com.wolt.courierapp", "com.wolt.other"), 10.0))
        assertEquals(10.0, OrderSettings.commissionPct(p, "com.wolt.other"), 0.0)
        assertEquals(setOf("commission:com.wolt.courierapp", "commission:com.wolt.other"), p.map.keys)
    }

    @Test fun onlyWoltAndBoltFoodGetACommissionRow() {
        val g = OrderSettings.platformPackages(setOf(bolt, wolt, "com.wolt.b", "com.example.notes"))
        assertEquals(listOf("Bolt Food", "Wolt").sorted(), g.keys.sorted())
        assertEquals(listOf("com.wolt.b", wolt), g.getValue("Wolt"))
        assertTrue(OrderSettings.platformPackages(emptySet()).isEmpty())
    }

    @Test fun keysAndDefaultsMatchRawstats() {
        assertEquals(listOf("overlay", "ocr", "customerCard"), listOf(OrderSettings.OVERLAY, OrderSettings.OCR, OrderSettings.CUSTOMER_CARD))
        assertEquals("customerCardSizePct", CustomerCard.SIZE_KEY); assertEquals("restaurantBubbleWidthPct", RestaurantBubble.WIDTH_KEY)
    }
}
