package com.kuryelog.logic.orders

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (RecordJson.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
internal fun JSONObject.str(k: String): String? = TODO("A3.5")

internal fun JSONObject.num(k: String): Double? = TODO("A3.5")

internal fun JSONObject.longOrNull(k: String): Long? = TODO("A3.5")

internal fun JSONArray.strings(): List<String> = TODO("A3.5")

internal fun JSONArray.objects(): List<org.json.JSONObject> = TODO("A3.5")

internal object RecordJson {
    fun restorable(today: List<JSONObject>, yesterday: List<JSONObject>): List<TripRecord> = TODO("A3.5")
    fun details(d: DeliveryDetails): JSONObject = TODO("A3.5")
    fun details(j: JSONObject): DeliveryDetails = TODO("A3.5")
    fun observation(o: CourierObservation): JSONObject = TODO("A3.5")
    fun observation(j: JSONObject): CourierObservation = TODO("A3.5")
    fun offer(o: OfferState): JSONObject = TODO("A3.5")
    fun offer(j: JSONObject): OfferState = TODO("A3.5")
    fun trip(t: TripRecord, withPhone: Boolean = false): JSONObject = TODO("A3.5")
    fun trip(j: JSONObject): TripRecord = TODO("A3.5")
}

