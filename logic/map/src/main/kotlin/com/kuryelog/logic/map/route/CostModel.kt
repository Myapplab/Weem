package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag
import com.kuryelog.logic.map.surface.Passability
import com.kuryelog.logic.map.surface.SurfaceKey
import com.kuryelog.logic.map.surface.SurfaceMark
import com.kuryelog.logic.map.surface.SurfaceSummary
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bir sorgu grubu için kenar maliyetleri — YolGo `CostModel` (`router.dart:26–342`; kurallar MAP.md 3.4–3.5). Her kenarın iki
 * yönü ayrı hücre: `i = edge * 2 + dir` (0 = çizim yönü `from → to`, 1 = ters). Way kimlikleri OSM way id; [ghost] ve
 * [wrongWayEdges] kenar indeksi. Kurallar: merdiven yok; yaya yolları onaylıysa sürülür, değilse "it/yürü" kipinde; tek yönde ters
 * giriş yalnız onaylı (ana cadde olmayan) sokakta ya da elle izinli kesimde; eğim limiti; kara liste; aşırı eğimli sokakta yokuş
 * yukarı yok; "yol yok" kenarı asla; ters yön sorun oranı eşiği aşan sokakta ters yön yok; kalite farkı toleransın altındaysa
 * belirleyici.
 *
 * WEEM 1.0 (MAP.md 3.16 SORU 1, varsayılan (a)): Y13 ölçülmüş yüzey girdileri (`surface`, `surfaceQuality`, `surfaceScale`) ve
 * "geçilebilirlik: kapalı" işareti yok; [marks]'tan yalnız Ses− kötü noktaları ([BadSpots]) etkilidir. Ölü `softBlock` taşınmadı.
 * Sayılar Dart'takiyle aynı tiplerde tutulur (kalite ve dönüş açıları f32), eşit maliyetli seçimler aynı çıksın diye.
 */
