package com.kuryelog.logic.orders

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (PocketCash.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.6'te.
object PocketCash {
    const val FILE = "economics-pocket-cash.jsonl"
    const val SCHEMA = 1
    const val VIA_REPLY = "notification_reply"
    const val VIA_WINDOW = "window"
    const val KEY_AMOUNT = "pocket_cash"
    const val EXTRA_SHIFT = "shift_start_ms"
    fun row(shiftStartMs: Long, amount: Double, answeredAtMs: Long, via: String): JSONObject = TODO("A3.6")
    fun parse(s: String?): Double? = TODO("A3.6")
    fun latest(rows: List<JSONObject>, shiftStartMs: Long): Double? = TODO("A3.6")
    fun forDay(rows: List<JSONObject>, day: String): List<Pair<Long, Double>> = TODO("A3.6")
    fun text(amount: Double, tl: (String, String) -> String): String = TODO("A3.6")
}

