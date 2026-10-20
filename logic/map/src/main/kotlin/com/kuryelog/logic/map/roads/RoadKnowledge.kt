package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.route.LearnedQuality
import com.kuryelog.logic.map.route.Quality
import com.kuryelog.logic.map.route.QualityRating
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.roundToLong

/**
 * Sokaklar hakkında öğrenilenler (OSM way id ile; harita güncellense de kalır) — `lib/roads/road_knowledge.dart` (MAP.md 9.2, 9.12):
 * yol kalitesi (dur-kalk, sarsıntı), onaylı sokakta ters yön geçiş/sorun sayısı, "yol yok" parçaları, ww kesimleri, elle puan.
 */

/** Dart `_r`: [decimals] 0 → tam sayı, aksi ondalık yuvarlama. */
private fun r(v: Double, decimals: Int = 0): Number {
    if (decimals <= 0) return Math.round(v)
    var f = 1.0
    repeat(decimals) { f *= 10 }
    return (v * f).roundToLong() / f
}

private fun num(a: JSONArray, i: Int): Number? = a.opt(i) as? Number

/** Bir sokakta sürüşten öğrenilenler. */
class WayStats {
    var m = 0.0 // sürülen metre
    var s = 0.0 // süre (sn)
    var stops = 0.0 // dur-kalk olayı (tam durma 1, durmadan yavaşlama 0.5)
    var vibSum = 0.0
    var vibN = 0
    var passes = 0
    var lastAt = 0L // ms

    /** Ortalama titreşim (m/s²); az ölçümde null. */
    val vib: Double? get() = if (vibN < 10) null else vibSum / vibN
    val stopsPerKm: Double get() = if (m <= 0) 0.0 else stops / (m / 1000)
    val avgKmh: Double get() = if (s <= 0) 0.0 else m / s * 3.6
    val score: Double get() = Quality.learnedScore(m, stops, vib)
    val confidence: Double get() = Quality.learnedConfidence(m)

    /** `[m, s, stops, vibSum, vibN, passes, lastAt]` (MAP.md 9.10). */
    fun toJson(): JSONArray = JSONArray().put(r(m)).put(r(s)).put(r(stops, 1)).put(r(vibSum, 1)).put(vibN).put(passes).put(lastAt)

    companion object {
        /** < 7 eleman ya da dizi değilse boş istatistik; sayı olmayan eleman 0. Gün defteri `List<Double>` de verir. */
        fun fromJson(j: Any?): WayStats {
            val w = WayStats()
            val a = when (j) {
                is JSONArray -> j
                is List<*> -> JSONArray(j)
                else -> return w
            }
            if (a.length() < 7) return w
            w.m = num(a, 0)?.toDouble() ?: 0.0
            w.s = num(a, 1)?.toDouble() ?: 0.0
            w.stops = num(a, 2)?.toDouble() ?: 0.0
            w.vibSum = num(a, 3)?.toDouble() ?: 0.0
            w.vibN = num(a, 4)?.toInt() ?: 0
            w.passes = num(a, 5)?.toInt() ?: 0
            w.lastAt = num(a, 6)?.toLong() ?: 0L
            return w
        }
    }
}

/** Onaylı bir sokakta ters yön geçişleri. */
class WwStats {
    var passes = 0
    var problems = 0.0 // cevap evet: 1 · algılandı ama cevap yok: 0.5
    var lastAt = 0L

    val ratio: Double get() = Quality.wrongWayRatio(passes, problems)

    fun toJson(): JSONArray = JSONArray().put(passes).put(r(problems, 1)).put(lastAt)

    companion object {
        fun fromJson(j: Any?): WwStats {
            val w = WwStats()
            val a = when (j) {
                is JSONArray -> j
                is List<*> -> JSONArray(j)
                else -> return w
            }
            if (a.length() < 3) return w
            w.passes = num(a, 0)?.toInt() ?: 0
            w.problems = num(a, 1)?.toDouble() ?: 0.0
            w.lastAt = num(a, 2)?.toLong() ?: 0L
            return w
        }
    }
}

