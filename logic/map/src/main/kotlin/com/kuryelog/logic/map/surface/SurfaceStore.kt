package com.kuryelog.logic.map.surface

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Y13 yüzey ölçümlerinin kendi sürümlü günlüğü (`files/surface-v2/`; YolGo `surface/surface_store.dart` çalışan izolatının işleri, aynı dosya
 * adları ve biçim, VERI_SOZLESMESI Y13): `observations.jsonl`, `metadata.json`, `decisions.jsonl`, `history.json`, bozuk satırlar
 * `<k>.unreadable.jsonl`, dışa aktarım `WEEM-surface-diagnostics.json`. Eski titreşim/trafik kayıtlarına dokunulmaz; bu depo onlardan fiziksel
 * ölçü uydurmaz. Elle kötü noktalar `MarkStore`'dadır (Ses−; A6.11), bu depoda değil. Çağrılar eşzamanlıdır ve arka plan iş parçacığından
 * yapılır; her çağrı kendi dosyasını atomik yazar (geçici dosya + yeniden adlandırma).
 */
class SurfaceStore(private val root: File, private val nowMs: () -> Long = System::currentTimeMillis) {
    private val journal = File(root, "observations.jsonl")
    private val metadata = File(root, "metadata.json")
    private val decisionFile = File(root, "decisions.jsonl")
    private val historyFile = File(root, "history.json")
    private val history = LinkedHashMap<String, SegmentHistory>()
    private var rolledDay = ""
    private val decisionMap = LinkedHashMap<String, JSONObject>()
    private val profileMap = LinkedHashMap<String, SurfaceProfile>()
    private val derived = LinkedHashSet<String>()
    private val unreadable = LinkedHashMap<String, MutableList<String>>()
    private var writable = false
    private var current = SurfaceProfile()
    private val observations = LinkedHashMap<String, SurfaceObservation>()
    private val deletedDays = LinkedHashSet<String>()
    private val bySegment = HashMap<String, LinkedHashMap<String, SurfaceObservation>>()
    private val byLeg = HashMap<String, MutableSet<String>>() // "$day|$leg" → gözlem id'leri
    private val cached = LinkedHashMap<String, SurfaceSummary>()
    private val dirty = LinkedHashSet<String>()
    private var decisionFileRows = 0
    private val listeners = ArrayList<(List<SurfaceSummary>) -> Unit>()

    /** Dışlanan aralıklar sefer (gün + etap) başına tutulur; geri alma yalnız o seferin satırlarını tarar. */
    private class LegSpans(val day: String, val leg: String) {
        val spans = ArrayList<LongArray>()
        fun covers(start: Long, end: Long) = spans.any { end >= it[0] && start <= it[1] }
    }

    private val exclusions = LinkedHashMap<String, LegSpans>()

    val profile: SurfaceProfile get() = current
    val profiles: List<SurfaceProfile> get() = profileMap.values.toList()
    val summaries: List<SurfaceSummary> @Synchronized get() = cached.values.toList()
    val decisions: List<JSONObject> @Synchronized get() = decisionMap.values.toList()
    val deleted: Set<String> @Synchronized get() = deletedDays.toSet()

    private var loadError: String? = null

    init {
        root.mkdirs()
        try { load() } catch (e: Exception) { writable = false; loadError = e.message }
    }

    @Synchronized fun onChanged(listener: (List<SurfaceSummary>) -> Unit) { listeners += listener }

    private fun legKey(day: String, leg: String) = "$day|$leg"

    private fun index(row: SurfaceObservation) {
        bySegment.getOrPut(row.segment) { LinkedHashMap() }[row.id] = row; dirty += row.segment
        byLeg.getOrPut(legKey(row.day, row.leg)) { LinkedHashSet() }.add(row.id)
    }

    private fun unindex(row: SurfaceObservation) {
        observations.remove(row.id); bySegment[row.segment]?.remove(row.id); dirty += row.segment
        val ids = byLeg[legKey(row.day, row.leg)]; ids?.remove(row.id)
        if (ids != null && ids.isEmpty()) byLeg.remove(legKey(row.day, row.leg))
    }

