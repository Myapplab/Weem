package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.calibration.RoadCalibrationScore
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.route.Quality
import com.kuryelog.logic.map.surface.SurfaceSummary
import kotlin.math.abs
import kotlin.math.max

// E11 (C4) sade "Yol puanları" — YolGo `lib/roads/street_scores.dart` @ 870ab6e. Sokak = OSM adı (adsız yollar yol başına ayrı); puan = sokağın
// E10 puanlı 50 m parçalarının uzunluk ağırlıklı ortancası. Puanlı parça STREET_MIN_PIECES'ten ya da puanlı uzunluk STREET_MIN_METERS'ten azsa
// puan yok: ekranda "ölçüm az" ("Belirsiz" yazılmaz).

const val STREET_MIN_PIECES = 3
const val STREET_MIN_METERS = 200.0

/**
 * Sokak ya da parça puanı: 1–10 değer veya gerçek referansa göre açık sınır. Sıra: sınırlar kendi seviyesinde, "k'den kötü" < k,0 < "k'den iyi".
 */
class StreetScore private constructor(val value: Double?, val betterThan: Int?, val worseThan: Int?) : Comparable<StreetScore> {
    companion object {
        fun value(v: Double) = StreetScore(v, null, null)
        fun betterThan(n: Int) = StreetScore(null, n, null)
        fun worseThan(n: Int) = StreetScore(null, null, n)
        fun of(s: RoadCalibrationScore): StreetScore = when {
            s.value != null -> value(s.value)
            s.betterThan != null -> betterThan(s.betterThan)
            else -> worseThan(s.worseThan!!)
        }
    }

    /** Sınırın kendi seviyesi; renk ve sıra için. */
    val level: Double get() = value ?: (betterThan ?: worseThan)!!.toDouble()
    private val rank get() = if (worseThan != null) -1 else if (betterThan != null) 1 else 0

    /** Rota için 1–10 → 0–1; sınır yarım seviye ötesinde sayılır ("7'den iyi" = 7,5). */
    val routeQuality: Double get() = Quality.streetScoreQuality(value ?: if (betterThan != null) betterThan + .5 else worseThan!! - .5)

    override fun compareTo(other: StreetScore): Int = level.compareTo(other.level).let { if (it != 0) it else rank.compareTo(other.rank) }
    override fun equals(other: Any?) = other is StreetScore && other.value == value && other.betterThan == betterThan && other.worseThan == worseThan
    override fun hashCode() = java.util.Objects.hash(value, betterThan, worseThan)
    override fun toString() = StreetScores.label(this, Lang.TR)
}

typealias PieceScorer = (SurfaceSummary) -> StreetScore?

/** Profil başına tek referans dizini; bir ekran kurulumundaki bütün satırlar paylaşır. */
class ProfileReferences(sessions: Iterable<RoadCalibrationRecord>) {
    private val grouped = HashMap<String, MutableList<RoadCalibrationRecord>>()
    private val references = HashMap<String, RoadCalibrationReferences>()

    init { for (r in sessions) grouped.getOrPut(r.profile) { ArrayList() }.add(r) }

    fun of(profile: String): RoadCalibrationReferences = references.getOrPut(profile) { RoadCalibrationReferences(grouped[profile].orEmpty()) }

    /** Parça puanı: E10 kuralı (profilin referansları, parçanın baskın hız bandı). */
    fun score(piece: SurfaceSummary): StreetScore? {
        val band = piece.dominantRoadBand ?: return null
        return of(piece.profile).score(band.rms, band.speed * 3.6)?.let { StreetScore.of(it) }
    }
}

class Street(
    /** `name:AD` ya da adsız yolda `way:OSM_ID`. */
    val key: String,
    /** null: OSM'de adsız. */
    val name: String?,
    val ways: Set<Long>,
    /** Sokağın bütün ölçülmüş parçaları (puansızlar dahil, iki yön ayrı). */
    val pieces: List<SurfaceSummary>,
    /** Parça anahtarı → E10 parça puanı (yoksa null). */
    val scores: Map<String, StreetScore?>,
    /** Puanlı fiziksel parça sayısı ve uzunluğu: iki yönde ölçülen aynı parça bir kez. */
    val scoredPieces: Int,
    val scoredMeters: Double,
    /** null: ölçüm az. */
    val score: StreetScore?,
)

object StreetScores {
    fun calibrationScorer(sessions: Iterable<RoadCalibrationRecord>): PieceScorer = ProfileReferences(sessions)::score

    /** Sokak kimliği: aynı ad tek sokak, adsız yol kendi başına. */
    fun streetKeyOf(way: Long, name: String?): String = if (name.isNullOrEmpty()) "way:$way" else "name:$name"

