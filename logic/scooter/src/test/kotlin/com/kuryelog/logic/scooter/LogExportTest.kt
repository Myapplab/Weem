package com.kuryelog.logic.scooter

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * NB `LogExportTest.java` (androidTest; 12 gizlilik denetimi, SCOOTER.md 13.1) JVM'de + ZIP düzeni (manifest, izin listesi, `device` null,
 * son oturum, tam teşhis). Sentetik değerler.
 */
class LogExportTest {
    private val sensitive = "SYNTHETIC-HANDSHAKE-SECRET"

    @Test fun rawAndUnknownEventsAreExcluded() {
        val raw = JSONObject().put("hex", sensitive).put("wire_hex", sensitive).put("detail", sensitive)
        for (kind in listOf("ble_raw", "ble_write", "protocol_tx", "protocol_rejected", "pair_reply", "future_event")) {
            assertNull("raw and unknown events excluded: $kind", LogExport.payload(kind, raw, true))
        }
        assertNull("probe rejects carrying raw bytes stay out", LogExport.payload("battery_probe_reject", raw, false))
    }

    @Test fun legacySecretFieldsAreRemovedAndLocationFollowsTheChoice() {
        val gps = JSONObject().put("lat", 40.123).put("lon", 49.456).put("altitude_m", 123).put("bearing_deg", 22).put("gps_speed_kmh", 12).put("accuracy_m", 3).put("secret", sensitive)
        val fields = JSONObject().put("battery_percent", JSONObject().put("value", 82).put("raw_hex", sensitive))
        val telemetry = JSONObject().put("fields", fields).put("dashboard_power_w", -25).put("wire_hex", sensitive)
        val sample = JSONObject().put("gps", gps).put("scooter_telemetry", telemetry).put("ble_state", sensitive).put("elapsed_ms", 123)
        val clean = LogExport.payload("sample", sample, false)!!
        assertFalse("legacy secret fields removed recursively", clean.toString().contains(sensitive))
        assertTrue("default export removes route", !clean.getJSONObject("gps").has("lat") && !clean.getJSONObject("gps").has("lon") && !clean.getJSONObject("gps").has("bearing_deg"))
        assertEquals("speed retained for calibration", 12, clean.getJSONObject("gps").getInt("gps_speed_kmh"))
        assertEquals("telemetry retained", 82, clean.getJSONObject("scooter_telemetry").getJSONObject("fields").getJSONObject("battery_percent").getInt("value"))
        val route = LogExport.payload("sample", sample, true)!!
        assertEquals("explicit GPS choice retains route", 40.123, route.getJSONObject("gps").getDouble("lat"), 0.0)
        assertFalse("GPS export still excludes handshake", route.toString().contains(sensitive))
    }

    @Test fun externalPackFieldsAndPackPowerAreExportedSweepIsNot() {
        val packs = JSONObject().put("bms_08_voltage_v", JSONObject().put("value", 43.7).put("raw_hex", sensitive)).put("bms_07_reg8a_raw", JSONObject().put("value", 9810))
            .put("sweep_16_10", JSONObject().put("value", 1))
        val packTelemetry = JSONObject().put("fields", packs).put("internal_power_w", -11.2).put("external_power_w", -249.1)
        val packClean = LogExport.payload("sample", JSONObject().put("scooter_telemetry", packTelemetry), false)!!.getJSONObject("scooter_telemetry")
        assertTrue("external pack and capacity fields exported", packClean.getJSONObject("fields").getJSONObject("bms_08_voltage_v").getDouble("value") == 43.7 &&
            packClean.getJSONObject("fields").has("bms_07_reg8a_raw"))
        assertTrue("sweep registers and raw bytes stay out", !packClean.getJSONObject("fields").has("sweep_16_10") && !packClean.toString().contains(sensitive))
        assertTrue("pack power split exported", packClean.getDouble("external_power_w") == -249.1 && packClean.getDouble("internal_power_w") == -11.2)
        assertTrue("probe result exported", LogExport.payload("battery_probe_found", JSONObject().put("detail", "board=8; board_hex=08"), false)!!.getString("detail").contains("board=8"))
    }

    @Test fun nonPlainValuesAreNeverCopied() {
        val nested = JSONObject().put("elapsed_ms", JSONObject().put("x", 1)).put("gps_fresh", true)
        val out = LogExport.payload("sample", nested, false)!!
        assertFalse(out.has("elapsed_ms")); assertTrue(out.getBoolean("gps_fresh"))
        assertTrue(out.isNull("scooter_telemetry")); assertTrue(out.isNull("gps"))
    }

    // ---- ZIP düzeni ----

