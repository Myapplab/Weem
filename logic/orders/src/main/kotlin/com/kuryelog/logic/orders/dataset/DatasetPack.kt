package com.kuryelog.logic.orders.dataset

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** Gün toplama, eşleştirme, mühür (`DatasetPack.kt`, MAP.md 13.7; saf: `java.io`, `java.util.zip`, `MessageDigest`). */
object DatasetPack {
    private val REQUIRED_MANUAL = listOf(
        "wolt_earnings", "bolt_earnings", "wolt_rating", "bolt_rating", "wolt_acceptance", "bolt_acceptance", "wolt_completion", "bolt_completion",
        "weekly_payout", "shift_note",
    )
    private val SUMMARY_FIELDS = listOf(
        "feeAzn", "platformKm", "pickupAirKm", "deliveryAirKm", "routeKm", "routeSeconds", "demGainM", "demLossM", "temperatureC", "windKmh", "rainMm",
        "scooter_id", "pickup", "dropoff", "courier",
    )

    /** Tam anahtar + 5 sn yedek bire bir; belirsiz yedek tahmin edilmez; ücret yok ya da >5 sn eşleşmez. Alanlar `matched`, `onlyRawstats`, `onlyYolgo`, `ratio`, `ambiguousFallback`, `pairs`. */
    fun match(rawstats: Map<String, JSONObject>, yolgo: Map<String, JSONObject>): JSONObject {
        val pairs = JSONArray()
        val unusedRaw = rawstats.keys.toMutableSet()
        val unusedLive = yolgo.keys.toMutableSet()
        fun pair(r: String, y: String, kind: String) {
            unusedRaw.remove(r)
            unusedLive.remove(y)
            pairs.put(JSONObject().put("rawOfferKey", r).put("yolgoOfferKey", y).put("method", kind))
        }
        for (key in rawstats.keys.intersect(yolgo.keys)) pair(key, key, "offerKey")
        fun candidates(y: JSONObject): List<String> = unusedRaw.filter { key ->
            val r = rawstats.getValue(key)
            val rt = DatasetContract.number(r, "firstSeenAtMs")
            val yt = DatasetContract.number(y, "firstSeenAtMs")
            val rf = DatasetContract.number(r, "feeAzn")
            val yf = DatasetContract.number(y, "feeAzn")
            rt != null && yt != null && rf != null && yf != null && r.optString("platform") == y.optString("platform") && abs(rt - yt) <= 5000 && abs(rf - yf) <= .010001
        }
        val candidateMap = unusedLive.associateWith { candidates(yolgo.getValue(it)) }
        for ((y, c) in candidateMap) if (c.size == 1 && candidateMap.values.count { c.single() in it } == 1) pair(c.single(), y, "platform_time_fee")
        val matched = pairs.length()
        val denominator = matched + unusedRaw.size + unusedLive.size
        return JSONObject().put("matched", matched).put("onlyRawstats", unusedRaw.size).put("onlyYolgo", unusedLive.size)
            .put("ratio", if (denominator == 0) JSONObject.NULL else matched.toDouble() / denominator)
            .put("ambiguousFallback", candidateMap.count { it.value.size > 1 || (it.value.size == 1 && candidateMap.values.count { xs -> it.value.single() in xs } > 1) })
            .put("pairs", pairs)
    }

    fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** `.csv` boş olmayan satır − 1 başlık, `.jsonl` boş olmayan satır; başka uzantı null. */
    fun lineCount(file: File): Long? {
        if (!file.name.endsWith(".csv") && !file.name.endsWith(".jsonl")) return null
        var n = 0L
        file.useLines(Charsets.UTF_8) { lines -> lines.forEach { if (it.isNotBlank()) n++ } }
        return if (file.name.endsWith(".csv")) (n - 1).coerceAtLeast(0) else n
    }

