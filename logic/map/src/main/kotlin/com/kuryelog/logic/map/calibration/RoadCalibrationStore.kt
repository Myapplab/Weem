package com.kuryelog.logic.map.calibration

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One atomic file per run. Existing sessions are never overwritten by a new run. */
class RoadCalibrationStore(private val root: File) {
    companion object {
        /** `files/` altındaki klasör (VERI_SOZLESMESI Y14); Android'de `:app` tek örnek tutar. */
        const val DIR = "road-calibration-v1"
        const val EXPORT = "WEEM-yol-kalibrasyonu.json"
    }
    private val summaries = mutableMapOf<String, JSONObject>()
    init { require(root.isDirectory || root.mkdirs()) }
    private fun atomic(file: File, content: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            java.io.FileOutputStream(temp).use { out -> out.write(content.toByteArray(Charsets.UTF_8)); out.fd.sync() }
            check(temp.renameTo(file)) { "Atomic rename failed" }
        } finally { temp.delete() }
    }
    @Synchronized fun save(id: String, config: CalibrationConfig, at: Long, elapsed: Double,
        result: CalibrationResult, interrupted: Boolean = false) {
        val j = JSONObject().put("schemaVersion", if (config.level == null) 1 else 2).put("id", id).put("at", at)
            .put("profile", config.profile).put("quality", config.quality).put("mode", config.mode)
            .put("requestedSeconds", config.seconds).put("elapsedSeconds", elapsed)
            .put("validSeconds", result.validSeconds).put("rms", result.rms ?: JSONObject.NULL)
            .put("averageKmh", result.averageKmh ?: JSONObject.NULL).put("valid", result.valid && !interrupted)
            .put("speedNotConstant", result.speedNotConstant).put("interrupted", interrupted)
            .put("rejectionReason", if (interrupted) "Kayıt kesildi" else result.rejectionReason ?: JSONObject.NULL)
            .put("targetKmh", result.targetKmh ?: JSONObject.NULL)
            .put("speedTrimmedSeconds", result.speedTrimmedSeconds)
            .put("units", "m/s², m/s, km/h, WGS84, UTC milliseconds, boot nanoseconds")
            .put("highPassHz", 2.0).put("longitudinalSource", "GPS speed derivative")
            .put("samples", JSONArray(result.points.map { p -> JSONObject()
                .put("monoNs", p.sample.ns).put("utcMs", p.sample.utc)
                .put("acceleration", JSONArray(p.sample.acceleration)).put("gravity", JSONArray(p.sample.gravity))
                .put("vertical", p.sample.vertical ?: JSONObject.NULL).put("filtered", p.sample.filtered ?: JSONObject.NULL)
                .put("speedMS", p.speed ?: JSONObject.NULL).put("longitudinalMS2", p.longitudinal ?: JSONObject.NULL)
                .put("lat", p.fix?.lat ?: JSONObject.NULL).put("lon", p.fix?.lon ?: JSONObject.NULL)
                .put("gpsUtcMs", p.fix?.utc ?: JSONObject.NULL).put("accuracyM", p.fix?.accuracy ?: JSONObject.NULL)
                .put("accepted", p.accepted).put("reason", p.reason)
                .put("quality", config.quality).put("mode", config.mode) }))
        result.roadRmsVersion?.let { version ->
            j.put("roadRmsVersion", version).put("roadRms", result.roadRms ?: JSONObject.NULL)
                .put("roadValidSeconds", result.roadValidSeconds).put("roadMeters", result.roadMeters)
                .put("roadWindows", result.roadWindows)
                .put("roadRmsRejectionReason", result.roadRmsRejectionReason ?: JSONObject.NULL)
        }
        config.level?.let { j.put("qualityLevel", it) }
        val target = file(id); check(!target.exists()) { "Session already exists" }
        atomic(target, j.toString())
        j.remove("samples"); summaries[id] = j
    }
    private fun file(id: String): File { require(id.matches(Regex("[a-zA-Z0-9-]+"))); return File(root, "$id.json") }
    @Synchronized fun sessions(raw: Boolean = false): List<JSONObject> = root.listFiles()
        ?.filter { it.name.endsWith(".json") }?.map { f ->
            if (raw) JSONObject(f.readText()) else summaries.getOrPut(f.nameWithoutExtension) {
                JSONObject(f.readText()).also { it.remove("samples") }
            }
        }?.sortedByDescending { it.optLong("at") } ?: emptyList()
    @Synchronized fun delete(id: String) {
        val target = file(id); check(!target.exists() || target.delete()); summaries.remove(id)
    }
    @Synchronized fun export(): String {
        val out = File(root.parentFile, EXPORT)
        atomic(out, JSONObject().put("schemaVersion", 1).put("sessions", JSONArray(sessions(true))).toString(2))
        return out.absolutePath
    }
}
