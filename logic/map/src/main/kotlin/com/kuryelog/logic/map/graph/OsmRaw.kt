package com.kuryelog.logic.map.graph

import org.json.JSONObject

/** Overpass'tan gelen ham OSM verisi (birden çok karo birleşik) — `lib/data/overpass.dart` (MAP.md 2.8, 2.14). */
class OsmRaw {
    val nodeIndex = HashMap<Long, Int>()
    val lat = ArrayList<Double>()
    val lon = ArrayList<Double>()
    val ways = ArrayList<OsmWay>()
    private val wayIds = HashSet<Long>()

    fun nodeFor(osmId: Long): Int = nodeIndex[osmId]!!

    fun addNode(id: Long, la: Double, lo: Double) {
        if (nodeIndex.containsKey(id)) return
        nodeIndex[id] = lat.size
        lat.add(la)
        lon.add(lo)
    }

    fun addWay(w: OsmWay) {
        if (wayIds.add(w.id)) ways.add(w)
    }

    /** Overpass JSON cevabını ekler. Karo sınırında tekrar eden yol/düğümler atlanır. */
    fun addOverpassJson(json: JSONObject): Unit = TODO("A6.3")
}

class OsmWay(val id: Long, val nodes: List<Long>, val tags: Map<String, String>)