    private class Rows(val sessions: List<Map<String, Any?>>, val events: List<Map<String, Any?>>) : LogExport.Source {
        override fun rows(table: String, latestOnly: Boolean, each: (Map<String, Any?>) -> Unit) {
            val all = if (table == "sessions") sessions else events
            val latest = sessions.maxOf { it["id"] as Long }
            for (r in all) if (!latestOnly || (if (table == "sessions") r["id"] else r["session_id"]) == latest) each(r)
        }
    }

    private fun session(id: Long) = linkedMapOf<String, Any?>("id" to id, "started_ms" to 1000L * id, "ended_ms" to null, "state" to "stopped", "device" to "AA:BB:CC:DD:EE:FF")
    private fun event(id: Long, session: Long, kind: String, payload: JSONObject) =
        linkedMapOf<String, Any?>("id" to id, "session_id" to session, "wall_ms" to 5L, "mono_ns" to 6L, "kind" to kind, "payload" to payload.toString())

    private val rows = Rows(
        listOf(session(1), session(2)),
        listOf(
            event(1, 1, "gps_fix", JSONObject().put("lat", 40.1).put("lon", 49.1).put("accuracy_m", 4)),
            event(2, 2, "ble_raw", JSONObject().put("hex", sensitive)),
            event(3, 2, "gps_fix", JSONObject().put("lat", 40.2).put("lon", 49.2).put("accuracy_m", 5)),
            event(4, 2, "sample", JSONObject().put("elapsed_ms", 1000).put("ble_state", sensitive)),
        ),
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; out[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        return out
    }

    private fun export(o: LogExport.Options): Map<String, String> {
        val b = ByteArrayOutputStream(); LogExport.write(b, rows, o, 1_760_000_000_000L); return unzip(b.toByteArray())
    }

    @Test fun defaultZipIsFilteredLatestOnlyWithoutDeviceOrLocation() {
        val z = export(LogExport.Options())
        assertEquals(listOf("manifest.json", "sessions.jsonl", "events.jsonl"), z.keys.toList())
        val m = JSONObject(z["manifest.json"]!!)
        assertEquals(1, m.getInt("schema")); assertEquals("Sürüş Paneli", m.getString("app")); assertEquals("0.4.3", m.getString("version"))
        assertTrue(m.getBoolean("decoder_available")); assertFalse(m.getBoolean("device_verified")); assertEquals(1_760_000_000_000L, m.getLong("utc_export_ms"))
        assertFalse(m.getBoolean("wire_transcript_sensitive")); assertFalse(m.getBoolean("full_diagnostics")); assertFalse(m.getBoolean("location_included"))
        assertTrue(m.getBoolean("latest_session_only")); assertTrue(m.getString("note").startsWith("Filtered ride samples only."))
        val sessions = z["sessions.jsonl"]!!.trim().lines().map(::JSONObject)
        assertEquals(listOf(2L), sessions.map { it.getLong("id") }); assertTrue(sessions.single().isNull("device"))
        val events = z["events.jsonl"]!!.trim().lines().map(::JSONObject)
        assertEquals(listOf(3L, 4L), events.map { it.getLong("id") }) // ble_raw dışarıda
        assertFalse(events[0].getJSONObject("payload").has("lat"))
        assertFalse(z["events.jsonl"]!!.contains(sensitive))
        assertEquals("NinebotLogger-5.zip", LogExport.fileName(5))
    }

    @Test fun locationChoiceAndAllSessions() {
        val z = export(LogExport.Options(includeLocation = true, latestOnly = false))
        assertTrue(JSONObject(z["manifest.json"]!!).getBoolean("location_included")); assertFalse(JSONObject(z["manifest.json"]!!).getBoolean("latest_session_only"))
        assertEquals(listOf(1L, 2L), z["sessions.jsonl"]!!.trim().lines().map { JSONObject(it).getLong("id") })
        val events = z["events.jsonl"]!!.trim().lines().map(::JSONObject)
        assertEquals(40.1, events[0].getJSONObject("payload").getDouble("lat"), 0.0)
        assertFalse(z["events.jsonl"]!!.contains(sensitive))
    }

    @Test fun fullDiagnosticsKeepsEverythingAndSaysSo() {
        val z = export(LogExport.Options(full = true))
        val m = JSONObject(z["manifest.json"]!!)
        assertTrue(m.getBoolean("wire_transcript_sensitive")); assertTrue(m.getBoolean("full_diagnostics")); assertTrue(m.getBoolean("location_included"))
        assertTrue(m.getString("note").startsWith("Full encrypted TX/RX handshake diagnostics"))
        assertEquals(listOf(2L, 3L, 4L), z["events.jsonl"]!!.trim().lines().map { JSONObject(it).getLong("id") }) // son oturum: 2, 3, 4
        assertEquals("AA:BB:CC:DD:EE:FF", JSONObject(z["sessions.jsonl"]!!.trim()).getString("device"))
        assertTrue(z["events.jsonl"]!!.contains(sensitive)) // tam teşhis: ham tel dahil (uyarılı dışa aktarma)
    }
}
