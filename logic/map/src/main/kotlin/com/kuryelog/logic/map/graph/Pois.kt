package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Geo
import org.json.JSONArray
import org.json.JSONObject

/** Yer noktası (restoran, fast food, kafe, AVM, market, metro, trafik ışığı) — `lib/data/pois.dart` (MAP.md 5.3). */
class Poi(val lat: Double, val lon: Double, val kind: Char, val name: String, val osmRef: String = "")

/** Bölgedeki noktalar. Enleme göre sıralı → görünür alan hızlı süzülür. */
class PoiSet(pois: Iterable<Poi>) {
    /** Eşit enlemde girdi sırası korunur (kararlı sıralama). */
    val items: List<Poi> = pois.sortedBy { it.lat }
    val length: Int get() = items.size

    /** [maxM] içindeki en yakın [kind] türü nokta (yoksa null; eşit uzaklıkta sonraki kazanır). */
    fun nearest(kind: Char, lat: Double, lon: Double, maxM: Double = 40.0): Poi? {
        val d = maxM / 111320.0
        var best: Poi? = null
        var bd = maxM
        for (i in lowerBound(lat - d) until items.size) {
            val p = items[i]
            if (p.lat > lat + d) break
            if (p.kind != kind || p.lon < lon - 1.4 * d || p.lon > lon + 1.4 * d) continue
            val m = Geo.haversine(lat, lon, p.lat, p.lon)
            if (m <= bd) { best = p; bd = m }
        }
        return best
    }

    /** Kutunun içindeki noktalar (ikili arama + boylam süzgeci). */
    fun within(south: Double, west: Double, north: Double, east: Double): List<Poi> {
        val out = ArrayList<Poi>()
        for (i in lowerBound(south) until items.size) {
            val p = items[i]
            if (p.lat > north) break
            if (p.lon in west..east) out += p
        }
        return out
    }

    /** Sıkı biçim: `{"v":1,"pois":[[enlem,boylam,"r","Ad","node/1"],…]}`. */
    fun encode(): ByteArray {
        val a = JSONArray()
        for (p in items) {
            a.put(JSONArray().put(r6(p.lat)).put(r6(p.lon)).put(p.kind.toString()).put(p.name).put(p.osmRef))
        }
        return JSONObject().put("v", 1).put("pois", a).toString().toByteArray(Charsets.UTF_8)
    }

    private fun lowerBound(lat: Double): Int {
        var lo = 0
        var hi = items.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (items[mid].lat < lat) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun r6(v: Double) = Math.round(v * 1e6) / 1e6

    companion object {
        /** ≥4 elemanlı satır kabul edilir (5. yoksa `osmRef` boş). */
        fun decode(bytes: ByteArray): PoiSet {
            val a = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("pois")
            val list = ArrayList<Poi>()
            for (i in 0 until a.length()) {
                val r = a.optJSONArray(i) ?: continue
                if (r.length() < 4) continue
                val kind = r.getString(2).firstOrNull() ?: continue
                list += Poi(r.getDouble(0), r.getDouble(1), kind, r.getString(3), if (r.length() > 4) r.optString(4, "") else "")
            }
            return PoiSet(list)
        }
    }
}

object Pois {
    private val MARKET = setOf("convenience", "supermarket", "bakery", "butcher")

    fun query(b: BBox): String {
        val x = b.overpass
        return "[out:json][timeout:120]; ( " +
            "nwr[\"amenity\"~\"^(restaurant|fast_food|cafe)$\"]($x); " +
            "nwr[\"shop\"=\"mall\"]($x); " +
            "nwr[\"shop\"~\"^(convenience|supermarket|bakery|butcher)$\"]($x); " +
            "nwr[\"station\"=\"subway\"]($x); " +
            "node[\"highway\"=\"traffic_signals\"]($x); " +
            "node[\"crossing\"=\"traffic_signals\"]($x); " +
            "node[\"highway\"=\"crossing\"][\"crossing:signals\"=\"yes\"]($x); " +
            "); out tags center qt;"
    }

    /** Etiketlerden tür: yoksa null. */
    fun kind(tags: Map<String, String>): Char? {
        when (tags["amenity"]) {
            "restaurant" -> return 'r'
            "fast_food" -> return 'f'
            "cafe" -> return 'c'
        }
        val shop = tags["shop"]
        if (shop == "mall") return 'm'
        if (shop != null && shop in MARKET) return 't'
        if (tags["station"] == "subway") return 's'
        if (tags["highway"] == "traffic_signals" || tags["crossing"] == "traffic_signals" ||
            (tags["highway"] == "crossing" && tags["crossing:signals"] == "yes")
        ) return 'l'
        return null
    }

    /** Overpass JSON → noktalar (etiketsiz ya da türsüz öğe atlanır; koordinat `lat/lon` ya da `center`). */
    fun fromOverpass(json: JSONObject): List<Poi> {
        val els = json.optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Poi>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val t = e.optJSONObject("tags") ?: continue
            val tags = HashMap<String, String>()
            for (k in t.keys()) tags[k] = t.optString(k)
            val kind = kind(tags) ?: continue
            val c = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else c?.optDouble("lat")
            val lon = if (e.has("lon")) e.optDouble("lon") else c?.optDouble("lon")
            if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) continue
            val name = if (kind == 'l') "" else listOf("name:az", "name", "brand", "name:en").map { clean(tags[it]) }.firstOrNull { it.isNotEmpty() } ?: ""
            val ref = if (e.has("id") && e.has("type")) "${e.optString("type")}/${e.optLong("id")}" else ""
            out += Poi(lat, lon, kind, name, ref)
        }
        return out
    }

    private fun clean(s: String?): String = s?.trim()?.replace(Regex("\\s+"), " ") ?: ""
}
