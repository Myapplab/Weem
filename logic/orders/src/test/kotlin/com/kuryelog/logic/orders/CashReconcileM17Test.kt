package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 17 mutabakatı: vardiya başı pending + vardiyada toplanan nakit − vardiyadaki offload ≈ vardiya sonu pending;
 * fark (> 0,01 ₼) kırmızı. Satırlar `CashPending` kayıtlarından (sayılar sentetik).
 */
class CashReconcileM17Test {
    private val tr = { a: String, _: String -> a }
    // 16 Ekim 2026 09:00 Bakü
    private val start = 1_792_126_800_000L
    private fun at(h: Int, m: Int) = start + ((h - 9) * 60 + m) * 60_000L
    private fun offload(clock: String, amount: Double, seen: Long) =
        CashPending.offloadRow(PlatformHistory.CashRow("2026-10-16", clock, null, amount), seen)

    @Test fun startPlusCollectedMinusOffloadMatchesTheEnd() {
        val rows = listOf(CashPending.shiftRow(CashPending.SHIFT_START, start, 8.21, start - 3_600_000, start),
            offload("14:50", 50.0, at(15, 0)), offload("14:50", 50.0, at(15, 5)), offload("08:10", 20.0, at(15, 0)),
            CashPending.shiftRow(CashPending.SHIFT_END, start, 12.40, at(18, 0), at(18, 30)))
        val r = CashPending.reconcile(rows, start, collected = 54.19)!!
        assertEquals(8.21, r.start, 1e-9); assertEquals(12.40, r.end, 1e-9); assertEquals(50.0, r.offloaded, 1e-9)
        assertEquals(12.40, r.expected, 1e-9); assertEquals(0.0, r.diff, 1e-9); assertTrue(r.ok)
        assertEquals("Pending: 8.21 → 12.40₼ · beklenen 12.40₼", CashPending.line(r, tr))
    }

    @Test fun aDifferenceIsFlagged() {
        val rows = listOf(CashPending.shiftRow(CashPending.SHIFT_START, start, 8.21, start, start),
            CashPending.shiftRow(CashPending.SHIFT_END, start, 13.60, at(18, 0), at(18, 30)))
        val r = CashPending.reconcile(rows, start, collected = 4.19)!!
        assertFalse(r.ok); assertEquals(1.20, r.diff, 1e-9)
        assertEquals("Pending: 8.21 → 13.60₼ · beklenen 12.40₼ · fark 1.20₼", CashPending.line(r, tr))
    }

    @Test fun withoutAKnownStartTheFirstValueSeenInTheShiftIsUsedAndTheLiveValueEndsIt() {
        val rows = listOf(CashPending.shiftRow(CashPending.SHIFT_START, start, null, null, start),
            CashPending.pendingRow(5.0, at(10, 0)), CashPending.pendingRow(9.0, at(12, 0)))
        val r = CashPending.reconcile(rows, start, collected = 4.0)!!
        assertEquals(5.0, r.start, 1e-9); assertEquals(9.0, r.end, 1e-9); assertTrue(r.ok)
    }

    @Test fun unknownWhenNothingWasSeen() {
        assertNull(CashPending.reconcile(listOf(CashPending.shiftRow(CashPending.SHIFT_START, start, null, null, start)), start, 10.0))
        assertNull(CashPending.reconcile(emptyList(), start, 10.0))
    }

    @Test fun shiftsOfADayFile() {
        val rows = listOf(CashPending.shiftRow(CashPending.SHIFT_START, start, 1.0, start, start),
            CashPending.shiftRow(CashPending.SHIFT_END, start, 2.0, start, at(13, 0)),
            CashPending.shiftRow(CashPending.SHIFT_START, at(15, 0), 2.0, start, at(15, 0)))
        assertEquals(listOf(start to at(13, 0), at(15, 0) to null), CashPending.shifts(rows))
    }
}
