package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (CashPending.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.6'da.
object CashPending {
    const val FILE = "economics-cash-pending.jsonl"
    const val SCHEMA = 1
    const val KIND_PENDING = "pending"
    const val KIND_OFFLOAD = "offload"
    const val SHIFT_START = "shift_start"
    const val SHIFT_END = "shift_end"
    const val KIND_ORDER = "order_snapshot"
    const val REWRITE_MS = 5 * 60_000L
    fun pending(pkg: String, texts: List<String>): Double? = TODO("A3.6")
    fun pendingRow(pending: Double, at: Long): JSONObject = TODO("A3.6")
    fun offloadRow(r: PlatformHistory.CashRow, at: Long): JSONObject = TODO("A3.6")
    fun shiftRow(kind: String, shiftStartMs: Long, pending: Double?, pendingAt: Long?, at: Long): JSONObject = TODO("A3.6")
    fun orderSnapshot(tripId: String, shiftStartMs: Long, pending: Double?, at: Long): JSONObject = TODO("A3.6")
    data class Reconcile(val start: Double, val end: Double, val collected: Double, val offloaded: Double, val expected: Double, val diff: Double, val ok: Boolean)
    fun shifts(rows: List<JSONObject>): List<Pair<Long, Long?>> = TODO("A3.6")
    fun reconcile(rows: List<JSONObject>, shiftStartMs: Long, collected: Double): Reconcile? = TODO("A3.6")
    fun line(r: Reconcile, tl: (String, String) -> String): String = TODO("A3.6")
    fun shouldWrite(last: Pair<Double, Long>?, value: Double, now: Long): Boolean = TODO("A3.6")
}

