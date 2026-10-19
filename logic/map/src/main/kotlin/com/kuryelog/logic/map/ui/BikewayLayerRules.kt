package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.settings.MapSettings

/** Bisiklet katmanı çizgisi: kenar, kalınlık 4, ARGB renk (%85 opak), elle işaretli kesikli `[10, 6]` (`MAP.md` 5.1). */
class BikewayLine(val edge: Int, val dashed: Boolean, val colorArgb: Int, val width: Int)

object BikewayLayerRules {
    /** Koyu tema `#3DDCE8`, açık `#00838F`, %85 opak. */
    const val COLOR_DARK: Int = 0xD93DDCE8.toInt()
    const val COLOR_LIGHT: Int = 0xD900838F.toInt()

    fun lines(graph: RoadGraph?, marks: BikewayMarks, settings: MapSettings, dark: Boolean): List<BikewayLine> = TODO("A6.8")
}
