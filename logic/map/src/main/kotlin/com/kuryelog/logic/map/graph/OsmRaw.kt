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
    fun addOverpassJson(json: JSONObject) {
        val elements = json.optJSONArray("elements") ?: return
        for (k in 0 until elements.length()) {
            val e = elements.opt(k) as? JSONObject ?: continue
            when (e.opt("type")) {
                "node" -> addNode(e.getLong("id"), e.getDouble("lat"), e.getDouble("lon"))
                "way" -> {
                    val arr = e.optJSONArray("nodes") ?: continue
                    if (arr.length() < 2) continue
                    val nodes = List(arr.length()) { arr.getLong(it) }
                    val tags = HashMap<String, String>()
                    (e.opt("tags") as? JSONObject)?.let { t -> for (key in t.keys()) tags[key] = t.get(key).toString() }
                    addWay(OsmWay(e.getLong("id"), nodes, tags))
                }
            }
        }
    }
}

class OsmWay(val id: Long, val nodes: List<Long>, val tags: Map<String, String>)
