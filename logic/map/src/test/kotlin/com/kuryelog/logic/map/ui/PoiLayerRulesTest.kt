package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.day.LightSpot
import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.graph.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yer katmanı seçimi (MAP.md 5.3 "Yeni"): yakınlık eşikleri, öncelik, 34 dp hücre, 180 sınırı, ışık işaretleri. */
class PoiLayerRulesTest {
    private val box = BBox(40.40, 49.80, 40.42, 49.84)
    private val lat = 40.41
    private val lon = 49.82

    @Test
    fun `12 altında yok, tür yakınlık eşikleri`() {
        val pois = listOf(Poi(lat, lon, 's', "M"), Poi(lat, lon + 0.001, 'm', "AVM"), Poi(lat, lon + 0.002, 'r', "R"), Poi(lat, lon + 0.003, 'l', ""))
        assertTrue(PoiLayerRules.select(pois, box, 11.9).isEmpty())
        assertEquals(listOf('s'), PoiLayerRules.select(pois, box, 14.0).map { it.kind })
        assertEquals(listOf('s', 'm'), PoiLayerRules.select(pois, box, 15.5).map { it.kind })
        assertEquals(listOf('l', 's', 'm', 'r'), PoiLayerRules.select(pois, box, 17.0).map { it.kind }) // öncelik: ışık, metro, AVM, öteki
    }

    @Test
    fun `yemek noktaları 34 dp hücrede bir, ışıklar hepsi, kutu dışı yok`() {
        val near = listOf(Poi(lat, lon, 'r', "A"), Poi(lat, lon, 'f', "B"), Poi(lat, lon + 0.01, 'c', "Uzak"), Poi(50.0, 50.0, 'r', "Dışarıda"))
        val out = PoiLayerRules.select(near, box, 17.0)
        assertEquals(listOf("A", "Uzak"), out.map { it.name })
        val lights = listOf(Poi(lat, lon, 'l', ""), Poi(lat + 0.00001, lon + 0.00001, 'l', ""))
        assertEquals(2, PoiLayerRules.select(lights, box, 17.0).size)
    }

    @Test
    fun `en çok 180 simge`() {
        val many = (0 until 400).map { Poi(40.40 + it * 0.00004, 49.81, 'l', "") }
        assertEquals(PoiZoomRules.MAX_MARKERS, PoiLayerRules.select(many, box, 18.0).size)
    }

    @Test
    fun `yok işareti OSM ışığını gizler, var işareti ışık yoksa ekler`() {
        val osm = listOf(Poi(lat, lon, 'l', ""), Poi(lat + 0.01, lon, 'l', ""), Poi(lat, lon, 'r', "R"))
        val hidden = PoiLayerRules.withLights(osm, listOf(LightSpot(lat + 0.00005, lon, false)))
        assertEquals(1, hidden.count { it.kind == 'l' })
        val added = PoiLayerRules.withLights(osm, listOf(LightSpot(lat + 0.02, lon, true), LightSpot(lat, lon + 0.00005, true)))
        assertEquals(3, added.count { it.kind == 'l' }) // yakındaki var işareti yeni ışık eklemez
        assertEquals(1, added.count { it.kind == 'r' })
    }

    @Test
    fun `bounds merkez çevresinde ve yakınlaştıkça küçülür`() {
        val a = PoiLayerRules.bounds(lat, lon, 400.0, 800.0, 16.0)
        val b = PoiLayerRules.bounds(lat, lon, 400.0, 800.0, 17.0)
        assertTrue(a.contains(lat, lon))
        assertTrue(b.east - b.west < a.east - a.west)
    }
}