    /** Yarıda kalan ham klasör değişimini onarır (`.raw-previous` → `rawstats`). */
    fun recoverRaw(dayDir: File) {
        val raw = File(dayDir, "rawstats")
        val old = File(dayDir, ".raw-previous")
        if (!raw.exists() && old.exists()) check(old.renameTo(raw))
    }

    /** Aynı gün yeniden toplama yalnız en yeni dosyaları tutar. */
    fun replaceRaw(dayDir: File, stage: File) {
        require(stage.isDirectory)
        recoverRaw(dayDir)
        val raw = File(dayDir, "rawstats")
        val old = File(dayDir, ".raw-previous")
        if (old.exists()) check(old.deleteRecursively())
        if (raw.exists()) check(raw.renameTo(old))
        if (!stage.renameTo(raw)) {
            if (old.exists()) check(old.renameTo(raw))
            error("replace failed")
        }
        old.deleteRecursively()
    }

    /** JSONL dosyasının her geçerli satırı (okunamayan atlanır). */
    fun rows(file: File, each: (JSONObject) -> Unit) {
        if (!file.isFile) return
        file.useLines(Charsets.UTF_8) { lines ->
            lines.filter { it.isNotBlank() }.forEach { line -> runCatching { JSONObject(line) }.getOrNull()?.let(each) }
        }
    }

    private fun last(file: File): LinkedHashMap<String, JSONObject> {
        val out = LinkedHashMap<String, JSONObject>()
        rows(file) { row ->
            val key = row.optString("offerKey")
            if (key.isNotEmpty()) out[key] = row
        }
        return out
    }

