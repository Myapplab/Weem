package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats CashPending.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/**
 * Ekim planı madde 17 (16 Oct; the F8 rule "no balance is written" changed for this one number at the courier's request): Wolt
 * Finances' "CASH PENDING" — plus = still to pay in to Wolt, 0 = all paid in, minus = paid in too much — and its "Cash offload"
 * rows, written to [FILE] as numbers only (append-only, schemaVersion 1). Rows: [KIND_PENDING] each time it is seen (on a change
 * or every [REWRITE_MS]), [KIND_OFFLOAD] once per row, [SHIFT_START]/[SHIFT_END] with the last known value at the shift's start
 * and end, and at the shift end one [KIND_ORDER] snapshot per order of the shift (the order record itself is not changed).
 */
object CashPending {
    const val FILE = "economics-cash-pending.jsonl"
    const val SCHEMA = 1
    const val KIND_PENDING = "pending"
    const val KIND_OFFLOAD = "offload"
    const val SHIFT_START = "shift_start"
    const val SHIFT_END = "shift_end"
    const val KIND_ORDER = "order_snapshot"
    const val REWRITE_MS = 5 * 60_000L

    private const val SP = "[\\s\\p{Z}]"
    private val AMOUNT = Regex("(-)?$SP*(?:azn|₼)$SP*(-)?$SP*([0-9]{1,6})[.,]([0-9]{2})(?![0-9])")
    private fun norm(s: String) = s.lowercase().replace(Regex("$SP+"), " ").trim()
    private fun amount(s: String): Double? = AMOUNT.find(norm(s))?.let { m ->
        val v = "${m.groupValues[3]}.${m.groupValues[4]}".toDouble()
        if (m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty()) -v else v
    }

    /** The cash pending of a Wolt Finances frame: the amount on the label's line or on one of the next two lines; null otherwise. */
    fun pending(pkg: String, texts: List<String>): Double? {
        if (pkg !in CourierParser.WOLT || texts.none { it.contains("pending", ignoreCase = true) }) return null
        val lines = texts.flatMap { it.split('\n') }.map(::norm).filter { it.isNotEmpty() }
        val i = lines.indexOfFirst { it.startsWith("cash pending") }
        if (i < 0) return null
        return amount(lines[i].removePrefix("cash pending")) ?: (i + 1..minOf(i + 2, lines.lastIndex)).firstNotNullOfOrNull { amount(lines[it]) }
    }

    private fun base(kind: String, at: Long) = JSONObject().put("schemaVersion", SCHEMA).put("kind", kind)
    private fun r2(x: Double) = java.math.BigDecimal.valueOf(x).setScale(2, java.math.RoundingMode.HALF_EVEN).toDouble()

    fun pendingRow(pending: Double, at: Long): JSONObject = base(KIND_PENDING, at).put("pending_azn", r2(pending)).put("observedAtMs", at)
    fun offloadRow(r: PlatformHistory.CashRow, at: Long): JSONObject =
        base(KIND_OFFLOAD, at).put("day", r.day).put("clock", r.clock).put("amount_azn", r2(r.amount)).put("observedAtMs", at)
    /** [kind] [SHIFT_START] or [SHIFT_END]; [pending] the last known value (seen at [pendingAt]), absent when never seen. */
    fun shiftRow(kind: String, shiftStartMs: Long, pending: Double?, pendingAt: Long?, at: Long): JSONObject = base(kind, at)
        .put("shiftStartMs", shiftStartMs).also { j -> pending?.let { j.put("pending_azn", r2(it)) }; pendingAt?.let { j.put("pendingObservedAtMs", it) } }
        .put("observedAtMs", at)
    fun orderSnapshot(tripId: String, shiftStartMs: Long, pending: Double?, at: Long): JSONObject = base(KIND_ORDER, at)
        .put("tripId", tripId).put("shiftStartMs", shiftStartMs).also { j -> pending?.let { j.put("pending_azn", r2(it)) } }.put("observedAtMs", at)

