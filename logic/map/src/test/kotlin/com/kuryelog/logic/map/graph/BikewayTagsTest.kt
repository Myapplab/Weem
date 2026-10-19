package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.geo.Baku
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `test/bikeway_e13_test.dart` "E13 etiketler" grubu (3 test); `MAP.md` 5.2. */
class BikewayTagsTest {
    private val residential = mapOf("highway" to "residential")

    @Test
    fun `her bisiklet etiketi kenara bisiklet yolu bayrağı yazar`() {
        val tagged = buildList<Map<String, String>> {
            add(mapOf("highway" to "cycleway"))
            for (v in listOf("lane", "track", "share_busway")) add(mapOf("highway" to "residential", "cycleway" to v))
            for (side in listOf("left", "right", "both")) {
                for (v in listOf("lane", "track")) add(mapOf("highway" to "secondary", "cycleway:$side" to v))
            }
            add(mapOf("highway" to "footway", "bicycle" to "designated"))
            add(mapOf("highway" to "path", "bicycle" to "designated"))
        }
        for (t in tagged) {
            assertTrue("$t", isBikewayTags(t))
            val g = TestGraphs.single(t)
            assertTrue("$t", g.isBikeway(0))
            assertEquals("$t", WayFlag.BIKEWAY, g.flagsOf(0) and WayFlag.BIKEWAY)
        }
    }

    @Test
    fun `bisiklet yolu sayılmayanlar`() {
        val plain = listOf(
            residential,
            mapOf("highway" to "residential", "cycleway" to "shared_lane"),
            mapOf("highway" to "residential", "cycleway" to "opposite"),
            mapOf("highway" to "residential", "cycleway:right" to "no"),
            mapOf("highway" to "residential", "cycleway:both" to "separate"),
            mapOf("highway" to "residential", "cycleway:left" to "share_busway"),
            mapOf("highway" to "footway", "bicycle" to "yes"),
            mapOf("highway" to "path", "bicycle" to "permissive"),
        )
        for (t in plain) {
            assertFalse("$t", isBikewayTags(t))
            assertFalse("$t", TestGraphs.single(t).isBikeway(0))
        }
        // Eski bayraklar aynen: bicycle=designated yaya yolunu sürülebilir yapar (bikeOk), oneway okunur.
        val f = wayFlagsFromTags(mapOf("highway" to "footway", "bicycle" to "designated", "oneway" to "yes"), RoadClass.FOOTWAY)
        val mask = WayFlag.BIKE_OK or WayFlag.ONEWAY_FWD or WayFlag.BIKEWAY
        assertEquals(mask, f and mask)
    }

    @Test
    fun `Overpass sorgusu bisiklet yollarını getirir, ham JSON etiketleri grafiğe ulaşır`() {
        val q = overpassQuery(Baku.centralBox)
        assertTrue(Regex("\"highway\"~\"[^\"]*\\|cycleway\\|").containsMatchIn(q))
        assertTrue(q.contains("out body")) // yolların bütün etiketleri (cycleway:*, bicycle) gelir
        val elements = JSONArray()
            .put(JSONObject().put("type", "node").put("id", 1).put("lat", lat(0.0)).put("lon", lon(0.0)))
            .put(JSONObject().put("type", "node").put("id", 2).put("lat", lat(300.0)).put("lon", lon(0.0)))
            .put(
                JSONObject().put("type", "way").put("id", 77).put("nodes", JSONArray(listOf(1, 2)))
                    .put("tags", JSONObject().put("highway", "tertiary").put("cycleway:both", "lane")),
            )
        val raw = OsmRaw().addOverpassJson(JSONObject().put("elements", elements).toString())
        val g = buildRoadGraph(raw) { _, _ -> 0.0 }
        assertTrue(g.isBikeway(TestGraphs.edge(g, 77)))
    }
}