    /** Gün klasörü özeti (`summary`, 13.7): eşleşme, boş alan, rota, scooter kapsaması, vardiyalar, eksik elle türler. */
    fun summary(dayDir: File, zone: ZoneId, nowMs: Long): JSONObject {
        recoverRaw(dayDir)
        val raw = last(File(dayDir, "rawstats/economics-offers.jsonl"))
        val live = last(File(dayDir, "yolgo-live.jsonl"))
        val routes = HashMap<String, JSONObject>()
        rows(File(dayDir, "yolgo-routes.jsonl")) { routes[it.getString("recordId")] = it }
        for (row in live.values) {
            routes[row.optString("recordId")]?.let { route ->
                for (key in listOf("pickupAirKm", "deliveryAirKm", "routeKm", "routeSeconds", "demGainM", "demLossM")) row.put(key, route.opt(key) ?: JSONObject.NULL)
            }
        }
        val matching = match(raw, live)
        val pairArr = matching.getJSONArray("pairs")
        val matchedLive = (0 until pairArr.length()).map { pairArr.getJSONObject(it).getString("yolgoOfferKey") }.toSet()
        val counts = JSONObject()
        for (platform in listOf("wolt", "bolt")) {
            counts.put(platform, raw.values.count { it.optString("platform") == platform } + live.filter { it.key !in matchedLive && it.value.optString("platform") == platform }.size)
        }
        val total = live.size * SUMMARY_FIELDS.size
        val empty = live.values.sumOf { row -> SUMMARY_FIELDS.count { !row.has(it) || row.isNull(it) } }
        val shifts = JSONArray()
        rows(File(dayDir, "shifts.jsonl")) { shifts.put(it) }
        val starts = HashMap<Long, Long>()
        for (i in 0 until shifts.length()) {
            val s = shifts.getJSONObject(i)
            val start = s.optLong("shiftStartMs", 0)
            if (start > 0) starts[start] = DatasetContract.number(s, "shiftEndMs")?.toLong() ?: nowMs
        }
        val dayStart = LocalDate.parse(dayDir.name.take(10)).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.parse(dayDir.name.take(10)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val windows = ArrayList<Pair<Long, Long>>()
        for ((start, end) in starts) {
            val a = maxOf(start, dayStart)
            val b = minOf(end, dayEnd)
            if (b > a) windows.add(a to b)
        }
        val samples = java.util.TreeSet<Long>()
        val scooterIds = HashSet<String>()
        rows(File(dayDir, "rawstats/economics-scooter.jsonl")) { row ->
            DatasetContract.number(row, "wall_ms")?.toLong()?.let { at -> if (windows.any { at in it.first..it.second }) samples.add(at) }
            row.optString("scooter_id").takeIf { !row.isNull("scooter_id") && it.isNotEmpty() }?.let { scooterIds.add(it) }
        }
        var covered = 0L
        var previous: Long? = null
        for (at in samples) {
            val p = previous
            if (p != null && at - p in 1..5000 && windows.any { p >= it.first && at <= it.second }) covered += at - p
            previous = at
        }
        val shiftMs = windows.sumOf { it.second - it.first }
        val manualKinds = HashSet<String>()
        rows(File(dayDir, "manual.jsonl")) { manualKinds.add(it.optString("kind")) }
        return JSONObject().put("offers", counts).put("liveOffers", live.size).put("rawOffers", raw.size).put("matching", matching)
            .put("routeComputedRatio", if (live.isEmpty()) JSONObject.NULL else live.values.count { !it.isNull("routeKm") && it.has("routeKm") }.toDouble() / live.size)
            .put("emptyFieldRatio", if (total == 0) JSONObject.NULL else empty.toDouble() / total)
            .put("scooterCoverageRatio", if (shiftMs == 0L) JSONObject.NULL else (covered.toDouble() / shiftMs).coerceIn(0.0, 1.0))
            .put("scooterCoverageMs", covered).put("shiftDurationMs", shiftMs).put("coverageAsOfMs", nowMs)
            .put("scooter_id", scooterIds.singleOrNull() ?: JSONObject.NULL).put("scooter_id_source", if (scooterIds.size == 1) "rawstats" else "unavailable_or_multiple")
            .put("shifts", shifts).put("missingManual", JSONArray(REQUIRED_MANUAL.filter { it !in manualKinds }))
    }

    /** Dosya listesi + `manifest.json` + ZIP (13.7); [root] veri seti kökü (`files/TeklifVeriSeti/`), [dayDir] `<root>/<gün>_<ad>/`. Dönüş: özet + `day`, `folder`, `zip`, `collectionComplete`. */
    fun seal(root: File, dayDir: File, day: String, version: String, versionCode: Long, zone: ZoneId, nowMs: Long, monoNs: Long): JSONObject {
        DatasetContract.date(day)
        val summary = summary(dayDir, zone, nowMs)
        val rawMeta = File(dayDir, "rawstats/feed-manifest.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
        val files = JSONArray()
        val listed = dayDir.walkTopDown().onEnter { it == dayDir || !it.name.startsWith('.') }
            .filter { it.isFile && !it.name.startsWith('.') && it.name != "manifest.json" }.sortedBy { it.relativeTo(dayDir).path }.toList()
        for (file in listed) {
            val rows = lineCount(file)
            files.put(
                JSONObject().put("name", file.relativeTo(dayDir).path).put("bytes", file.length()).put("rows", rows ?: JSONObject.NULL)
                    .put("rows_source", if (rows == null) "binary_or_json_document" else "counted").put("sha256", sha(file)),
            )
        }
        val manifest = DatasetContract.common(nowMs, monoNs, scooter = summary.optString("scooter_id").takeIf { !summary.isNull("scooter_id") })
            .put("day", day).put("dayName", DatasetContract.folder(day).substring(11)).put("yolgoVersion", version).put("yolgoVersionCode", versionCode)
            .put("rawstatsVersion", rawMeta?.optJSONObject("producer")?.opt("versionName") ?: JSONObject.NULL)
            .put("flowVersions", JSONObject().put("live", if (File(dayDir, "yolgo-live.jsonl").isFile) 2 else JSONObject.NULL).put("logs", if (rawMeta == null) JSONObject.NULL else 3))
            .put("specVersion", DayLogSnapshot.SPEC_VERSION).put("timezone", zone.id).put("files", files).put("summary", summary)
            .put("matching", summary.getJSONObject("matching")).put("shifts", summary.getJSONArray("shifts"))
            .put("rawstatsFiles", rawMeta?.optJSONArray("files") ?: JSONArray())
            .put("collection", File(dayDir, "collection-status.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) } ?: JSONObject().put("complete", false).put("reason", "not_collected"))
        File(dayDir, "manifest.json").writeText(DatasetContract.flagUnknowns(manifest).toString())
        val name = "WEEM_teklif_${DatasetContract.folder(day)}.zip"
        val output = File(root, name)
        val temp = File(root, ".$name.tmp")
        ZipOutputStream(temp.outputStream().buffered()).use { zip ->
            for (file in listed + File(dayDir, "manifest.json")) {
                zip.putNextEntry(ZipEntry("${dayDir.name}/${file.relativeTo(dayDir).path}"))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        check(temp.renameTo(output))
        return JSONObject(summary.toString()).put("day", day).put("folder", dayDir.name).put("zip", output.path)
            .put("collectionComplete", manifest.getJSONObject("collection").optBoolean("complete"))
    }

    /** `collection-status.json`: ortak alanlar + `complete`, `reason`. */
    fun writeStatus(dayDir: File, ok: Boolean, nowMs: Long, monoNs: Long) {
        File(dayDir, "collection-status.json").writeText(
            DatasetContract.common(nowMs, monoNs).put("complete", ok).put("reason", if (ok) "verified_v3" else "v3_not_collected_retry_required").toString(),
        )
    }

    /** `YYYY-MM-DD_…` klasörleri yeniden eskiye; her biri için özet + `day`, `folder`, `collectionComplete`, varsa `zip`. */
    fun list(root: File, zone: ZoneId, nowMs: Long): List<JSONObject> =
        root.listFiles().orEmpty().filter { it.isDirectory && Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}_.+").matches(it.name) }.sortedByDescending { it.name }.map { dir ->
            val status = File(dir, "collection-status.json").takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            summary(dir, zone, nowMs).put("day", dir.name.take(10)).put("folder", dir.name).put("collectionComplete", status?.optBoolean("complete") == true)
                .put("zip", File(root, "WEEM_teklif_${dir.name}.zip").takeIf { it.isFile }?.path ?: JSONObject.NULL)
        }

    /**
     * Gün toplama (13.6; sağlayıcı/sayfa yok): [source] `:core` kilidi altında dondurulmuş gün klasörü kopyası. Geçici `.raw-<uuid>` → [DayLogSnapshot.build] →
     * `replaceRaw` → durum → mühür. Hata: durum `complete=false`, hata yukarı.
     */
    fun collect(
        root: File, source: File, day: String, secret: ByteArray, version: String, versionCode: Long, zone: ZoneId, nowMs: Long, monoNs: Long,
    ): JSONObject {
        DatasetContract.date(day)
        val dayDir = File(root, DatasetContract.folder(day)).also { it.mkdirs() }
        val stage = File(dayDir, ".raw-${java.util.UUID.randomUUID()}").also { check(it.mkdir()) }
        try {
            val meta = DayLogSnapshot.build(source, stage, day, secret, version, nowMs, monoNs)
            val files = meta.getJSONArray("files")
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val output = File(stage, f.getString("name"))
                require(output.length() == f.getLong("bytes") && sha(output) == f.getString("sha256"))
                require(lineCount(output) == f.getLong("rows"))
            }
            val names = (0 until files.length()).map { files.getJSONObject(it).getString("name") }.toSet()
            require("economics-offers.jsonl" in names && "economics-energy.jsonl" in names && "gps.csv" in names && "screens.jsonl" in names)
            replaceRaw(dayDir, stage)
            writeStatus(dayDir, true, nowMs, monoNs)
            return seal(root, dayDir, day, version, versionCode, zone, nowMs, monoNs)
        } catch (e: Exception) {
            writeStatus(dayDir, false, nowMs, monoNs)
            throw e
        } finally {
            stage.deleteRecursively()
        }
    }
}
