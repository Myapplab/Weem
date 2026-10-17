package com.kuryelog.logic.core

// Rawstats RedactorParityTest.kt @ 6388c04 (4 test), aynen; vektörler economics/redact_vectors.json (= docs/ekonomi-v2/vektorler/).

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Redactor must equal docs/ekonomi-v2/redact.py exactly (economics/redact_vectors.json, secret "test-secret"). */
class RedactorParityTest {
    private val vectors: JSONObject = JSONObject(
        (javaClass.classLoader!!.getResourceAsStream("economics/redact_vectors.json") ?: error("economics/redact_vectors.json missing"))
            .use { it.readBytes().toString(Charsets.UTF_8) })
    private val r = Redactor(vectors.getString("secret").toByteArray(Charsets.UTF_8))
    private fun JSONArray.list() = (0 until length()).map { getString(it) }

    @Test fun linesEqualPythonForEveryRow() {
        val rows = vectors.getJSONArray("rows")
        assertTrue(rows.length() >= 10)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            assertEquals("row $i", row.getJSONArray("out").list(), r.lines(row.getString("pkg"), row.getJSONArray("lines").list()))
        }
    }

    @Test fun freeEqualsPythonForEveryText() {
        val free = vectors.getJSONObject("free")
        val keys = free.keys()
        var n = 0
        while (keys.hasNext()) { val k = keys.next(); assertEquals(k, free.getString(k), r.free(k)); n++ }
        assertTrue(n >= 10)
    }

    @Test fun pseudonymsDependOnTheSecret() {
        val other = Redactor("another-secret".toByteArray(Charsets.UTF_8))
        assertNotEquals(r.free("Order #408 ready"), other.free("Order #408 ready"))
        assertEquals(r.free("Order #408 ready"), Redactor("test-secret".toByteArray(Charsets.UTF_8)).free("Order #408 ready"))
        // Shape is kept: digits stay digits, letter case stays.
        assertTrue(Regex("#[A-Z][0-9][A-Z][0-9][A-Z]").matches(r.free("#A1B2C")))
    }

    @Test fun nonAsciiWhitespaceAndCaseFollowPython() {
        assertEquals("tel[tel]", r.free("tel 055 123 45 67"))
        assertEquals("[code]", r.free("КОД ДВЕРИ 7788"))
        assertEquals("ünvan", Redactor.norm("  Ünvan  "))
        assertEquals("#408ş", r.free("#408ş"))
    }
}
