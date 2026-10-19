package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.LearnedQuality
import org.json.JSONArray

/**
 * Sokaklar hakkında öğrenilenler (OSM way id ile; harita güncellense de kalır) — `lib/roads/road_knowledge.dart` (MAP.md 9.12).
 * İskelet (A6.2a): gövdeler A6.11'de. Sayaç alanları düz veridir.
 */

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
    val vib: Double? get() = TODO("A6.11")
    val score: Double get() = TODO("A6.11")
    val confidence: Double get() = TODO("A6.11")

    /** `[m, s, stops, vibSum, vibN, passes, lastAt]` (MAP.md 9.10). */
    fun toJson(): JSONArray = TODO("A6.11")

    companion object {
        fun fromJson(j: Any?): WayStats = TODO("A6.11")
    }
}

/** Onaylı bir sokakta ters yön geçişleri. */
class WwStats {
    var passes = 0
    var problems = 0.0 // cevap evet: 1 · algılandı ama cevap yok: 0.5
    var lastAt = 0L

    val ratio: Double get() = TODO("A6.11")
    fun toJson(): JSONArray = TODO("A6.11")

    companion object {
        fun fromJson(j: Any?): WwStats = TODO("A6.11")
    }
}

/** Haritada görünen ama gerçekte olmayan yol parçası (ya da sokağın tamamı). */
class GhostMark(val way: Long, val lat: Double, val lon: Double, val name: String, val at: Long, val all: Boolean = false)

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
    fun nameOf(way: Long, lang: Lang = Lang.TR): String = TODO("A6.11")

    fun stats(way: Long): WayStats = TODO("A6.11")
    fun wwStats(way: Long): WwStats = TODO("A6.11")

    /** Rota motoruna: öğrenilmiş ya da elle puanlanmış sokaklar. */
    fun learnedForRouting(): Map<Long, LearnedQuality> = TODO("A6.11")

    /** Sorun oranı eşiği ([threshold] 0–1) aşan sokaklar (en az [minPasses] geçiş). */
    fun wrongWayAvoid(threshold: Double, minPasses: Int = 3): Set<Long> = TODO("A6.11")

    fun ghostEdges(g: RoadGraph): Set<Int> = TODO("A6.11")
    fun wwSegEdges(g: RoadGraph): Set<Int> = TODO("A6.11")

    companion object {
        fun <T> decodeMap(raw: String?, f: (Any?) -> T): Map<Long, T> = TODO("A6.11")
        fun <T> encodeMap(m: Map<Long, T>, f: (T) -> Any?): String = TODO("A6.11")
        fun decodeGhosts(raw: String?): List<GhostMark> = TODO("A6.11")
        fun encodeGhosts(g: List<GhostMark>): String = TODO("A6.11")
    }
}