    private fun accept(row: SurfaceObservation) = row.day !in deletedDays && exclusions[legKey(row.day, row.leg)]?.covers(row.startMs, row.endMs) != true

    private fun removeRejected() { observations.values.filter { !accept(it) }.forEach { unindex(it) } }

    private fun addExclusion(j: JSONObject) {
        val day = j.opt("day").toString(); val leg = j.opt("leg").toString()
        exclusions.getOrPut(legKey(day, leg)) { LegSpans(day, leg) }.spans.add(longArrayOf(j.getLong("since"), j.getLong("until")))
    }

    private fun atomic(file: File, text: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(temp.renameTo(file)) { "Atomic rename failed" }
    }

    private fun append(file: File, text: String) = FileOutputStream(file, true).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }

    private fun saveMetadata() {
        // Include archived setup identities and provenance for removable references.
        val ex = JSONArray()
        for (e in exclusions.values) for (x in e.spans) ex.put(JSONObject().put("day", e.day).put("leg", e.leg).put("since", x[0]).put("until", x[1]))
        atomic(metadata, JSONObject().put("v", SURFACE_ALGORITHM_VERSION).put("profile", current.toJson())
            .put("profiles", JSONArray(profileMap.values.map { it.toJson() })).put("derived", JSONArray(derived.toList()))
            .put("deleted", JSONArray(deletedDays.toList())).put("exclusions", ex).toString())
    }

    private fun preserveUnreadable() {
        // Preserve malformed/unknown rows verbatim before replacing a journal. They cannot contribute invented measurements, but remain recoverable.
        for ((name, lines) in unreadable) if (lines.isNotEmpty()) append(File(root, "$name.unreadable.jsonl"), lines.joinToString("\n") + "\n")
        unreadable.clear()
    }

    private fun compact() {
        preserveUnreadable()
        atomic(journal, observations.values.joinToString("") { it.toJson().toString() + "\n" })
    }

    private fun compactDecisions() {
        preserveUnreadable()
        atomic(decisionFile, decisionMap.values.joinToString("") { it.toString() + "\n" })
        decisionFileRows = decisionMap.size
    }

    /** Açık "düz" ve "pürüzlü" etiketli geçişlerden hız bandı başına referans; her etikette ≥3 geçiş (≥25 m) ve ayrışma gerekir. */
    private fun references(base: SurfaceProfile): SurfaceProfile {
        var updated = SurfaceProfile.fromJson(base.toJson().put("bands", JSONObject()))
        for (bin in 0..3) {
            fun labeled(label: String): List<Double> {
                val passes = LinkedHashMap<String, MutableList<SurfaceObservation>>()
                for (r in observations.values) if (r.profile == base.id && SurfaceProfile.speedBand(r.speed) == bin && r.calibrationLabel == label)
                    passes.getOrPut(r.pass) { ArrayList() }.add(r)
                return passes.values.filter { rows -> rows.sumOf { it.meters } >= 25 }
                    .map { rows -> Math.sqrt(rows.sumOf { it.rms * it.rms * it.meters } / rows.sumOf { it.meters }) }
            }
            val smooth = labeled("smooth"); val rough = labeled("rough")
            if (smooth.size >= 3 && rough.size >= 3) {
                val band = CalibrationBand(median(smooth), median(rough), smooth.size, rough.size, nowMs())
                if (band.validated) updated = updated.calibrated(bin, band)
            }
        }
        return updated
    }

    private fun refreshReferences() {
        for (id in derived) profileMap[id]?.let { profileMap[id] = references(it) }
        current = profileMap[current.id] ?: current
        dirty.addAll(bySegment.keys); dirty.addAll(history.keys)
    }

    private fun saveHistory() {
        atomic(historyFile, JSONObject().put("v", 1).put("segments", JSONObject().also { o -> for ((k, v) in history) o.put(k, v.toJson()) }).toString())
    }

    /** Gün silinince (kullanıcı) o günün özetlenmiş geçişleri de gider. */
    private fun dropDeletedHistory() {
        var changed = false
        for ((key, h) in history) {
            val before = h.passes.size
            h.passes.removeAll { it.dayId in deletedDays }
            if (h.passes.size != before) { changed = true; dirty += key }
        }
        history.entries.removeAll { it.value.passes.isEmpty() }
        if (changed) saveHistory()
    }

    /**
     * Gün sonu (1.7.2, kullanıcı): bütün pencereleri dünden önce biten geçişler özete indirilir, ham satırları silinir. Kalibrasyon etiketli
     * sürüşler silinmez (referans profil onlardan hesaplanır). Gün değişince bir kez çalışır; ham pencere dosyası süresiz büyümez.
     */
    private fun rollUp(now: Long) {
        val today = BakuDay.of(now)
        if (rolledDay == today) return
        rolledDay = today
        val yesterday = BakuDay.of(now - 86_400_000L)
        val passes = LinkedHashMap<String, MutableList<SurfaceObservation>>()
        for (r in observations.values) {
            if (r.calibrationLabel != null || r.meters <= 0) continue
            passes.getOrPut(r.segment + "\u0000" + r.pass) { ArrayList() }.add(r)
        }
        val done = ArrayList<SurfaceObservation>()
        for (rows in passes.values) {
            val last = rows.maxOf { it.at }
            if (BakuDay.of(last).compareTo(yesterday) >= 0) continue
            val evidence = SurfaceScoring.passEvidence(rows, profileMap[rows.first().profile] ?: current)
            if (evidence != null) {
                val h = history.getOrPut(rows.first().segment) { SegmentHistory(SegmentMeta.of(rows.first())) }
                if (h.passes.none { it.id == evidence.id }) h.passes.add(evidence)
            }
            done.addAll(rows)
        }
        for (h in history.values) h.prune(now)
        history.entries.removeAll { it.value.passes.isEmpty() }
        if (done.isEmpty()) return
        // Önce özet, sonra ham satır silinir: arada kapanırsa satır yeniden özetlenir (aynı geçiş tekrar eklenmez).
        saveHistory()
        done.forEach { unindex(it) }
        compact()
    }

    private fun load() {
        if (metadata.exists()) {
            try {
                val meta = JSONObject(metadata.readText())
                current = SurfaceProfile.fromJson(meta.getJSONObject("profile"))
                meta.optJSONArray("profiles")?.let { a -> for (i in 0 until a.length()) SurfaceProfile.fromJson(a.getJSONObject(i)).let { profileMap[it.id] = it } }
                meta.optJSONArray("derived")?.let { a -> for (i in 0 until a.length()) derived += a.getString(i) }
                meta.optJSONArray("deleted")?.let { a -> for (i in 0 until a.length()) deletedDays += a.getString(i) }
                meta.optJSONArray("exclusions")?.let { a -> for (i in 0 until a.length()) addExclusion(a.getJSONObject(i)) }
            } catch (e: Exception) { throw IllegalStateException("Unreadable surface metadata") }
        }
        var rejected = 0
        if (journal.exists()) for (line in journal.readLines()) {
            try {
                val row = SurfaceObservation.fromJson(JSONObject(line))
                if (accept(row)) { observations[row.id] = row; index(row) } else rejected++
            } catch (e: Exception) { if (line.isNotBlank()) unreadable.getOrPut("observations") { ArrayList() }.add(line) }
        }
        profileMap[current.id] = current
        if (historyFile.exists()) {
            try {
                val j = JSONObject(historyFile.readText())
                j.optJSONObject("segments")?.let { s -> for (k in s.keys()) history[k] = SegmentHistory.fromJson(s.getJSONObject(k)) }
            } catch (e: Exception) {
                // Bozuk özet dosyası ölçümü durdurmaz: kenara alınır, ham pencerelerle devam edilir.
                history.clear()
                historyFile.renameTo(File(root, "history.unreadable.${nowMs()}.json"))
            }
        }
        if (decisionFile.exists()) for (line in decisionFile.readLines()) {
            try {
                val row = JSONObject(line)
                if (row.opt("day") !in deletedDays) decisionMap[row.opt("id").toString()] = row
            } catch (e: Exception) { if (line.isNotBlank()) unreadable.getOrPut("decisions") { ArrayList() }.add(line) }
        }
        decisionFileRows = decisionMap.size
        while (decisionMap.size > 1000) decisionMap.remove(decisionMap.keys.first())
        writable = true
        // Geç gelen paketler en çok bir önceki günün etabına ait olabilir. Daha eski dışlamalar (satırları dosyadan çoktan silinmiş) budanır;
        // dosyada hâlâ dışlanmış satır varsa önce sıkıştırılır.
        val now = nowMs()
        fun stale(e: LegSpans): Boolean {
            val stamp = if (e.day.startsWith("yolgo-")) e.day.substring(6).toLongOrNull() else null
            return e.day in deletedDays || (stamp != null && now - stamp > 3 * 86_400_000L)
        }
        if (exclusions.values.any { stale(it) }) {
            if (rejected > 0) compact()
            exclusions.entries.removeAll { stale(it.value) }
            saveMetadata()
        }
        dropDeletedHistory()
        rollUp(now)
        dirty.addAll(history.keys)
        flush(full = true)
    }

    private fun requireWritable() { if (!writable) throw IllegalStateException("Unreadable surface data; preserve originals" + (loadError?.let { ": $it" } ?: "")) }

    /** Değişen kesimlerin özetlerini yeniden hesaplar; bir şey değiştiyse dinleyicilere haber verir. */
    private fun flush(full: Boolean = false, metaChanged: Boolean = false) {
        var changed = full || metaChanged
        val now = nowMs()
        for (key in dirty.toList()) {
            val hist = history[key]
            val s = SurfaceScoring.summarizeSurface(bySegment[key]?.values.orEmpty(), current, now, if (hist == null) emptyMap() else mapOf(key to hist))
            if (s.isEmpty()) { if (cached.remove(key) != null) changed = true } else { cached[key] = s.single(); changed = true }
        }
        dirty.clear()
        if (changed) { val snapshot = cached.values.toList(); listeners.forEach { it(snapshot) } }
    }

    // ---------- İşlemler ----------

    @Synchronized fun add(rows: List<SurfaceObservation>) {
        requireWritable()
        if (rows.isEmpty()) return
        rollUp(nowMs())
        val accepted = ArrayList<SurfaceObservation>()
        for (r in rows) if (r.day !in deletedDays && accept(r) && !observations.containsKey(r.id) && accepted.none { it.id == r.id }) accepted += r
        if (accepted.isNotEmpty()) {
            append(journal, accepted.joinToString("") { it.toJson().toString() + "\n" })
            for (r in accepted) { observations[r.id] = r; index(r) }
        }
        flush()
    }

    /** Geç gelen yürüme/varış sınırı: [since]..[until] (yoksa sonsuz) aralığındaki satırlar ve sonradan gelecekler dışlanır. */
    @Synchronized fun retract(day: String, leg: String, since: Long, until: Long? = null) {
        requireWritable()
        val end0 = until ?: Long.MAX_VALUE
        val spans = exclusions.getOrPut(legKey(day, leg)) { LegSpans(day, leg) }
        // Aynı sınır paketi yeniden gelirse (onay öncesi tekrar yoklama) iş yok.
        if (spans.spans.any { it[0] <= since && it[1] >= end0 }) return
        val overlapping = spans.spans.filter { it[0] <= end0 && it[1] >= since }
        var start = since; var end = end0
        for (x in overlapping) { if (x[0] < start) start = x[0]; if (x[1] > end) end = x[1] }
        spans.spans.removeAll(overlapping.toSet()); spans.spans.add(longArrayOf(start, end))
        // Yalnız bu seferin, yeni aralıkla çakışan satırları silinir.
        val removed = byLeg[legKey(day, leg)].orEmpty().mapNotNull { observations[it] }.filter { it.endMs >= start && it.startMs <= end }
        removed.forEach { unindex(it) }
        var meta = false
        if (removed.isNotEmpty()) { refreshReferences(); compact(); meta = true }
        saveMetadata()
        flush(metaChanged = meta)
    }

    @Synchronized fun deleteDay(day: String) {
        deletedDays += day
        requireWritable()
        applyDeletion()
    }

    /** Bir Bakü tarihinin bütün günlerini siler (gün kimliği `yolgo-<ms>`); silinen gün kimliklerini döndürür. */
    @Synchronized fun deleteDate(date: String): List<String> {
        requireWritable()
        val ids = (observations.values.map { it.day } + decisionMap.values.map { it.opt("day").toString() } + history.values.flatMap { h -> h.passes.map { it.dayId } })
            .filter { day -> day.startsWith("yolgo-") && day.substring(6).toLongOrNull()?.let { BakuDay.of(it) == date } == true }.toSet()
        deletedDays += ids
        applyDeletion()
        return ids.toList()
    }

    private fun applyDeletion() {
        exclusions.entries.removeAll { it.value.day in deletedDays }; removeRejected(); dropDeletedHistory(); refreshReferences()
        decisionMap.entries.removeAll { it.value.opt("day") in deletedDays }; compactDecisions(); saveMetadata(); compact()
        flush(metaChanged = true)
    }

    @Synchronized fun saveProfile(value: SurfaceProfile) {
        requireWritable()
        profileMap[current.id] = current
        current = value; profileMap[current.id] = current
        dirty.addAll(bySegment.keys); saveMetadata(); flush(metaChanged = true)
    }

    /** Etiketli düz/pürüzlü geçişlerden bu profilin hız bandı referanslarını (yeniden) hesaplar. */
    @Synchronized fun calibrate() {
        requireWritable()
        current = references(current); profileMap[current.id] = current; derived += current.id
        dirty.addAll(bySegment.keys); saveMetadata(); flush(metaChanged = true)
    }

    @Synchronized fun recordDecisions(rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        requireWritable()
        val fresh = ArrayList<JSONObject>()
        for (row in rows) {
            val id = row.opt("id").toString()
            if (row.opt("day") !in deletedDays && !decisionMap.containsKey(id)) { decisionMap[id] = row; fresh += row }
        }
        if (fresh.isNotEmpty()) { append(decisionFile, fresh.joinToString("") { it.toString() + "\n" }); decisionFileRows += fresh.size }
        while (decisionMap.size > 1000) decisionMap.remove(decisionMap.keys.first())
        // Dosya 1000 sınırına gelince her yeni kararda baştan yazılıyordu; artık 2000 satırı geçince bir kez sıkıştırılır.
        if (decisionFileRows > 2000) compactDecisions()
        if (fresh.isNotEmpty()) flush(metaChanged = true)
    }

    /** Bir kesimdeki darbe olayları (kimlikle tekil, en yeni 100). */
    @Synchronized fun impactEvents(segment: String): List<JSONObject> {
        requireWritable()
        val events = LinkedHashMap<String, JSONObject>()
        for (row in bySegment[segment]?.values.orEmpty()) {
            if (row.profile != current.id) continue
            for (hit in row.impacts) {
                val at = (hit.opt("at") as? Number)?.toLong() ?: row.endMs
                events[hit.opt("id").toString()] = JSONObject(hit.toString()).put("day", row.day).put("leg", row.leg).put("utc", row.at - (row.endMs - at))
                    .put("lat", row.lat).put("lon", row.lon).put("accuracy", row.accuracy).put("way", row.way).put("source", "automatic-neutral")
            }
        }
        return events.values.sortedByDescending { it.getLong("utc") }.take(100)
    }

    @Synchronized fun export(): String {
        requireWritable()
        val file = File(root, "WEEM-surface-diagnostics.json")
        file.writeText(JSONObject().put("v", SURFACE_ALGORITHM_VERSION).put("units", "SI; UTC + boot milliseconds").put("profile", current.toJson())
            .put("profiles", JSONArray(profileMap.values.map { it.toJson() })).put("decisions", JSONArray(decisionMap.values.toList()))
            .put("observations", JSONArray(observations.values.map { it.toJson() }))
            .put("history", JSONObject().also { o -> for ((k, v) in history) o.put(k, v.toJson()) }).toString())
        return file.path
    }
}
