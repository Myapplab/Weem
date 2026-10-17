package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Ekim planı madde 15 (16 Ekim, dördüncü tur): vardiya başlayınca "Şu an cebinde ne kadar nakit var?" sorulur; cevap
 * vardiya başına "cebindeki nakit" olarak `economics-pocket-cash.jsonl`'e yazılır ve gün sayfasında görünür.
 */
class PocketCashM15Test {
    private val tr = { a: String, _: String -> a }
    // 16 Ekim 2026 09:00 Bakü (UTC+4) = 05:00 UTC
    private val shift = 1_792_126_800_000L

    @Test fun rowHoldsOnlyTheShiftTheAmountAndHow() {
        val r = PocketCash.row(shift, 30.0, shift + 40_000, PocketCash.VIA_REPLY)
        assertEquals(PocketCash.SCHEMA, r.getInt("schemaVersion"))
        assertEquals(shift, r.getLong("shift_start_ms")); assertEquals(30.0, r.getDouble("amount_azn"), 1e-9)
        assertEquals(shift + 40_000, r.getLong("answered_at_ms")); assertEquals("notification_reply", r.getString("via"))
        assertEquals(setOf("schemaVersion", "shift_start_ms", "amount_azn", "answered_at_ms", "via"), r.keys().asSequence().toSet())
        assertEquals("economics-pocket-cash.jsonl", PocketCash.FILE)
    }

    @Test fun typedAmountsZeroIncluded() {
        assertEquals(30.0, PocketCash.parse("30")!!, 1e-9); assertEquals(12.5, PocketCash.parse("12,50 ₼")!!, 1e-9)
        assertEquals(0.0, PocketCash.parse("0")!!, 1e-9)
        assertNull(PocketCash.parse("çok")); assertNull(PocketCash.parse("")); assertNull(PocketCash.parse("-5"))
    }

    @Test fun newestAnswerPerShiftWinsAndOtherSchemasAreSkipped() {
        val rows = listOf(PocketCash.row(shift, 30.0, shift + 1_000, PocketCash.VIA_REPLY),
            PocketCash.row(shift, 35.0, shift + 9_000, PocketCash.VIA_WINDOW),
            JSONObject().put("schemaVersion", 9).put("shift_start_ms", shift).put("amount_azn", 99.0).put("answered_at_ms", shift + 20_000),
            PocketCash.row(shift - 86_400_000, 10.0, shift - 86_000_000, PocketCash.VIA_REPLY))
        assertEquals(35.0, PocketCash.latest(rows, shift)!!, 1e-9)
        assertNull(PocketCash.latest(rows, shift + 1))
        val day = PocketCash.forDay(rows, "2026-10-16")
        assertEquals(listOf(shift to 35.0), day)
        assertEquals("Vardiya başında cepte: 35.00₼", PocketCash.text(35.0, tr))
    }

}
