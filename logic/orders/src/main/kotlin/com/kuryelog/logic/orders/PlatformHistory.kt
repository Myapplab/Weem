package com.kuryelog.logic.orders

import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (PlatformHistory.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object PlatformHistory {
    const val FILE = "economics-platform-history.jsonl"
    const val SCHEMA_VERSION = 1
    const val SOURCE = "platform_history"
    const val MATCH_WINDOW_MS = 120_000L
    const val FEE_MATCH_WINDOW_MS = 15 * 60_000L
    data class Row(val platform: String, val restaurant: String?, val clock: String, val day: String?, val daySource: String,
                   val amount: Double, val amountText: String, val distanceKm: Double?, val orderCode: String?,
                   val dayTotalText: String?, val dayDeliveries: Int?, val extraWait: Double? = null, val tip: Double? = null) {
        fun key(effectiveDay: String): String = TODO("A3.5")
    }
    data class Match(val tripId: String, val by: String, val dtS: Long, val state: String, val tripGross: Double?)
    fun norm(s: String): String = TODO("A3.5")
    fun dateHeader(s: String): String? = TODO("A3.5")
    fun parse(pkg: String, texts: List<String>, at: Long, tz: TimeZone): List<Row>? = TODO("A3.5")
    fun toMs(day: String, clock: String, tz: TimeZone): Long = TODO("A3.5")
    fun dayOf(at: Long, tz: TimeZone): String = TODO("A3.5")
    fun sameRestaurant(a: String?, b: String?): Boolean = TODO("A3.5")
    fun state(t: TripRecord): String = TODO("A3.5")
    fun match(row: Row, rowAt: Long, trips: Collection<TripRecord>, taken: Set<String>): Match? = TODO("A3.5")
    data class CashRow(val day: String, val clock: String, val restaurant: String?, val amount: Double)
    fun cashRows(pkg: String, texts: List<String>): List<CashRow> = TODO("A3.5")
    fun offloadRows(pkg: String, texts: List<String>): List<CashRow> = TODO("A3.5")
    fun cashMatch(row: CashRow, at: Long, trips: Collection<TripRecord>): TripRecord? = TODO("A3.5")
    const val SAME_DELIVERY_MIN = 5
    fun sameDelivery(a: Row, b: Row): Boolean = TODO("A3.5")
    fun rowOf(j: JSONObject): Row? = TODO("A3.5")
    fun owner(r: Row, day: String, written: List<JSONObject>): String? = TODO("A3.5")
    fun records(rows: List<Row>, at: Long, captureSource: String, pkg: String, tz: TimeZone, keys: (String) -> MutableSet<String>,
                taken: (String) -> MutableSet<String>, tripsOn: (String) -> Collection<TripRecord>,
                written: ((String) -> MutableList<JSONObject>)? = null): List<Pair<String, JSONObject>> = TODO("A3.5")
    fun loaded(lines: Sequence<String>): Triple<MutableSet<String>, MutableSet<String>, MutableList<JSONObject>> = TODO("A3.5")
    fun byTrip(rows: List<JSONObject>): Map<String, JSONObject> = TODO("A3.5")
    fun missed(rows: List<JSONObject>, platform: String?): List<JSONObject> = TODO("A3.5")
    fun platformKey(tab: String): String? = TODO("A3.5")
    fun cardLine(r: JSONObject, tl: (String, String) -> String): String = TODO("A3.5")
    fun missedLine(r: JSONObject): String = TODO("A3.5")
}

