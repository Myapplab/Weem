package com.kuryelog.logic.orders

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats PocketCash.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * Ekim planı madde 15 (16 Ekim): the cash in the courier's pocket when a shift starts ("Şu an cebinde ne kadar nakit var?").
 * One row per answer in [FILE] (append-only, schemaVersion 1, only the shift start, the amount and how it was given); the newest
 * row of a shift wins, so a correction simply appends again. Pure: [PocketCashPrompt] asks, OrderTracker writes.
 */
object PocketCash {
    const val FILE = "economics-pocket-cash.jsonl"
    const val SCHEMA = 1
    const val VIA_REPLY = "notification_reply"
    const val VIA_WINDOW = "window"
    /** RemoteInput key and extras of the question. */
    const val KEY_AMOUNT = "pocket_cash"
    const val EXTRA_SHIFT = "shift_start_ms"

    fun row(shiftStartMs: Long, amount: Double, answeredAtMs: Long, via: String): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA).put("shift_start_ms", shiftStartMs)
        .put("amount_azn", BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_EVEN).toDouble())
        .put("answered_at_ms", answeredAtMs).put("via", via)

    /** A typed amount (`30`, `12,50 ₼`, `AZN 30`); 0 is an answer (empty pocket). Null for anything else. */
    fun parse(s: String?): Double? = CashTips.parseInput(s)?.takeIf { it >= 0.0 }

    private fun ok(j: JSONObject) = j.optInt("schemaVersion") == SCHEMA && j.has("shift_start_ms") && j.has("amount_azn") && j.has("answered_at_ms")

    /** The newest answer for the shift that started at [shiftStartMs]. */
    fun latest(rows: List<JSONObject>, shiftStartMs: Long): Double? = rows.filter { ok(it) && it.getLong("shift_start_ms") == shiftStartMs }
        .maxByOrNull { it.getLong("answered_at_ms") }?.getDouble("amount_azn")

    /** Every shift that started on Baku day [day] with its newest answer, oldest shift first. */
    fun forDay(rows: List<JSONObject>, day: String): List<Pair<Long, Double>> = rows.filter { ok(it) && BoostLog.dayOf(it.getLong("shift_start_ms")) == day }
        .groupBy { it.getLong("shift_start_ms") }.map { (s, r) -> s to r.maxByOrNull { it.getLong("answered_at_ms") }!!.getDouble("amount_azn") }.sortedBy { it.first }

    fun text(amount: Double, tl: (String, String) -> String): String =
        tl("Vardiya başında cepte: ", "Növbənin əvvəlində cibdə: ") + String.format(java.util.Locale.US, "%.2f", amount) + "₼"
}
