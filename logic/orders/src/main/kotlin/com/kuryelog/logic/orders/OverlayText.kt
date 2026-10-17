package com.kuryelog.logic.orders

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (OverlayText.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object OverlayText {
    const val SEP = " · "
    fun distance(km: Double): String = TODO("A3.5")
    fun offer(platform: String, gross: Double?, label: String?, net: Double? = null, tl: (String, String) -> String): String = TODO("A3.5")
    fun net(gross: Double?, commissionPct: Double?): Double? = TODO("A3.5")
    fun trip(platform: String, label: String?, phase: TripPhase, tl: (String, String) -> String): String = TODO("A3.5")
    fun tripStatus(platform: String, phase: TripPhase, tl: (String, String) -> String): String = TODO("A3.5")
    fun phase(p: TripPhase, tl: (String, String) -> String): String = TODO("A3.5")
    fun status(platform: String, online: Boolean, tl: (String, String) -> String): String = TODO("A3.5")
    fun today(delivered: Int, tl: (String, String) -> String): String = TODO("A3.5")
    fun badge(core: DeliveryCore, sessions: OnlineSessions, delivered: Int, names: Boolean, commission: (String) -> Double?,
              platform: (String) -> String, tl: (String, String) -> String): String = TODO("A3.5")
}

