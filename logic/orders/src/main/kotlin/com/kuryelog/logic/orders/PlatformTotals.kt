package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (PlatformTotals.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.6'te.
object PlatformTotals {
    const val FILE = "economics-platform-totals.jsonl"
    const val SCHEMA_VERSION = 1
    const val WOLT_STATS = "wolt_stats"
    const val WOLT_DAILY = "wolt_daily_details"
    const val WOLT_CASH = "wolt_cash_collection"
    const val BOLT_DAYS = "bolt_all_deliveries"
    data class Total(val platform: String, val period: String, val source: String, val deliveries: Int? = null, val earnings: Double? = null,
                     val earningsNoTips: Double? = null, val tips: Double? = null, val distanceKm: Double? = null, val cash: Double? = null,
                     val amountBasis: String? = null)
    fun woltDaily(pkg: String, texts: List<String>): List<Total> = TODO("A3.6")
    fun woltCash(rows: List<PlatformHistory.CashRow>): List<Total> = TODO("A3.6")
    fun boltDays(pkg: String, texts: List<String>): List<Total> = TODO("A3.6")
    fun fromStats(o: CourierObservation): Total? = TODO("A3.6")
    fun key(t: Total): String = TODO("A3.6")
    fun dayOf(period: String): String? = TODO("A3.6")
    fun record(t: Total, observedAtMs: Long): JSONObject = TODO("A3.6")
    fun parse(j: JSONObject): Total? = TODO("A3.6")
    fun latest(lines: Iterable<String>): List<Total> = TODO("A3.6")
    data class Ours(val deliveries: Int, val gross: Double, val net: Double, val platformTips: Double, val cash: Double)
    fun ours(d: OrderHistory.Day): Ours = TODO("A3.6")
    data class Check(val platform: String, val field: String, val source: String, val official: Double, val ours: Double?, val ok: Boolean)
    const val DISTANCE_SHARE = 0.10
    fun compare(platform: String, totals: List<Total>, ours: Ours, shiftKm: Double?): List<Check> = TODO("A3.6")
    fun line(c: Check, tl: (String, String) -> String): String = TODO("A3.6")
    fun feeDiffs(rows: List<JSONObject>, platform: String): List<JSONObject> = TODO("A3.6")
    fun diffLine(r: JSONObject): String = TODO("A3.6")
}

