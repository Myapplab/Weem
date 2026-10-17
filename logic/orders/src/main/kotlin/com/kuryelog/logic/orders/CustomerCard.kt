package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (CustomerCard.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object CustomerCard {
    const val STRAIGHT_M = 300.0
    const val ROUTE_M = 300.0
    const val LAST_TURNS = 2
    const val REQUEST_SLACK_MS = 60_000L
    const val FIX_MAX_AGE_MS = 15_000L
    const val FIX_MAX_ACCURACY_M = 40.0
    const val SIZE_KEY = "customerCardSizePct"
    const val DEFAULT_SIZE_PCT = 85
    val SIZE_CHOICES: List<Int> get() = TODO("A3.5")
    enum class Why { STRAIGHT, ROUTE, TURNS }
    fun routeFor(state: RouteStatus?, since: Long?, now: Long): RouteStatus.Route? = TODO("A3.5")
    fun straight(fixAt: Long, lat: Double, lon: Double, accuracyM: Double?, toLat: Double?, toLon: Double?, now: Long): Double? = TODO("A3.5")
    fun trigger(straightM: Double?, route: RouteStatus.Route?): Why? = TODO("A3.5")
    fun reason(why: Why, straightM: Double?, route: RouteStatus.Route?, tl: (String, String) -> String): String = TODO("A3.5")
    fun live(r: RouteStatus.Route, tl: (String, String) -> String): String = TODO("A3.5")
    fun sizePct(stored: Int): Int = TODO("A3.5")
    fun scale(pct: Int): Float = TODO("A3.5")
    fun heightShare(pct: Int, expanded: Boolean): Double = TODO("A3.5")
}

