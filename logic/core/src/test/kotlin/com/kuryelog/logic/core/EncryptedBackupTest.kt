package com.kuryelog.logic.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * N3 otomatik şifreli yedek (yeni özellik, eski kodu yok; kullanıcı kararı S22, 18 Ekim). Haftalık dosya, parola (PBKDF2 + AES-GCM), ev konumu
 * hariç bütün bilgiler (gün kayıtları, restaurants.json, tercihler, courier.db satırları), geri yükleme içe aktarma kurallarıyla.
 */
class EncryptedBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val password = "test-parola-1".toCharArray()
    private val fast = 10_000 // PBKDF2 turu yalnız testte düşük; uygulama ITERATIONS kullanır
    private val week = EncryptedBackup.week("2026-10-14") // Çarşamba → 12–18 Ekim

    private val orders = "{\"t\":\"2026-10-14T12:00:00.000+0400\",\"type\":\"accepted\",\"pkg\":\"com.wolt.courierapp\",\"id\":\"trip-1\"}\n"
    private fun trip(id: String, day: String, phone: String?) = JSONObject().put("id", id).put("pkg", "com.wolt.courierapp").put("day", day)
        .put("customers", JSONArray().put(JSONObject().put("id", "name:aysel testova").put("details", JSONObject().put("name", "Aysel Testova"))
            .also { if (phone != null) it.put("phone", phone) }))

    private class Phone(base: File) {
        val files = File(base, "files").apply { mkdirs() }
        val logs = File(files, "logs").apply { mkdirs() }
        val cache = File(base, "cache").apply { mkdirs() }
        fun write(path: String, text: String) = File(logs, path).also { it.parentFile.mkdirs(); it.writeText(text) }
    }

    private fun filled() = Phone(temp.newFolder("a")).apply {
        write("2026-10-14/orders.jsonl", orders)
        write("2026-10-18/gps.csv", "${LogImporter.GPS_HEADER}\n2026-10-18T10:00:00.000+0400,40.4093123,49.8671456,5.0,1.0,0.0,10.0\n")
        write("2026-10-19/orders.jsonl", "{\"id\":\"next-week\"}\n") // next week: not in this file
        write("2026-10-11/orders.jsonl", "{\"id\":\"last-week\"}\n")
        write("2026-10-14/debug.txt", "x\n")
        File(files, BackupExport.RESTAURANTS).writeText(JSONObject().put("izmir", JSONObject().put("n", 2)).toString())
    }

    private val allPrefs: Map<String, Any?> = mapOf("lang" to "az", "dailyPack" to false, "commission.com.wolt.courierapp" to 20.0f, "manualPolicy" to 1,
        "crashSeenAt" to 5L, "targets" to setOf("ee.mtakso.courier", "com.wolt.courierapp"),
        "home.lat" to "40.4093123", "home.lon" to "49.8671456", "home.updatedAt" to 9L, "home.startBlocked" to true, "home.endArmed" to false,
        "shift" to true, "manualSession" to true, "shiftStart" to 1L, "dist" to 3.0f, "weem.deviceId" to "dev-1", "empty" to null)

    private fun backup(p: Phone, pw: CharArray = password): ByteArray {
        val staging = File(p.cache, "n3").apply { mkdirs() }
        try {
            val files = EncryptedBackup.stage(p.logs, p.files, week, staging)!!
            val content = EncryptedBackup.Content(files, EncryptedBackup.encodePrefs(allPrefs),
                listOf(trip("trip-1", "2026-10-14", "0551234567"), trip("trip-0", "2026-10-11", "0559999999")),
                listOf(JSONObject().put("id", "offer-1").put("pkg", "com.wolt.courierapp").put("day", "2026-10-14") to "accepted"))
            return ByteArrayOutputStream().also { EncryptedBackup.write(week, content, pw, it, "1.0", 1_792_000_000_000L, fast) }.toByteArray()
        } finally { staging.deleteRecursively() }
    }

    @Test fun weeksRunMondayToSundayAndNameTheFile() {
        assertEquals(LocalDate.parse("2026-10-12"), week.monday); assertEquals(LocalDate.parse("2026-10-18"), week.sunday)
        assertEquals((12..18).map { "2026-10-$it" }, week.days)
        assertEquals("WEEM_yedek_2026-10-12_2026-10-18.weem", week.fileName)
        assertEquals(week, EncryptedBackup.week("2026-10-12")); assertEquals(week, EncryptedBackup.week("2026-10-18"))
        assertEquals(week, EncryptedBackup.weekOf(week.fileName))
        for (bad in listOf("WEEM_yedek_2026-10-13_2026-10-19.weem", "WEEM_gunluk_2026-10-12.zip", "WEEM_yedek_2026-10-12_2026-10-18.zip")) assertNull(bad, EncryptedBackup.weekOf(bad))
        assertTrue("2026-10-18" in week); assertFalse("2026-10-19" in week); assertFalse("x" in week)
        assertEquals("WEEM/yedek", EncryptedBackup.FOLDER)
        // A shift from Sunday evening into Monday writes both weeks.
        val sunday = 1_792_342_800_000L // 2026-10-18 21:00 Bakü
        assertEquals(listOf(week), EncryptedBackup.weeks(sunday, sunday + 1_800_000L, baku))
        assertEquals(listOf(week, EncryptedBackup.week("2026-10-19")), EncryptedBackup.weeks(sunday, sunday + 6 * 3_600_000L, baku))
    }

    @Test fun prefsKeepEverythingButTheCouriersHomeAndTheLiveShift() {
        val j = EncryptedBackup.encodePrefs(allPrefs)
        assertEquals(setOf("commission.com.wolt.courierapp", "crashSeenAt", "dailyPack", "lang", "manualPolicy", "targets"), j.keys().asSequence().toSet())
        val back = EncryptedBackup.decodePrefs(j)
        assertEquals("az", back["lang"]); assertEquals(false, back["dailyPack"]); assertEquals(20.0f, back["commission.com.wolt.courierapp"])
        assertEquals(1, back["manualPolicy"]); assertEquals(5L, back["crashSeenAt"]); assertEquals(setOf("ee.mtakso.courier", "com.wolt.courierapp"), back["targets"])
        // A hand-edited file cannot bring the home back.
        j.put("home.lat", JSONObject().put("type", "string").put("value", "1")).put("odd", JSONObject().put("type", "blob").put("value", 1))
        assertFalse(EncryptedBackup.decodePrefs(j).containsKey("home.lat")); assertFalse(EncryptedBackup.decodePrefs(j).containsKey("odd"))
        // Restore never overwrites a setting the phone already has.
        assertEquals(mapOf("dailyPack" to false, "manualPolicy" to 1), EncryptedBackup.missingPrefs(back, setOf("lang", "commission.com.wolt.courierapp", "crashSeenAt", "targets")))
        for (k in listOf(KuryelogPrefs.HOME_LAT, KuryelogPrefs.HOME_LON, KuryelogPrefs.HOME_UPDATED_AT, KuryelogPrefs.SHIFT, KuryelogPrefs.DEVICE_ID))
            assertTrue(k, EncryptedBackup.excludedPref(k))
    }

    @Test fun theFileIsEncryptedAndRestoresOnAnotherPhone() {
        val bytes = backup(filled())
        assertEquals(EncryptedBackup.MAGIC, String(bytes, 0, 8, Charsets.US_ASCII))
        val text = String(bytes, Charsets.ISO_8859_1)
        for (s in listOf("orders.jsonl", "trip-1", "0551234567", "Aysel", "izmir", "40.4093", "kuryelog", "WEEM_YEDEK")) assertFalse(s, text.contains(s))
        val b = Phone(temp.newFolder("b"))
        b.write("2026-10-14/orders.jsonl", "{\"id\":\"b-own\"}\n")
        File(b.files, BackupExport.RESTAURANTS).writeText(JSONObject().put("izmir", JSONObject().put("n", 5)).toString())
        val (opened, names) = EncryptedBackup.restore(ByteArrayInputStream(bytes), password, b.files, b.logs, b.cache)
        assertEquals(listOf("2026-10-14/orders.jsonl", "2026-10-18/gps.csv", BackupExport.RESTAURANTS), names)
        assertEquals("{\"id\":\"b-own\"}\n$orders", File(b.logs, "2026-10-14/orders.jsonl").readText())
        assertFalse(File(b.logs, "2026-10-19").exists()); assertFalse(File(b.logs, "2026-10-11").exists())
        assertEquals(5, JSONObject(File(b.files, BackupExport.RESTAURANTS).readText()).getJSONObject("izmir").getInt("n"))
        // courier.db rows of the week only, phone included (encrypted file, S22); prefs without the home.
        assertEquals(listOf("trip-1"), opened.trips.map { it.getString("id") })
        assertEquals("0551234567", opened.trips[0].getJSONArray("customers").getJSONObject(0).getString("phone"))
        assertEquals(listOf("offer-1" to "accepted"), opened.offers.map { it.first.getString("id") to it.second })
        assertFalse(opened.prefs.keys.any { it.startsWith("home.") }); assertEquals("az", opened.prefs["lang"])
        val m = opened.manifest
        assertEquals(EncryptedBackup.SCHEMA, m.getString("schema")); assertEquals("2026-10-12", m.getJSONObject("week").getString("from"))
        assertEquals(1, m.getInt("trips")); assertEquals(1, m.getInt("offers"))
        // Restoring the same file again changes nothing.
        val once = b.logs.walkTopDown().filter { it.isFile }.associate { it.path to it.readText() }
        EncryptedBackup.restore(ByteArrayInputStream(bytes), password, b.files, b.logs, b.cache)
        assertEquals(once, b.logs.walkTopDown().filter { it.isFile }.associate { it.path to it.readText() })
        assertTrue(b.cache.listFiles()!!.isEmpty())
    }

    @Test fun aWrongPasswordOrATamperedFileChangesNothing() {
        val bytes = backup(filled())
        val b = Phone(temp.newFolder("b"))
        b.write("2026-10-14/orders.jsonl", "{\"id\":\"b-own\"}\n")
        assertThrows(EncryptedBackup.WrongPassword::class.java) { EncryptedBackup.restore(ByteArrayInputStream(bytes), "baska-parola".toCharArray(), b.files, b.logs, b.cache) }
        for (i in listOf(9, 20, 40, bytes.size / 2, bytes.size - 1)) {
            val bad = bytes.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows("byte $i", Exception::class.java) { EncryptedBackup.restore(ByteArrayInputStream(bad), password, b.files, b.logs, b.cache) }
        }
        assertThrows(Exception::class.java) { EncryptedBackup.open(ByteArrayInputStream(bytes.copyOf(bytes.size - 5)), password) }
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackup.open(ByteArrayInputStream("PK\u0003\u0004 not ours, a plain zip...........................".toByteArray()), password) }
        assertEquals("{\"id\":\"b-own\"}\n", File(b.logs, "2026-10-14/orders.jsonl").readText())
        assertEquals(listOf("2026-10-14/orders.jsonl"), b.logs.walkTopDown().filter { it.isFile }.map { it.relativeTo(b.logs).invariantSeparatorsPath }.toList())
        assertThrows(IllegalArgumentException::class.java) { backup(Phone(temp.newFolder("c")).apply { write("2026-10-14/orders.jsonl", orders) }, "kisa".toCharArray()) }
    }

    @Test fun eachFileHasItsOwnSaltAndTheRealIterationCount() {
        val p = filled()
        val a = backup(p); val b = backup(p)
        assertFalse(a.copyOfRange(12, 40).contentEquals(b.copyOfRange(12, 40)))
        assertEquals(fast, java.nio.ByteBuffer.wrap(a, 8, 4).int)
        assertEquals(600_000, EncryptedBackup.ITERATIONS)
        assertNull("a week without records writes no file", EncryptedBackup.stage(p.logs, p.files, EncryptedBackup.week("2026-11-02"), temp.newFolder("none")))
    }

    @Test fun restoredRowsOnlyAddMissingPhones() {
        val backup = trip("trip-1", "2026-10-14", "0551234567")
        assertEquals(backup.toString(), EncryptedBackup.mergeTrip(null, backup).toString())
        // After an import courier.db is rebuilt from orders.jsonl, which never has the phone: the backup gives it back.
        val merged = EncryptedBackup.mergeTrip(trip("trip-1", "2026-10-14", null).put("phase", "FINISHED"), backup)!!
        assertEquals("0551234567", merged.getJSONArray("customers").getJSONObject(0).getString("phone")); assertEquals("FINISHED", merged.getString("phase"))
        assertNull(EncryptedBackup.mergeTrip(trip("trip-1", "2026-10-14", "0550000000"), backup))
        assertNull(EncryptedBackup.mergeTrip(trip("trip-1", "2026-10-14", null), trip("trip-1", "2026-10-14", null)))
    }

    @Test fun deletingAnExportedWeekLeavesTodaysOpenShiftAlone() {
        val days = listOf("2026-10-11", "2026-10-14", "2026-10-18", "2026-10-19")
        assertEquals(listOf("2026-10-14", "2026-10-18"), EncryptedBackup.deletableDays(week, days, "2026-10-18", shiftOn = false))
        assertEquals(listOf("2026-10-14"), EncryptedBackup.deletableDays(week, days, "2026-10-18", shiftOn = true))
        assertEquals(emptyList<String>(), EncryptedBackup.deletableDays(week, listOf("2026-10-19"), "2026-10-19", shiftOn = false))
    }
}
