package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.Geo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

typealias ElevationFn = (lat: Double, lon: Double) -> Double

/** `lib/data/graph_builder.dart` (MAP.md 2.4). Saf: test edilir, arka planda çalışır. */
object GraphBuilder {
    /** Eğim hesabı için geometri sıklaştırma (m). */
    const val MAX_SPACING = 25.0

    /** Kısa parçalarda eğim şişmesin (m). */
    const val MIN_GRADE_RUN = 20.0

    private val UNPAVED = setOf(
        "unpaved", "gravel", "fine_gravel", "dirt", "ground", "sand", "grass", "mud", "earth", "pebblestone", "rock", "woodchips",
    )
    private val ROUGH = setOf("sett", "cobblestone", "unhewn_cobblestone")
    private val BIKE_LANE = setOf("lane", "track")
    private val SKIP_SERVICE = setOf("driveway", "parking_aisle", "drive-through", "emergency_access")
    private val ACCESS_NO = setOf("private", "no", "customers", "delivery", "destination", "permit")
    private val VEHICLE_NO = setOf("private", "no", "customers", "permit")

    fun classifyHighway(h: String?): RoadClass? = when (h) {
        "trunk", "trunk_link" -> RoadClass.TRUNK
        "primary", "primary_link" -> RoadClass.PRIMARY
        "secondary", "secondary_link" -> RoadClass.SECONDARY
        "tertiary", "tertiary_link" -> RoadClass.TERTIARY
        "unclassified" -> RoadClass.UNCLASSIFIED
        "residential" -> RoadClass.RESIDENTIAL
        "living_street" -> RoadClass.LIVING_STREET
        "service" -> RoadClass.SERVICE
        "road" -> RoadClass.ROAD
        "track" -> RoadClass.TRACK
        "cycleway" -> RoadClass.CYCLEWAY
        "path" -> RoadClass.PATH
        "footway" -> RoadClass.FOOTWAY
        "pedestrian" -> RoadClass.PEDESTRIAN
        "steps" -> RoadClass.STEPS
        else -> null
    }

    /**
     * E13: bisiklet yolu etiketleri (kullanıcı listesi, 14 Ekim). Şerit/pist yolun herhangi bir yanında olabilir; yön kuralları
     * ayrı ([WayFlag.ONEWAY_FWD], [WayFlag.CONTRAFLOW]) ve değişmez.
     */
    fun isBikewayTags(t: Map<String, String>): Boolean =
        t["highway"] == "cycleway" ||
            t["cycleway"] in BIKE_LANE ||
            t["cycleway"] == "share_busway" ||
            t["cycleway:left"] in BIKE_LANE ||
            t["cycleway:right"] in BIKE_LANE ||
            t["cycleway:both"] in BIKE_LANE ||
            t["bicycle"] == "designated"

    fun wayFlagsFromTags(t: Map<String, String>, cls: RoadClass): Int {
        var f = 0
        val oneway = t["oneway"]
        val junction = t["junction"]
        if (oneway == "yes" || oneway == "true" || oneway == "1") {
            f = f or WayFlag.ONEWAY_FWD
        } else if (oneway == "-1" || oneway == "reverse") {
            f = f or WayFlag.ONEWAY_BWD
        } else if (oneway != "no" && (junction == "roundabout" || junction == "circular")) {
            f = f or WayFlag.ONEWAY_FWD
        }
        val contra = t["oneway:bicycle"] == "no" || t.entries.any { it.key.startsWith("cycleway") && it.value.startsWith("opposite") }
        if (contra) f = f or WayFlag.CONTRAFLOW

        val surface = t["surface"]
        if (surface in UNPAVED || (cls == RoadClass.TRACK && surface == null)) f = f or WayFlag.UNPAVED
        if (surface in ROUGH) f = f or WayFlag.ROUGH

        val bicycle = t["bicycle"]
        if (bicycle == "yes" || bicycle == "designated" || bicycle == "permissive") f = f or WayFlag.BIKE_OK
        if (t["foot"] == "no" || t["foot"] == "private") f = f or WayFlag.FOOT_NO
        if (t["access"] in ACCESS_NO || t["vehicle"] in VEHICLE_NO || bicycle in VEHICLE_NO) f = f or WayFlag.RESTRICTED
        if (t["footway"] == "sidewalk" || t["footway"] == "crossing") f = f or WayFlag.SIDEWALK
        if (isBikewayTags(t)) f = f or WayFlag.BIKEWAY
        return f
    }

    /** Ekim planı madde 10: park alanı etiketleri (Overpass sorgusu ve CI aracıyla aynı). */
    fun isParkAreaTags(t: Map<String, String>): Boolean =
        t["leisure"] == "park" || t["leisure"] == "garden" || t["landuse"] == "recreation_ground"

    private class Line(val way: OsmWay, val cls: RoadClass, var flags: Int)

