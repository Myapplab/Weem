package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.LogTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Eski toplamların güvenilir gün bilgisi yoktur: o taban korunur, yalnız yeni otomatik farklar gerçek gün kimliğiyle (`yolgo-<ms>`)
 * kaydedilir (MAP.md 9.3.5, 9.10 `roads.days.v2`; `day_contributions.dart` birebir). Gün silme yalnız o günün farkını geri alır.
 * Satır: way `[m, s, stops, vibSum, vibN, passes, lastAt]`, ww `[passes, problems, lastAt]`; son eleman toplanmaz, en büyüğü alınır.
 * [zone]: takvim günü dilimi (WEEM `Clock.zone`, testte Bakü +4). [stored] okunamazsa kurucu fırlatır (çağıran `damaged` kopyası alır).
 */
class RoadDayContributions(knowledge: RoadKnowledge, stored: String? = null, val zone: ZoneId = ZoneId.systemDefault()) {
    private val baseWays = LinkedHashMap<Long, DoubleArray>()
    private val baseWw = LinkedHashMap<Long, DoubleArray>()
    private val days = LinkedHashMap<String, Pair<MutableMap<Long, DoubleArray>, MutableMap<Long, DoubleArray>>>()
    private val seenWays = HashMap<Long, DoubleArray>()
    private val seenWw = HashMap<Long, DoubleArray>()

    init {
        if (stored != null) {
            val j = JSONObject(stored)
            baseWays.putAll(map(j.opt("baseWays")))
            baseWw.putAll(map(j.opt("baseWw")))
            val d = j.getJSONObject("days")
            for (key in d.keys()) {
                val o = d.getJSONObject(key)
                days[key] = map(o.opt("ways")) to map(o.opt("ww"))
            }
            rebuild(knowledge)
        } else {
            for ((id, s) in knowledge.ways) baseWays[id] = way(s)
            for ((id, s) in knowledge.ww) baseWw[id] = ww(s)
        }
        remember(knowledge)
    }

    private fun remember(k: RoadKnowledge) {
        seenWays.clear()
        seenWw.clear()
        for ((id, s) in k.ways) seenWays[id] = way(s)
        for ((id, s) in k.ww) seenWw[id] = ww(s)
        k.touchedWays.clear()
        k.touchedWw.clear()
    }

    fun capture(k: RoadKnowledge, day: String) {
        val bucket = days.getOrPut(day) { LinkedHashMap<Long, DoubleArray>() to LinkedHashMap() }
        capture(k, bucket.first, bucket.second)
    }

    /** Elle yapılan değişiklik tabana işlenir. */
    fun manual(k: RoadKnowledge) = capture(k, baseWays, baseWw)

    private fun capture(k: RoadKnowledge, ways: MutableMap<Long, DoubleArray>, wws: MutableMap<Long, DoubleArray>) {
        fun delta(id: Long, now: DoubleArray, seen: MutableMap<Long, DoubleArray>, target: MutableMap<Long, DoubleArray>) {
            val old = seen[id] ?: DoubleArray(now.size)
            val row = target.getOrPut(id) { DoubleArray(now.size) }
            for (i in 0 until now.size - 1) row[i] += now[i] - old[i]
            row[row.size - 1] = now[now.size - 1]
            seen[id] = now
        }
        for (id in k.touchedWays) k.ways[id]?.let { delta(id, way(it), seenWays, ways) }
        for (id in k.touchedWw) k.ww[id]?.let { delta(id, ww(it), seenWw, wws) }
        k.touchedWays.clear()
        k.touchedWw.clear()
    }

    /** [date] (`yyyy-MM-dd`) günlerinin katkısını siler; silinen gün kimliklerini döner. */
    fun deleteDate(k: RoadKnowledge, date: String): Set<String> {
        val removed = days.keys.filterTo(LinkedHashSet()) { id ->
            val ms = if (id.startsWith("yolgo-")) id.substring(6).toLongOrNull() else null
            ms != null && LogTime.day(ms, zone) == date
        }
        for (id in removed) days.remove(id)
        rebuild(k)
        remember(k)
        return removed
    }

    /** Tabandan ve bütün kovalardan siler ([id] null: hepsi), görüleni unutur. */
    fun resetWay(id: Long?, wrongWay: Boolean = false) {
        val baseline = if (wrongWay) baseWw else baseWays
        if (id == null) baseline.clear() else baseline.remove(id)
        for (d in days.values) {
            val rows = if (wrongWay) d.second else d.first
            if (id == null) rows.clear() else rows.remove(id)
        }
        val seen = if (wrongWay) seenWw else seenWays
        if (id == null) seen.clear() else seen.remove(id)
    }

    private fun rebuild(k: RoadKnowledge) {
        fun sum(baseline: Map<Long, DoubleArray>, isWw: Boolean): Map<Long, DoubleArray> {
            val out = LinkedHashMap<Long, DoubleArray>()
            for ((id, v) in baseline) out[id] = v.copyOf()
            for (d in days.values) {
                for ((id, v) in if (isWw) d.second else d.first) {
                    val row = out.getOrPut(id) { DoubleArray(v.size) }
                    for (i in 0 until row.size - 1) row[i] += v[i]
                    if (row[row.size - 1] < v[v.size - 1]) row[row.size - 1] = v[v.size - 1]
                }
            }
            return out
        }
        k.ways.clear()
        k.ww.clear()
        for ((id, v) in sum(baseWays, false)) k.ways[id] = WayStats.fromJson(v.toList())
        for ((id, v) in sum(baseWw, true)) k.ww[id] = WwStats.fromJson(v.toList())
    }

    fun encode(): String {
        fun rows(m: Map<Long, DoubleArray>) = JSONObject().also { o -> for ((id, v) in m) o.put("$id", JSONArray(v.toList())) }
        val d = JSONObject()
        for ((key, b) in days) d.put(key, JSONObject().put("ways", rows(b.first)).put("ww", rows(b.second)))
        return JSONObject().put("v", 2).put("baseWays", rows(baseWays)).put("baseWw", rows(baseWw)).put("days", d).toString()
    }

    private companion object {
        fun way(s: WayStats) = doubleArrayOf(s.m, s.s, s.stops, s.vibSum, s.vibN.toDouble(), s.passes.toDouble(), s.lastAt.toDouble())
        fun ww(s: WwStats) = doubleArrayOf(s.passes.toDouble(), s.problems, s.lastAt.toDouble())

        /** Dart `_map`: nesne değilse boş; anahtar sayı değilse ya da öğe sayı dizisi değilse fırlatır (bozuk kayıt). */
        fun map(j: Any?): MutableMap<Long, DoubleArray> {
            val out = LinkedHashMap<Long, DoubleArray>()
            if (j !is JSONObject) return out
            for (key in j.keys()) {
                val a = j.get(key) as JSONArray
                out[key.toLong()] = DoubleArray(a.length()) { (a.get(it) as Number).toDouble() }
            }
            return out
        }
    }
}
