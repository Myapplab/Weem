package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * F4.8/F4.9 (Ekim planı madde 11, SORU-9/10): the tip notification's quick buttons (1/2/3/5 ₼) and the small window record
 * into the same K32 file. A quick button adds the tip: received = cash + tip. The window shows the cash amount (K31) and
 * computes the tip live from what was received; when the cash amount was not read the courier types it there too. Only
 * cash orders are asked (unchanged); the K32 row stays schemaVersion 1, new values only.
 */
class TipRecordF48Test {
    private val wolt = "com.wolt.courierapp"
    private fun cashTrip(cash: Double?) = TripRecord("w1", wolt, null, 1_000L).also {
        it.paymentMethod = CashPayment.CASH; it.paymentSource = CashPayment.SOURCE_CASH_SCREEN; it.cashAmount = cash; it.completedAt = 9_000L
    }

    @Test fun quickButtons() {
        assertEquals(listOf(1.0, 2.0, 3.0, 5.0), CashTips.QUICK_TIPS)
        assertEquals(22.40, CashTips.receivedFor(2.0, 20.40)!!, 1e-9)
        assertEquals(25.40, CashTips.receivedFor(5.0, 20.40)!!, 1e-9)
        assertNull(CashTips.receivedFor(2.0, null)) // cash unknown: no quick button, the window asks for the cash too
        assertTrue(CashTips.quickAllowed(20.40)); assertFalse(CashTips.quickAllowed(null))
    }

    @Test fun quickTipRow() {
        val j = CashTips.record(cashTrip(20.40), CashTips.receivedFor(2.0, 20.40)!!, 10_000L, CashTips.VIA_QUICK, tipSource = CashTips.SOURCE_QUICK)
        assertEquals(1, j.getInt("schemaVersion"))
        assertEquals(22.40, j.getDouble("received_azn"), 1e-9)
        assertEquals(2.0, j.getDouble("tip_azn"), 1e-9)
        assertEquals("notification_quick", j.getString("via"))
        assertEquals("user_quick_tip", j.getString("tip_source"))
    }

    @Test fun windowLivePreview() {
        assertEquals(4.60, CashTips.preview("25", 20.40)!!, 1e-9)
        assertEquals(4.60, CashTips.preview("25,00 ₼", 20.40)!!, 1e-9)
        assertNull(CashTips.preview("", 20.40)); assertNull(CashTips.preview("abc", 20.40)); assertNull(CashTips.preview("25", null))
        assertEquals(-0.40, CashTips.preview("20", 20.40)!!, 1e-9) // shown, flagged as minus, never clamped
    }

    @Test fun cashTypedInTheWindowWhenUnread() {
        val t = cashTrip(null)
        val j = CashTips.record(t, 25.0, 10_000L, CashTips.VIA_WINDOW, cashOverride = 18.0)
        assertEquals(18.0, j.getDouble("cash_amount_azn"), 1e-9)
        assertEquals("user_manual", j.getString("cash_amount_source"))
        assertEquals(7.0, j.getDouble("tip_azn"), 1e-9)
        assertEquals("tip_window", j.getString("via"))
        // A read amount is never replaced by a typed one.
        val read = CashTips.record(cashTrip(20.40), 25.0, 10_000L, CashTips.VIA_WINDOW, cashOverride = 18.0)
        assertEquals(20.40, read.getDouble("cash_amount_azn"), 1e-9)
        assertEquals(CashPayment.SOURCE_CASH_SCREEN, read.getString("cash_amount_source"))
    }

    @Test fun oldCallsUnchanged() {
        val j = CashTips.record(cashTrip(20.40), 25.0, 10_000L, CashTips.VIA_NOTIFICATION)
        assertEquals("user_cash_input", j.getString("tip_source"))
        assertEquals("notification", j.getString("via"))
        assertEquals(CashTips.latest(sequenceOf(j.toString())).keys, setOf("w1"))
    }
}
