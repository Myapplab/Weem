package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (BoostLog.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
class BoostLog {
    companion object {
        const val FILE = "economics-boosts.jsonl"
        const val SCHEMA = 1
        const val SLOT_MS = 30 * 60_000L
        const val MAX_GAP_SLOTS = 8
        const val FRESH_MS = 30 * 60_000L
        const val UNIT_PCT = "pct"
        const val UNIT_X = "x"
        const val SOURCE_OFFER = "offer_screen"
        const val SOURCE_HOME = "home_screen"
        const val SOURCE_PLAN = "wolt_plan"
        const val SOURCE_PLAN_LIST = "wolt_earn_extra"
        const val PLAN_LIST = "earn_extra"
        const val PLAN_HOME = "home_screen"
        const val FROM_SLOT_MIN = 60
        fun minuteOfDay(at: Long): Int = TODO("A3.5")
        fun dayOf(at: Long): String = TODO("A3.5")
        const val NOT_OBSERVED = "not_observed"
        fun platform(pkg: String): String = TODO("A3.5")
        data class Stats(val changes: Int, val slotsObserved: Int, val slotsMissing: Int, val slotsBoosted: Int, val max: Double?, val maxUnit: String?)
        fun stats(rows: List<JSONObject>, platform: String): Stats = TODO("A3.5")
    }
    data class Seen(val pkg: String, val at: Long, val value: Double?, val unit: String?, val active: Boolean, val window: String?,
                    val startsInMin: Int?, val stage: String) {
        val key: String get() = TODO("A3.5")
    }
    data class AtOffer(val value: Double?, val unit: String?, val source: String, val seenAt: Long?)
    data class PlanSlot(val day: String, val start: Int, val end: Int, val pct: Int, val seenAt: Long, val active: Boolean,
                        val source: String = PLAN_HOME)
    fun planAt(pkg: String, at: Long): PlanSlot? = TODO("A3.5")
    fun seen(o: CourierObservation): Seen? = TODO("A3.5")
    fun observe(o: CourierObservation, day: String, loc: JSONObject?): List<JSONObject> = TODO("A3.5")
    fun atOffer(o: CourierObservation, at: Long): AtOffer = TODO("A3.5")
}

