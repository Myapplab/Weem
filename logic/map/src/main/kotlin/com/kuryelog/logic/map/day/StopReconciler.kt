package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo

/** Ölçer ve motor kayıtlarının birleşmesi (`_reconcileStops`, MAP.md 11.10): aynı duruşun iki kez kaydını önler. */
object StopReconciler {
    private const val OVERLAP_MS = 3000L
    private const val NEAR_M = 40.0

    /**
     * [events]: bu adımın motor olayları; [measured]: ölçerin yeni kaydı (önceden `state.stops`'a eklenmiş). Dönen: kalan olaylar (+ ölçer kaydı için `stop`).
     */
    fun reconcile(state: DayState, events: List<DayEvent>, measured: StopRec?): List<DayEvent> {
        val out = ArrayList<DayEvent>()
        for (e in events) {
            if (e.type != "stop") { out += e; continue }
            val j = e.data["stop"] as? org.json.JSONObject
            val raw = if (j == null) null else state.stops.firstOrNull { !it.measured && it.startMs == j.optLong("start", -1L) }
            if (raw == null) { out += e; continue }
            val matching = state.stops.filter {
                it.measured && it.startMs < raw.endMs + OVERLAP_MS && it.endMs > raw.startMs - OVERLAP_MS &&
                    Geo.haversine(it.lat, it.lon, raw.lat, raw.lon) <= NEAR_M
            }
            if (raw.kind == "order") {
                state.stops.removeAll(matching.toSet())
                out += e
            } else if (matching.isNotEmpty()) {
                state.stops.remove(raw)
            } else {
                out += e
            }
        }
        if (measured != null && state.stops.contains(measured)) out += DayEvent("stop", measured.endMs, mapOf("stop" to measured.toJson()))
        return out
    }
}
