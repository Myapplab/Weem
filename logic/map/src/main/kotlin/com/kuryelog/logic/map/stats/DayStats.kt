package com.kuryelog.logic.map.stats

import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.map.day.DayState
import org.json.JSONObject
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToLong

/** Gün özeti (`ShiftController.summarize`, MAP.md 12.6). */
object DayStats {
    private fun r2(v: Double) = (v * 100).roundToLong() / 100.0

    fun summarize(d: DayState, nowMs: Long): JSONObject {
        val end = d.endedAtMs ?: nowMs
        val lights = d.lights
        val done = d.orders.filter { it.deliveredAtMs != null }
        val home = d.homeReturnAtMs
        return JSONObject()
            .put("totalS", if (d.startedAtMs == null) 0 else (end - d.startedAtMs!!) / 1000)
            .put("movingS", d.movingS.roundToLong())
            .put("km", r2(d.distM / 1000))
            .put("homeKm", r2((d.homeDistM + if (home == null) 0.0 else max(0.0, d.distM - d.homeStartM)) / 1000))
            .put("homeS", d.homeTimeS + if (home == null) 0 else max(0L, (end - home) / 1000))
            .put("stops", d.stops.size)
            .put("lights", lights.size)
            .put("lightWaitS", lights.sumOf { it.durS })
            .put("lightCandidates", d.lightCandidates.size)
            .put("orders", d.orders.size)
            .put("delivered", done.size)
            .put("restWaitS", done.sumOf { it.restWaitS ?: 0 })
            .put("custWaitS", done.sumOf { it.custWaitS ?: 0 })
    }
}

/** Gün raporu dosyası (`.gun.json`, MAP.md 12.6): ad ve kodek. */
object DayReportFile {
    /** `<LogTime.day(başlangıç)>_<HHmm yerel>.gun.json` (başlangıç yoksa [nowMs]). */
    fun name(startedAtMs: Long?, nowMs: Long, zone: ZoneId): String {
        val t = startedAtMs ?: nowMs
        return "${LogTime.day(t, zone)}_${DurationText.clock(t, zone).replace(":", "")}.gun.json"
    }

    /** `DayState.toJson()` + `summary` + `lightsLearned` (onaylı ışıklar, ham JSON dizisi), bir boşluk girintili. */
    fun encode(d: DayState, nowMs: Long, lightsLearned: org.json.JSONArray): String =
        d.toJson().put("summary", DayStats.summarize(d, nowMs)).put("lightsLearned", lightsLearned).toString(1)

    fun decode(text: String): DayState = DayState.fromJson(JSONObject(text))
}

/** Gün geçmişi (`history`, MAP.md 12.6): süren gün + en yeni 60 rapor + son biten gün; başlangıç ms'ine göre tekil, başlangıca göre azalan. */
object DayHistory {
    const val MAX_FILES = 60

    /** [files] ada göre azalan sıradaki (okunamayanlar çıkarılmış) raporlar; ilk gelen kazanır: süren gün, dosya, son gün. */
    fun merge(active: DayState?, files: List<DayState>, last: DayState?): List<DayState> {
        val out = ArrayList<DayState>()
        val seen = HashSet<Long>()
        if (active != null) {
            out += active
            active.startedAtMs?.let { seen += it }
        }
        for (d in files.take(MAX_FILES)) {
            val k = d.startedAtMs ?: continue
            if (seen.add(k)) out += d
        }
        if (last != null) {
            val k = last.startedAtMs
            if (k != null && seen.add(k)) out += last
        }
        return out.sortedByDescending { it.startedAtMs ?: Long.MIN_VALUE }
    }
}
