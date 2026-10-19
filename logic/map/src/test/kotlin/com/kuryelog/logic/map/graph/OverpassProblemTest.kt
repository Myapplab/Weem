package com.kuryelog.logic.map.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `overpass_test.dart` (3 test; A6.2a, MAP.md 2.13). Uygulama: A6.3. */
class OverpassProblemTest {
    private fun b(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test
    fun `düzgün cevap kabul edilir`() {
        assertNull(Overpass.problem(b("""{"version":0.6,"elements":[]}""")))
    }

    @Test
    fun `sondaki runtime error notu yakalanır (eksik veri)`() {
        val body = "{\"version\":0.6,\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":40.4,\"lon\":49.8}],\n" +
            "\"remark\": \"runtime error: Query timed out in \\\"query\\\" at line 3 after 91 seconds.\"}"
        assertTrue(Overpass.problem(b(body))!!.contains("runtime error"))
    }

    @Test
    fun `JSON olmayan ve boş cevap reddedilir`() {
        assertEquals("JSON değil", Overpass.problem(b("""<?xml version="1.0"?><osm></osm>""")))
        assertEquals("boş cevap", Overpass.problem(ByteArray(0)))
    }
}
