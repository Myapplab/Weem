package com.kuryelog.logic.map.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `test/overpass_test.dart` (3 test); `MAP.md` 2.13. */
class OverpassProblemTest {
    private fun b(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test
    fun `düzgün cevap kabul edilir`() {
        assertNull(overpassProblem(b("""{"version":0.6,"elements":[]}""")))
    }

    @Test
    fun `sondaki runtime error notu yakalanır (eksik veri)`() {
        val body = "{\"version\":0.6,\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":40.4,\"lon\":49.8}],\n" +
            "\"remark\": \"runtime error: Query timed out in \\\"query\\\" at line 3 after 91 seconds.\"}"
        assertTrue(overpassProblem(b(body))!!.contains("runtime error"))
    }

    @Test
    fun `JSON olmayan ve boş cevap reddedilir`() {
        assertEquals("JSON değil", overpassProblem(b("""<?xml version="1.0"?><osm></osm>""")))
        assertEquals("boş cevap", overpassProblem(ByteArray(0)))
    }
}
