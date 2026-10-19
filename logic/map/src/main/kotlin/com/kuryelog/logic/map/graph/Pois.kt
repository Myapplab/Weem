package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Geo
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Yer noktası (restoran, fast food, kafe, AVM, market, metro, trafik ışığı) — `lib/data/pois.dart` (MAP.md 5.3). */
class Poi(val lat: Double, val lon: Double, val kind: Char, val name: String, val osmRef: String = "")

/** Bölgedeki noktalar. Enleme göre sıralı → görünür alan hızlı süzülür (eşit enlemde girdi sırası korunur). */
class PoiSet(pois: Iterable<Poi>) {
    val items: List<Poi> = pois.sortedBy { it.lat }
    val length: Int get() = items.size

    /** [maxM] içindeki en yakın [kind] türü nokta (yoksa null). Eşitlikte sonraki kazanır. */
    fun nearest(kind: Char, lat: Double, lon: Double, maxM: Double = 40.0): Poi? {
        val d = maxM / 111320
        var best: Poi? = null
        var bd = maxM
        for (p in within(lat - d, lon - d * 1.4, lat + d, lon + d * 1.4)) {
            if (p.kind != kind) continue
            val m = Geo.haversine(lat, lon, p.lat, p.lon)
            if (m <= bd) {
                bd = m
                best = p
            }
        }
        return best
    }

    /** Kutunun içindeki noktalar (ikili arama + boylam süzgeci). */
    fun within(south: Double, west: Double, north: Double, east: Double): List<Poi> {
        var lo = 0
        var hi = items.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (items[mid].lat < south) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<Poi>()
        var i = lo
        while (i < items.size && items[i].lat <= north) {
            val p = items[i]
            if (p.lon >= west && p.lon <= east) out += p
            i++
        }
        return out
    }

    /** Sıkı biçim: `{"v":1,"pois":[[enlem,boylam,"r","Ad","node/1"],…]}`. */
    fun encode(): ByteArray {
        val arr = JSONArray()
        for (p in items) {
            arr.put(JSONArray().put(r6(p.lat)).put(r6(p.lon)).put(p.kind.toString()).put(p.name).put(p.osmRef))
        }
        return JSONObject().put("v", 1).put("pois", arr).toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        private fun r6(v: Double): Double = String.format(Locale.ROOT, "%.6f", v).toDouble()

        fun decode(bytes: ByteArray): PoiSet {
            val list = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("pois") ?: JSONArray()
            val out = ArrayList<Poi>()
            for (i in 0 until list.length()) {
                val e = list.optJSONArray(i) ?: continue
                if (e.length() < 4) continue
                val lat = (e.opt(0) as? Number)?.toDouble() ?: continue
                val lon = (e.opt(1) as? Number)?.toDouble() ?: continue
                out += Poi(lat, lon, e.opt(2).toString().firstOrNull() ?: continue, e.opt(3).toString(), if (e.length() > 4) e.opt(4).toString() else "")
            }
            return PoiSet(out)
        }
    }
}

object Pois {
    /** Overpass: restoran / fast food / kafe, AVM'ler, market/bakkal, metro istasyonları ve trafik ışıkları. */
    fun query(b: BBox): String {
        val o = b.overpass
        return """
[out:json][timeout:120];
(
  nwr["amenity"~"^(restaurant|fast_food|cafe)$"]($o);
  nwr["shop"="mall"]($o);
  nwr["shop"~"^(convenience|supermarket|bakery|butcher)$"]($o);
  nwr["station"="subway"]($o);
  node["highway"="traffic_signals"]($o);
  node["crossing"="traffic_signals"]($o);
  node["highway"="crossing"]["crossing:signals"="yes"]($o);
);
out tags center qt;
"""
    }

    private val KINDS = mapOf("restaurant" to 'r', "fast_food" to 'f', "cafe" to 'c')
    private val SHOPS = setOf("convenience", "supermarket", "bakery", "butcher")

    /** Etiketlerden tür: yoksa null. */
    fun kind(tags: Map<String, String>): Char? {
        KINDS[tags["amenity"]]?.let { return it }
        if (tags["shop"] == "mall") return 'm'
        if (tags["shop"] in SHOPS) return 't'
        if (tags["station"] == "subway") return 's'
        if (tags["highway"] == "traffic_signals" || tags["crossing"] == "traffic_signals" ||
            (tags["highway"] == "crossing" && tags["crossing:signals"] == "yes")
        ) {
            return 'l'
        }
        return null
    }

    /** Overpass JSON → noktalar. */
    fun fromOverpass(json: JSONObject): List<Poi> {
        val out = ArrayList<Poi>()
        val els = json.optJSONArray("elements") ?: return out
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val t = e.optJSONObject("tags") ?: continue
            val tags = HashMap<String, String>()
            for (k in t.keys()) (t.opt(k) as? String)?.let { tags[k] = it }
            val kind = kind(tags) ?: continue
            val c = e.optJSONObject("center")
            val lat = (e.opt("lat") ?: c?.opt("lat")) as? Number ?: continue
            val lon = (e.opt("lon") ?: c?.opt("lon")) as? Number ?: continue
            val name = if (kind == 'l') {
                ""
            } else {
                listOf("name:az", "name", "brand", "name:en").mapNotNull { tags[it] }
                    .map { it.trim().replace(Regex("\\s+"), " ") }.firstOrNull { it.isNotEmpty() } ?: ""
            }
            out += Poi(lat.toDouble(), lon.toDouble(), kind, name, if (e.has("id")) "${e.opt("type")}/${e.opt("id")}" else "")
        }
        return out
    }
}
