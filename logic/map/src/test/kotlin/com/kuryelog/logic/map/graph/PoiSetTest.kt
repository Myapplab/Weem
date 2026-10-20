package com.kuryelog.logic.map.graph

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `pois_test.dart` (4 test; A6.2a, MAP.md 5.6). `kind` tek harf: `Char`. Uygulama: A6.8. */
class PoiSetTest {
    @Test
    fun `Overpass → yemek noktaları (düğüm ve yapı merkezi, hastane yok)`() {
        val pois = Pois.fromOverpass(JSONObject("""{"elements":[
            {"type":"node","id":1,"lat":40.40,"lon":49.85,"tags":{"amenity":"restaurant","name":"Firuze"}},
            {"type":"way","id":2,"center":{"lat":40.41,"lon":49.86},
             "tags":{"amenity":"fast_food","name":"Doner","name:az":"Dönər"}},
            {"type":"node","id":3,"lat":40.42,"lon":49.87,"tags":{"amenity":"hospital","name":"X"}}]}"""))
        assertEquals(2, pois.size)
        assertEquals('f', pois[1].kind)
        assertEquals("Dönər", pois[1].name)
        assertEquals(40.41, pois[1].lat, 1e-9)
    }

    @Test
    fun `görünür alan süzgeci ve kodlama`() {
        val set = PoiSet(listOf(
            Poi(40.50, 49.85, 'r', "D"),
            Poi(40.30, 49.80, 'r', "A"),
            Poi(40.40, 49.85, 'c', "B"),
            Poi(40.41, 49.99, 'f', "C"),
        ))
        assertEquals(listOf("B"), set.within(40.35, 49.80, 40.45, 49.90).map { it.name })
        val again = PoiSet.decode(set.encode())
        assertEquals(4, again.length)
        assertEquals("A", again.items.first().name)
    }

    @Test
    fun `eksik isimde marka, sinyalli yaya geçidi ve bakkal alınır`() {
        val pois = Pois.fromOverpass(JSONObject("""{"elements":[
            {"type":"node","id":10,"lat":40.4,"lon":49.8,
             "tags":{"shop":"convenience","name:az":" ","brand":"Araz"}},
            {"type":"node","id":11,"lat":40.41,"lon":49.81,
             "tags":{"highway":"crossing","crossing:signals":"yes"}}]}"""))
        assertEquals('t', pois.first().kind)
        assertEquals("Araz", pois.first().name)
        assertEquals("node/10", pois.first().osmRef)
        assertEquals('l', pois.last().kind)
        val again = PoiSet.decode(PoiSet(pois).encode())
        assertTrue(again.items.map { it.osmRef }.contains("node/10"))
    }

    @Test
    fun `eski dört alanlı yer verisi okunur`() {
        val bytes = """{"v":1,"pois":[[40.4,49.8,"r","Eski"]]}""".toByteArray(Charsets.UTF_8)
        val set = PoiSet.decode(bytes)
        assertEquals("Eski", set.items.single().name)
        assertTrue(set.items.single().osmRef.isEmpty())
    }
}
