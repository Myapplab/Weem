package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.route.TestGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `bikeway_e13_test.dart` "harita katmanı: OSM düz, elle kesikli, ayrı renk; kapatılınca yok" (A6.2a, MAP.md 5.2). Saf kural
 * `BikewayLayerRules` (Dart `Polyline` yerine `BikewayLine`). Renk koyu `#3DDCE8`, %85 opak. Uygulama: A6.8.
 */
class BikewayLayerRulesTest {
    @Test
    fun `harita katmanı OSM düz, elle kesikli, ayrı renk, kapatılınca yok`() {
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        val osm = TestGraphs.EAST_WAYS.sumOf { g.edgesOfOsmWay(it).size }
        var marks = BikewayMarks()
        assertEquals(osm, BikewayLayerRules.lines(g, marks, showLayer = true, dark = true).size)
        assertTrue(BikewayLayerRules.lines(g, marks, true, true).all { it.rgb == 0x3DDCE8 && it.alpha == 0.85 && !it.dashed })
        marks = BikewayMarks(ways = setOf(302L), names = mapOf(302L to "Batı"))
        val lines = BikewayLayerRules.lines(g, marks, true, true)
        val manual = g.edgesOfOsmWay(302).size
        assertEquals(osm + manual, lines.size)
        assertEquals(manual, lines.count { it.dashed }) // elle: kesikli
        assertTrue(BikewayLayerRules.lines(g, marks, showLayer = false, dark = true).isEmpty())
    }
}
