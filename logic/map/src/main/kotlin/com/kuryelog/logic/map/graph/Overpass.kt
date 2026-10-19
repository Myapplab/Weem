package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox

/** `lib/data/overpass.dart` (MAP.md 2.8). İskelet (A6.2a): gövdeler A6.3'te; ağ `HttpPort` ile. */
object Overpass {
    /** Scooter için anlamlı yol tipleri (park ve yaya alanı dahil). */
    fun query(b: BBox): String = TODO("A6.3")

    /**
     * Sunucu 200 dönse bile cevabın sonuna "runtime error" notu düşebilir (zaman aşımı, bellek): o zaman yollar eksiktir.
     * Sorun yoksa null; varsa metin ("boş cevap", "JSON değil" ya da `remark` değeri).
     */
    fun problem(body: ByteArray): String? = TODO("A6.3")
}
