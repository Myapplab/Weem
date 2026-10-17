package com.kuryelog.logic.orders

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// İskelet (A3.2): imzalar Rawstats'tan (OfferWeather.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.5'te.
data class OfferWeather(val tempC: Double?, val feelsC: Double?, val rainMm: Double?, val rainPct: Double?, val windKmh: Double?,
                        val gustKmh: Double?, val code: Int?, val fetchedAt: Long, val source: String) {
    companion object {
        const val SOURCE_YOLGO = "yolgo_open_meteo"
        const val NOT_OBSERVED = "not_observed"
        const val MAX_AGE_MS = 90 * 60_000L
        fun parse(json: String?, now: Long): OfferWeather? = TODO("A3.5")
        fun fromRecord(j: JSONObject): OfferWeather? = TODO("A3.5")
    }
    fun into(j: JSONObject): JSONObject = TODO("A3.5")
    fun kind(tl: (String, String) -> String): String? = TODO("A3.5")
}

