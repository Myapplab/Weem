package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (Readiness.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
object Readiness {
    enum class Kind { READY, NOW, COUNTDOWN, PREPARING, UNKNOWN }
    data class State(val kind: Kind, val minutes: Int? = null)
    fun of(p: PickupRecord, now: Long): State = TODO("A3.5")
    fun headline(s: State, tl: (String, String) -> String): String = TODO("A3.5")
    fun short(s: State, tl: (String, String) -> String): String = TODO("A3.5")
    fun fromOffer(offer: OfferState?): Long? = TODO("A3.5")
}

