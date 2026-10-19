package com.kuryelog.logic.map.graph

/** `lib/data/road_graph.dart` (MAP.md 2.5, 2.6): yol sınıfı sırası KNG1 dosyasındaki u8 değerdir, değişmez. */
enum class RoadClass {
    TRUNK, PRIMARY, SECONDARY, TERTIARY, UNCLASSIFIED, RESIDENTIAL, LIVING_STREET, SERVICE, ROAD, TRACK, CYCLEWAY, PATH, FOOTWAY,
    PEDESTRIAN, STEPS,
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
    val dist: Double,
    val fraction: Double,
    val lat: Double,
    val lon: Double,
    val segBearing: Double,
)

/**
 * Telefonda tutulan yol ağı. Düğümler = kavşaklar ve yol uçları; kenarlar = iki kavşak arası yol parçası (+ çizim geometrisi).
 * İskelet (A6.2a): sorgular ve dosya biçimi A6.3'te.
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
    val wayClass: ByteArray,
    val wayFlags: ShortArray,
    val wayName: IntArray,
    val names: List<String>,
    val zoneStart: IntArray,
    val zoneLat: DoubleArray,
    val zoneLon: DoubleArray,
    val features: Int = 0,
) {
    val nodeCount get() = nodeLat.size
    val edgeCount get() = edgeFrom.size
    val zoneCount get() = zoneStart.size - 1

    fun wayOf(e: Int): Int = edgeWay[e]
    fun classOf(e: Int): RoadClass = TODO("A6.3")
    fun flagsOf(e: Int): Int = TODO("A6.3")

    /** E13: OSM'de bisiklet yolu (elle işaretlenenler `route.BikewayMarks`'ta). */
    fun isBikeway(e: Int): Boolean = TODO("A6.3")

    /** E13: dosya bisiklet yolu bilgisini taşıyor mu (false: E13 öncesi harita, güncellenmeli). */
    val hasBikewayData: Boolean get() = TODO("A6.3")
    val hasParkData: Boolean get() = TODO("A6.3")
    fun osmIdOf(e: Int): Long = TODO("A6.3")
    fun nameOf(e: Int): String = TODO("A6.3")

    /** OSM way id → sokak indeksi (yoksa null). */
    fun wayIndexOf(osmId: Long): Int? = TODO("A6.3")

    /** Bir OSM yoluna ait tüm kenarlar. */
    fun edgesOfOsmWay(osmId: Long): List<Int> = TODO("A6.3")
    fun nameOfOsmWay(osmId: Long): String = TODO("A6.3")
    fun classOfOsmWay(osmId: Long): RoadClass? = TODO("A6.3")

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
    ): EdgeHit? = TODO("A6.3")

    fun edgesWithin(south: Double, west: Double, north: Double, east: Double): List<Int> = TODO("A6.3")
    fun inPedestrianZone(lat: Double, lon: Double): Boolean = TODO("A6.3")

    /** KNG1 ikili biçimi (little-endian; MAP.md 2.5). */
    fun serialize(): ByteArray = TODO("A6.3")

    companion object {
        const val FORMAT_VERSION = 2

        /** Sihir ya da sürüm farklıysa [FormatException]. */
        fun deserialize(bytes: ByteArray): RoadGraph = TODO("A6.3")
    }
}
