package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (OrderHistory.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object OrderHistory {
    data class Day(val date: String, val trips: List<TripRecord>) {
        val delivered: List<TripRecord> get() = TODO("A3.5")
        val orders: Int get() = TODO("A3.5")
        val gross: Double get() = TODO("A3.5")
        val net: Double get() = TODO("A3.5")
        val tips: Double get() = TODO("A3.5")
        val unpriced: Int get() = TODO("A3.5")
        val unconfirmed: Int get() = TODO("A3.5")
        val inferred: Int get() = TODO("A3.5")
        val waitMinutes: Double get() = TODO("A3.5")
        val lateMinutes: Double get() = TODO("A3.5")
    }
    val PLATFORMS: List<String> get() = TODO("A3.5")
    data class Averages(val energyWh: Double?, val restaurantMin: Double?, val customerMin: Double?, val orderMin: Double?, val beforeOfferMin: Double?)
    fun customerWaitMinutes(t: TripRecord): Double? = TODO("A3.5")
    fun restaurantWaitMinutes(t: TripRecord): Double? = TODO("A3.5")
    fun durationText(min: Double, tl: (String, String) -> String): String = TODO("A3.5")
    fun restaurantWaitMissing(t: TripRecord, tl: (String, String) -> String): String? = TODO("A3.5")
    fun restaurantWaitShort(t: TripRecord, tl: (String, String) -> String): String = TODO("A3.5")
    fun restaurantWaitLine(t: TripRecord, tl: (String, String) -> String): String = TODO("A3.5")
    fun boostText(t: TripRecord, tl: (String, String) -> String): String = TODO("A3.5")
    fun temperatureText(t: TripRecord): String? = TODO("A3.5")
    fun boostTempLine(t: TripRecord, tl: (String, String) -> String): String = TODO("A3.5")
    fun listLine(t: TripRecord, tl: (String, String) -> String): String = TODO("A3.5")
    fun averages(trips: List<TripRecord>, platformOf: (String) -> String): Averages = TODO("A3.5")
    fun energyOf(t: TripRecord): Double? = TODO("A3.5")
    enum class Metric { ENERGY, RESTAURANT, CUSTOMER, ORDER, BEFORE_OFFER }
    data class MetricRow(val tripId: String, val at: Long, val platform: String, val restaurant: String?, val value: Double?, val why: String?)
    fun metricRows(trips: List<TripRecord>, m: Metric, platformOf: (String) -> String, tl: (String, String) -> String): List<MetricRow> = TODO("A3.5")
    fun metricRowText(r: MetricRow, m: Metric, tl: (String, String) -> String): String = TODO("A3.5")
    val INFERRED: Pair<String, String> get() = TODO("A3.5")
    val LIST_VERIFIED: Pair<String, String> get() = TODO("A3.5")
    fun status(t: TripRecord, tl: (String, String) -> String): String = TODO("A3.5")
    fun deliveredOrders(trips: Collection<TripRecord>): Int = TODO("A3.5")
    fun days(stored: List<TripRecord>, live: List<TripRecord>, platform: String?, platformOf: (String) -> String, dayOf: (Long) -> String): List<Day> = TODO("A3.5")
    fun listTime(t: TripRecord, platform: String): Long = TODO("A3.5")
    fun acceptTime(t: TripRecord): Long = TODO("A3.5")
    fun energyLine(t: TripRecord, tl: (String, String) -> String): String? = TODO("A3.5")
    fun readLine(o: OfferState, tl: (String, String) -> String): String? = TODO("A3.5")
    fun offerAt(t: TripRecord): Long = TODO("A3.5")
    fun pickedAt(t: TripRecord): Long? = TODO("A3.5")
    fun totalMinutes(t: TripRecord): Double? = TODO("A3.5")
    fun waitBeforeOffer(t: TripRecord, sameDay: List<TripRecord>): Double? = TODO("A3.5")
    fun beforeOfferMissing(t: TripRecord, sameDay: List<TripRecord>, tl: (String, String) -> String): String? = TODO("A3.5")
    fun restaurant(t: TripRecord): String? = TODO("A3.5")
    fun dateLabel(date: String, az: Boolean): String = TODO("A3.5")
}

