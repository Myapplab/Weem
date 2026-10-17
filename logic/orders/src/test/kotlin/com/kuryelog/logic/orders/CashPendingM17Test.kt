package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 17 (16 Ekim, dördüncü tur; F8 kuralı değişti): Wolt Finances'teki "CASH PENDING" sayısı (artı = Wolt'a
 * yatırılacak, 0 = hepsi yatırıldı, eksi = fazla yatırıldı) ve "Cash offload" satırları okunur, `economics-cash-pending.jsonl`'e
 * yalnız sayı olarak yazılır. Satır düzeni kullanıcının Finances görüntüsünden (restoran adları ve sayılar değiştirildi).
 */
class CashPendingM17Test {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    private val finances = listOf("Finances", "GROSS EARNINGS", "AZN 31.40", "Next payout after 22 Oct", "CASH PENDING", "AZN 8.21", "Offload",
        "Recent transactions", "16 October 2026", "Cash offload", "14:50", "-AZN 50.00", "Cash collection", "Test Sushi", "13:41", "AZN 39.19",
        "Cash collection", "Test Market", "13:14", "AZN 17.25", "Cash collection", "Test Crispy", "12:52", "AZN 11.35", "15 October 2026")

    @Test fun pendingIsReadAsASignedNumber() {
        assertEquals(8.21, CashPending.pending(wolt, finances)!!, 1e-9)
        assertEquals(8.21, CashPending.pending(wolt, listOf("Finances", "CASH PENDING ⓘ AZN 8.21"))!!, 1e-9)
        assertEquals(-3.0, CashPending.pending(wolt, listOf("CASH PENDING", "-AZN 3.00"))!!, 1e-9)
        assertEquals(-3.0, CashPending.pending(wolt, listOf("CASH PENDING", "AZN -3.00"))!!, 1e-9)
        assertEquals(0.0, CashPending.pending(wolt, listOf("CASH PENDING", "(i)", "AZN 0.00"))!!, 1e-9)
        assertNull(CashPending.pending(bolt, finances))
        assertNull(CashPending.pending(wolt, listOf("Cash collection", "AZN 8.21")))
        assertNull("tutar yoksa sayı yok", CashPending.pending(wolt, listOf("CASH PENDING", "Offload")))
    }

    @Test fun offloadRowsAreReadAndCollectionsStayAsTheyWere() {
        assertEquals(listOf(PlatformHistory.CashRow("2026-10-16", "14:50", null, 50.0)), PlatformHistory.offloadRows(wolt, finances))
        assertEquals(listOf("13:41" to 39.19, "13:14" to 17.25, "12:52" to 11.35), PlatformHistory.cashRows(wolt, finances).map { it.clock to it.amount })
        // OCR sırası: tutar saatten önce.
        assertEquals(listOf(PlatformHistory.CashRow("2026-10-16", "14:50", null, 50.0)),
            PlatformHistory.offloadRows(wolt, listOf("16 October 2026", "Cash offload", "-AZN 50.00", "14:50")))
        assertTrue(PlatformHistory.offloadRows(bolt, finances).isEmpty())
    }

    @Test fun rowsHoldOnlyNumbers() {
        val p = CashPending.pendingRow(8.21, 1_000L)
        assertEquals(setOf("schemaVersion", "kind", "pending_azn", "observedAtMs"), p.keys().asSequence().toSet())
        assertEquals("pending", p.getString("kind")); assertEquals(CashPending.SCHEMA, p.getInt("schemaVersion"))
        val o = CashPending.offloadRow(PlatformHistory.CashRow("2026-10-16", "14:50", null, 50.0), 2_000L)
        assertEquals(setOf("schemaVersion", "kind", "day", "clock", "amount_azn", "observedAtMs"), o.keys().asSequence().toSet())
        val s = CashPending.shiftRow(CashPending.SHIFT_START, 5_000L, 8.21, 1_000L, 5_000L)
        assertEquals(setOf("schemaVersion", "kind", "shiftStartMs", "pending_azn", "pendingObservedAtMs", "observedAtMs"), s.keys().asSequence().toSet())
        val unknown = CashPending.shiftRow(CashPending.SHIFT_END, 5_000L, null, null, 9_000L)
        assertFalse(unknown.has("pending_azn"))
        val snap = CashPending.orderSnapshot("trip-1", 5_000L, 12.4, 9_000L)
        assertEquals(setOf("schemaVersion", "kind", "tripId", "shiftStartMs", "pending_azn", "observedAtMs"), snap.keys().asSequence().toSet())
        assertEquals("order_snapshot", snap.getString("kind"))
    }

    @Test fun pendingIsWrittenOnChangeOrEveryFiveMinutes() {
        assertTrue(CashPending.shouldWrite(null, 8.21, 0L))
        assertFalse(CashPending.shouldWrite(8.21 to 0L, 8.21, 299_999L))
        assertTrue(CashPending.shouldWrite(8.21 to 0L, 8.21, 300_000L))
        assertTrue(CashPending.shouldWrite(8.21 to 0L, 12.4, 1_000L))
    }

}
