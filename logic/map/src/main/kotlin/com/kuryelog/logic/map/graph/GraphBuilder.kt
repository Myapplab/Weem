package com.kuryelog.logic.map.graph

typealias ElevationFn = (lat: Double, lon: Double) -> Double

/** `lib/data/graph_builder.dart` (MAP.md 2.4). İskelet (A6.2a): gövdeler A6.3'te. */
object GraphBuilder {
    fun classifyHighway(h: String?): RoadClass? = TODO("A6.3")

    /** E13: bisiklet yolu etiketleri (kullanıcı listesi, 14 Ekim). */
    fun isBikewayTags(t: Map<String, String>): Boolean = TODO("A6.3")

    fun wayFlagsFromTags(t: Map<String, String>, cls: RoadClass): Int = TODO("A6.3")

    /** Park alanı etiketleri (Overpass sorgusu ve CI aracıyla aynı). */
    fun isParkAreaTags(t: Map<String, String>): Boolean = TODO("A6.3")

    /** Ham OSM + yükseklik → rota grafiği. */
    fun buildRoadGraph(raw: OsmRaw, elevation: ElevationFn): RoadGraph = TODO("A6.3")
}
