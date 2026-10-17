package com.kuryelog.logic.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * R17 tam yedek ZIP'i (RS `MainActivity.exportLogs/stage`, `:1557-1610`; Rawstats'ta testi yoktu, Activity'deydi). Biçim kilidi: girdiler
 * `YYYY-MM-DD/<logNames>` + bütün günlerde kökte `restaurants.json`, işaret dosyası yok, içerik bayt bayt aynı; WEEM ZIP'i [LogImporter]
 * (Rawstats `LogImporter` kuralları) ile geri yüklenir. Gün sırları, `courier.db`, tercihler, işaretler ve tanınmayan dosyalar girmez.
 */
class BackupExportTest {
    @get:Rule val temp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val day1 = "2026-10-16"; private val day2 = "2026-10-17"
    private val orders1 = "{\"t\":\"2026-10-16T12:00:00.000+0400\",\"type\":\"offer\",\"pkg\":\"com.wolt.courierapp\",\"offer_id\":\"offer-1\"}\n" +
        "{\"t\":\"2026-10-16T12:00:05.000+0400\",\"type\":\"accepted\",\"pkg\":\"com.wolt.courierapp\",\"id\":\"trip-1\"}\n"
    private val gps2 = "${LogImporter.GPS_HEADER}\n2026-10-17T10:00:00.000+0400,40.4093123,49.8671456,5.0,1.0,0.0,10.0\n"
    private val restaurants = JSONObject().put("izmir doner", JSONObject().put("n", 3).put("name", "Izmir Doner")).toString()

    /** Bir telefonun `files/` (+ `no_backup/`, `databases/`, `shared_prefs/`) klasörü; kayıtlar [BackupFiles.logNames] ve yabancı dosyalarla. */
    private class Phone(base: File) {
        val files = File(base, "files").apply { mkdirs() }
        val logs = File(files, "logs").apply { mkdirs() }
        val cache = File(base, "cache").apply { mkdirs() }
        fun write(path: String, text: String) = File(logs, path).also { it.parentFile.mkdirs(); it.writeText(text) }
        fun snapshot(): Map<String, String> = logs.walkTopDown().filter { it.isFile }.associate { it.relativeTo(logs).invariantSeparatorsPath to it.readText() }
    }
    private fun phone(name: String) = Phone(temp.newFolder(name))

    private fun filled(): Phone = phone("a").apply {
        write("$day1/orders.jsonl", orders1)
        write("$day1/economics-cash-tips.jsonl", "{\"schemaVersion\":1,\"trip_id\":\"trip-1\",\"received_azn\":10.0}\n")
        write("$day1/.archived-orders.jsonl", "")
        write("$day1/debug.txt", "Aysel Testova 055 123 45 67\n")
        write("$day2/gps.csv", gps2)
        write("$day2/events.jsonl", "{\"t\":\"2026-10-17T10:00:00.000+0400\",\"type\":\"shift_start\",\"auto\":false}\n")
        write("$day2/.archived", "")
        write("$day2/screens.jsonl.bak", "{}\n")
        write("not-a-day/orders.jsonl", "{}\n")
        File(files, BackupExport.RESTAURANTS).writeText(restaurants)
        File(files, "crashes.jsonl").writeText("{\"t\":1}\n")
        // Yedeğe girmeyenler (CORE.md 5.1, VERI_SOZLESMESI 7.1, 9): gün sırları, courier.db (müşteri telefonu), tercihler.
        File(files.parentFile, "no_backup/economics-day-secrets-v3").apply { mkdirs() }.resolve(day1).writeBytes(ByteArray(32) { 7 })
        File(files.parentFile, "databases").apply { mkdirs() }.resolve("courier.db").writeText("0551234567")
        File(files.parentFile, "shared_prefs").apply { mkdirs() }.resolve("kuryelog.xml").writeText("<map/>")
    }

