package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.BikewayMarksStore

/** Uzun basış menüsü satırı (MAP.md 5.1 `bikewayMenuAction`). */
enum class BikewayMenuAction { MARK, ALREADY_OSM, UNMARK }

object BikewayMenu {
    fun action(g: RoadGraph, edge: Int, marks: BikewayMarks): BikewayMenuAction = TODO("A6.8")

    /** İşaretler/kaldırır, bildirim "Bisiklet yolu işaretlendi: $ad" / "… işareti kaldırıldı: $ad" ve "Geri al" (7 sn). */
    fun toggleWithUndo(store: BikewayMarksStore, toasts: Toasts, g: RoadGraph, edge: Int, name: String, lang: Lang = Lang.TR): Unit =
        TODO("A6.8")
}

/** Harita katmanındaki bir bisiklet yolu çizgisi (MAP.md 5.1 `bikewayLines`). */
class BikewayLine(val edge: Int, val dashed: Boolean, val rgb: Int, val alpha: Double, val width: Double)

object BikewayLayerRules {
    /** `showLayer` kapalıysa boş; OSM düz, elle işaretli kesikli; renk koyu `#3DDCE8`, açık `#00838F`, %85 opak. */
    fun lines(g: RoadGraph, marks: BikewayMarks, showLayer: Boolean, dark: Boolean): List<BikewayLine> = TODO("A6.8")
}
