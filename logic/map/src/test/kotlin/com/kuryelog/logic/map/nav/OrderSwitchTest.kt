package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** YolGo `NavController.build` sipariş değişimi (MAP.md 7.3): ilk sipariş rota açmaz; değişince restoran ya da müşteri; sipariş bitince rota silinir. */
class OrderSwitchTest {
    private val rest = GeoPoint(40.40, 49.85)
    private val cust = GeoPoint(40.41, 49.86)

    private fun order(n: Int, left: Boolean) = OrderRec(n, 0L).apply {
        restTarget = rest; customerTarget = cust; restName = "Kafe"
        if (left) restLeftAtMs = 1L
    }

    @Test
    fun `ilk sipariş ve aynı numara rota açmaz`() {
        assertNull(OrderSwitch.on(null, order(1, left = false)))
        assertNull(OrderSwitch.on(1, order(1, left = true)))
    }

    @Test
    fun `yeni sipariş restorandan çıkılmadıysa restorana, çıkıldıysa müşteriye`() {
        assertEquals(OrderSwitch.Action(2, rest, true, "Kafe"), OrderSwitch.on(1, order(2, left = false)))
        assertEquals(OrderSwitch.Action(2, cust, false, null), OrderSwitch.on(1, order(2, left = true)))
    }

    @Test
    fun `hedefsiz sipariş ve biten sipariş rotayı siler`() {
        assertEquals(OrderSwitch.Action(3, null, true, ""), OrderSwitch.on(2, OrderRec(3, 0L)))
        assertEquals(OrderSwitch.Action(null, null, true, null), OrderSwitch.on(2, null))
    }
}
