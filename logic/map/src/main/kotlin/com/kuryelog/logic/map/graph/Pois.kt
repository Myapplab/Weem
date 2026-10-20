package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import org.json.JSONObject

/** Yer noktası (restoran, fast food, kafe, AVM, market, metro, trafik ışığı) — `lib/data/pois.dart` (MAP.md 5.3). */
class Poi(val lat: Double, val lon: Double, val kind: Char, val name: String, val osmRef: String = "")

/** Bölgedeki noktalar. Enleme göre sıralı → görünür alan hızlı süzülür. */
class PoiSet(pois: Iterable<Poi>) {
    val items: List<Poi> = TODO("A6.8")
    val length: Int get() = items.size

    /** [maxM] içindeki en yakın [kind] türü nokta (yoksa null). */
    fun nearest(kind: Char, lat: Double, lon: Double, maxM: Double = 40.0): Poi? = TODO("A6.8")

    /** Kutunun içindeki noktalar (ikili arama + boylam süzgeci). */
    fun within(south: Double, west: Double, north: Double, east: Double): List<Poi> = TODO("A6.8")

    /** Sıkı biçim: `{"v":1,"pois":[[enlem,boylam,"r","Ad","node/1"],…]}`. */
    fun encode(): ByteArray = TODO("A6.8")

    companion object {
        fun decode(bytes: ByteArray): PoiSet = TODO("A6.8")
    }
}

object Pois {
    fun query(b: BBox): String = TODO("A6.8")

    /** Etiketlerden tür: yoksa null. */
    fun kind(tags: Map<String, String>): Char? = TODO("A6.8")

    /** Overpass JSON → noktalar. */
    fun fromOverpass(json: JSONObject): List<Poi> = TODO("A6.8")
}
