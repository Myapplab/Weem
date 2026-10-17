package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (ArrivalDistance.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object ArrivalDistance {
    const val YOLGO = "nav_yolgo"
    const val MAPS = "nav_google_maps"
    const val GPS = "gps"
    const val FRESH_MS = 30_000L
    const val SLOW_KMH = 10.0
    const val MAX_ALLOWANCE_M = 20.0
    const val MAX_ACCURACY_M = 30.0
    const val RESTAURANT_M = 5.0
    const val CUSTOMER_M = 10.0
    data class Source(val metres: Double, val at: Long, val kind: String, val accuracyM: Double? = null)
    fun pick(yolgo: Source?, maps: Source?, gps: Source?, now: Long): Source? = TODO("A3.5")
    fun near(s: Source, limitM: Double): Boolean = TODO("A3.5")
    fun slow(speedKmh: Double?): Boolean = TODO("A3.5")
}

