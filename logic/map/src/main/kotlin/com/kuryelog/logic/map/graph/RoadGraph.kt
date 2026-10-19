package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.LocalProjection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** `lib/data/road_graph.dart` (MAP.md 2.5, 2.6): yol sınıfı sırası KNG1 dosyasındaki u8 değerdir, değişmez. */
enum class RoadClass {
    TRUNK, PRIMARY, SECONDARY, TERTIARY, UNCLASSIFIED, RESIDENTIAL, LIVING_STREET, SERVICE, ROAD, TRACK, CYCLEWAY, PATH, FOOTWAY,
    PEDESTRIAN, STEPS,
    ;

    val isMainRoad: Boolean get() = this == TRUNK || this == PRIMARY || this == SECONDARY

    /** Yaya yolu sınıfları: varsayılan olarak sürülmez (onaylanırsa sürülür). */
    val isFootClass: Boolean get() = this == PATH || this == FOOTWAY || this == PEDESTRIAN
}

/** Yol bayrakları (way başına; KNG1 u16). */
object WayFlag {
    const val ONEWAY_FWD = 1
    const val ONEWAY_BWD = 2
    const val CONTRAFLOW = 4
    const val UNPAVED = 8
    const val ROUGH = 16
    const val BIKE_OK = 32
    const val AREA = 64
    const val FOOT_NO = 128
    const val RESTRICTED = 256
    const val SIDEWALK = 512
    const val BIKEWAY = 1024
    const val PARK = 2048
}

/** Dosya başlığının son alanı (E13 öncesi dosyalarda 0). */
object GraphFeature {
    const val BIKEWAYS = 1
    const val PARKS = 2
}

/** Dart `FormatException` karşılığı: bozuk ya da eski sürüm harita dosyası. */
class FormatException(message: String) : Exception(message)

class EdgeHit(
    val edge: Int,
    /** Metre. */
    val dist: Double,
    /** Kenar boyunca 0–1. */
    val fraction: Double,
    /** İzdüşüm noktası. */
    val lat: Double,
    val lon: Double,
    /** İzdüşülen parçanın çizim yönündeki açısı. */
    val segBearing: Double,
)

/** Kenar geometrisindeki bir nokta (yükseklikli). */
data class GeomPoint(val lat: Double, val lon: Double, val ele: Double)

/**
 * Telefonda tutulan yol ağı. Düğümler = kavşaklar ve yol uçları; kenarlar = iki kavşak arası yol parçası (+ çizim geometrisi).
 * `lib/data/road_graph.dart` (MAP.md 2.5, 2.6). Kurulurken komşuluk (CSR) ve uzamsal ızgara hesaplanır.
 */
