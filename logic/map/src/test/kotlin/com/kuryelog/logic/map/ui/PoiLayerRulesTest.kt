package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.day.LightSpot
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.PoiSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y16 yer katmanı seçimi (MAP.md 5.3; Dart `displayPoisProvider` + `PoiLayer`): yakınlık eşikleri, öncelik, 34 dp hücre, 180/55 sınırı. Karakterizasyon. */
class PoiLayerRulesTest {
    private val lat = 40.4
    private val lon = 49.85
    private fun at(m: Double, kind: Char, name: String = "") = Poi(lat + m / 111195.08, lon, kind, name)

    @Test
    fun `ışık yok işareti 25 m içindeki OSM ışığını gizler, ışık var işareti yoksa ekler`() {
        val osm = PoiSet(listOf(at(0.0, 'l'), at(500.0, 'l'), at(10.0, 'r', "Yemek")))
        val shown = PoiLayerRules.displayPois(osm, listOf(LightSpot(lat + 10 / 111195.08, lon, false), LightSpot(lat + 505 / 111195.08, lon, true), LightSpot(lat + 900 / 111195.08, lon, true)))!!
        assertEquals(1, shown.items.count { it.kind == 'r' })
        assertEquals("yok işareti 0 m'deki ışığı gizledi; 500 m ışığı var işaretiyle kaldı; 900 m'e yeni eklendi", 2, shown.items.count { it.kind == 'l' })
        assertNull(PoiLayerRules.displayPois(null, emptyList()))
        assertEquals(1, PoiLayerRules.displayPois(null, listOf(LightSpot(lat, lon, true)))!!.length)
    }

    @Test
    fun `yakınlık 12 altında hiçbir şey, türe göre eşikler`() {
        val all = listOf(at(0.0, 's', "Metro"), at(100.0, 'm', "AVM"), at(200.0, 'l'), at(300.0, 'r', "Yemek"))
        assertTrue(PoiLayerRules.select(all, 11.9, 0.0).markers.isEmpty())
        assertEquals(listOf('s'), PoiLayerRules.select(all, 13.0, 0.0).markers.map { it.poi.kind })
        assertEquals(listOf('s', 'm'), PoiLayerRules.select(all, 15.0, 0.0).markers.map { it.poi.kind })
        assertEquals(listOf('l', 's', 'm', 'r'), PoiLayerRules.select(all, 16.5, 0.0).markers.map { it.poi.kind }.let { k -> k.sortedBy { PoiZoomRules.priority(it) } })
        assertEquals("ışık → metro → AVM → yemek sırasıyla seçilir", listOf('l', 's', 'm', 'r'), PoiLayerRules.select(all, 16.5, 0.0).markers.map { it.poi.kind })
        assertEquals(listOf(16, 30, 32, 30), PoiLayerRules.select(all, 16.5, 0.0).markers.map { it.sizeDp })
    }

    @Test
    fun `yemek simgeleri 34 dp hücrede tek, ad eşikleri ve çakışan ad atlanır`() {
        val twins = listOf(at(0.0, 'r', "A"), at(0.5, 'f', "B"), at(400.0, 'c', "C"))
        val sel = PoiLayerRules.select(twins, 17.6, 0.0)
        assertEquals(listOf("A", "C"), sel.markers.map { it.poi.name })
        assertEquals(listOf("A", "C"), sel.labels.map { it.poi.name })
        assertEquals(listOf(32, 32), sel.labels.map { it.offsetDp })
        assertTrue(PoiLayerRules.select(twins, 17.5, 0.0).labels.isEmpty())
        val sameCellFar = PoiLayerRules.select(listOf(at(0.0, 'm', "AVM1"), at(0.0 + 0.2, 'm', "AVM2")), 17.0, 0.0)
        assertEquals("AVM'ler hücreye bağlı değil: iki simge, ikinci ad çakışır", listOf(2, 1), listOf(sameCellFar.markers.size, sameCellFar.labels.size))
        val noName = PoiLayerRules.select(listOf(at(0.0, 'l')), 19.0, 0.0)
        assertTrue(noName.labels.isEmpty())
    }

    @Test
    fun `en çok 180 simge ve 55 ad`() {
        val many = (0 until 400).map { i -> Poi(lat + (i / 20) * 0.001, lon + (i % 20) * 0.001, 'm', "AVM $i") }
        val sel = PoiLayerRules.select(many, 17.0, 0.0)
        assertEquals(PoiZoomRules.MAX_MARKERS, sel.markers.size)
        assertTrue(sel.labels.size <= PoiZoomRules.MAX_LABELS)
        val rows = (0 until 400).map { i -> Poi(lat + (i / 20) * 0.01, lon + (i % 20) * 0.01, 'm', "AVM $i") }
        assertEquals(PoiZoomRules.MAX_LABELS, PoiLayerRules.select(rows, 17.0, 0.0).labels.size)
    }

    @Test
    fun `görünür kutu yakınlaştıkça küçülür ve ekranın uzun kenarını kapsar`() {
        val c = com.kuryelog.logic.map.geo.GeoPoint(lat, lon)
        val z17 = PoiLayerRules.visibleBox(c, 17.0, 360.0, 800.0)
        val z16 = PoiLayerRules.visibleBox(c, 16.0, 360.0, 800.0)
        assertEquals("zoom 17: 400 dp · 0,91 m/dp ≈ 364 m yarı kenar (enlem 40,4)", 364.0, (z17.north - c.lat) * 111320.0, 5.0)
        assertEquals("bir zoom aşağı: kutu iki kat", 2.0, (z16.north - c.lat) / (z17.north - c.lat), 1e-6)
        assertTrue(z17.contains(lat + 0.001, lon + 0.001))
        assertTrue(!z17.contains(lat + 0.01, lon))
    }
}
