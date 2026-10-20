package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset

/**
 * A7.1 (MAP.md 12.17): YolGo `duzyol-journal-v1` dışa aktarmasını içe aktarma. YolGo'da karşılığı yok (içe aktarma WEEM'de yeni); girdiler
 * `log_store.dart:67-87` biçiminde elle kurulur (Dart `jsonEncode` öğe sırası `name, day, kind, content`).
 */
class JournalImportTest {
    private lateinit var dir: File
    private lateinit var tmp: File
    private val zone = ZoneOffset.ofHours(4)

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("journal-import-").toFile()
        dir = File(tmp, "logs").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun item(name: String, content: Any?, day: String = name.take(10), kind: String = "day") =
        JSONObject().put("name", name).put("day", day).put("kind", kind).put("content", content ?: JSONObject.NULL).toString()

    private fun journal(vararg items: String, day: String? = null) =
        "{\"format\":\"duzyol-journal-v1\",\"day\":${if (day == null) "null" else JSONObject.quote(day)},\"files\":[${items.joinToString(",")}]}"

    private fun import(text: String, skip: (String) -> Boolean = { false }) = JournalImport.import(StringReader(text), dir, skip)

    private fun names() = dir.list()!!.sorted()

    @Test
    fun `YolGo dışa aktarması ham metniyle aynı adlarla kayıt klasörüne yazılır`() {
        val day = "{\"t\":1,\"k\":\"ev\",\"e\":\"day_start\"}\n{\"t\":2,\"k\":\"gps\",\"lat\":40.4,\"lon\":49.8}\n"
        val report = "{\n  \"street\": \"Nizami küç. \\\"A\\\" — ünlü/ə\",\n  \"emoji\": \"🛴\"\n}"
        val ride = "{\"k\":\"ev\",\"e\":\"ride_start\",\"reason\":\"route\"}\n"
        val route = "{\"format\":\"duzyol-route-v1\",\"track\":[[1,40.1,49.1,5.0]]}"
        val text = journal(
            item("2026-10-02.jsonl", day),
            item("2026-10-02_1030.gun.json", report, kind = "report"),
            item("2026-10-02_ride_1790900000000.jsonl", ride, kind = "ride"),
            item("2026-10-01_route_1790800000000.json", route, kind = "route"),
            item("2026-09-30.gun.json", "{}", kind = "report"),
        )
        val r = import(text)
        assertEquals(5, r.added.size)
        assertEquals(0, r.existing)
        assertEquals(0, r.invalid)
        assertEquals(listOf("2026-09-30", "2026-10-01", "2026-10-02"), r.days)
        assertEquals(day, File(dir, "2026-10-02.jsonl").readText())
        assertEquals(report, File(dir, "2026-10-02_1030.gun.json").readText())
        assertEquals(route, File(dir, "2026-10-01_route_1790800000000.json").readText())
        val kinds = RideLogStore(dir, zone).entries().associate { it.name to it.kind }
        assertEquals("ride", kinds["2026-10-02_ride_1790900000000.jsonl"])
        assertEquals("route", kinds["2026-10-01_route_1790800000000.json"])
        assertEquals("report", kinds["2026-09-30.gun.json"])
        assertEquals(names(), kinds.keys.sorted())
    }

    @Test
    fun `kaçışlı Unicode, vekil çiftler ve denetim karakterleri aynen çözülür`() {
        val text = "{\"format\":\"duzyol-journal-v1\",\"day\":\"2026-10-02\",\"files\":[{\"name\":\"2026-10-02.jsonl\",\"day\":\"2026-10-02\",\"kind\":\"day\"," +
            "\"content\":\"a\\u0259\\ud83d\\udef4\\t\\/\\b\\f\\r\\n\\\\ \\\"q\\\"\"}]}"
        import(text)
        assertEquals("aə🛴\t/\b\u000C\r\n\\ \"q\"", File(dir, "2026-10-02.jsonl").readText(Charsets.UTF_8))
    }

    @Test
    fun `var olan kayıt ezilmez, eksikler eklenir`() {
        File(dir, "2026-10-02.jsonl").writeText("WEEM")
        val r = import(journal(item("2026-10-02.jsonl", "YolGo"), item("2026-10-01.jsonl", "eski")))
        assertEquals(listOf("2026-10-01.jsonl"), r.added)
        assertEquals(1, r.existing)
        assertEquals("WEEM", File(dir, "2026-10-02.jsonl").readText())
        assertEquals("eski", File(dir, "2026-10-01.jsonl").readText())
        val again = import(journal(item("2026-10-02.jsonl", "YolGo"), item("2026-10-01.jsonl", "eski")))
        assertEquals(0, again.added.size)
        assertEquals(2, again.existing)
    }

