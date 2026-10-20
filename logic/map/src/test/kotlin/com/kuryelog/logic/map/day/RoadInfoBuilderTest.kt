package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.graph.Poi
import com.kuryelog.logic.map.graph.PoiSet
import com.kuryelog.logic.map.route.TestGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yol bilgisi (MAP.md 11.8 "Yeni"): OSM ışığı + öğrenilmiş "yok", AVM 80 m, kaldırım 12 m, graf yokken, kötü GPS yüzeyi. */
class RoadInfoBuilderTest {
    private val lat = 40.4093
    private val lon = 49.8671
    private fun north(m: Double) = lat + m / 111195.08

    private fun builder(pois: List<Poi> = emptyList(), lights: LightsDb = LightsDb(), marks: SidewalkMarks = SidewalkMarks(), max: Double = 5.0) =
        RoadInfoBuilder({ PoiSet(pois) }, { null }, lights, marks, { max })

    @Test
    fun `OSM ışığı 55 m içinde, uzaklık ve bilinen ışık`() {
        val b = builder(listOf(Poi(north(3.0), lon, 'l', "")))
        val i = b.at(lat, lon, 5.0)
        assertEquals(true, i.known)
        assertEquals(3.0, i.lightM!!, 0.2)
        assertEquals(5.0, i.maxLightM, 0.0)
        assertNull(builder().at(lat, lon, 5.0).known)
    }

    @Test
    fun `öğrenilmiş ışık yok OSM ışığını bastırır, öğrenilmiş ışık var yerini verir`() {
        val no = LightsDb(listOf("$lat,$lon,0,2,0"))
        assertEquals(false, builder(listOf(Poi(north(3.0), lon, 'l', "")), no).at(lat, lon, 5.0).known)
        val yes = LightsDb(listOf("${north(10.0)},$lon,2,0,40"))
        val i = builder(lights = yes).at(lat, lon, 5.0)
        assertEquals(true, i.known)
        assertEquals(10.0, i.lightM!!, 0.3)
    }

    @Test
    fun `AVM 80 m ve elle kaldırım 12 m, graf yokken yüzey`() {
        val mall = builder(listOf(Poi(north(70.0), lon, 'm', "AVM"))).at(lat, lon, 5.0)
        assertTrue(mall.mall)
        assertFalse(builder(listOf(Poi(north(90.0), lon, 'm', "AVM"))).at(lat, lon, 5.0).mall)
        val marks = SidewalkMarks().also { it.mark(com.kuryelog.logic.map.geo.GeoPoint(north(8.0), lon), false) }
        val i = builder(marks = marks).at(lat, lon, 30.0)
        assertTrue(i.manualSidewalk)
        assertEquals(StopSurface.SIDEWALK, i.surface)
        assertFalse(i.onRoad)
        assertTrue(builder().at(lat, lon, 30.0).onRoad)
    }

    @Test
    fun `graf varken yol ve kavşak uzaklığı, belirsiz GPS yüzeyi unknown yapar ama yol kalır`() {
        val g = TestGraphs.parallel()
        val p = g.geometry(TestGraphs.edgeOf(g, 302)).let { it[it.size / 2] }
        val b = RoadInfoBuilder({ null }, { g }, LightsDb(), SidewalkMarks())
        val good = b.at(p.lat, p.lon, 5.0)
        assertTrue(good.onRoad)
        assertEquals(StopSurface.ROAD, good.surface)
        val bad = b.at(p.lat, p.lon, 20.0)
        assertTrue("kötü GPS yüzeyi bilinmez yapar ama yol çizgisi ≤ 15 m", bad.onRoad)
        assertEquals(StopSurface.UNKNOWN, bad.surface)
    }
}
