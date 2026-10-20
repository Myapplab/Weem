package com.kuryelog.logic.orders.dataset

import org.json.JSONObject

/**
 * Satır seyreltme (`OfferRowGate`, MAP.md 13.3): teklif ilk kez → yaz; tam iz aynı → yazma; çekirdek iz değişti ya da son yazımdan ≥ [gapMs] → yaz;
 * değilse beklet. Görünmeyen teklifin bekleyen satırı yazılır (son görülme kaybolmaz). En çok [keep] teklif hatırlanır.
 */
class OfferRowGate(private val gapMs: Long = DatasetContract.ROW_GAP_MS, private val keep: Int = 512) {
    private class Seen(val day: String, var full: String, var core: String, var writtenAt: Long, var held: JSONObject?)

    private val seen = LinkedHashMap<String, Seen>()

    /** Bu okumanın [day] günündeki teklif [rows]'u (teklif başına bir) [now] (geçen ms) anında: eklenecek satırlar, eskiden yeniye. */
    fun select(day: String, rows: List<JSONObject>, nowMs: Long): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val present = rows.mapTo(HashSet()) { it.getString("offerKey") }
        for ((key, s) in seen) {
            val held = s.held ?: continue
            if (s.day != day || key.substringAfter('/') in present) continue
            out.add(held)
            s.full = DatasetContract.changedFingerprint(held)
            s.core = DatasetContract.coreFingerprint(held)
            s.writtenAt = nowMs
            s.held = null
        }
        for (row in rows) {
            val key = "$day/${row.getString("offerKey")}"
            val full = DatasetContract.changedFingerprint(row)
            val core = DatasetContract.coreFingerprint(row)
            val s = seen[key]
            when {
                s == null -> {
                    seen[key] = Seen(day, full, core, nowMs, null)
                    out.add(row)
                }
                full == s.full -> s.held = null
                core != s.core || nowMs - s.writtenAt >= gapMs -> {
                    s.full = full
                    s.core = core
                    s.writtenAt = nowMs
                    s.held = null
                    out.add(row)
                }
                else -> s.held = row
            }
        }
        while (seen.size > keep) seen.remove(seen.keys.first())
        return out
    }
}