    /** Ham OSM + yükseklik → rota grafiği. */
    fun buildRoadGraph(raw: OsmRaw, elevation: ElevationFn): RoadGraph {
        // 1) Sınıflandır, yaya meydanlarını ve park alanlarını ayır.
        val lines = ArrayList<Line>()
        val zoneStart = arrayListOf(0)
        val zoneLat = ArrayList<Double>()
        val zoneLon = ArrayList<Double>()
        val parks = ArrayList<Polygon>()
        for (w in raw.ways) {
            if (isParkAreaTags(w.tags) && w.tags["highway"] == null && w.nodes.size >= 4 && w.nodes.first() == w.nodes.last() &&
                w.nodes.all { raw.nodeIndex.containsKey(it) }
            ) {
                parks += Polygon(w.nodes.map { raw.lat[raw.nodeFor(it)] }, w.nodes.map { raw.lon[raw.nodeFor(it)] })
                continue
            }
            val cls = classifyHighway(w.tags["highway"]) ?: continue
            if (w.tags["service"] in SKIP_SERVICE) continue
            if (w.nodes.any { !raw.nodeIndex.containsKey(it) }) continue
            if (cls == RoadClass.PEDESTRIAN && w.tags["area"] == "yes") {
                for (n in w.nodes) {
                    val i = raw.nodeFor(n)
                    zoneLat += raw.lat[i]
                    zoneLon += raw.lon[i]
                }
                zoneStart += zoneLat.size
                continue
            }
            lines += Line(w, cls, wayFlagsFromTags(w.tags, cls))
        }
        // Ekim planı madde 10: düğümlerinin en az yarısı bir parkın içindeki yaya yolu park yoludur.
        if (parks.isNotEmpty()) {
            for (l in lines) {
                if (!l.cls.isFootClass || l.way.tags["bicycle"] == "no") continue
                var inside = 0
                for (n in l.way.nodes) {
                    val i = raw.nodeFor(n)
                    if (parks.any { it.contains(raw.lat[i], raw.lon[i]) }) inside++
                }
                if (inside * 2 >= l.way.nodes.size) l.flags = l.flags or WayFlag.PARK
            }
        }

        // 2) Kavşakları bul: ≥ 2 kez kullanılan düğümler + yol uçları.
        val n0 = raw.lat.size
        val usage = IntArray(n0)
        val isEnd = BooleanArray(n0)
        for (l in lines) {
            for (n in l.way.nodes) {
                val i = raw.nodeFor(n)
                if (usage[i] < 255) usage[i]++
            }
            isEnd[raw.nodeFor(l.way.nodes.first())] = true
            isEnd[raw.nodeFor(l.way.nodes.last())] = true
        }
        val graphId = IntArray(n0) { -1 }
        val nLat = DoubleList()
        val nLon = DoubleList()
        for (i in 0 until n0) {
            if ((usage[i] >= 2 || isEnd[i]) && usage[i] != 0) {
                graphId[i] = nLat.size
                nLat += raw.lat[i]
                nLon += raw.lon[i]
            }
        }

        // 3) Yolları kavşaklarda kenarlara böl.
        val eFrom = IntList()
        val eTo = IntList()
        val eWay = IntList()
        val gStart = IntList()
        val gCount = IntList()
        val eLen = DoubleList()
        val upF = DoubleList()
        val clF = DoubleList()
        val upB = DoubleList()
        val clB = DoubleList()
        val mgF = DoubleList()
        val mgB = DoubleList()
        val gLat = DoubleList()
        val gLon = DoubleList()
        val gEle = DoubleList()
        val wayId = ArrayList<Long>()
        val wayCls = ArrayList<Int>()
        val wayFl = ArrayList<Int>()
        val wayNm = IntList()
        val names = ArrayList<String>()
        val nameIdx = HashMap<String, Int>()

        fun emitEdge(from: Int, to: Int, way: Int, la: List<Double>, lo: List<Double>) {
            // Sıklaştır
            val pLat = arrayListOf(la.first())
            val pLon = arrayListOf(lo.first())
            for (k in 1 until la.size) {
                val d = Geo.haversine(la[k - 1], lo[k - 1], la[k], lo[k])
                val steps = ceil(d / MAX_SPACING).toInt()
                for (s in 1 until steps) {
                    val t = s.toDouble() / steps
                    pLat += la[k - 1] + (la[k] - la[k - 1]) * t
                    pLon += lo[k - 1] + (lo[k] - lo[k - 1]) * t
                }
                pLat += la[k]
                pLon += lo[k]
            }
            // Yükseklik + iç noktalarda 3'lü hareketli ortalama
            val rawEle = DoubleArray(pLat.size) { elevation(pLat[it], pLon[it]) }
            val ele = rawEle.copyOf()
            for (k in 1 until rawEle.size - 1) ele[k] = (rawEle[k - 1] + rawEle[k] + rawEle[k + 1]) / 3
            var len = 0.0
            var uf = 0.0
            var cf = 0.0
            var ub = 0.0
            var cb = 0.0
            var gf = 0.0
            var gb = 0.0
            for (k in 1 until pLat.size) {
                val d = Geo.haversine(pLat[k - 1], pLon[k - 1], pLat[k], pLon[k])
                val dh = ele[k] - ele[k - 1]
                len += d
                val grade = dh / max(d, MIN_GRADE_RUN)
                if (dh > 0) {
                    uf += d
                    cf += dh
                    gf = max(gf, grade)
                } else if (dh < 0) {
                    ub += d
                    cb -= dh
                    gb = max(gb, -grade)
                }
            }
            eFrom += from
            eTo += to
            eWay += way
            gStart += gLat.size
            gCount += pLat.size
            pLat.forEach { gLat += it }
            pLon.forEach { gLon += it }
            ele.forEach { gEle += it }
            eLen += len
            upF += uf
            clF += cf
            upB += ub
            clB += cb
            mgF += gf
            mgB += gb
        }

        for (l in lines) {
            val w = l.way
            val way = wayId.size
            wayId += w.id
            wayCls += l.cls.ordinal
            wayFl += l.flags
            // Dart `??`: yalnız null'a bakar; boş `name` → adsız.
            val name = w.tags["name"] ?: w.tags["name:az"] ?: w.tags["name:en"] ?: ""
            wayNm += if (name.isEmpty()) -1 else nameIdx.getOrPut(name) { names += name; names.size - 1 }

            var startNode = graphId[raw.nodeFor(w.nodes.first())]
            var la = ArrayList<Double>()
            var lo = ArrayList<Double>()
            for (k in w.nodes.indices) {
                val i = raw.nodeFor(w.nodes[k])
                la += raw.lat[i]
                lo += raw.lon[i]
                if (k > 0 && graphId[i] >= 0) {
                    if (la.size >= 2) emitEdge(startNode, graphId[i], way, la, lo)
                    startNode = graphId[i]
                    la = arrayListOf(raw.lat[i])
                    lo = arrayListOf(raw.lon[i])
                }
            }
        }

        return RoadGraph(
            nodeLat = nLat.toArray(),
            nodeLon = nLon.toArray(),
            edgeFrom = eFrom.toArray(),
            edgeTo = eTo.toArray(),
            edgeWay = eWay.toArray(),
            edgeGeomStart = gStart.toArray(),
            edgeGeomCount = gCount.toArray(),
            edgeLen = eLen.toFloatArray(),
            upLenF = upF.toFloatArray(),
            climbF = clF.toFloatArray(),
            upLenB = upB.toFloatArray(),
            climbB = clB.toFloatArray(),
            maxGradeF = mgF.toFloatArray(),
            maxGradeB = mgB.toFloatArray(),
            geomLat = gLat.toArray(),
            geomLon = gLon.toArray(),
            geomEle = gEle.toFloatArray(),
            wayOsmId = wayId.toLongArray(),
            wayClass = ByteArray(wayCls.size) { wayCls[it].toByte() },
            wayFlags = ShortArray(wayFl.size) { wayFl[it].toShort() },
            wayName = wayNm.toArray(),
            names = names,
            zoneStart = zoneStart.toIntArray(),
            zoneLat = zoneLat.toDoubleArray(),
            zoneLon = zoneLon.toDoubleArray(),
            features = GraphFeature.BIKEWAYS or GraphFeature.PARKS,
        )
    }
}

