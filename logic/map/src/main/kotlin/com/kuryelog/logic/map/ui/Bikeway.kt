package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.BikewayMarksStore

/** Uzun basış menüsü satırı (MAP.md 5.1 `bikewayMenuAction`). */
enum class BikewayMenuAction { MARK, ALREADY_OSM, UNMARK }

object BikewayMenu {
    fun action(g: RoadGraph, edge: Int, marks: BikewayMarks): BikewayMenuAction = when {
        g.osmIdOf(edge) in marks.ways -> BikewayMenuAction.UNMARK
        g.isBikeway(edge) -> BikewayMenuAction.ALREADY_OSM
        else -> BikewayMenuAction.MARK
    }

    /** İşaretler/kaldırır, bildirim "Bisiklet yolu işaretlendi: $ad" / "… işareti kaldırıldı: $ad" ve "Geri al" (7 sn). */
    fun toggleWithUndo(store: BikewayMarksStore, toasts: Toasts, g: RoadGraph, edge: Int, name: String, lang: Lang = Lang.TR) {
        val way = g.osmIdOf(edge)
        val shown = name.ifEmpty { if (lang == Lang.AZ) "adsız yol" else "isimsiz yol" }
        val wasMarked = store.isMarked(way)
        if (wasMarked) store.unmark(way) else store.mark(way, name)
        val text = if (wasMarked) {
            if (lang == Lang.AZ) "Velosiped yolu qeydi silindi: $shown" else "Bisiklet yolu işareti kaldırıldı: $shown"
        } else {
            if (lang == Lang.AZ) "Velosiped yolu qeyd olundu: $shown" else "Bisiklet yolu işaretlendi: $shown"
        }
        toasts.show(
            MapToast(text, ToastTone.BIKEWAY, seconds = 7, actionLabel = if (lang == Lang.AZ) "Geri qaytar" else "Geri al") {
                if (wasMarked) store.mark(way, name) else store.unmark(way)
                toasts.hide()
            },
        )
    }
}

/** OSM'de bisiklet yolu olan sokak sayısı ve toplam uzunluk (m): Yollarım → Bisiklet istatistiği. */
object BikewayStats {
    class Stats(val ways: Int, val meters: Double)

    fun of(g: RoadGraph): Stats {
        val ways = HashSet<Int>()
        var meters = 0.0
        for (e in 0 until g.edgeCount) {
            if (!g.isBikeway(e)) continue
            ways += g.wayOf(e)
            meters += g.edgeLen[e]
        }
        return Stats(ways.size, meters)
    }
}

/** Harita katmanındaki bir bisiklet yolu çizgisi (MAP.md 5.1 `bikewayLines`). */
class BikewayLine(val edge: Int, val dashed: Boolean, val rgb: Int, val alpha: Double, val width: Double)

object BikewayLayerRules {
    const val DARK_RGB = 0x3DDCE8
    const val LIGHT_RGB = 0x00838F

    /** `showLayer` kapalıysa boş; OSM düz, elle işaretli kesikli; renk koyu `#3DDCE8`, açık `#00838F`, %85 opak. */
    fun lines(g: RoadGraph, marks: BikewayMarks, showLayer: Boolean, dark: Boolean): List<BikewayLine> {
        if (!showLayer) return emptyList()
        val out = ArrayList<BikewayLine>()
        for (e in 0 until g.edgeCount) {
            val osm = g.isBikeway(e)
            if (!osm && g.osmIdOf(e) !in marks.ways) continue
            out += BikewayLine(e, dashed = !osm, rgb = if (dark) DARK_RGB else LIGHT_RGB, alpha = 0.85, width = 4.0)
        }
        return out
    }
}
