package com.kuryelog.logic.scooter

import org.json.JSONObject
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * "Sürüş Paneli" dışa aktarma ZIP'i `NinebotLogger-<ms>.zip` (SCOOTER.md 7.4; NB `LogExport.java` + `LogStore.export` satır satır;
 * VERI_SOZLESMESI 7.2). Saf: satırları depo ([Source], Android'de `ninebot-logger.db`) verir, burada izin listesi, manifest ve
 * ZIP düzeni kurulur. İzin listesi eski veritabanları için de geçerlidir: ham tel, el sıkışma ve serbest metin teşhisi dışarı çıkmaz.
 */
object LogExport {
    /** Sürüş Paneli `BuildInfo.VERSION` (manifest ve `session_start` `version`); WEEM sürümü değil, kayıt biçiminin sürümü. */
    const val VERSION = "0.4.3"

    /** Dosya adı (`NinebotLogger-<ms>.zip`; analiz araçları bunu bekler). */
    fun fileName(nowMs: Long): String = "NinebotLogger-$nowMs.zip"

    /** Depodan satırlar: sütun adı sırasıyla `Long`, `String` ya da null (SQLite `FIELD_TYPE_INTEGER` → Long, öteki → String). */
    interface Source {
        /** [table] `sessions` ya da `events`, `id` sırasıyla; [latestOnly]: yalnız en son oturum. */
        fun rows(table: String, latestOnly: Boolean, each: (Map<String, Any?>) -> Unit)

        /** Tutarlı okuma (SQLite'ta `beginTransactionNonExclusive`). */
        fun <T> consistent(block: () -> T): T = block()
    }

    /** [full] = eski tam teşhis ZIP'i (ham tel, el sıkışma, cihaz, GPS): yalnız açık, uyarılı dışa aktarma; konum da dahil sayılır. */
    class Options(includeLocation: Boolean = false, val latestOnly: Boolean = true, val full: Boolean = false) {
        val includeLocation: Boolean = includeLocation || full
    }

    fun write(output: OutputStream, source: Source, options: Options, nowMs: Long) {
        source.consistent {
            ZipOutputStream(output).use { zip ->
                entry(zip, "manifest.json", manifest(options, nowMs).toString())
                table(zip, source, "sessions", options)
                table(zip, source, "events", options)
            }
        }
    }

    fun manifest(o: Options, nowMs: Long): JSONObject =
        if (o.full) {
            json(
                "schema", 1, "app", "Sürüş Paneli", "version", VERSION, "decoder_available", true, "device_verified", false, "utc_export_ms", nowMs,
                "wire_transcript_sensitive", true, "full_diagnostics", true, "location_included", true, "latest_session_only", o.latestOnly,
                "note", "Full encrypted TX/RX handshake diagnostics and experimental G3 reads. Pairing material may be recovered from transcripts. " +
                    "No plaintext credential store is exported. Location/device identifiers included; unknown dashboard fields remain null.",
            )
        } else {
            json(
                "schema", 1, "app", "Sürüş Paneli", "version", VERSION, "decoder_available", true, "device_verified", false, "utc_export_ms", nowMs,
                "wire_transcript_sensitive", false, "full_diagnostics", false, "location_included", o.includeLocation, "latest_session_only", o.latestOnly,
                "note", "Filtered ride samples only. No device identifiers, raw wire, handshake, credential store or sweep data. " +
                    "Timestamps and speeds remain; location only if explicitly selected.",
            )
        }

    private fun table(zip: ZipOutputStream, source: Source, table: String, o: Options) {
        zip.putNextEntry(ZipEntry("$table.jsonl"))
        source.rows(table, o.latestOnly) { c ->
            var payload: JSONObject? = null
            if (table == "events" && o.full) {
                payload = JSONObject(c["payload"] as String)
            } else if (table == "events") {
                payload = payload(c["kind"] as String, JSONObject(c["payload"] as String), o.includeLocation) ?: return@rows
            }
            val row = JSONObject()
            for ((name, v) in c) {
                var value: Any? = v ?: JSONObject.NULL
                if (name == "device" && !o.full) value = JSONObject.NULL
                if (name == "payload") value = payload
                row.put(name, value)
            }
            zip.write((row.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
        }
        zip.closeEntry()
    }

    private fun entry(z: ZipOutputStream, name: String, content: String) {
        z.putNextEntry(ZipEntry(name))
        z.write(content.toByteArray(StandardCharsets.UTF_8))
        z.closeEntry()
    }

    /** An allowlist, including for old databases: wire/handshake/free-text diagnostics never leave. Null = the row is left out. */
    fun payload(kind: String, input: JSONObject, location: Boolean): JSONObject? {
        if (kind == "sample") {
            val out = pick(input, "elapsed_ms", "gps_fresh", "gps_request_interval_ms")
            val gps = input.optJSONObject("gps")
            out.put("gps", if (gps == null) JSONObject.NULL else gps(gps, location))
            val t = input.optJSONObject("scooter_telemetry")
            if (t != null) {
                val clean = pick(
                    t, "mapping", "device_verified", "scooter_speed_kmh", "dashboard_power_w", "dashboard_power_source", "internal_power_w", "external_power_w",
                    "dashboard_trip_km", "dashboard_ride_minutes", "dashboard_ride_seconds",
                )
                val fields = JSONObject()
                val source = t.optJSONObject("fields")
                // Mapped vehicle registers and pack fields of every board (external pack included); sweep registers never leave.
                if (source != null) {
                    val keys = source.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val v = source.optJSONObject(key)
                        if (v != null && NinebotMetrics.exportable(key)) {
                            fields.put(key, pick(v, "value", "last_value", "age_ms", "fresh", "unit", "board", "register", "decoded_mono_ms"))
                        }
                    }
                }
                clean.put("fields", fields)
                out.put("scooter_telemetry", clean)
            } else {
                out.put("scooter_telemetry", JSONObject.NULL)
            }
            return out
        }
        if (kind == "gps_fix") return gps(input, location)
        if (kind == "log_backpressure" || kind == "log_summary") return pick(input, "dropped_ble_events", "omitted_diagnostic_events")
        if (kind == "session_start") return pick(input, "version", "sdk", "decoder_available", "device_verified", "sweep")
        if (kind == "gps_cadence") return pick(input, "requested_interval_ms")
        // External pack probe results: generated text with board numbers and pack voltage only, no wire bytes.
        if (kind == "battery_config") return pick(input, "probe", "known_boards")
        if (kind == "battery_boards" || kind == "battery_probe_candidate" || kind == "battery_probe_found" || kind == "battery_probe_partial" ||
            kind == "battery_probe_miss" || kind == "battery_probe_done" || kind == "battery_lost" || kind == "battery_quiet" || kind == "battery_back"
        ) {
            return pick(input, "detail")
        }
        return null
    }

    private fun gps(`in`: JSONObject, location: Boolean): JSONObject {
        val out = pick(`in`, "fix_wall_ms", "fix_mono_ns", "gps_age_ms", "accuracy_m", "gps_speed_kmh", "speed_accuracy_mps", "mock")
        if (location) for (k in arrayOf("lat", "lon", "altitude_m", "bearing_deg", "bearing_accuracy_deg")) if (`in`.has(k)) out.put(k, `in`.get(k))
        return out
    }

    /** Copies only plain values (number, boolean, string, JSON null) of the listed keys. */
    private fun pick(`in`: JSONObject, vararg keys: String): JSONObject {
        val out = JSONObject()
        for (key in keys) {
            if (`in`.has(key)) {
                val value = `in`.get(key)
                if (value === JSONObject.NULL || value is Number || value is Boolean || value is String) out.put(key, value)
            }
        }
        return out
    }

    /** NB `LogStore.json`: key/value pairs → event payload; a null value is written as `JSONObject.NULL`. */
    fun json(vararg kv: Any?): JSONObject {
        val j = JSONObject()
        var i = 0
        while (i < kv.size) {
            j.put(kv[i] as String, kv[i + 1] ?: JSONObject.NULL)
            i += 2
        }
        return j
    }
}
