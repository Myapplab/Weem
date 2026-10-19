package com.kuryelog.logic.map.graph

/** Ham OSM düğüm/yol deposu (YolGo `OsmRaw`/`OsmWay`, data/overpass.dart). İskelet (A6.2a): gövdeler A6.3'te. */
class OsmWay(val id: Long, val nodes: List<Long>, val tags: Map<String, String>)

class OsmRaw {
    val nodeLat = HashMap<Long, Double>()
    val nodeLon = HashMap<Long, Double>()
    val ways = ArrayList<OsmWay>()

    fun addNode(id: Long, lat: Double, lon: Double): OsmRaw = apply {
        nodeLat[id] = lat
        nodeLon[id] = lon
    }

    fun addWay(way: OsmWay): OsmRaw = apply { ways.add(way) }
}
