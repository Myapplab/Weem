package com.kuryelog.logic.orders

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (OnlineSessions.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
class OnlineSessions {
    data class Session(val pkg: String, val startAt: Long, val startObserved: Boolean, var lastOnlineAt: Long,
        var offers: Int = 0, var accepted: Int = 0, var delivered: Int = 0,
        val id: String = idFor(pkg, startAt), var rejected: Int = 0,
        val offerIds: MutableList<String> = mutableListOf(), val tripIds: MutableList<String> = mutableListOf(),
        val startEvidence: String? = null)
    data class Closed(val pkg: String, val startAt: Long, val endAt: Long, val startObserved: Boolean, val endObserved: Boolean,
        val endReason: String, val offers: Int, val accepted: Int, val delivered: Int,
        val id: String = idFor(pkg, startAt), val rejected: Int = 0,
        val offerIds: List<String> = emptyList(), val tripIds: List<String> = emptyList(),
        /** Last online screen before the end: the real end lies between this and endAt. */
        val lastOnlineAt: Long = endAt, val startEvidence: String? = null) {
        val minutes: Double get() = TODO("A3.5")
    }
    fun openSessions(): List<Session> = TODO("A3.5")
    fun currentId(pkg: String): String? = TODO("A3.5")
    fun known(): Set<String> = TODO("A3.5")
    fun state(pkg: String): String? = TODO("A3.5")
    fun observe(o: CourierObservation): Closed? = TODO("A3.5")
    fun event(e: CoreEvent, tripOrders: Int = 1, inferredBefore: Boolean = false): Unit = TODO("A3.5")
    fun stop(reason: String = "shift_stop"): List<Closed> = TODO("A3.5")
    fun encode(): String = TODO("A3.5")
    fun restore(raw: String?): Unit = TODO("A3.5")
    companion object {
        fun idFor(pkg: String, startAt: Long): String = TODO("A3.5")
        fun json(c: Closed): JSONObject = TODO("A3.5")
        fun closed(j: JSONObject): Closed = TODO("A3.5")
    }
}

