package com.kuryelog.logic.map.logs

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset

/**
 * YolGo `log_store_test.dart` ilk 4 test + `v140_features_test.dart` "rota dosyaları kayıt listesinde route olarak görünür" (1) = 5 test (A6.2b, MAP.md 12.4, 12.13).
 * Kalan 3 test (sürüş açma/kapama, gece yarısı, etkin kayıt) [RideLogWriterTest]'te. `DateTime(…)` yerel → sabit ms; bölge Bakü (+04:00).
 */
class RideLogStoreTest {
    private lateinit var dir: File
    private val zone = ZoneOffset.ofHours(4)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("duzyol-logs-").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `eski günlük, gün özeti ve sürüş aynı güne gruplanır, yabancı dosyalar dışarıda kalır`() {
        for (name in listOf("2026-10-02.jsonl", "2026-10-02.gun.json", "2026-10-02_1030.gun.json", "2026-10-02_ride_1790900000000.jsonl", "2026-10-01.jsonl", "private.txt")) {
            File(dir, name).writeText("{}")
        }
        val entries = RideLogStore(dir, zone).entries()
        val groups = RideLogStore.groupByDay(entries)
        assertEquals(5, entries.size)
        assertEquals(listOf("2026-10-02", "2026-10-01"), groups.keys.toList())
        assertEquals(4, groups["2026-10-02"]!!.size)
        val report = entries.single { it.name.endsWith("_1030.gun.json") }
        assertEquals(10, Instant.ofEpochMilli(report.startedAtMs!!).atZone(zone).hour)
        assertNotNull(entries.single { it.kind == "ride" }.startedAtMs)
    }

    @Test
    fun `tek kayıt silme diğer sürüş ve gün özetini korur`() {
        val store = RideLogStore(dir, zone)
        for (name in listOf("2026-10-02.jsonl", "2026-10-02.gun.json")) File(dir, name).writeText("{}")
        val entries = store.entries()
        store.delete(entries.first { it.kind == "day" })
        assertEquals("report", store.entries().single().kind)
    }

    @Test
    fun `dosya silme kayıt klasörü dışına çıkamaz`() {
        val outside = Files.createTempDirectory("duzyol-outside-").toFile()
        try {
            val file = File(outside, "2026-10-02.jsonl").also { it.writeText("keep") }
            val entry = RideLogEntry(file, "2026-10-02", "day", 4, null)
            assertThrows(IllegalArgumentException::class.java) { RideLogStore(dir, zone).delete(entry) }
            assertEquals("keep", file.readText())
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun `tüm günlük tek dosyada eksiksiz dışa aktarılır, günlük seçimi filtrelenir`() {
        val raw = "{\"k\":\"gps\",\"t\":1}\n{\"e\":\"ride_end\",\"t\":2}\n"
        File(dir, "2026-10-02.jsonl").writeText(raw)
        File(dir, "2026-10-01.gun.json").writeText("{\"delivered\":3}")
        val store = RideLogStore(dir, zone)
        val target = File(dir, "exports")
        val all = JSONObject(store.export(target).readText())
        assertEquals("duzyol-journal-v1", all.getString("format"))
        assertEquals(2, all.getJSONArray("files").length())
        val files = (0 until 2).map { all.getJSONArray("files").getJSONObject(it) }
        assertEquals(raw, files.first { it.getString("kind") == "day" }.getString("content"))
        val day = JSONObject(store.export(target, day = "2026-10-02").readText())
        assertEquals(1, day.getJSONArray("files").length())
    }

    @Test
    fun `rota dosyaları kayıt listesinde route olarak görünür`() {
        File(dir, "2026-10-06_route_1791288000000.json").writeText("{}")
        File(dir, "2026-10-06_ride_1791288000000.jsonl").writeText("")
        val entries = RideLogStore(dir, zone).entries()
        assertEquals(setOf("route", "ride"), entries.map { it.kind }.toSet())
        val route = entries.first { it.kind == "route" }
        assertEquals(1791288000000L, route.startedAtMs)
    }
}
