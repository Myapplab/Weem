package com.kuryelog.logic.orders.dataset

import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.OfferSummaries
import com.kuryelog.logic.orders.Platforms
import org.json.JSONObject
import java.time.ZoneId

/**
 * Canlı teklif satırları (`yolgo-live.jsonl`; MAP.md 13.3, 13.11): `:orders`'ın iç teklif durumundan feed v2 karşılığı kurulur (adres metinleri yalnız koordinat / Plus Code
 * çıkarmak için okunur, satıra yazılmaz), [DatasetContract.offer] satıra çevirir, hava önbelleği (≤ 20 dk) ve okuma ölçüsü eklenir, [OfferRowGate] seyreltir.
 * Okuma/yoklama yok: çağıran teklif durumu değişince (ya da saniyelik tikte) [rows]'u çağırır; `readTrigger` = `observer`.
 */
class DatasetRecorder(private val zone: () -> ZoneId, private val region: () -> String?) {
    private val gate = OfferRowGate()

    /** `v2` + üst düzey alanlar: Rawstats `OfferFeedContract.offer` karşılığı (müşteri adı, telefon, not yok). */
    fun feedOffer(o: OfferState): JSONObject? {
        val platform = Platforms.of(o.pkg)?.takeIf { it == "wolt" || it == "bolt" } ?: return null
        val d = o.data
        val s = OfferSummaries.shown(d)
        fun n(v: Double?): Any = v ?: JSONObject.NULL
        val shown = JSONObject().put("km", n(s.km)).put("minLo", n(s.minLo)).put("minHi", n(s.minHi)).put("stops", n(s.stops?.toDouble())).put("readyInMin", n(s.readyInMin))
            .put("approachMin", n(s.approachMin)).put("deliveryMin", n(s.deliveryMin)).put("totalMin", n(s.totalMin)).put("multiplier", n(s.multiplier)).put("boostPct", n(s.boostPct))
        val feeSource = when (o.fieldSource["gross"] ?: d.source) { "ocr" -> "ocr"; "notification" -> "notification"; else -> "accessibility" }
        val confidence = when { d.gross == null -> "low"; feeSource == "accessibility" && d.complete -> "high"; else -> "medium" }
        val v2 = JSONObject().put("offerKey", o.id).put("platform", platform).put("firstSeenAtMs", o.firstAt).put("lastSeenAtMs", maxOf(o.lastSeenAt, o.firstAt))
            .put("shown", shown).put("parse", JSONObject().put("feeSource", feeSource).put("confidence", confidence))
        return JSONObject().put("displayedFeeAzn", n(d.gross)).put("platformDistanceKm", n(d.km))
            .put("pickupAddress", d.restaurantAddress ?: "").put("dropoffAddress", d.customerAddress ?: "").put("v2", v2)
    }

    /** `Gün → satırlar`: bu okumanın yazılacak satırları (seyreltilmiş). [elapsedMs] monoton ms (seyreltme saati); [courier] gözlenmiş kurye noktası ya da null. */
    fun rows(offers: Collection<OfferState>, wallMs: Long, monoNs: Long, elapsedMs: Long, courier: JSONObject?, weather: WeatherNow?): Pair<String, List<JSONObject>> {
        val z = zone()
        val day = DatasetContract.day(wallMs, z)
        val rows = ArrayList<JSONObject>()
        for (o in offers) {
            val feed = feedOffer(o) ?: continue
            val row = DatasetContract.offer(feed, wallMs, monoNs, courier, region(), z) ?: continue
            withWeather(row, weather, wallMs)
            DatasetContract.measure(row, wallMs, o.lastSeenAt.takeIf { it > 0 }, "observer")
            DatasetContract.flagUnknowns(row)
            rows += row
        }
        return day to gate.select(day, rows, elapsedMs)
    }

    /** `WeatherPort.current()` en çok 20 dk eskiyse üç değer (bayrak `existing_weather_cache`); değilse `unavailable` kalır. */
    private fun withWeather(row: JSONObject, w: WeatherNow?, wallMs: Long) {
        if (w == null || wallMs - w.fetchedAtMs !in 0..MAX_WEATHER_AGE_MS) return
        val flags = row.getJSONObject("field_sources")
        for ((key, v) in listOf("temperatureC" to w.tempC, "windKmh" to w.windKmh, "rainMm" to w.rainMm)) {
            if (v == null) continue
            row.put(key, v)
            flags.put(key, "existing_weather_cache")
        }
        row.put("weatherFetchedAtMs", w.fetchedAtMs)
    }

    companion object {
        const val FILE = "yolgo-live.jsonl"
        const val MAX_WEATHER_AGE_MS = 1_200_000L
    }
}
