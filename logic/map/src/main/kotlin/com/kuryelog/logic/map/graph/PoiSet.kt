package com.kuryelog.logic.map.graph

/**
 * Yemek/yer noktası (`MAP.md` 5.3). [kind] tek harf: 'r' restoran · 'f' fast food · 'c' kafe · 'm' AVM · 't' market · 's' metro ·
 * 'l' trafik ışığı. [osmRef] `node/123` ya da `way/123`; eski dört alanlı dosyalar boş okunur.
 */
data class Poi(val lat: Double, val lon: Double, val kind: String, val name: String, val osmRef: String = "")

/** Enleme göre sıralı nokta kümesi (görünür alan hızlı süzülür). İskelet (A6.2a): gövdeler A6.8'de. */
class PoiSet(pois: Iterable<Poi>) {
    val items: List<Poi> get() = TODO("A6.8")
    val length: Int get() = TODO("A6.8")

    fun within(south: Double, west: Double, north: Double, east: Double): List<Poi> = TODO("A6.8")
    fun nearest(kind: String, lat: Double, lon: Double, maxM: Double = 40.0): Poi? = TODO("A6.8")

    /** Sıkı biçim: `{"v":1,"pois":[[enlem,boylam,"r","Ad","node/1"],…]}`. */
    fun encode(): ByteArray = TODO("A6.8")

    companion object {
        fun decode(bytes: ByteArray): PoiSet = TODO("A6.8")
    }
}

/** Overpass JSON → noktalar (`MAP.md` 5.3). */
fun poisFromOverpass(json: String): List<Poi> = TODO("A6.8")