class RoadGraph(
    val nodeLat: DoubleArray,
    val nodeLon: DoubleArray,
    val edgeFrom: IntArray,
    val edgeTo: IntArray,
    val edgeWay: IntArray,
    val edgeGeomStart: IntArray,
    val edgeGeomCount: IntArray,
    val edgeLen: FloatArray,
    val upLenF: FloatArray,
    val climbF: FloatArray,
    val upLenB: FloatArray,
    val climbB: FloatArray,
    val maxGradeF: FloatArray,
    val maxGradeB: FloatArray,
    val geomLat: DoubleArray,
    val geomLon: DoubleArray,
    val geomEle: FloatArray,
    val wayOsmId: LongArray,
    /** u8 (RoadClass sırası). */
    val wayClass: ByteArray,
    /** u16 ([WayFlag]). */
    val wayFlags: ShortArray,
    val wayName: IntArray,
    val names: List<String>,
    /** Yaya alanı çokgenleri (`zoneCount + 1`). */
    val zoneStart: IntArray,
    val zoneLat: DoubleArray,
    val zoneLon: DoubleArray,
    /** [GraphFeature] bitleri. */
    val features: Int = 0,
) {
    val nodeCount get() = nodeLat.size
    val edgeCount get() = edgeFrom.size
    val zoneCount get() = zoneStart.size - 1

    fun wayOf(e: Int): Int = edgeWay[e]
    fun classOf(e: Int): RoadClass = CLASSES[wayClass[edgeWay[e]].toInt() and 0xFF]
    fun flagsOf(e: Int): Int = wayFlags[edgeWay[e]].toInt() and 0xFFFF

    /** E13: OSM'de bisiklet yolu (elle işaretlenenler `route.BikewayMarks`'ta). */
    fun isBikeway(e: Int): Boolean = (flagsOf(e) and WayFlag.BIKEWAY) != 0

    /** E13: dosya bisiklet yolu bilgisini taşıyor mu (false: E13 öncesi harita, güncellenmeli). */
    val hasBikewayData: Boolean get() = (features and GraphFeature.BIKEWAYS) != 0
    val hasParkData: Boolean get() = (features and GraphFeature.PARKS) != 0
    fun osmIdOf(e: Int): Long = wayOsmId[edgeWay[e]]
    fun nameOf(e: Int): String = nameOfWay(edgeWay[e])

    private fun nameOfWay(w: Int): String = wayName[w].let { if (it < 0) "" else names[it] }

    private val wayIdx: Map<Long, Int> by lazy {
        HashMap<Long, Int>(wayOsmId.size * 2).also { m -> for (w in wayOsmId.indices) m[wayOsmId[w]] = w }
    }

    /** Way → kenarları (kenar sırasıyla); Dart doğrusal taramasıyla aynı sonuç. */
    private val wayEdges: Array<IntArray> by lazy {
        val count = IntArray(wayOsmId.size)
        for (e in 0 until edgeCount) count[edgeWay[e]]++
        val out = Array(wayOsmId.size) { IntArray(count[it]) }
        val fill = IntArray(wayOsmId.size)
        for (e in 0 until edgeCount) {
            val w = edgeWay[e]
            out[w][fill[w]++] = e
        }
        out
    }

    /** OSM way id → sokak indeksi (yoksa null). Aynı id iki kez varsa sonuncusu (Dart sözlüğü gibi). */
    fun wayIndexOf(osmId: Long): Int? = wayIdx[osmId]

    /** Bir OSM yoluna ait tüm kenarlar (kenar sırasıyla). */
    fun edgesOfOsmWay(osmId: Long): List<Int> {
        val out = ArrayList<Int>()
        for (w in wayOsmId.indices) if (wayOsmId[w] == osmId) wayEdges[w].forEach { out += it }
        if (out.size > 1) out.sort()
        return out
    }

    fun nameOfOsmWay(osmId: Long): String {
        for (w in wayOsmId.indices) if (wayOsmId[w] == osmId) return nameOfWay(w)
        return ""
    }

    fun classOfOsmWay(osmId: Long): RoadClass? {
        for (w in wayOsmId.indices) if (wayOsmId[w] == osmId) return CLASSES[wayClass[w].toInt() and 0xFF]
        return null
    }

    // ---------------- Komşuluk (CSR) ----------------
    val adjStart: IntArray
    val adjEdge: IntArray

    /** 0: düğüm kenarın `from` ucu (kenar çizim yönünde çıkar); 1: `to` ucu. */
    val adjDir: ByteArray

    fun degree(n: Int): Int = adjStart[n + 1] - adjStart[n]

    // ---------------- Uzamsal ızgara ----------------
    private val gLat0: Double
    private val gLon0: Double
    private val gRows: Int
    private val gCols: Int
    private val cellStart: IntArray
    private val cellEdges: IntArray

    /** Sorgu damgası (tekrarları eler). Dart gibi tek iş parçacığı varsayar: sorgular eşzamanlı çağrılmaz. */
    private val seen: IntArray
    private var stamp = 0

    init {
        val start = IntArray(nodeCount + 1)
        for (e in 0 until edgeCount) {
            start[edgeFrom[e] + 1]++
            start[edgeTo[e] + 1]++
        }
        for (n in 0 until nodeCount) start[n + 1] += start[n]
        val fill = start.copyOf()
        val edges = IntArray(edgeCount * 2)
        val dirs = ByteArray(edgeCount * 2)
        for (e in 0 until edgeCount) {
            val a = edgeFrom[e]
            val b = edgeTo[e]
            edges[fill[a]] = e
            dirs[fill[a]++] = 0
            edges[fill[b]] = e
            dirs[fill[b]++] = 1
        }
        adjStart = start
        adjEdge = edges
        adjDir = dirs

        var minLat = 90.0
        var minLon = 180.0
        var maxLat = -90.0
        var maxLon = -180.0
        for (i in geomLat.indices) {
            minLat = min(minLat, geomLat[i])
            maxLat = max(maxLat, geomLat[i])
            minLon = min(minLon, geomLon[i])
            maxLon = max(maxLon, geomLon[i])
        }
        if (geomLat.isEmpty()) {
            minLat = 0.0; maxLat = 0.0; minLon = 0.0; maxLon = 0.0
        }
        gLat0 = minLat
        gLon0 = minLon
        gRows = floor((maxLat - minLat) / CELL_LAT).toInt() + 1
        gCols = floor((maxLon - minLon) / CELL_LON).toInt() + 1
        val cells = gRows * gCols
        val counts = IntArray(cells + 1)
        val last = IntArray(cells)
        last.fill(-1)
        for (e in 0 until edgeCount) forCells(e, last) { counts[it + 1]++ }
        for (i in 0 until cells) counts[i + 1] += counts[i]
        val cFill = counts.copyOf()
        val list = IntArray(counts[cells])
        last.fill(-1)
        for (e in 0 until edgeCount) forCells(e, last) { list[cFill[it]++] = e }
        cellStart = counts
        cellEdges = list
        seen = IntArray(edgeCount)
    }

    private inline fun forCells(e: Int, last: IntArray, f: (Int) -> Unit) {
        val s = edgeGeomStart[e]
        val n = edgeGeomCount[e]
        for (k in 0 until n - 1) {
            val r0 = row(geomLat[s + k])
            val r1 = row(geomLat[s + k + 1])
            val c0 = col(geomLon[s + k])
            val c1 = col(geomLon[s + k + 1])
            for (r in min(r0, r1)..max(r0, r1)) for (c in min(c0, c1)..max(c0, c1)) {
                val cell = r * gCols + c
                if (last[cell] != e) {
                    last[cell] = e
                    f(cell)
                }
            }
        }
    }

    private fun row(lat: Double) = Geo.clampInt(floor((lat - gLat0) / CELL_LAT).toInt(), 0, gRows - 1)
    private fun col(lon: Double) = Geo.clampInt(floor((lon - gLon0) / CELL_LON).toInt(), 0, gCols - 1)

    /** Gerçek geometri sınırı (ızgara kapsamı + pay); görünen altlık rota kapsamı değildir. */
    fun contains(lat: Double, lon: Double, marginM: Double = 100.0): Boolean =
        geomLat.isNotEmpty() && lat >= gLat0 - marginM / 111320 &&
            lat <= gLat0 + gRows * CELL_LAT + marginM / 111320 &&
            lon >= gLon0 - marginM / 85000 &&
            lon <= gLon0 + gCols * CELL_LON + marginM / 85000

    /** Art arda [nearestEdge], bulunanları dışlayarak (en çok [limit]). */
    fun nearestEdges(lat: Double, lon: Double, maxDist: Double = 80.0, accept: ((Int) -> Boolean)? = null, limit: Int = 4): List<EdgeHit> {
        val hits = ArrayList<EdgeHit>()
        val taken = HashSet<Int>()
        for (i in 0 until limit) {
            val hit = nearestEdge(lat, lon, maxDist, { e -> e !in taken && (accept == null || accept(e)) }) ?: break
            hits += hit
            taken += hit.edge
        }
        return hits
    }

    /**
     * En yakın kenar. [accept] ile süzülür; [heading] verilirse yönü ±[headingTol] dışında kalan parçalar atlanır
     * (iki yön de kabul).
     */
    fun nearestEdge(
        lat: Double,
        lon: Double,
        maxDist: Double = 60.0,
        accept: ((Int) -> Boolean)? = null,
        heading: Double? = null,
        headingTol: Double = 50.0,
    ): EdgeHit? {
        if (edgeCount == 0) return null
        val proj = LocalProjection(lat, lon)
        val reach = ceil(maxDist / 50).toInt() + 1
        val r0 = row(lat)
        val c0 = col(lon)
        val st = ++stamp
        var best: EdgeHit? = null
        for (r in r0 - reach..r0 + reach) {
            if (r < 0 || r >= gRows) continue
            for (c in c0 - reach..c0 + reach) {
                if (c < 0 || c >= gCols) continue
                val cell = r * gCols + c
                for (i in cellStart[cell] until cellStart[cell + 1]) {
                    val e = cellEdges[i]
                    if (seen[e] == st) continue
                    seen[e] = st
                    if (accept != null && !accept(e)) continue
                    val hit = projectOnEdge(e, proj, heading, headingTol)
                    if (hit != null && hit.dist <= maxDist && (best == null || hit.dist < best.dist)) best = hit
                }
            }
        }
        return best
    }

    private fun projectOnEdge(e: Int, p: LocalProjection, heading: Double?, tol: Double): EdgeHit? {
        val s = edgeGeomStart[e]
        val n = edgeGeomCount[e]
        var total = 0.0
        val segLen = DoubleArray(max(0, n - 1))
        for (k in 0 until n - 1) {
            val dx = p.x(geomLon[s + k + 1]) - p.x(geomLon[s + k])
            val dy = p.y(geomLat[s + k + 1]) - p.y(geomLat[s + k])
            segLen[k] = sqrt(dx * dx + dy * dy)
            total += segLen[k]
        }
        var best: EdgeHit? = null
        var along = 0.0
        for (k in 0 until n - 1) {
            val aLat = geomLat[s + k]
            val aLon = geomLon[s + k]
            val bLat = geomLat[s + k + 1]
            val bLon = geomLon[s + k + 1]
            val segB = Geo.bearing(aLat, aLon, bLat, bLon)
            val okHeading = heading == null ||
                Geo.angleDiff(heading, segB) <= tol ||
                Geo.angleDiff(heading, (segB + 180) % 360) <= tol
            if (okHeading) {
                val r = Geo.pointToSegment(0.0, 0.0, p.x(aLon), p.y(aLat), p.x(bLon), p.y(bLat))
                if (best == null || r.dist < best.dist) {
                    val frac = if (total == 0.0) 0.0 else (along + r.t * segLen[k]) / total
                    best = EdgeHit(
                        edge = e,
                        dist = r.dist,
                        fraction = Geo.clampD(frac, 0.0, 1.0),
                        lat = aLat + (bLat - aLat) * r.t,
                        lon = aLon + (bLon - aLon) * r.t,
                        segBearing = segB,
                    )
                }
            }
            along += segLen[k]
        }
        return best
    }

    /** Görünür alan / yakın kavşak sorgusu: uzamsal hücrelerdeki tekil kenarlar (hücre sırasıyla). */
    fun edgesWithin(south: Double, west: Double, north: Double, east: Double): List<Int> {
        val r0 = row(south)
        val r1 = row(north)
        val c0 = col(west)
        val c1 = col(east)
        val out = ArrayList<Int>()
        val taken = HashSet<Int>()
        for (r in r0..r1) for (c in c0..c1) {
            val cell = r * gCols + c
            for (a in cellStart[cell] until cellStart[cell + 1]) {
                val e = cellEdges[a]
                if (taken.add(e)) out += e
            }
        }
        return out
    }

    /** Kenar geometrisinin [f0, f1] aralığı (sıralanır); [reverse] ise to → from. Uçlar ve yükseklik ara değerle. */
    fun geometry(e: Int, f0: Double = 0.0, f1: Double = 1.0, reverse: Boolean = false): List<GeomPoint> {
        val s = edgeGeomStart[e]
        val n = edgeGeomCount[e]
        val cum = DoubleArray(n)
        for (k in 1 until n) {
            cum[k] = cum[k - 1] + Geo.haversine(geomLat[s + k - 1], geomLon[s + k - 1], geomLat[s + k], geomLon[s + k])
        }
        val total = cum[n - 1]
        fun at(d: Double): GeomPoint {
            if (total == 0.0) return GeomPoint(geomLat[s], geomLon[s], geomEle[s].toDouble())
            var k = 1
            while (k < n - 1 && cum[k] < d) k++
            val seg = cum[k] - cum[k - 1]
            val t = if (seg == 0.0) 0.0 else Geo.clampD((d - cum[k - 1]) / seg, 0.0, 1.0)
            val e0 = geomEle[s + k - 1].toDouble()
            return GeomPoint(
                geomLat[s + k - 1] + (geomLat[s + k] - geomLat[s + k - 1]) * t,
                geomLon[s + k - 1] + (geomLon[s + k] - geomLon[s + k - 1]) * t,
                e0 + (geomEle[s + k].toDouble() - e0) * t,
            )
        }
        val d0 = min(f0, f1) * total
        val d1 = max(f0, f1) * total
        val pts = ArrayList<GeomPoint>()
        pts += at(d0)
        for (k in 0 until n) {
            if (cum[k] > d0 && cum[k] < d1) pts += GeomPoint(geomLat[s + k], geomLon[s + k], geomEle[s + k].toDouble())
        }
        pts += at(d1)
        if (reverse) pts.reverse()
        return pts
    }

    /** Nokta bir yaya alanının (meydan) içinde mi? (ışın atma) */
    fun inPedestrianZone(lat: Double, lon: Double): Boolean {
        for (z in 0 until zoneCount) {
            val a = zoneStart[z]
            val b = zoneStart[z + 1]
            var inside = false
            var j = b - 1
            for (i in a until b) {
                val yi = zoneLat[i]
                val yj = zoneLat[j]
                val xi = zoneLon[i]
                val xj = zoneLon[j]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            if (inside) return true
        }
        return false
    }

    /** KNG1 ikili biçimi (little-endian; her dizi 8 bayta dolgulu, adlar sonda dolgusuz; MAP.md 2.5). */
    fun serialize(): ByteArray {
        val nameBytes = names.joinToString("\u0000").toByteArray(Charsets.UTF_8)
        fun pad(n: Int) = (n + 7) and 7.inv()
        val size = HEADER_BYTES +
            pad(nodeCount * 8) * 2 +
            pad(edgeCount * 4) * 5 +
            pad(edgeCount * 4) * 7 +
            pad(geomLat.size * 8) * 2 + pad(geomEle.size * 4) +
            pad(wayOsmId.size * 8) + pad(wayClass.size) + pad(wayFlags.size * 2) + pad(wayName.size * 4) +
            pad(zoneStart.size * 4) + pad(zoneLat.size * 8) * 2 +
            nameBytes.size
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        fun align() {
            while (b.position() % 8 != 0) b.put(0)
        }
        for (v in intArrayOf(MAGIC, FORMAT_VERSION, nodeCount, edgeCount, geomLat.size, wayOsmId.size, zoneStart.size, zoneLat.size, nameBytes.size, features)) {
            b.putInt(v)
        }
        align()
        fun f64(a: DoubleArray) { a.forEach { b.putDouble(it) }; align() }
        fun f32(a: FloatArray) { a.forEach { b.putFloat(it) }; align() }
        fun i32(a: IntArray) { a.forEach { b.putInt(it) }; align() }
        f64(nodeLat); f64(nodeLon)
        i32(edgeFrom); i32(edgeTo); i32(edgeWay); i32(edgeGeomStart); i32(edgeGeomCount)
        f32(edgeLen); f32(upLenF); f32(climbF); f32(upLenB); f32(climbB); f32(maxGradeF); f32(maxGradeB)
        f64(geomLat); f64(geomLon); f32(geomEle)
        wayOsmId.forEach { b.putLong(it) }; align()
        b.put(wayClass); align()
        wayFlags.forEach { b.putShort(it) }; align()
        i32(wayName)
        i32(zoneStart); f64(zoneLat); f64(zoneLon)
        b.put(nameBytes)
        check(b.position() == size)
        return b.array()
    }

    companion object {
        /** 'KNG1'. */
        const val MAGIC = 0x4B4E4731
        const val FORMAT_VERSION = 2
        private const val HEADER_BYTES = 40
        private const val CELL_LAT = 0.0005 // ~55 m
        private const val CELL_LON = 0.00065 // ~55 m (40° enlemde)
        private val CLASSES = RoadClass.entries.toTypedArray()

        /** Sihir ya da sürüm farklıysa [FormatException]; kısa ya da bozuk dosya da [FormatException]. */
        fun deserialize(bytes: ByteArray): RoadGraph {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            try {
                if (b.getInt(0) != MAGIC || b.getInt(4) != FORMAT_VERSION) throw FormatException("Harita dosyası eski sürüm")
                val h = IntArray(10) { b.getInt(it * 4) }
                val nN = h[2]; val nE = h[3]; val nG = h[4]; val nW = h[5]; val nZs = h[6]; val nZ = h[7]; val nNames = h[8]
                if (h.slice(2..8).any { it < 0 }) throw FormatException("Harita dosyası bozuk")
                b.position(HEADER_BYTES)
                fun align() = b.position((b.position() + 7) and 7.inv())
                fun f64(n: Int) = DoubleArray(n) { b.getDouble() }.also { align() }
                fun f32(n: Int) = FloatArray(n) { b.getFloat() }.also { align() }
                fun i32(n: Int) = IntArray(n) { b.getInt() }.also { align() }
                val nodeLat = f64(nN); val nodeLon = f64(nN)
                val edgeFrom = i32(nE); val edgeTo = i32(nE); val edgeWay = i32(nE); val gStart = i32(nE); val gCount = i32(nE)
                val eLen = f32(nE); val upF = f32(nE); val clF = f32(nE); val upB = f32(nE); val clB = f32(nE)
                val mgF = f32(nE); val mgB = f32(nE)
                val gLat = f64(nG); val gLon = f64(nG); val gEle = f32(nG)
                val wayId = LongArray(nW) { b.getLong() }.also { align() }
                val wayCls = ByteArray(nW).also { b.get(it); align() }
                val wayFl = ShortArray(nW) { b.getShort() }.also { align() }
                val wayNm = i32(nW)
                val zStart = i32(nZs); val zLat = f64(nZ); val zLon = f64(nZ)
                val nameArr = ByteArray(nNames).also { b.get(it) }
                val names = if (nNames == 0) emptyList() else String(nameArr, Charsets.UTF_8).split('\u0000')
                return RoadGraph(
                    nodeLat, nodeLon, edgeFrom, edgeTo, edgeWay, gStart, gCount,
                    eLen, upF, clF, upB, clB, mgF, mgB,
                    gLat, gLon, gEle, wayId, wayCls, wayFl, wayNm, names,
                    zStart, zLat, zLon, features = h[9],
                )
            } catch (e: java.nio.BufferUnderflowException) {
                throw FormatException("Harita dosyası bozuk")
            } catch (e: IndexOutOfBoundsException) {
                throw FormatException("Harita dosyası bozuk")
            } catch (e: IllegalArgumentException) {
                throw FormatException("Harita dosyası bozuk")
            }
        }
    }
}