    @Test
    fun `kalıba uymayan ad, yol kaçışı ve metin olmayan içerik geçersiz sayılır`() {
        val text = journal(
            item("../2026-10-02.jsonl", "x"),
            item("2026-10-02.jsonl/../../evil.jsonl", "x"),
            item("private.txt", "x"),
            item("2026-10-02.jsonl", null),
            "{\"name\":\"2026-10-03.jsonl\",\"content\":42}",
            "{\"name\":\"2026-10-04.jsonl\"}",
            "\"2026-10-05.jsonl\"",
            "[1,2]",
            item("2026-10-06.jsonl", "ok"),
        )
        val r = import(text)
        assertEquals(listOf("2026-10-06.jsonl"), r.added)
        assertEquals(8, r.invalid)
        assertEquals(listOf("2026-10-06.jsonl"), names())
        assertEquals(listOf("logs"), tmp.list()!!.toList())
    }

    @Test
    fun `aynı ad dosyada iki kez geçerse ilki alınır`() {
        val r = import(journal(item("2026-10-02.jsonl", "ilk"), item("2026-10-02.jsonl", "ikinci")))
        assertEquals(listOf("2026-10-02.jsonl"), r.added)
        assertEquals(1, r.existing)
        assertEquals("ilk", File(dir, "2026-10-02.jsonl").readText())
    }

    @Test
    fun `yanlış biçim ya da yarım dosyada klasöre hiçbir şey yazılmaz`() {
        val good = item("2026-10-02.jsonl", "x")
        val bad = listOf(
            "{\"format\":\"duzyol-route-v1\",\"files\":[$good]}",
            "{\"files\":[$good]}",
            journal(good).dropLast(3),
            "[$good]",
            "",
            "{\"format\":\"duzyol-journal-v1\",\"files\":{}}",
            "PK\u0003\u0004",
        )
        for (text in bad) {
            assertThrows(text, JournalFormatException::class.java) { import(text) }
            assertTrue(text, names().isEmpty())
        }
    }

    @Test
    fun `alan sırası ve bilinmeyen alanlar fark etmez`() {
        val text = "{ \"extra\": {\"a\": [1, -2.5e3, true, false, null, {\"b\": \"}\"}]}, \"files\" : [ " +
            "{\"content\":\"c\",\"meta\":[null],\"name\":\"2026-10-02_ride_5.jsonl\"} ], \"day\": null, \"format\": \"duzyol-journal-v1\" }\n"
        val r = import(text)
        assertEquals(listOf("2026-10-02_ride_5.jsonl"), r.added)
        assertEquals("c", File(dir, "2026-10-02_ride_5.jsonl").readText())
    }

    @Test
    fun `boş dosya listesi hiçbir şey eklemez`() {
        val r = import(journal())
        assertEquals(0, r.added.size + r.existing + r.invalid)
        assertTrue(names().isEmpty())
    }

    @Test
    fun `yarım kalmış eski içe aktarma klasörü temizlenir`() {
        File(dir, ".import-1/2026-10-02.jsonl").apply { parentFile.mkdirs() }.writeText("yarım")
        import(journal(item("2026-10-01.jsonl", "x")))
        assertEquals(listOf("2026-10-01.jsonl"), names())
    }

    @Test
    fun `WEEM dışa aktarması içe aktarılınca aynı kayıtlar geri gelir`() {
        val src = File(tmp, "src").apply { mkdirs() }
        File(src, "2026-10-02.jsonl").writeText("{\"e\":\"ə\"}\n")
        File(src, "2026-10-02_0930.gun.json").writeText("{\"x\":\"\\\"\"}")
        File(src, "2026-10-02_route_7.json").writeText("{}")
        val export = RideLogStore(src, zone).export(File(tmp, "out"))
        val r = export.reader(Charsets.UTF_8).use { JournalImport.import(it, dir) }
        assertEquals(3, r.added.size)
        for (name in src.list()!!) assertEquals(File(src, name).readText(), File(dir, name).readText())
    }

    @Test
    fun `büyük içerik akışla yazılır`() {
        val line = "{\"t\":1790900000000,\"k\":\"gps\",\"lat\":40.409264,\"lon\":49.867092,\"acc\":4.2,\"sp\":5.1}\n"
        val content = line.repeat(40_000)
        import(journal(item("2026-10-02.jsonl", content)))
        assertEquals(content.length.toLong(), File(dir, "2026-10-02.jsonl").length())
    }

    @Test
    fun `etkin gün dosyası ve açık sürüş içe aktarmada atlanır`() {
        val clock = FakeClock(Instant.parse("2026-10-02T06:00:00Z").toEpochMilli())
        val logger = RideLogWriter(dir, MemoryMapPrefs(), clock)
        logger.setDay("yolgo-${clock.nowMs}")
        logger.event("route")
        logger.flush()
        val ride = logger.records().single { it.kind == "ride" }.name
        File(dir, ride).delete()
        File(dir, "2026-10-02.jsonl").delete()
        val r = logger.importJournal(StringReader(journal(item("2026-10-02.jsonl", "YolGo"), item(ride, "YolGo"), item("2026-10-01.jsonl", "eski"))))
        assertEquals(listOf("2026-10-01.jsonl"), r.added)
        assertEquals(2, r.existing)
        assertFalse(File(dir, ride).exists())
    }
}
