package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.day.LightSpot
import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `rota ışıkları rotanın 20 m yakınında, 13'ten itibaren ve listenin sonunda`() {
        // Doğu-batı rota, enlem 40.41 boyunca.
        val route = RouteLights(listOf(listOf(GeoPoint(lat, 49.81), GeoPoint(lat, 49.83))))
        assertTrue(route.near(lat + 0.0001, 49.82)) // ~11 m
        assertFalse(route.near(lat + 0.0003, 49.82)) // ~33 m
        assertFalse(route.near(lat, 49.835)) // rotanın ucundan ~420 m ötede
        val onRoute = Poi(lat + 0.0001, 49.82, 'l', "")
        val off = Poi(lat + 0.003, 49.82, 'l', "")
        val metro = Poi(lat, lon, 's', "M")
        val pois = listOf(onRoute, off, metro)
        // 14'te sıradan ışık görünmez (16,5), rota ışığı görünür; sona eklenir.
        assertEquals(listOf(metro, onRoute), PoiLayerRules.select(pois, box, 14.0, route = route))
        assertEquals(listOf(metro), PoiLayerRules.select(pois, box, 14.0))
        // 12,5'te yalnız metro yok (13 altı), rota ışığı da yok (13 altı).
        assertEquals(emptyList<Poi>(), PoiLayerRules.select(pois, box, 12.5, route = route))
        // 17'de rota ışığı iki kez gelmez.
        val at17 = PoiLayerRules.select(pois, box, 17.0, route = route)
        assertEquals(1, at17.count { it === onRoute })
        assertEquals(onRoute, at17.last())
    }

    @Test
    fun `simge seçimi yalnız belirgin kayma ya da yakınlık değişince yenilenir`() {
        // 16 yakınlıkta 360 dp kısa kenar: çeyreği 90 dp ≈ 90·0,0000215° ≈ 0,0019° boylam.
        assertFalse(PoiLayerRules.refreshNeeded(lat, lon, 16.0, lat, lon + 0.0005, 16.1, 360.0, 760.0))
        assertTrue(PoiLayerRules.refreshNeeded(lat, lon, 16.0, lat, lon + 0.003, 16.0, 360.0, 760.0))
        assertTrue(PoiLayerRules.refreshNeeded(lat, lon, 16.0, lat, lon, 16.3, 360.0, 760.0))
        val wide = PoiLayerRules.bounds(lat, lon, 400.0, 800.0, 16.0, margin = PoiLayerRules.SELECT_MARGIN)
        val plain = PoiLayerRules.bounds(lat, lon, 400.0, 800.0, 16.0)
        assertEquals((plain.east - plain.west) * PoiLayerRules.SELECT_MARGIN, wide.east - wide.west, 1e-12)
    }

    @Test
    fun `ışık birleştirme eski karşılaştırmayla aynı sonucu verir`() {
        val rnd = java.util.Random(7)
        fun r(span: Double) = (rnd.nextDouble() - 0.5) * span
        val base = List(400) { Poi(lat + r(0.02), lon + r(0.02), if (it % 3 == 0) 'r' else 'l', "p$it") }
        val spots = List(300) { LightSpot(lat + r(0.02), lon + r(0.02), it % 2 == 0) }
        // Eski (karesel) tanım.
        val hidden = spots.filter { !it.exists }
        val osm = base.filter { it.kind == 'l' }
        fun near(a: Double, b: Double, c: Double, d: Double) = com.kuryelog.logic.map.geo.Geo.haversine(a, b, c, d) <= 25.0
        val kept = osm.filter { l -> hidden.none { near(l.lat, l.lon, it.lat, it.lon) } }
        val added = spots.filter { s -> s.exists && osm.none { near(it.lat, it.lon, s.lat, s.lon) } }
        val expected = base.filter { it.kind != 'l' }.map { it.name } + kept.map { it.name } + added.map { "${it.lat},${it.lon}" }
        val got = PoiLayerRules.withLights(base, spots).map { if (it.name.isEmpty()) "${it.lat},${it.lon}" else it.name }
        assertEquals(expected, got)
    }
}
