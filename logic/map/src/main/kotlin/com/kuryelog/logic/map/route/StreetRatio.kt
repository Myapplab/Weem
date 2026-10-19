package com.kuryelog.logic.map.route

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.surface.SurfaceMark

/** Bir caddenin kötü kesim oranı: kötü noktaların ±25 m aralıklarının birleşimi / cadde uzunluğu (MAP.md 9.12 `StreetRatio`). */
class StreetRatio(val badM: Double, val totalM: Double) {
    val percent: Int get() = TODO("A6.11")
}

object StreetRatios {
    /** WEEM 1.0: yalnız kötü noktalar (ölçülmüş yüzey kesimleri Y13 ile, SORU-Q1). */
    fun of(g: RoadGraph, way: Long, marks: Iterable<SurfaceMark>): StreetRatio = TODO("A6.11")

    fun text(r: StreetRatio, lang: Lang = Lang.TR): String = TODO("A6.11")
}
