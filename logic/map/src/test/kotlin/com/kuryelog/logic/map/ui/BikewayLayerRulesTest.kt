package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.route.BikewayMarks
import com.kuryelog.logic.map.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `bikeway_e13_test.dart` "harita katmanı: OSM düz, elle kesikli, ayrı renk; kapatılınca yok" (1 test); `MAP.md` 5.1, 5.2. */
class BikewayLayerRulesTest {
    @Test
    fun `harita katmanı - OSM düz, elle kesikli, ayrı renk - kapatılınca yok`() {
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        val eastWays = listOf(401L, 402L, 403L)
        val osm = eastWays.sumOf { g.edgesOfOsmWay(it).size }
        val settings = MapSettings()
        val none = BikewayLayerRules.lines(g, BikewayMarks(), settings, dark = true)
        assertEquals(osm, none.size)
        assertTrue(none.all { it.colorArgb == 0xD93DDCE8.toInt() }) // %85 opak bisiklet rengi (koyu tema)
        assertTrue(BikewayLayerRules.lines(g, BikewayMarks(), settings, dark = false).all { it.colorArgb == 0xD900838F.toInt() })
        val marked = BikewayMarks(ways = setOf(302L), names = mapOf(302L to "Batı"))
        val lines = BikewayLayerRules.lines(g, marked, settings, dark = true)
        assertEquals(osm + g.edgesOfOsmWay(302).size, lines.size)
        assertEquals(g.edgesOfOsmWay(302).size, lines.count { it.dashed }) // elle: kesikli
        assertTrue(BikewayLayerRules.lines(g, marked, settings.copy(showBikewayLayer = false), dark = true).isEmpty())
    }
}