    /** "6,3" · "7'den iyi" / "7-dən yaxşı" · "5'ten kötü" / "5-dən pis" · "ölçüm az". */
    fun label(s: StreetScore?, lang: Lang): String {
        if (s == null) return tl(lang, "ölçüm az", "ölçmə az")
        if (s.value != null) return String.format(java.util.Locale.US, "%.1f", s.value).replace('.', ',')
        val n = (s.betterThan ?: s.worseThan)!!
        // TR: bir-iki-yedi-sekiz 'den, üç-dört-beş 'ten, altı-dokuz-on 'dan; AZ: altı-doqquz-on -dan, diğerleri -dən.
        val tr = when (n) { 3, 4, 5 -> "'ten"; 6, 9, 10 -> "'dan"; else -> "'den" }
        val az = when (n) { 6, 9, 10 -> "-dan"; else -> "-dən" }
        return if (s.betterThan != null) tl(lang, "$n$tr iyi", "$n$az yaxşı") else tl(lang, "$n$tr kötü", "$n$az pis")
    }

    /** Parça anahtarı 'way/anchor/yön/sıra'; yön atılınca aynı fiziksel parça. */
    private fun physical(p: SurfaceSummary): String = p.key.split('/').let { if (it.size < 4) p.key else "${it[0]}/${it[1]}/${it[3]}" }

    /** Uzunluk ağırlıklı ortanca. Tam yarıda bölünürse iki değerin ortası; taraflardan biri sınırsa ortalama alınamaz, kötü olan seçilir. */
    private fun weightedMedian(items: MutableList<Pair<StreetScore, Double>>): StreetScore? {
        if (items.isEmpty()) return null
        items.sortWith { a, b -> a.first.compareTo(b.first) }
        val total = items.sumOf { it.second }
        val half = total / 2
        val eps = total * 1e-9
        var sum = 0.0
        for (i in items.indices) {
            sum += items[i].second
            if (sum < half - eps) continue
            val low = items[i].first
            if (abs(sum - half) <= eps && i + 1 < items.size && low.value != null && items[i + 1].first.value != null) {
                return StreetScore.value((low.value + items[i + 1].first.value!!) / 2)
            }
            return low
        }
        return items.last().first
    }

    /** Sokaklar kötüden iyiye; "ölçüm az" sonda; eşitlikte ad, sonra en küçük OSM id. */
    fun buildStreets(pieces: Iterable<SurfaceSummary>, scoreOf: PieceScorer, nameOf: (Long) -> String?): List<Street> {
        val groups = LinkedHashMap<String, MutableList<SurfaceSummary>>()
        val names = HashMap<String, String?>()
        for (p in pieces) {
            val name = nameOf(p.way)
            val key = streetKeyOf(p.way, name)
            groups.getOrPut(key) { ArrayList() } += p
            names[key] = if (name.isNullOrEmpty()) null else name
        }
        val streets = ArrayList<Street>()
        for ((key, group) in groups) {
            val scores = LinkedHashMap<String, StreetScore?>()
            for (p in group) scores[p.key] = scoreOf(p)
            val scored = group.filter { scores[it.key] != null }
            val physical = HashMap<String, Double>()
            for (p in scored) physical[physical(p)] = max(physical[physical(p)] ?: 0.0, p.segmentLength)
            val meters = physical.values.sum()
            val enough = physical.size >= STREET_MIN_PIECES && meters >= STREET_MIN_METERS - 1e-6
            streets += Street(key, names[key], group.map { it.way }.toSet(), group, scores, physical.size, meters,
                if (enough) weightedMedian(scored.map { scores[it.key]!! to it.segmentLength }.toMutableList()) else null)
        }
        fun minWay(s: Street) = s.ways.min()
        streets.sortWith { a, b ->
            val sa = a.score; val sb = b.score
            if (sa == null || sb == null) {
                if (sa != sb) return@sortWith if (sa == null) 1 else -1
            } else {
                val c = sa.compareTo(sb)
                if (c != 0) return@sortWith c
            }
            val n = (a.name ?: "").compareTo(b.name ?: "")
            if (n != 0) n else minWay(a).compareTo(minWay(b))
        }
        return streets
    }

    /**
     * Rota için: puanlı sokağın ölçülmüş her parçası (puansız parçaları dahil) → sokağın 0–1 kalitesi. "Ölçüm az" sokakların parçaları yok: rota
     * onlarda eski 0–100 parça puanını kullanır.
     */
    fun routeQuality(streets: Iterable<Street>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (s in streets) s.score?.let { score -> for (p in s.pieces) out[p.key] = score.routeQuality }
        return out
    }

    /** Ölçülmüş parçalar, kalibrasyon kayıtları ve yol adlarından rotanın sokak kalitesi haritası (parça yoksa boş). */
    fun routeQuality(pieces: List<SurfaceSummary>, sessions: List<RoadCalibrationRecord>, nameOf: (Long) -> String?): Map<String, Double> =
        if (pieces.isEmpty()) emptyMap() else routeQuality(buildStreets(pieces, calibrationScorer(sessions), nameOf))
}
