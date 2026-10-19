package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.GraphBuilder
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.PoiSet
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `RoadInfoBuilder` (MAP.md 11.8; Dart `_roadInfo`): 1.–7. adımlar; karakterizasyon (eski test yok). */
class RoadInfoBuilderTest {
    private fun builder(
        pois: PoiSet? = null,
        graph: RoadGraph? = null,
        lights: LightsDb = LightsDb(MemoryMapPrefs()),
        sidewalks: SidewalkMarks = SidewalkMarks(MemoryMapPrefs()),
        maxLightM: Double = 5.0,
        accuracy: Double? = 5.0,
    ) = RoadInfoBuilder(pois, graph, lights, sidewalks, maxLightM, accuracy)

    /** Doğu-batı ana yol (way 1, residential) ve kuzey-güney yan yol (way 2) x=0'da kesişir: kavşak (derece 4), 25 m ötede yaya yolu (way 3). */
    private fun cross(): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(-100.0))
        raw.addNode(2, lat(0.0), lon(0.0))
        raw.addNode(3, lat(0.0), lon(100.0))
        raw.addNode(4, lat(-100.0), lon(0.0))
        raw.addNode(5, lat(100.0), lon(0.0))
        raw.addNode(6, lat(30.0), lon(-100.0))
        raw.addNode(7, lat(30.0), lon(100.0))
        raw.addWay(OsmWay(1, listOf(1, 2, 3), TestGraphs.RESIDENTIAL))
        raw.addWay(OsmWay(2, listOf(4, 2, 5), TestGraphs.RESIDENTIAL))
        raw.addWay(OsmWay(3, listOf(6, 7), TestGraphs.FOOTWAY))
        return GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
    }

    @Test
    fun `graf yokken yol sayılır, elle kaldırım yüzey kaldırım ve yolda değil`() {
        val sw = SidewalkMarks(MemoryMapPrefs())
        val none = builder().build(40.4, 49.85)
        assertTrue(none.onRoad); assertEquals(StopSurface.UNKNOWN, none.surface); assertNull(none.known)
        sw.mark(GeoPoint(40.4, 49.85))
        val manual = builder(sidewalks = sw).build(40.4, 49.85)
        assertFalse(manual.onRoad); assertEquals(StopSurface.SIDEWALK, manual.surface); assertTrue(manual.manualSidewalk)
        assertFalse(manual.atLight)
    }

    @Test
    fun `haritadaki ışık 55 m içinde bilinen ışık, öğrenilmiş yok olan yerde ışık değil`() {
        val sig = Poi(40.4 + 4 / 111195.08, 49.85, 'l', "")
        val pois = PoiSet(listOf(sig))
        val info = builder(pois = pois).build(40.4, 49.85)
        assertEquals(true, info.known)
        assertEquals(4.0, info.lightM!!, 0.1)
        assertEquals(sig.lat, info.lightLat!!, 1e-9)
        val lights = LightsDb(MemoryMapPrefs(mapOf(LightsDb.KEY to listOf("${sig.lat},${sig.lon},0,2,0"))))
        val denied = builder(pois = pois, lights = lights).build(40.4, 49.85)
        assertEquals(false, denied.known)
        assertFalse(denied.atLight)
    }

    @Test
    fun `yerel ışık var işareti haritada olmasa da bilinen ışıktır, ayardaki mesafe sınırlar`() {
        val lights = LightsDb(MemoryMapPrefs(mapOf(LightsDb.KEY to listOf("${40.4 + 8 / 111195.08},49.85,1,0,0"))))
        assertEquals(true, builder(lights = lights).build(40.4, 49.85).known)
        assertFalse("8 m, sınır 5 m", builder(lights = lights).build(40.4, 49.85).atLight)
        val wide = builder(lights = lights, maxLightM = 10.0).build(40.4, 49.85)
        assertTrue("sınır 10 m (graf yok: yol sayılır)", wide.atLight)
    }

    @Test
    fun `AVM 80 m içinde mall, uzakta değil`() {
        val mall = PoiSet(listOf(Poi(40.4 + 70 / 111195.08, 49.85, 'm', "AVM")))
        assertTrue(builder(pois = mall).build(40.4, 49.85).mall)
        assertFalse(builder(pois = mall).build(40.4 - 20 / 111195.08, 49.85).mall)
    }

    @Test
    fun `graf varken yol 15 m içinde onRoad, kavşak uzaklığı ve yaya yolu mesafesi`() {
        val g = cross()
        val onJunction = builder(graph = g).build(lat(0.0), lon(3.0))
        assertTrue(onJunction.onRoad)
        assertEquals(StopSurface.ROAD, onJunction.surface)
        assertEquals(3.0, onJunction.junctionM!!, 0.5)
        assertEquals(30.0, onJunction.footM!!, 1.0)
        val far = builder(graph = g).build(lat(-60.0), lon(60.0))
        assertFalse("35 m'de yol yok", far.onRoad)
        assertNull(far.junctionM)
    }

    @Test
    fun `yaya yoluna yakın ve doğruluk iyi ise kaldırım, doğruluk 12 m üstü yüzey bilinmiyor ama yol çizgisine yakınsa onRoad`() {
        val g = cross()
        val onFoot = builder(graph = g).build(lat(29.0), lon(60.0))
        assertEquals(StopSurface.SIDEWALK, onFoot.surface)
        assertFalse(onFoot.onRoad)
        val blurry = builder(graph = g, accuracy = 20.0).build(lat(2.0), lon(60.0))
        assertEquals(StopSurface.UNKNOWN, blurry.surface)
        assertTrue("belirsiz yüzey ışığı tek başına silmez", blurry.onRoad)
        val noFix = builder(graph = g, accuracy = null).build(lat(2.0), lon(60.0))
        assertEquals(StopSurface.UNKNOWN, noFix.surface)
        assertNotNull(noFix.footM)
    }

    @Test
    fun `yol kenarı park şerit dışında mı yarım genişlikten uzak ve doğruluk 8 m altı`() {
        val g = cross()
        val kerb = builder(graph = g, accuracy = 5.0).build(lat(5.0), lon(60.0)) // residential yarım genişlik 4 m, yol 5 m
        assertTrue(kerb.roadside)
        val lane = builder(graph = g, accuracy = 5.0).build(lat(3.0), lon(60.0))
        assertFalse(lane.roadside)
        assertFalse(builder(graph = g, accuracy = 12.0).build(lat(5.0), lon(60.0)).roadside)
    }
}