    private fun export(p: Phone, date: String? = null, file: String? = null): ByteArray {
        val staging = File(p.cache, "export-1").apply { mkdirs() }
        try {
            val entries = BackupExport.stage(p.logs, p.files, date, file, staging)
            return ByteArrayOutputStream().also { BackupExport.zip(entries, it) }.toByteArray()
        } finally { staging.deleteRecursively() }
    }
    private fun entries(zip: ByteArray): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z -> while (true) { val e = z.nextEntry ?: break; out[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        return out
    }

    @Test fun theFullExportIsTheRawstatsLayout() {
        val p = filled()
        val zip = entries(export(p))
        assertEquals(listOf("$day1/economics-cash-tips.jsonl", "$day1/orders.jsonl", "$day2/events.jsonl", "$day2/gps.csv", BackupExport.RESTAURANTS), zip.keys.toList())
        assertEquals(orders1, zip["$day1/orders.jsonl"]); assertEquals(gps2, zip["$day2/gps.csv"]); assertEquals(restaurants, zip[BackupExport.RESTAURANTS])
        // Every entry passes the import rules; no marker (the anonymous export has one), nothing outside the day folders and restaurants.json.
        for (name in zip.keys) assertEquals(name, BackupFiles.checkedPath(name))
        assertFalse(zip.keys.any { it.contains("ANALIZ") || it.startsWith(".") || "/." in it || it.endsWith(".db") || "secret" in it || "crash" in it })
        val all = zip.values.joinToString("\n")
        assertFalse(all.contains("0551234567")); assertFalse(all.contains("Aysel"))
        // The originals are untouched and the staging folder is gone.
        assertEquals(orders1, File(p.logs, "$day1/orders.jsonl").readText())
        assertTrue(File(p.logs, "$day1/.archived-orders.jsonl").exists())
        assertTrue(p.cache.listFiles()!!.isEmpty())
    }

    @Test fun oneDayAndOneFileLeaveRestaurantsOut() {
        val p = filled()
        assertEquals(listOf("$day1/economics-cash-tips.jsonl", "$day1/orders.jsonl"), entries(export(p, day1)).keys.toList())
        assertEquals(listOf("$day2/gps.csv"), entries(export(p, day2, "gps.csv")).keys.toList())
        assertThrows(BackupExport.NoLogs::class.java) { export(p, day2, "orders.jsonl") }
        assertThrows(BackupExport.NoLogs::class.java) { export(phone("empty")) }
        // An invalid day is refused like the records page refuses it (path traversal).
        assertThrows(Exception::class.java) { export(p, "../$day1") }
    }

    @Test fun aWeemExportRestoresOnAnotherPhoneAndAgainChangesNothing() {
        val a = filled()
        val zip = export(a)
        val b = phone("b")
        b.write("$day1/orders.jsonl", "{\"t\":\"2026-10-16T09:00:00.000+0400\",\"type\":\"offer\",\"pkg\":\"ee.mtakso.courier\",\"offer_id\":\"offer-0\"}\n")
        File(b.files, BackupExport.RESTAURANTS).writeText(JSONObject().put("izmir doner", JSONObject().put("n", 5).put("name", "İzmir Döner")).toString())
        val names = LogImporter.restore(ByteArrayInputStream(zip), b.files, b.logs, b.cache)
        assertEquals(entries(zip).keys.toList(), names)
        // Rawstats rules: lines merged (existing first), the larger restaurant count wins.
        assertEquals("{\"t\":\"2026-10-16T09:00:00.000+0400\",\"type\":\"offer\",\"pkg\":\"ee.mtakso.courier\",\"offer_id\":\"offer-0\"}\n" + orders1,
            File(b.logs, "$day1/orders.jsonl").readText())
        assertEquals(gps2, File(b.logs, "$day2/gps.csv").readText())
        assertEquals("İzmir Döner", JSONObject(File(b.files, BackupExport.RESTAURANTS).readText()).getJSONObject("izmir doner").getString("name"))
        val once = b.snapshot()
        LogImporter.restore(ByteArrayInputStream(zip), b.files, b.logs, b.cache)
        assertEquals(once, b.snapshot())
        // Into an empty phone the day files come back byte for byte; archive markers and foreign files stay behind.
        val c = phone("c")
        LogImporter.restore(ByteArrayInputStream(zip), c.files, c.logs, c.cache)
        assertEquals(entries(zip).filterKeys { it != BackupExport.RESTAURANTS }, c.snapshot())
        assertEquals(listOf(day2, day1), DailyRecords(c.logs).days().map { it.date })
        assertFalse(DailyRecords(c.logs).days().any { it.archived })
    }

    @Test fun namesAndFolders() {
        val at = 1_792_224_245_678L // 2026-10-17 12:04:05.678 Bakü
        assertEquals("WEEM_all_logs_120405678.zip", BackupExport.fileName(null, null, at, baku))
        assertEquals("WEEM_2026-10-17_logs_120405678.zip", BackupExport.fileName(day2, null, at, baku))
        assertEquals("WEEM_2026-10-17_economics-cash-tips_120405678.zip", BackupExport.fileName(day2, "economics-cash-tips.jsonl", at, baku))
        assertEquals("WEEM_analiz_anonim_2026-10-17_120405678.zip", BackupExport.analysisName(at, baku))
        assertEquals("WEEM", BackupExport.FOLDER)
    }

    // ---- RS DailyPackTest.nameAndFolder (WEEM adları) ve DailyPack.days/stage
    @Test fun dailyPackNameFolderAndDays() {
        assertEquals("WEEM_gunluk_2026-10-10.zip", DailyPack.fileName("2026-10-10"))
        assertEquals("WEEM/gunluk", DailyPack.FOLDER)
        assertEquals("dailyPack", DailyPack.PREF)
        assertTrue(DailyPack.enabled(KuryelogPrefs.Memory()))
        assertFalse(DailyPack.enabled(KuryelogPrefs.Memory(mutableMapOf(DailyPack.PREF to false))))
        val evening = 1_792_242_000_000L // 2026-10-17 17:00 Bakü
        assertEquals(listOf(day2), DailyPack.days(evening, evening + 3 * 3_600_000L, baku))
        // A shift past midnight packs both days.
        assertEquals(listOf(day2, "2026-10-18"), DailyPack.days(evening, evening + 9 * 3_600_000L, baku))
    }

    @Test fun dailyPackStagesOneDayWithRestaurants() {
        val p = filled()
        val staging = temp.newFolder("daily")
        assertEquals(listOf("$day1/economics-cash-tips.jsonl", "$day1/orders.jsonl", BackupExport.RESTAURANTS),
            DailyPack.stage(p.logs, p.files, day1, staging)!!.map { it.first })
        assertNull(DailyPack.stage(p.logs, p.files, "2026-10-01", temp.newFolder("none")))
    }
}