    /** One shift's reconciliation (M17): [start] + [collected] − [offloaded] = [expected] ≈ [end]; [ok] when |[diff]| ≤ 0.01. */
    data class Reconcile(val start: Double, val end: Double, val collected: Double, val offloaded: Double, val expected: Double, val diff: Double, val ok: Boolean)

    private val BAKU = java.util.TimeZone.getTimeZone("Asia/Baku")
    private fun ok(j: JSONObject) = j.optInt("schemaVersion") == SCHEMA
    private fun num(j: JSONObject, k: String) = if (j.has(k)) j.optDouble(k).takeIf { it.isFinite() } else null

    /** Every shift of a day file: its start and, when written, the moment its end row was written. */
    fun shifts(rows: List<JSONObject>): List<Pair<Long, Long?>> {
        val starts = rows.filter { ok(it) && it.optString("kind") == SHIFT_START }.map { it.getLong("shiftStartMs") }.distinct().sorted()
        return starts.map { s -> s to rows.lastOrNull { ok(it) && it.optString("kind") == SHIFT_END && it.optLong("shiftStartMs") == s }?.getLong("observedAtMs") }
    }

    /**
     * The shift that started at [shiftStartMs]: its start value (the shift start row, else the first value seen in the shift), its end
     * value (the shift end row, else the last value seen) and the offloads made in between (a row seen twice counts once); null when
     * either value is unknown. [collected]: the cash of the shift's delivered cash orders.
     */
    fun reconcile(rows: List<JSONObject>, shiftStartMs: Long, collected: Double): Reconcile? {
        val mine = rows.filter(::ok)
        val endRow = mine.lastOrNull { it.optString("kind") == SHIFT_END && it.optLong("shiftStartMs") == shiftStartMs }
        val until = endRow?.getLong("observedAtMs") ?: Long.MAX_VALUE
        val seen = mine.filter { it.optString("kind") == KIND_PENDING && it.optLong("observedAtMs") in shiftStartMs..until }.sortedBy { it.getLong("observedAtMs") }
        val start = mine.lastOrNull { it.optString("kind") == SHIFT_START && it.optLong("shiftStartMs") == shiftStartMs }?.let { num(it, "pending_azn") }
            ?: seen.firstOrNull()?.let { num(it, "pending_azn") } ?: return null
        val end = endRow?.let { num(it, "pending_azn") } ?: seen.lastOrNull()?.let { num(it, "pending_azn") } ?: return null
        val offloaded = mine.filter { it.optString("kind") == KIND_OFFLOAD }
            .map { Triple(it.optString("day"), it.optString("clock"), num(it, "amount_azn") ?: 0.0) }.distinct()
            .filter { (d, c, _) -> runCatching { PlatformHistory.toMs(d, c, BAKU) }.getOrNull()?.let { it in shiftStartMs..until } == true }
            .sumOf { it.third }
        val expected = r2(start + collected - offloaded)
        val diff = r2(end - expected)
        return Reconcile(start, end, r2(collected), r2(offloaded), expected, diff, Math.abs(diff) <= 0.01)
    }

    private fun money(x: Double) = String.format(java.util.Locale.US, "%.2f", x)
    /** "Pending: 8.21 → 12.40₼ · beklenen 12.40₼", with " · fark 1.20₼" when they differ. */
    fun line(r: Reconcile, tl: (String, String) -> String): String =
        "Pending: ${money(r.start)} → ${money(r.end)}₼ · " + tl("beklenen ", "gözlənilən ") + money(r.expected) + "₼" +
            if (r.ok) "" else " · " + tl("fark ", "fərq ") + money(r.diff) + "₼"

    /** A seen value is written when it differs from the last written one ([last] value to time) or that one is [REWRITE_MS] old. */
    fun shouldWrite(last: Pair<Double, Long>?, value: Double, now: Long): Boolean =
        last == null || Math.abs(last.first - value) >= 0.005 || now - last.second >= REWRITE_MS
}
