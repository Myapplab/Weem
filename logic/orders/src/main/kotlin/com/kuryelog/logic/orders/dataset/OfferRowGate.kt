package com.kuryelog.logic.orders.dataset

import org.json.JSONObject

/**
 * Satır seyreltme (`OfferRowGate`, MAP.md 13.3): teklif ilk kez → yaz; tam iz aynı → yazma; çekirdek iz değişti ya da son yazımdan ≥ [DatasetContract.ROW_GAP_MS] → yaz;
 * değilse beklet. Görünmeyen teklifin bekleyen satırı yazılır (son görülme kaybolmaz). En çok 512 teklif hatırlanır. İskelet (A6.2b).
 */
class OfferRowGate {
    fun select(day: String, rows: List<JSONObject>, nowMs: Long): List<JSONObject> = TODO("A6.9")
}