/** Haritada görünen ama gerçekte olmayan yol parçası (ya da sokağın tamamı); ww kesimi listesi de bu tipi kullanır (`all` yok). */
class GhostMark(val way: Long, val lat: Double, val lon: Double, val name: String, val at: Long, val all: Boolean = false) {
    fun toJson(): JSONObject = JSONObject()
        .put("w", way)
        .put("la", Math.round(lat * 1e6) / 1e6)
        .put("lo", Math.round(lon * 1e6) / 1e6)
        .put("n", name)
        .put("t", at)
        .also { if (all) it.put("all", true) }

    companion object {
        fun fromJson(j: Any?): GhostMark? {
            if (j !is JSONObject) return null
            val w = j.opt("w") as? Number ?: return null
            val la = j.opt("la") as? Number ?: return null
            val lo = j.opt("lo") as? Number ?: return null
            val n = j.opt("n")
            return GhostMark(
                way = w.toLong(), lat = la.toDouble(), lon = lo.toDouble(),
                name = if (n == null || n == JSONObject.NULL) "" else "$n",
                at = (j.opt("t") as? Number)?.toLong() ?: 0L,
                all = j.opt("all") == true,
            )
        }
    }
}

class RoadKnowledge(
    val ways: MutableMap<Long, WayStats> = HashMap(),
    val ww: MutableMap<Long, WwStats> = HashMap(),
    val ghosts: MutableList<GhostMark> = ArrayList(),
    val wwSegs: MutableList<GhostMark> = ArrayList(),
    val ratings: MutableMap<Long, Int> = HashMap(),
    val names: MutableMap<Long, String> = HashMap(),
) {
    val touchedWays = HashSet<Long>()
    val touchedWw = HashSet<Long>()

    /** Adsız yol: TR "isimsiz yol", AZ "adsız yol". */
    fun nameOf(way: Long, lang: Lang = Lang.TR): String {
        val n = names[way]
        return if (n.isNullOrEmpty()) tl(lang, "isimsiz yol", "adsız yol") else n
    }

    fun stats(way: Long): WayStats {
        touchedWays += way
        return ways.getOrPut(way) { WayStats() }
    }

    fun wwStats(way: Long): WwStats {
        touchedWw += way
        return ww.getOrPut(way) { WwStats() }
    }

    val learnedKm: Double get() = ways.values.sumOf { it.m } / 1000

    /**
     * Rota motoruna: **yalnız elle puanlar** `(0, 0, r)`. Otomatik dur-kalk/titreşim ekranda kalır, rotaya girmez (YolGo 1.11.0;
     * MAP.md 9.9).
     */
    fun learnedForRouting(): Map<Long, LearnedQuality> {
        val out = LinkedHashMap<Long, LearnedQuality>()
        for ((way, rating) in ratings) {
            if (rating == QualityRating.NONE || way in out) continue
            out[way] = LearnedQuality(0.0, 0.0, rating)
        }
        return out
    }

    /** Bir sokağın son kalitesi (ekran için). Graf varsa OSM ön tahmini katılır, yoksa 0,8. */
    fun qualityOf(way: Long, g: RoadGraph? = null): Double {
        var prior = 0.8
        val wi = g?.wayIndexOf(way)
        if (g != null && wi != null) {
            val cls = g.classOfOsmWay(way)
            if (cls != null) prior = Quality.priorQuality(cls, g.wayFlags[wi].toInt() and 0xFFFF)
        }
        val st = ways[way]
        val rating = ratings[way] ?: QualityRating.NONE
        val l = when {
            st != null -> LearnedQuality(st.score, st.confidence, rating)
            rating == QualityRating.NONE -> null
            else -> LearnedQuality(0.0, 0.0, rating)
        }
        return Quality.blendQuality(prior, l)
    }

    /** Ters yön: yumuşatılmış sorun oranı (yalnız geçişi olan sokaklar). */
    fun wrongWayRatios(): Map<Long, Double> {
        val out = LinkedHashMap<Long, Double>()
        for ((way, w) in ww) if (w.passes > 0) out[way] = Quality.wrongWaySmoothed(w.passes, w.problems)
        return out
    }

    /** Sorun oranı eşiği ([threshold] 0–1) aşan sokaklar (en az [minPasses] geçiş). */
    fun wrongWayAvoid(threshold: Double, minPasses: Int = 3): Set<Long> {
        val out = LinkedHashSet<Long>()
        for ((way, w) in ww) if (w.passes >= minPasses && w.ratio >= threshold) out += way
        return out
    }

    /** "Yol yok" işaretlerinin bu haritadaki kenarları. */
    fun ghostEdges(g: RoadGraph): Set<Int> {
        val out = LinkedHashSet<Int>()
        for (m in ghosts) {
            if (m.all) {
                out += g.edgesOfOsmWay(m.way)
                continue
            }
            g.nearestEdge(m.lat, m.lon, maxDist = 30.0, accept = { e -> g.osmIdOf(e) == m.way })?.let { out += it.edge }
        }
        return out
    }

    /** Ters yön izinli kesimlerin bu haritadaki kenarları. */
    fun wwSegEdges(g: RoadGraph): Set<Int> {
        val out = LinkedHashSet<Int>()
        for (m in wwSegs) g.nearestEdge(m.lat, m.lon, maxDist = 30.0, accept = { e -> g.osmIdOf(e) == m.way })?.let { out += it.edge }
        return out
    }

    /** Bu kenarın kesiminde ters yön izni var mı (orta noktaya < 15 m, aynı way; uzun basış satırı). */
    fun wwSegAt(g: RoadGraph, edge: Int): GhostMark? {
        val way = g.osmIdOf(edge)
        val mid = g.geometry(edge, 0.5, 0.5).first()
        return wwSegs.firstOrNull { it.way == way && Geo.haversine(it.lat, it.lon, mid.lat, mid.lon) < 15 }
    }

    companion object {
        /** Bozuk JSON → boş (YolGo); dosya düzeyinde `damaged` kopyası için [decodeMapOrNull]. */
        fun <T> decodeMap(raw: String?, f: (Any?) -> T): Map<Long, T> = decodeMapOrNull(raw, f) ?: LinkedHashMap()

        /** Çözülemeyen (nesne olmayan ya da bozuk) metin → null; boş/yok → boş harita. */
        fun <T> decodeMapOrNull(raw: String?, f: (Any?) -> T): Map<Long, T>? {
            val out = LinkedHashMap<Long, T>()
            if (raw.isNullOrEmpty()) return out
            return try {
                val j = JSONTokener(raw).nextValue() as? JSONObject ?: return null
                for (k in j.keys()) {
                    val id = k.toLongOrNull() ?: continue
                    out[id] = f(j.opt(k).takeUnless { it == JSONObject.NULL })
                }
                out
            } catch (e: Exception) {
                null
            }
        }

        fun <T> encodeMap(m: Map<Long, T>, f: (T) -> Any?): String {
            val j = JSONObject()
            for ((k, v) in m) j.put("$k", f(v))
            return j.toString()
        }

        fun decodeGhosts(raw: String?): List<GhostMark> = decodeGhostsOrNull(raw) ?: emptyList()

        fun decodeGhostsOrNull(raw: String?): List<GhostMark>? {
            if (raw.isNullOrEmpty()) return emptyList()
            return try {
                val j = JSONTokener(raw).nextValue() as? JSONArray ?: return null
                (0 until j.length()).mapNotNull { GhostMark.fromJson(j.opt(it)) }
            } catch (e: Exception) {
                null
            }
        }

        fun encodeGhosts(g: List<GhostMark>): String = JSONArray().also { a -> g.forEach { a.put(it.toJson()) } }.toString()
    }
}
