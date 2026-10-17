package com.kuryelog.logic.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * CORE.md 5.3 (RS `LogImporter.kt:10-66`): Rawstats ZIP yedeği WEEM `.dev`'e alınır (A2.6). Önce hepsi denetlenir; biri geçersizse
 * hiçbir kayıt değişmez. Rawstats'ta bu sınıfın testi yoktu (Android'e bağlıydı); WEEM'de saf.
 */
class LogImporterTest {
    @get:Rule val temp = TemporaryFolder()
    private val files by lazy { temp.newFolder("files") }
    private val logs by lazy { File(files, "logs").apply { mkdirs() } }
    private val cache by lazy { temp.newFolder("cache") }

    private fun zip(vararg entries: Pair<String, String>, dirs: List<String> = emptyList()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for (d in dirs) { z.putNextEntry(ZipEntry(d)); z.closeEntry() }
            for ((name, text) in entries) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
        }
        return out.toByteArray()
    }
    /** ZipOutputStream refuses a repeated name; a crafted file can still carry one (same-length name patched in the bytes). */
    private fun duplicate(): ByteArray {
        val b = zip("2026-10-02/orders.jsonl" to "{}\n", "2026-10-02/orderX.jsonl" to "{}\n")
        val text = String(b, Charsets.ISO_8859_1).replace("2026-10-02/orderX.jsonl", "2026-10-02/orders.jsonl")
        return text.toByteArray(Charsets.ISO_8859_1)
    }
    private fun restore(bytes: ByteArray) = LogImporter.restore(ByteArrayInputStream(bytes), files, logs, cache)
    private fun snapshot(): Map<String, String> = files.walkTopDown().filter { it.isFile }.associate { it.relativeTo(files).path to it.readText() }

    @Test fun aRawstatsBackupMergesIntoTheDayFoldersWithoutDuplicates() {
        File(logs, "2026-10-02").mkdirs()
        File(logs, "2026-10-02/orders.jsonl").writeText("{\"id\":\"a\"}\n")
        val names = restore(zip(
            "2026-10-02/orders.jsonl" to "{\"id\":\"a\"}\n{\"id\":\"b\"}\n\n",
            "2026-10-03/gps.csv" to "${LogImporter.GPS_HEADER}\n2026-10-03T10:00:00.000+0400,40.1,49.8,5.0,1.0,0.0,10.0\n",
            "2026-10-03/economics-offers.jsonl" to "{}\n",
            dirs = listOf("2026-10-03/"),
        ))
        assertEquals(listOf("2026-10-02/orders.jsonl", "2026-10-03/gps.csv", "2026-10-03/economics-offers.jsonl"), names)
        assertEquals("{\"id\":\"a\"}\n{\"id\":\"b\"}\n", File(logs, "2026-10-02/orders.jsonl").readText())
        assertTrue(File(logs, "2026-10-03/gps.csv").readText().startsWith(LogImporter.GPS_HEADER + "\n"))
        assertEquals(listOf("2026-10-03", "2026-10-02"), DailyRecords(logs).days().map { it.date })
        // Importing the same export again changes nothing.
        val before = snapshot()
        restore(zip("2026-10-02/orders.jsonl" to "{\"id\":\"b\"}\n"))
        assertEquals(before, snapshot())
        assertTrue("staging is removed", cache.listFiles()!!.isEmpty())
        assertFalse(logs.walkTopDown().any { it.name.endsWith(".restore") })
    }

    @Test fun restaurantLearningKeepsTheLargerCountPerKey() {
        File(files, "restaurants.json").writeText("""{"a":{"n":5,"v":"old"},"b":{"n":1}}""")
        restore(zip("restaurants.json" to """{"a":{"n":3,"v":"in"},"b":{"n":2,"v":"in"},"c":{"n":1},"d":7}"""))
        val j = JSONObject(File(files, "restaurants.json").readText())
        assertEquals("old", j.getJSONObject("a").getString("v"))
        assertEquals("in", j.getJSONObject("b").getString("v"))
        assertEquals(1, j.getJSONObject("c").getInt("n"))
        assertFalse("not an object", j.has("d"))
    }

    @Test fun anyInvalidEntryLeavesEveryRecordUntouched() {
        File(logs, "2026-10-02").mkdirs()
        File(logs, "2026-10-02/orders.jsonl").writeText("{\"id\":\"a\"}\n")
        File(files, "restaurants.json").writeText("{}")
        val before = snapshot()
        val good = "2026-10-02/orders.jsonl" to "{\"id\":\"z\"}\n"
        val bad = listOf(
            zip(good, "2026-10-02/notes.txt" to "x"),                     // not a log name
            zip(good, "../2026-10-02/orders.jsonl" to "{}"),              // traversal
            zip(good, "/2026-10-02/orders.jsonl" to "{}"),
            zip(good, "2026-10-02\\orders.jsonl" to "{}"),
            zip(good, "2026-02-30/orders.jsonl" to "{}"),                 // no such date
            zip(good, "2026-10-02/events.jsonl" to "{}\nnot json\n"),     // a broken line
            zip(good, "2026-10-02/events.jsonl" to "[1]\n"),              // not an object
            zip(good, "restaurants.json" to "[]"),
            zip(good, "2026-10-02/gps.csv" to "t,lat,lon\n"),             // wrong GPS header
            duplicate(),                                                  // the same name twice
            zip(),                                                        // empty
            zip(dirs = listOf("2026-10-02/")),                            // only a folder
        )
        for ((i, b) in bad.withIndex()) {
            assertThrows("case $i", Exception::class.java) { restore(b) }
            assertEquals("case $i", before, snapshot())
        }
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun tooManyFilesAreRejected() {
        val entries = (0 until LogImporter.MAX_FILES + 1).map { i ->
            "%04d-01-01/orders.jsonl".format(2000 + i % 2000).let { if (i >= 2000) "2026-10-02/events.jsonl" else it } to "{}\n"
        }.toTypedArray()
        val e = assertThrows(IllegalArgumentException::class.java) { restore(zip(*entries)) }
        assertEquals("Backup has too many files", e.message)
        assertTrue(logs.listFiles()!!.isEmpty())
    }

    @Test fun limitsAreRawstats() {
        assertEquals(2000, LogImporter.MAX_FILES)
        assertEquals(32L * 1024 * 1024, LogImporter.MAX_FILE_BYTES)
        assertEquals(256L * 1024 * 1024, LogImporter.MAX_TOTAL_BYTES)
        assertEquals("t,lat,lon,acc_m,speed_ms,bearing,alt_m", LogImporter.GPS_HEADER)
    }
}
