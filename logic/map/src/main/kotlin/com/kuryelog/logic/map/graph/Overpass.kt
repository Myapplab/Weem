package com.kuryelog.logic.map.graph

/** Overpass cevabı sağlam mı? Sorun yoksa null, varsa metni (`MAP.md` 2.5). İskelet (A6.2a). */
fun overpassProblem(body: ByteArray): String? = TODO("A6.3")

/** Overpass sorgusu (`MAP.md` 2.5): yol sınıfları `cycleway` dahil, çıktı `out body`. İskelet (A6.2a). */
fun overpassQuery(box: com.kuryelog.logic.map.geo.BBox): String = TODO("A6.3")
