package com.kuryelog.logic.map.route

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.surface.SurfaceMark
import java.util.Locale
import kotlin.math.roundToLong

/** Bir caddenin kötü kesim oranı: kötü noktaların ±25 m aralıklarının birleşimi / cadde uzunluğu (MAP.md 9.7, `street_ratio.dart`). */
class StreetRatio(val badM: Double, val totalM: Double) {
    val percent: Int get() = if (totalM <= 0) 0 else (badM / totalM * 100).roundToLong().toInt()
}

object StreetRatios {
    /** WEEM 1.0: yalnız kötü noktalar (ölçülmüş yüzey kesimleri Y13 ile, SORU-Q1). Tek nokta yalnız kendi 50 m'sini sayar. */
    fun of(g: RoadGraph, way: Long, marks: Iterable<SurfaceMark>): StreetRatio {
        val edges = g.edgesOfOsmWay(way)
        if (edges.isEmpty()) return StreetRatio(0.0, 0.0)
        val spans = HashMap<Int, MutableList<Pair<Double, Double>>>()
        for (s in BadSpots.spans(g, marks.filter { it.way == way })) spans.getOrPut(s.edge) { ArrayList() } += s.f0 to s.f1
        var total = 0.0
        var bad = 0.0
        for (e in LinkedHashSet(edges)) {
            val len = g.edgeLen[e].toDouble()
            total += len
            val list = (spans[e] ?: emptyList<Pair<Double, Double>>()).sortedBy { it.first }
            var covered = 0.0
            var end = 0.0
            for ((a, b) in list) {
                val from = maxOf(a, end)
                if (b > from) covered += b - from
                if (b > end) end = b
            }
            bad += covered * len
        }
        return StreetRatio(bad, total)
    }

    /** Uzun basış satırı başlığı (TR/AZ aynen; km Dart `toStringAsFixed(1)` gibi noktalı). */
    fun text(r: StreetRatio, lang: Lang = Lang.TR): String {
        fun km(m: Double) = if (m < 1000) "${m.roundToLong()} m" else "${String.format(Locale.ROOT, "%.1f", m / 1000)} km"
        if (r.badM < 1) return tl(lang, "Kötü kesim yok · ${km(r.totalM)}", "Pis hissə yoxdur · ${km(r.totalM)}")
        return tl(
            lang,
            "%${r.percent} kötü · ${r.badM.roundToLong()} m / ${km(r.totalM)}",
            "%${r.percent} pis · ${r.badM.roundToLong()} m / ${km(r.totalM)}",
        )
    }
}
