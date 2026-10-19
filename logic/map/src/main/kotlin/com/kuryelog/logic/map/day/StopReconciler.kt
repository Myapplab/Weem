package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo

/** Ölçer ve motor duruş kayıtlarının birleşmesi (Dart `_reconcileStops`, MAP.md 11.10): aynı duruşun iki kez kaydını önler. */
object StopReconciler {
    private const val WINDOW_MS = 3_000L
    private const val NEAR_M = 40.0

    /**
     * [events]: bu adımın motor olayları; [measured]: ölçerin yeni kaydı (önceden `state.stops`'a eklenmiş). Ölçer kazanır (3 sn eşik, bitiş = ilk hareket);
     * sipariş duruşu eşleşen ölçer kayıtlarını siler. Dönen liste kalan olaylar (+ ölçer kaydının `stop` olayı); `state.stops` yerinde değişir.
     */
    fun reconcile(state: DayState, events: List<DayEvent>, measured: StopRec?): List<DayEvent> {
        val kept = ArrayList<DayEvent>()
        for (event in events) {
            if (event.type != "stop") { kept += event; continue }
            val start = (event.data["start"] as? Number)?.toLong()
            val raw = state.stops.firstOrNull { !it.measured && it.startMs == start }
            if (raw == null) { kept += event; continue }
            val matching = state.stops.filter {
                it.measured && it.startMs < raw.endMs + WINDOW_MS && it.endMs > raw.startMs - WINDOW_MS &&
                    Geo.haversine(it.lat, it.lon, raw.lat, raw.lon) <= NEAR_M
            }
            if (raw.kind == "order") {
                state.stops.removeAll { s -> matching.any { it === s } }
                kept += event
            } else if (matching.isNotEmpty()) {
                state.stops.remove(raw)
            } else {
                kept += event
            }
        }
        if (measured != null && state.stops.any { it === measured }) kept += DayEvent("stop", measured.endMs, measured.toJsonMap())
        return kept
    }
}
