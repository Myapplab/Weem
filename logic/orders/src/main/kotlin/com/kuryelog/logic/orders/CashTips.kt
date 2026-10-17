package com.kuryelog.logic.orders

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (CashTips.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object CashPayment {
    const val CASH = "cash"
    const val CARD_INFERRED = "card_inferred"
    const val SOURCE_CASH_SCREEN = "wolt_cash_screen"
    const val SOURCE_STEPS_NO_CASH = "wolt_delivery_steps_no_cash"
    const val NOT_OBSERVED = "not_observed"
    data class Screen(val cash: Boolean, val amount: Double?, val noChangeNeeded: Boolean, val steps: Boolean,
                      val changeAzn: Double? = null, val checked: Boolean? = null)
    fun parse(pkg: String, texts: List<String>, checked: Boolean? = null): Screen? = TODO("A3.5")
    fun attach(o: CourierObservation, s: Screen?): CourierObservation = TODO("A3.5")
    fun cardLine(t: TripRecord, clock: (Long) -> String, tl: (String, String) -> String): String? = TODO("A3.5")
    fun dayTotal(trips: Collection<TripRecord>): Double = TODO("A3.5")
}

object CashTips {
    const val FILE = "economics-cash-tips.jsonl"
    const val SCHEMA = 1
    const val SOURCE = "user_cash_input"
    const val VIA_NOTIFICATION = "notification"
    const val VIA_CARD = "card"
    const val VIA_QUICK = "notification_quick"
    const val VIA_WINDOW = "tip_window"
    const val SOURCE_QUICK = "user_quick_tip"
    const val CASH_SOURCE_MANUAL = "user_manual"
    val QUICK_TIPS: List<Double> get() = TODO("A3.5")
    const val KEY_RECEIVED = "cash_received"
    const val EXTRA_TRIP = "trip_id"
    const val EXTRA_TIP = "tip_azn"
    fun tip(received: Double, cash: Double?): Double? = TODO("A3.5")
    fun parseInput(s: String?): Double? = TODO("A3.5")
    fun receivedFor(tip: Double, cash: Double?): Double? = TODO("A3.5")
    fun quickAllowed(cash: Double?): Boolean = TODO("A3.5")
    fun preview(receivedText: String?, cash: Double?): Double? = TODO("A3.5")
    fun shouldAsk(t: TripRecord): Boolean = TODO("A3.5")
    fun record(t: TripRecord, received: Double, at: Long, via: String, cashOverride: Double? = null, tipSource: String = SOURCE): JSONObject = TODO("A3.5")
    fun latest(lines: Sequence<String>): Map<String, JSONObject> = TODO("A3.5")
    fun num(j: JSONObject, k: String): Double? = TODO("A3.5")
}

