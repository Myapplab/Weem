package com.kuryelog.logic.orders.dataset

import com.kuryelog.logic.orders.AnalysisExport
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Rawstats `LogFeedV3Test.kt` @ 6388c04 (10; A6.2b, MAP.md 13.6, 13.18) — UYARLANIR: sağlayıcı ve sayfalama (`page`) yok (aynı uygulama, S7); 7. test sayfa yerine bütün
 * dosyanın baytları, hash'i ve satır sayısı; 10. test yalnız gün ve yol denetimi (sayfa/ofset/sınır kısmı düşer). `build` çıktıyı doğrudan [out] klasörüne yazar.
 */
class DayLogSnapshotTest {
    private val day = "2026-10-10"
    private val secret = ByteArray(32) { it.toByte() }

    private fun inTemp(test: (File) -> Unit) {
        val root = Files.createTempDirectory("log-v3-test").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }

    private fun build(root: File, raw: File, secretBytes: ByteArray = secret, out: File = File(root, "out-" + System.nanoTime())): Pair<JSONObject, File> =
        DayLogSnapshot.build(raw, out.also { it.mkdirs() }, day, secretBytes, "0.23.1", 100L, 200L) to out

    private fun meta(manifest: JSONObject, name: String): JSONObject = manifest.getJSONArray("files").let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("name") == name }
    }

    @Test
    fun screenAndClickUseTheExistingRedactorWithoutCustomerData() = inTemp { root ->
        val raw = File(root, "raw").apply { mkdir() }
        val screen = JSONObject().put("pkg", "com.wolt.courierapp")
            .put("texts", org.json.JSONArray(listOf("Dropoff to", "Test Customer", "Private road 7", "Instructions", "Secret personal message 055 123 45 67", "Call", "kod 1234")))
            .put("actions", org.json.JSONArray(listOf("Test Customer Private road 7", "Call")))
            .put("customer", "Test Customer").put("door_code", "1234")
        File(raw, "screens.jsonl").writeText(screen.toString() + "\n")
        File(raw, "events.jsonl").writeText(JSONObject().put("type", "click").put("text", "Test Customer Private road 7").toString() + "\n")
        val (_, out) = build(root, raw)
        val data = File(out, "screens.jsonl").readText() + File(out, "events.jsonl").readText()
        for (privateValue in listOf("Test Customer", "Private road", "Secret personal", "055 123", "1234")) assertFalse(privateValue, data.contains(privateValue))
        assertTrue(data.contains("[card]"))
        assertTrue(data.contains("[note]"))
        val legacy = AnalysisExport(secret).row(AnalysisExport.Kind.SCREENS, screen)!!
        val exported = JSONObject(File(out, "screens.jsonl").readText())
        assertEquals(legacy.getJSONArray("texts").toString(), exported.getJSONArray("texts").toString())
        assertEquals(legacy.getJSONArray("actions").toString(), exported.getJSONArray("actions").toString())
    }

    @Test
    fun perDaySecretSurvivesRecreationAndNeverAppearsInManifest() = inTemp { root ->
        val secretRoot = File(root, "phone-secrets")
        val a = DailyLogSecret(secretRoot).forDay(day)
        assertArrayEquals(a, DailyLogSecret(secretRoot).forDay(day))
        assertFalse(a.contentEquals(DailyLogSecret(secretRoot).forDay("2026-10-11")))
        val raw = File(root, "raw").apply { mkdir() }
        val line = "{\"pkg\":\"com.wolt.courierapp\",\"texts\":[\"Dropoff to\",\"Test Customer\",\"Private road\"]}\n"
        File(raw, "screens.jsonl").writeText(line)
        val (first, firstOut) = build(root, raw, a)
        val (_, nextOut) = build(root, raw, DailyLogSecret(secretRoot).forDay(day))
        assertEquals(File(firstOut, "screens.jsonl").readText(), File(nextOut, "screens.jsonl").readText())
        assertFalse(first.toString().contains(a.joinToString("") { "%02x".format(it) }))
        assertFalse(first.getJSONObject("redaction").getBoolean("secretExported"))
    }

    @Test
    fun offerKeyIsIdenticalToTheGeneratedLiveKeyAndUnknownClocksStayNull() = inTemp { root ->
        val raw = File(root, "raw").apply { mkdir() }
        val key = "offer-1791630000000-42" // DeliveryCore.observe's customer-free key
        File(raw, "economics-offers.jsonl").writeText("{\"offerKey\":\"$key\",\"feeAzn\":2.5}\n")
        val (_, out) = build(root, raw)
        val row = JSONObject(File(out, "economics-offers.jsonl").readText())
        assertEquals(key, row.getString("offerKey"))
        for (field in listOf("wall_ms", "mono_ns", "scooter_id")) assertTrue(field, row.isNull(field))
        assertEquals("unavailable", row.getString("mono_ns_source"))
        assertEquals(3, row.getInt("schema_version"))
        assertEquals("kuryeplus", row.getString("source"))
        assertFalse(row.getBoolean("power_verified"))
        assertEquals("unavailable", row.getJSONObject("field_sources").getString("mono_ns"))
    }

    @Test
    fun existingEnergyClocksAndScooterIdAreKeptButPowerIsUnverified() {
        val original = JSONObject().put("wall_ms", 1234L).put("mono_ns", 999L).put("scooter_id", "scooter-a").put("source", "surus_paneli").put("power_verified", true)
        val row = DayLogSnapshot.common(JSONObject(original.toString()), original, "economics-energy.jsonl")
        assertEquals(1234L, row.getLong("wall_ms")); assertEquals(999L, row.getLong("mono_ns"))
        assertEquals("scooter-a", row.getString("scooter_id")); assertEquals("surus_paneli", row.getString("source"))
        assertFalse(row.getBoolean("power_verified"))
    }

    @Test
    fun nestedUnknownValuesHaveSourceFlagsWithoutInventingValues() {
        val j = JSONObject().put("shown", JSONObject().put("km", JSONObject.NULL)).put("temp_c", JSONObject.NULL)
        val row = DayLogSnapshot.common(j, JSONObject(), "economics-scooter.jsonl")
        assertTrue(row.getJSONObject("shown").isNull("km"))
        assertEquals("unavailable", row.getJSONObject("field_sources").getString("shown.km"))
        assertEquals("unavailable", row.getJSONObject("field_sources").getString("temp_c"))
    }

    @Test
    fun copyingASnapshotOmitsLaterAppendsAndRejectsTruncatedSource() {
        val out = java.io.ByteArrayOutputStream()
        DayLogSnapshot.copyPrefix("first\nnext\n".byteInputStream(), out, 6)
        assertEquals("first\n", out.toString("UTF-8"))
        try {
            DayLogSnapshot.copyPrefix("short".byteInputStream(), out, 100)
            fail("truncated source accepted")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun largeUtf8FileKeepsItsBytesAndTheHashAndRowCountMatch() = inTemp { root ->
        val raw = File(root, "raw").apply { mkdir() }
        File(raw, "platforms.jsonl").bufferedWriter().use { w ->
            repeat(4000) { w.append("{\"t\":\"2026-10-10T12:00:00.000+0400\",\"label\":\"əğü ₼\"}\n") }
        }
        val (manifest, out) = build(root, raw)
        val copied = File(out, "platforms.jsonl")
        // JVM org.json U+2000–U+20FF aralığını \u kaçışıyla yazar (Android JSONObject yazmaz): ikisi de aynı değeri taşır.
        val text = copied.readText(Charsets.UTF_8)
        assertTrue(text.contains("əğü") && (text.contains("₼") || text.contains("\\u20bc")))
        assertEquals(DayLogSnapshot.sha256(copied), meta(manifest, "platforms.jsonl").getString("sha256"))
        assertEquals(4000, meta(manifest, "platforms.jsonl").getInt("rows"))
        assertEquals(copied.length(), meta(manifest, "platforms.jsonl").getLong("size"))
    }

    @Test
    fun refreshingDayReplacesTheViewWithoutMutatingAnEarlierSnapshot() = inTemp { root ->
        val raw = File(root, "raw").apply { mkdir() }
        File(raw, "economics-offers.jsonl").writeText("{\"offerKey\":\"offer-1-1\"}\n")
        val (first, firstOut) = build(root, raw)
        File(raw, "economics-offers.jsonl").appendText("{\"offerKey\":\"offer-2-2\"}\n")
        val (second, secondOut) = build(root, raw)
        assertNotEquals(first.getString("snapshot"), second.getString("snapshot"))
        assertEquals(1, File(firstOut, "economics-offers.jsonl").readLines().size)
        assertEquals(2, File(secondOut, "economics-offers.jsonl").readLines().size)
    }

    @Test
    fun malformedRowsAndAbsentFilesAreExplicit() = inTemp { root ->
        val raw = File(root, "raw").apply { mkdir() }
        File(raw, "economics-offers.jsonl").writeText("not JSON\n{}\n")
        File(raw, "customer-private.jsonl").writeText("private data")
        val (manifest, _) = build(root, raw)
        assertEquals(1, meta(manifest, "economics-offers.jsonl").getInt("rejectedRows"))
        assertFalse(meta(manifest, "economics-payouts.jsonl").getBoolean("available"))
        assertFalse(manifest.toString().contains("customer-private"))
    }

    @Test
    fun pathsAndDatesFailClosed() = inTemp { root ->
        for (bad in listOf("../2026-10-10", "2026-02-30", "2026-1-1")) {
            assertTrue(bad, runCatching { DailyLogSecret(root).forDay(bad) }.isFailure)
            val raw = File(root, "raw-$bad".replace("/", "_")).apply { mkdirs() }
            assertTrue(bad, runCatching { DayLogSnapshot.build(raw, File(root, "out-bad").also { it.mkdirs() }, bad, secret, "0.23.1", 100L, 200L) }.isFailure)
        }
        assertNull(runCatching { DailyLogSecret(root).forDay(day) }.exceptionOrNull())
    }
}
