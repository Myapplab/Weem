package com.kuryelog.logic.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/** CORE.md 5.2 (RS `Store.kt:28-88`): gün klasörüne satır ekleme, olay ve çökmeyen hata satırları (A2.3). */
class LogStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private var now = 1_792_228_260_123L // 2026-10-17 13:11:00.123 Bakü
    private val written = mutableListOf<Pair<String, String>>()
    private fun store(prefix: String = "Kayıt yazılamadı: ") =
        LogStore(File(temp.root, "logs"), { now }, { ZoneId.of("Asia/Baku") }, { prefix }, { d, n -> written += d to n })

    @Test fun linesGoToTodaysFolderAndBringItBackFromTheArchive() {
        val s = store()
        val dir = File(temp.root, "logs/2026-10-17").apply { mkdirs() }
        File(dir, ".archived").writeText(""); File(dir, ".archived-orders.jsonl").writeText(""); File(dir, ".archived-gps.csv").writeText("")
        assertTrue(s.append("orders.jsonl", """{"a":1}"""))
        assertTrue(s.append("orders.jsonl", """{"a":2}"""))
        assertEquals("{\"a\":1}\n{\"a\":2}\n", File(dir, "orders.jsonl").readText())
        assertFalse(File(dir, ".archived").exists())
        assertFalse(File(dir, ".archived-orders.jsonl").exists())
        assertTrue("another file's marker stays", File(dir, ".archived-gps.csv").exists())
        assertEquals(listOf("2026-10-17" to "orders.jsonl", "2026-10-17" to "orders.jsonl"), written)
        assertNull(s.lastError)
    }

    @Test fun theDayIsTheWriteMomentsLocalDate() {
        val s = store()
        now = 1_792_267_199_000L // 23:59:59 Bakü
        s.append("gps.csv", "a")
        now += 1_000
        s.append("gps.csv", "b")
        assertEquals("a\n", File(temp.root, "logs/2026-10-17/gps.csv").readText())
        assertEquals("b\n", File(temp.root, "logs/2026-10-18/gps.csv").readText())
    }

    @Test fun anExplicitDayWritesIntoThatFolder() {
        val s = store()
        assertTrue(s.append("economics-waits.jsonl", "{}", day = "2026-10-16"))
        assertEquals("{}\n", File(temp.root, "logs/2026-10-16/economics-waits.jsonl").readText())
    }

    @Test fun aBadDayOrAFailedWriteReturnsFalseAndNeverThrows() {
        val s = store("Qeyd yazılmadı: ")
        assertFalse(s.append("orders.jsonl", "{}", day = "../x"))
        assertTrue(s.lastError!!.startsWith("Qeyd yazılmadı: "))
        assertFalse(File(temp.root, "x").exists())
        File(temp.root, "logs/2026-10-17/orders.jsonl").mkdirs() // a directory where the file should be
        assertFalse(s.append("orders.jsonl", "{}"))
        assertTrue(written.isEmpty())
    }

    @Test fun aFailingListenerDoesNotLoseTheLine() {
        val s = LogStore(File(temp.root, "logs"), { now }, { ZoneId.of("Asia/Baku") }, dayWritten = { _, _ -> error("listener") })
        assertTrue(s.append("events.jsonl", "{}"))
        assertEquals("{}\n", File(temp.root, "logs/2026-10-17/events.jsonl").readText())
    }

    @Test fun eventsCarryTimeTypeAndTheLastFixWithItsAge() {
        val s = store()
        s.event("shift_start", JSONObject().put("auto", false).put("reason", "manual"))
        s.lastLocation = LogStore.Loc(40.4, 49.8, 7.5, now - 12_900)
        s.event("gps_disabled")
        val lines = File(temp.root, "logs/2026-10-17/events.jsonl").readLines().map(::JSONObject)
        assertEquals("shift_start", lines[0].getString("type"))
        assertEquals("2026-10-17T13:11:00.123+0400", lines[0].getString("t"))
        assertEquals("manual", lines[0].getString("reason"))
        assertFalse(lines[0].has("loc"))
        val loc = lines[1].getJSONObject("loc")
        assertEquals(40.4, loc.getDouble("lat"), 0.0)
        assertEquals(49.8, loc.getDouble("lon"), 0.0)
        assertEquals(7.5, loc.getDouble("acc"), 0.0)
        assertEquals(12L, loc.getLong("age_s"))
    }

    @Test fun failuresAreWrittenToTheDaysErrorLog() {
        val s = store()
        s.failure("gps", IllegalStateException("no fix"))
        assertEquals("gps: IllegalStateException: no fix", s.lastError)
        val j = JSONObject(File(temp.root, "logs/2026-10-17/errors.jsonl").readText().trim())
        assertEquals("gps", j.getString("source"))
        assertEquals("gps: IllegalStateException: no fix", j.getString("error"))
        assertEquals("2026-10-17T13:11:00.123+0400", j.getString("t"))
        assertNull(guarded("tick", s) { throw NoClassDefFoundError("X") })
        assertEquals("tick: NoClassDefFoundError: X", s.lastError)
    }

    /** A10.2: `errors.jsonl` tam yedeğe girer; Android `JSONException` ayrıştırılan satırın tamamını alıntılar. Yapay numara. */
    @Test fun errorLogNeverCarriesACustomerPhoneOrTheWholeParsedLine() {
        val s = store()
        val line = "{\"name\":\"Test Müştəri\",\"phone\":\"+994 50 123 45 67\",\"note\":\"" + "x".repeat(400)
        s.failure("courier_db", RuntimeException("Unterminated string at character 400 of $line\nnext line"))
        val error = JSONObject(File(temp.root, "logs/2026-10-17/errors.jsonl").readText().trim()).getString("error")
        assertFalse(error, error.contains("123 45 67"))
        assertTrue(error, error.contains("[tel]"))
        assertFalse(error.contains("next line"))
        assertTrue(error, error.length <= "courier_db: RuntimeException: ".length + 200)
        assertEquals(error, s.lastError)
    }
}
