package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `sidewalk.zones.v1` (MAP.md 11.12): 12 m kuralı, bozuk öğe atlanır, bozuk JSON → boş. */
class SidewalkMarksTest {
    private val p = GeoPoint(40.4, 49.85)

    @Test
    fun `işaret 12 m içinde, ekleme yakındakileri siler, kaldırma yalnız siler`() {
        val prefs = MemoryMapPrefs()
        val m = SidewalkMarks(prefs)
        m.mark(p)
        assertTrue(m.near(p.lat + 11 / 111195.0, p.lon))
        assertFalse(m.near(p.lat + 13 / 111195.0, p.lon))
        m.mark(GeoPoint(p.lat + 5 / 111195.0, p.lon)) // 12 m içi: eskisi gider
        assertEquals(1, m.all.size)
        assertEquals(1, SidewalkMarks(prefs).all.size)
        m.mark(p, remove = true)
        assertTrue(SidewalkMarks(prefs).all.isEmpty())
    }

    @Test
    fun `bozuk öğe atlanır ve bozuk JSON boş liste verir`() {
        val ok = SidewalkMarks(MemoryMapPrefs(mapOf(SidewalkMarks.KEY to """[{"lat":40.4,"lon":49.85},{"lat":"x"},7]""")))
        assertEquals(1, ok.all.size)
        assertTrue(SidewalkMarks(MemoryMapPrefs(mapOf(SidewalkMarks.KEY to "{bozuk"))).all.isEmpty())
    }
}