/** Kapalı çokgen (ışın atma), sınır kutusuyla ön eleme. */
internal class Polygon(private val lat: List<Double>, private val lon: List<Double>) {
    private val minLat = lat.min()
    private val maxLat = lat.max()
    private val minLon = lon.min()
    private val maxLon = lon.max()

    fun contains(la: Double, lo: Double): Boolean {
        if (la < minLat || la > maxLat || lo < minLon || lo > maxLon) return false
        var inside = false
        var j = lat.size - 1
        for (i in lat.indices) {
            if ((lat[i] > la) != (lat[j] > la) && lo < (lon[j] - lon[i]) * (la - lat[i]) / (lat[j] - lat[i]) + lon[i]) inside = !inside
            j = i
        }
        return inside
    }
}

/** Kutulamasız büyüyen dizi (Bakü grafında ~1M geometri noktası: `ArrayList<Double>` yerine). */
internal class DoubleList {
    private var a = DoubleArray(16)
    var size = 0
        private set

    operator fun plusAssign(v: Double) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    fun toArray(): DoubleArray = a.copyOf(size)

    /** Dart `Float32List.fromList` gibi en yakın `f32`'ye yuvarlar. */
    fun toFloatArray(): FloatArray = FloatArray(size) { a[it].toFloat() }
}

internal class IntList {
    private var a = IntArray(16)
    var size = 0
        private set

    operator fun plusAssign(v: Int) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    fun toArray(): IntArray = a.copyOf(size)
}
