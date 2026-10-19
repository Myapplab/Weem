package com.kuryelog.logic.map.graph

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `test/pois_test.dart` (4 test); `MAP.md` 5.6. */
class PoiSetTest {
    private fun node(id: Int, lat: Double, lon: Double, vararg tags: Pair<String, String>) =
        JSONObject().put("type", "node").put("id", id).put("lat", lat).put("lon", lon).put("tags", JSONObject(mapOf(*tags)))

    @Test
    fun `Overpass - yemek noktaları (düğüm ve yapı merkezi, hastane yok)`() {
        val way = JSONObject().put("type", "way").put("id", 2).put("center", JSONObject().put("lat", 40.41).put("lon", 49.86))
            .put("tags", JSONObject(mapOf("amenity" to "fast_food", "name" to "Doner", "name:az" to "Dönər")))
        val json = JSONObject().put(
            "elements",
            JSONArray()
                .put(node(1, 40.40, 49.85, "amenity" to "restaurant", "name" to "Firuze"))
                .put(way)
                .put(node(3, 40.42, 49.87, "amenity" to "hospital", "name" to "X")),
        )
        val pois = poisFromOverpass(json.toString())
        assertEquals(2, pois.size)
        assertEquals("f", pois[1].kind)
        assertEquals("Dönər", pois[1].name)
        assertEquals(40.41, pois[1].lat, 1e-9)
    }

    @Test
    fun `görünür alan süzgeci ve kodlama`() {
        val set = PoiSet(
            listOf(
                Poi(40.50, 49.85, "r", "D"),
                Poi(40.30, 49.80, "r", "A"),
                Poi(40.40, 49.85, "c", "B"),
                Poi(40.41, 49.99, "f", "C"),
            ),
        )
        assertEquals(listOf("B"), set.within(40.35, 49.80, 40.45, 49.90).map { it.name })
        val again = PoiSet.decode(set.encode())
        assertEquals(4, again.length)
        assertEquals("A", again.items.first().name)
    }

    @Test
    fun `eksik isimde marka, sinyalli yaya geçidi ve bakkal alınır`() {
        val json = JSONObject().put(
            "elements",
            JSONArray()
                .put(node(10, 40.4, 49.8, "shop" to "convenience", "name:az" to " ", "brand" to "Araz"))
                .put(node(11, 40.41, 49.81, "highway" to "crossing", "crossing:signals" to "yes")),
        )
        val pois = poisFromOverpass(json.toString())
        assertEquals("t", pois.first().kind)
        assertEquals("Araz", pois.first().name)
        assertEquals("node/10", pois.first().osmRef)
        assertEquals("l", pois.last().kind)
        val again = PoiSet.decode(PoiSet(pois).encode())
        assertTrue(again.items.map { it.osmRef }.contains("node/10"))
    }

    @Test
    fun `eski dört alanlı yer verisi okunur`() {
        val bytes = """{"v":1,"pois":[[40.4,49.8,"r","Eski"]]}""".toByteArray(Charsets.UTF_8)
        val set = PoiSet.decode(bytes)
        assertEquals("Eski", set.items.single().name)
        assertEquals("", set.items.single().osmRef)
    }
}