class CostModel(
    val g: RoadGraph,
    val mode: RideMode,
    val params: RideParams,
    val approved: Set<Long>,
    val blocked: Set<Long>,
    /** Aşırı eğimli: yokuş yukarı kapalı. */
    val steep: Set<Long> = emptySet(),
    val avoidBad: Boolean = false,
    /** Haritada var ama gerçekte yok: hiçbir koşulda kullanılmaz (kenar indeksi). */
    val ghost: Set<Int> = emptySet(),
    val learned: Map<Long, LearnedQuality> = emptyMap(),
    val marks: List<SurfaceMark> = emptyList(),
    /** OSM way id → ters yön sorun oranı (yumuşatılmış, 0–1). */
    val wwRatio: Map<Long, Double> = emptyMap(),
    /** Ters yönde sorun oranı eşiği aştı: ters yön kapalı. */
    val wwAvoid: Set<Long> = emptySet(),
    val reverseHours: Map<Long, ReverseHours> = emptyMap(),
    /** Ana caddede bile ters yön izni verilen kesimler (kenar indeksi, elle işaretli). */
    val wrongWayEdges: Set<Int> = emptySet(),
    /** E13: elle işaretlenmiş bisiklet yolları (OSM way id); OSM bayrağı gibi. */
    val bikeways: Set<Long> = emptySet(),
    /**
     * Y13 (A10.4b): ölçülmüş yüzey kesimleri. Anahtar çapası (kenar başı düğümünün koordinatı) grafla eşleşmeyen ya da yolda (≤ 25 m) olmayan
     * kesim yok sayılır. `allowedAt` (kötüden kaçınırken kesin kötü kesim) ve `segmentCost` (yüzey cezası) bunları kullanır.
     */
    val surface: List<SurfaceSummary> = emptyList(),
    /** E11 (C4): kesim anahtarı → sokak puanından kalite (0–1); varsa ceza onu tam güvenle kullanır, yoksa kesimin 0–100 puanı ve güveni. */
    val surfaceQuality: Map<String, Double> = emptyMap(),
    /** 1.7.2: ölçülmüş yüzey cezasının çarpanı. 0: yalnız kesin kötü kesim dışlaması kalır; [Planner.QUALITY_SEEK_SCALE]: düzgün yol arayan aday. */
    val surfaceScale: Double = 1.0,
    val departure: Instant = Instant.now(),
) {
    val cost = DoubleArray(g.edgeCount * 2)
    val time = DoubleArray(g.edgeCount * 2)
    val wh = DoubleArray(g.edgeCount * 2)
    val wrong = ByteArray(g.edgeCount * 2)

    /** Ekim planı madde 9: kenar yönü scooter itilerek yürünür (1). */
    val pushed = ByteArray(g.edgeCount * 2)

    /** Kenar başına 0–1 (Dart `Float32List`). */
    val quality = FloatArray(g.edgeCount)

    val junctionCost: Double
    val turnCost: Double

    /** A* sezgisinin çarpanı (E13: bisiklet yolu varken `bikewayFactor`, yoksa 1). */
    val heuristicScale: Double

    /** Grafa bağlı tablolar (açılar, ön kalite, way dizini): bütün sorgular paylaşır. */
    private val tables = GraphTables.of(g)

    /** Kötü nokta aralıkları: kenar → aralıklar (işaret sırasıyla); işaret yoksa boş. */
    private val spots: Array<ArrayList<SpotSpan>?> = arrayOfNulls(if (marks.isEmpty()) 0 else g.edgeCount)

    // Way başına kural girdileri: kenar başına küme aramasıyla aynı sonuç, girdi kümeleri üstünden bir kez işaretlenir.
    private val wayCount = g.wayOsmId.size
    private val wayApproved = wayMask(approved)
    private val wayBlocked = wayMask(blocked)
    private val waySteep = wayMask(steep)
    private val wayAvoidWw = wayMask(wwAvoid)
    private val wayBike = wayMask(bikeways)
    private val wayBad = BooleanArray(wayCount)
    private val wayRatio = DoubleArray(wayCount)
    private val wayHours: Array<ReverseHours?> = arrayOfNulls(if (reverseHours.isEmpty()) 0 else wayCount)
    private val ghostEdge = edgeMask(ghost)

    /** "Geçilebilirlik: kapalı" işaretli kenarlar (yalnız Y13 yüzey menüsünden doğar): iki yön de kapalı, yürünmez. */
    private val closedEdge = BooleanArray(g.edgeCount)

    /** Ölçülmüş kesimler: `kenar·2 + yön` → kesimler (yok ise null). */
    private val surfaceCells = HashMap<Int, ArrayList<SurfaceSummary>>()
    private val wwEdge = edgeMask(wrongWayEdges)

    init {
        val wayQ = FloatArray(wayCount)
        for (w in 0 until wayCount) wayQ[w] = tables.prior[w].toFloat()
        for ((osm, l) in learned) {
            val ways = tables.waysOf(osm) ?: continue
            val bad = badWay(osm)
            for (w in ways) {
                wayBad[w] = bad
                wayQ[w] = Quality.blendQuality(tables.prior[w], l).toFloat()
            }
        }
        for ((osm, ratio) in wwRatio) tables.waysOf(osm)?.forEach { wayRatio[it] = ratio }
        for ((osm, h) in reverseHours) tables.waysOf(osm)?.forEach { wayHours[it] = h }
        for (e in 0 until g.edgeCount) quality[e] = wayQ[g.edgeWay[e]]
        for (summary in surface) {
            val hit = g.nearestEdge(summary.lat, summary.lon, maxDist = 25.0, accept = { e -> g.osmIdOf(e) == summary.way }) ?: continue
            val node = g.edgeFrom[hit.edge]
            if (SurfaceKey.anchorOf(summary.key) != SurfaceKey.anchor(g.nodeLat[node].toDouble(), g.nodeLon[node].toDouble())) continue
            surfaceCells.getOrPut(hit.edge * 2 + summary.direction) { ArrayList() }.add(summary)
        }
        for (mark in marks) {
            if (mark.removed || mark.source != "user" || mark.access != Passability.CLOSED) continue
            g.nearestEdge(mark.lat, mark.lon, maxDist = 25.0, accept = { e -> g.osmIdOf(e) == mark.way })?.let { closedEdge[it.edge] = true }
        }
        // Kötü noktalar yalnız kendi ±25 m aralığını etkiler (segmentCost/allowedAt).
        for (span in BadSpots.spans(g, marks)) (spots[span.edge] ?: ArrayList<SpotSpan>().also { spots[span.edge] = it }).add(span)
        for (e in 0 until g.edgeCount) {
            eval(e, 0)
            eval(e, 1)
        }
        // E13: bisiklet yolu kenarı metre başına en ucuz maliyetin altına inebilir; A* alt sınırı aynı oranda küçülür ki rota en
        // iyi kalsın. Bisiklet yolu yoksa sezgi eskisiyle aynı.
        val anyBike = bikeways.isNotEmpty() || tables.anyBikeway
        heuristicScale = if (anyBike) min(1.0, params.bikewayFactor) else 1.0
        junctionCost = params.combine(mode, 2.0, 0.0)
        turnCost = params.combine(mode, params.turnPenaltyS, 0.0)
    }

    private fun wayMask(osmIds: Set<Long>): BooleanArray =
        BooleanArray(wayCount).also { m -> for (osm in osmIds) tables.waysOf(osm)?.forEach { m[it] = true } }

    private fun spotsOf(e: Int): List<SpotSpan>? = if (spots.isEmpty()) null else spots[e]

    private fun classOfWay(w: Int): RoadClass = RoadClass.entries[g.wayClass[w].toInt() and 0xFF]

    private fun edgeMask(edges: Set<Int>): BooleanArray =
        BooleanArray(g.edgeCount).also { m -> for (e in edges) if (e in m.indices) m[e] = true }

    /** [inE]/[inDir] ile gelip [outE]/[outDir] ile çıkarken dönüş açısı (işaretli: + sağa, − sola). */
    fun turnAngle(inE: Int, inDir: Int, outE: Int, outDir: Int): Double =
        (tables.startB[outE * 2 + outDir].toDouble() - tables.endB[inE * 2 + inDir].toDouble() + 540) % 360 - 180

    /** Az dönüş tercihi: belirgin dönüşün ek maliyeti. */
    fun turnExtra(inE: Int, inDir: Int, outE: Int, outDir: Int): Double {
        if (turnCost <= 0) return 0.0
        val a = abs(turnAngle(inE, inDir, outE, outDir))
        return if (a < 35) 0.0 else if (a > 120) turnCost * 1.5 else turnCost
    }

    /** Öğrenilmiş kötü yol: elle puan ≤ %25 ya da güvenli düşük skor. */
    fun badWay(osm: Long): Boolean {
        val l = learned[osm] ?: return false
        return if (l.r > 0) l.r <= QualityRating.BAD else l.c >= 0.8 && l.q < 0.3
    }

    fun allowed(e: Int, dir: Int): Boolean = cost[e * 2 + dir].isFinite()

    /** Sürülebilir (itilen yaya yolları değil): başlangıç, hedef ve yaya bölgesinden çıkış bunlara bağlanır. */
    fun rideable(e: Int): Boolean =
        (allowed(e, 0) && pushed[e * 2].toInt() == 0) || (allowed(e, 1) && pushed[e * 2 + 1].toInt() == 0)

    /** Son yürüyüş ve yaya çıkışı (MAP.md 3.5): `service` ve `sidewalk` yürünür; tek yön yürüyüşte geçerli değil. */
    fun walkable(e: Int, dir: Int): Boolean {
        val w = g.edgeWay[e]
        if (ghostEdge[e] || closedEdge[e] || wayBlocked[w]) return false
        val flags = g.wayFlags[w].toInt() and 0xFFFF
        if ((flags and WayFlag.RESTRICTED) != 0 || (avoidBad && (wayBad[w] || (flags and WayFlag.UNPAVED) != 0))) return false
        val cls = classOfWay(w)
        if (cls == RoadClass.STEPS || cls == RoadClass.TRUNK || (flags and WayFlag.FOOT_NO) != 0) return false
        val grade = (if (dir == 0) g.maxGradeF[e] else g.maxGradeB[e]).toDouble()
        if (grade > params.maxGrade) return false
        if (waySteep[w]) {
            val up = (if (dir == 0) g.climbF[e] else g.climbB[e]).toDouble()
            val down = (if (dir == 0) g.climbB[e] else g.climbF[e]).toDouble()
            if (!(down >= 1 && up <= down * 0.3)) return false
        }
        return true
    }

    /**
     * [elapsedS] saniye sonra bu yön (kenarın [f0]–[f1] kesimi, süre payı [portion]) kullanılabilir mi: kötüden kaçınırken
     * doğrulanmış kötü nokta kesimi kapalı; ters yön kenarında saatli izin geçiş bitmeden kapanmamalı (MAP.md 3.5, 4.3).
     */
    fun allowedAt(e: Int, dir: Int, elapsedS: Double, portion: Double = 1.0, f0: Double = 0.0, f1: Double = 1.0): Boolean {
        if (!allowed(e, dir)) return false
        // Y13: yeterince güvenli, kalibre ve kesin kötü ölçülmüş kesim (bu yönün) aralığa değiyorsa dışlanır.
        if (avoidBad && surfaceCells[e * 2 + dir]?.any { it.calibrated && it.confidence >= .65 && it.score < 30 && overlap(e, it, f0, f1) > 0 } == true) return false
        // Farklı günlerde doğrulanmış kötü nokta: yalnız o kesim dışlanır.
        if (avoidBad) {
            val list = spotsOf(e)
            if (list != null && list.any { it.confirmed && it.overlap(f0, f1) > 0 }) return false
        }
        if (portion <= 0 || wrong[e * 2 + dir].toInt() == 0) return true
        val hours = if (wayHours.isEmpty()) null else wayHours[g.edgeWay[e]]
        return hours == null || hours.allowsTrip(departure.plusMillis(Math.round(elapsedS * 1000)), time[e * 2 + dir] * portion)
    }

    /** Kesimin maliyeti: taban × pay; kötü nokta kesimi 4×, doğrulanmışsa 7× pahalı; kenarın kalanı etkilenmez. */
    fun segmentCost(e: Int, dir: Int, f0: Double, f1: Double): Double {
        val base = cost[e * 2 + dir]
        val portion = abs(f1 - f0)
        if (!base.isFinite()) return Double.POSITIVE_INFINITY
        var total = base * portion
        // Fiziksel yüzey cezası yalnız ölçülmüş kesime ve yöne uygulanır; sokağın ölçülmemiş kalanı eski maliyetini korur.
        surfaceCells[e * 2 + dir]?.let { cells ->
            for (s in cells) {
                val overlap = overlap(e, s, f0, f1)
                val street = surfaceQuality[s.key]
                val q = street ?: (s.score / 100)
                val confidence = if (street == null) s.confidence else 1.0
                total += base * overlap * (1 - q) * confidence * (if (q < .3) 3.0 else params.qualityWeight) * surfaceScale
            }
        }
        val list = spotsOf(e) ?: return total
        for (s in list) total += base * s.overlap(f0, f1) * (if (s.confirmed) 6 else 3)
        return total
    }

    /** Kesimin kenarın [f0]–[f1] aralığıyla örtüşmesi (kesim kapsamıyla çarpılmış kesir). */
    private fun overlap(e: Int, s: SurfaceSummary, f0: Double, f1: Double): Double {
        val len = g.edgeLen[e].toDouble()
        if (len <= 0) return 0.0
        val start = s.segmentIndex * s.segmentGridM / len
        val end = min(1.0, (s.segmentIndex * s.segmentGridM + s.segmentLength) / len)
        return max(0.0, min(max(f0, f1), end) - max(min(f0, f1), start)) * s.coverage
    }

    private fun eval(e: Int, dir: Int) {
        val i = e * 2 + dir
        cost[i] = Double.POSITIVE_INFINITY
        if (ghostEdge[e] || closedEdge[e]) return // haritada var, gerçekte yok (ya da elle "kapalı")
        val w = g.edgeWay[e]
        val cls = classOfWay(w)
        val flags = g.wayFlags[w].toInt() and 0xFFFF
        val isApproved = wayApproved[w]

        if ((flags and (WayFlag.RESTRICTED or WayFlag.SIDEWALK)) != 0) return
        // Dükkan önü/cep yolları geçiş koridoru değildir; son yaklaşım yaya olur.
        if (cls == RoadClass.SERVICE) return
        if (avoidBad && (wayBad[w] || (flags and WayFlag.UNPAVED) != 0)) return
        if (cls == RoadClass.STEPS) return
        val park = (flags and WayFlag.PARK) != 0 // Ekim planı madde 10: park yolu onaysız sürülür
        if (cls.isFootClass && (flags and WayFlag.BIKE_OK) == 0 && !isApproved && !park) {
            // Ekim planı madde 9: sürülemeyen yaya yolu "it/yürü" kipinde (yürüme hızı, × PUSH_COST_FACTOR + iniş-biniş
            // PUSH_PENALTY_S); yalnız sürülebilir alternatif yoksa ya da çok uzunsa seçilir. Tek yön yürüyüşte geçerli değil.
            if ((flags and WayFlag.FOOT_NO) != 0 || wayBlocked[w]) return
            val t = g.edgeLen[e].toDouble() * 1.2 / params.walkMps
            cost[i] = params.combine(mode, t * PUSH_COST_FACTOR + PUSH_PENALTY_S, 0.0)
            time[i] = t
            wh[i] = 0.0
            wrong[i] = 0
            pushed[i] = 1
            return
        }

        val against = if (dir == 0) (flags and WayFlag.ONEWAY_BWD) != 0 else (flags and WayFlag.ONEWAY_FWD) != 0
        val wrongWay = against && (flags and WayFlag.CONTRAFLOW) == 0
        if (wrongWay && !((isApproved && !cls.isMainRoad) || wwEdge[e])) return
        if (wrongWay && wayAvoidWw[w]) return // ters yönde çok sorun yaşandı
        // Ekim planı madde 10b: "sorun yok" ile onaylanan, hiç sorun bildirilmemiş ara sokak iki yönlü sayılır (ters yön hız
        // tavanı ve çarpanı yok). İlk "Evet, sorun" cevabıyla eski ceza döner; ana caddeler onaylanmaz.
        val twoWay = wrongWay && isApproved && !cls.isMainRoad && wayRatio[w] == 0.0

        val maxGrade = (if (dir == 0) g.maxGradeF[e] else g.maxGradeB[e]).toDouble()
        if (maxGrade > params.maxGrade) return

        // Aşırı eğimli (Ses− iki kez): yokuş yukarı kapalı. Açıkça iniş olan yön serbest; yükseklik yönü ayırt edemiyorsa ikisi
        // de kapalı.
        if (waySteep[w]) {
            val upHere = (if (dir == 0) g.climbF[e] else g.climbB[e]).toDouble()
            val upBack = (if (dir == 0) g.climbB[e] else g.climbF[e]).toDouble()
            val downhill = upBack >= 1.0 && upHere <= 0.3 * upBack
            if (!downhill) return
        }

        val len = g.edgeLen[e].toDouble()
        val up = (if (dir == 0) g.upLenF[e] else g.upLenB[e]).toDouble()
        val climb = (if (dir == 0) g.climbF[e] else g.climbB[e]).toDouble()

        val sf = if ((flags and WayFlag.UNPAVED) != 0) 0.6 else if ((flags and WayFlag.ROUGH) != 0) 0.8 else 1.0
        var vFlat = params.flatMps * sf
        var vClimb = params.climbMps(if (up > 1) climb / up else 0.0) * sf
        if (wrongWay && !twoWay) {
            vFlat = min(vFlat, params.wrongWayMps)
            vClimb = min(vClimb, params.wrongWayMps)
        }
        if (cls.isFootClass && !park) {
            // Onaylı yaya yolu: yayalar var, hız 15 km/s tavan (park yollarında tavan yok).
            vFlat = min(vFlat, 15 / 3.6)
            vClimb = min(vClimb, 15 / 3.6)
        }
        val t = (len - up) / vFlat + up / vClimb
        val energy = params.energyWh(len * (if ((flags and WayFlag.UNPAVED) != 0) 1.3 else 1.0), climb)

        var tCost = t
        if (cls == RoadClass.TRUNK || cls == RoadClass.PRIMARY) tCost *= params.mainRoadPenalty
        if (wrongWay && !twoWay) tCost *= 1.2 + 2.0 * wayRatio[w]
        var c = params.combine(mode, tCost, energy)
        // Yol kalitesi: süre/mesafe farkı toleransın altındaysa az dur-kalklı yol kazanır.
        c *= 1 + params.qualityWeight * (1 - quality[e].toDouble())
        // Küçük bir kazanç için ana caddeden ara sokağa sapma.
        if (cls == RoadClass.RESIDENTIAL || cls == RoadClass.LIVING_STREET || cls == RoadClass.UNCLASSIFIED || cls == RoadClass.ROAD) {
            c *= 1.35
        }
        if (wayBad[w]) c *= 4
        // E13: bisiklet yolu (OSM ya da elle) tercih edilir; yalnız izinli yönde ve ters yön değilken.
        if (!wrongWay && ((flags and WayFlag.BIKEWAY) != 0 || wayBike[w])) c *= params.bikewayFactor

        if (wayBlocked[w]) return
        cost[i] = c
        time[i] = t
        wh[i] = energy
        wrong[i] = if (wrongWay) 1 else 0
    }

    companion object {
        /** Ekim planı madde 9: itilen yaya yolunun maliyet çarpanı ve kenar başına iniş-biniş cezası (sn). */
        const val PUSH_COST_FACTOR = 2.5
        const val PUSH_PENALTY_S = 20.0
    }
}
