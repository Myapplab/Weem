package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Elle kaldırım işareti (MAP.md 11.12): 12 m kuralı, kodek. */
class SidewalkMarksTest {
    private val p = GeoPoint(40.4093, 49.8671)

    @Test
    fun `işaret 12 m içinde kaldırım, işaretin yanına ekleme eskisini siler`() {
        val m = SidewalkMarks()
        m.mark(p, remove = false)
        assertTrue(m.contains(p.lat + 10 / 111195.08, p.lon))
        assertFalse(m.contains(p.lat + 15 / 111195.08, p.lon))
        m.mark(GeoPoint(p.lat + 5 / 111195.08, p.lon), remove = false)
        assertEquals(1, m.all.size)
        m.mark(p, remove = true)
        assertTrue(m.all.isEmpty())
    }

    @Test
    fun `kodek gidiş dönüş, bozuk öğe atlanır, bozuk JSON boş`() {
        val m = SidewalkMarks().also { it.mark(p, false) }
        assertEquals(1, SidewalkMarks.fromJsonString(m.toJsonString()).all.size)
        assertEquals(1, SidewalkMarks.fromJsonString("""[{"lat":40.4,"lon":49.8},{"lat":1},5]""").all.size)
        assertTrue(SidewalkMarks.fromJsonString("{x").all.isEmpty())
    }
}
