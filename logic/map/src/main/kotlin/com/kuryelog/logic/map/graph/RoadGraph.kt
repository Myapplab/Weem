package com.kuryelog.logic.map.graph

/** Yol sınıfı (YolGo `RoadClass`, `MAP.md` 2.4); sıra eski sıradır. */
enum class RoadClass {
    TRUNK, PRIMARY, SECONDARY, TERTIARY, UNCLASSIFIED, RESIDENTIAL, LIVING_STREET, SERVICE, ROAD, TRACK,
    CYCLEWAY, PATH, FOOTWAY, PEDESTRIAN, STEPS,
}

/** Yol bayrakları (u16, way başına). */
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

/** `nearestEdge` sonucu. */
class EdgeHit(val edge: Int, val dist: Double, val fraction: Double, val lat: Double, val lon: Double, val segBearing: Double)

/** Yol grafı (KNG1 ikili dosya). İskelet (A6.2a): alanlar ve sorgular A6.3'te. */
class RoadGraph {
    val nodeCount: Int get() = TODO("A6.3")
    val edgeCount: Int get() = TODO("A6.3")
    val nodeLat: DoubleArray get() = TODO("A6.3")
    val nodeLon: DoubleArray get() = TODO("A6.3")
    val edgeFrom: IntArray get() = TODO("A6.3")
    val climbF: FloatArray get() = TODO("A6.3")
    val names: List<String> get() = TODO("A6.3")
    val hasBikewayData: Boolean get() = TODO("A6.3")
    val hasParkData: Boolean get() = TODO("A6.3")

    fun nearestEdge(lat: Double, lon: Double, maxDist: Double = 60.0): EdgeHit? = TODO("A6.3")
    fun flagsOf(e: Int): Int = TODO("A6.3")
    fun osmIdOf(e: Int): Long = TODO("A6.3")
    fun isBikeway(e: Int): Boolean = TODO("A6.3")
    fun edgesOfOsmWay(osmId: Long): List<Int> = TODO("A6.3")
    fun serialize(): ByteArray = TODO("A6.3")

    companion object {
        /** KNG1 sürümü; eski sürüm `FormatException` ile reddedilir. */
        const val FORMAT_VERSION = 2

        fun deserialize(bytes: ByteArray): RoadGraph = TODO("A6.3")
    }
}

/** `elevation(lat, lon)` metre; A6.3'te `buildRoadGraph` YolGo `graph_builder.dart` 85–274'ü taşır. */
fun buildRoadGraph(raw: OsmRaw, elevation: (Double, Double) -> Double): RoadGraph = TODO("A6.3")

/** E13: bisiklet yolu etiketleri (`MAP.md` 5.1). */
fun isBikewayTags(tags: Map<String, String>): Boolean = TODO("A6.3")

/** Yol etiketlerinden bayrak bitleri. */
fun wayFlagsFromTags(tags: Map<String, String>, cls: RoadClass): Int = TODO("A6.3")
