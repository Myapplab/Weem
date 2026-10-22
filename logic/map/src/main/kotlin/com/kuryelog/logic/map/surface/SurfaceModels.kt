package com.kuryelog.logic.map.surface

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Y13 yüzey ve titreşim ölçümü — modeller ve puanlama (YolGo `lib/surface/surface_models.dart` @ 870ab6e, satır satır; sürüm 2).
// Birimler: m, m/s, m/s², m/s³, rad/s. UTC tarihleri ile önyüklemeden beri monoton ms ayrıdır; monoton zaman cihaz yeniden başlayınca
// karşılaştırılmaz. JSON alan adları YolGo `surface-v2/` biçimiyle aynıdır (VERI_SOZLESMESI).

const val SURFACE_ALGORITHM_VERSION = 2

/** Bakü takvim günü (sabit UTC+4): `yyyy-MM-dd` (YolGo `bakuDay`). */
object BakuDay {
    fun of(atMs: Long): String = java.time.LocalDate.ofEpochDay(Math.floorDiv(atMs + 4 * 3_600_000L, 86_400_000L)).toString()
}

/** Bakü yerel saati (sabit UTC+4) `yyyy-MM-dd HH:mm[:ss]` (YolGo `bakuTime(...).toIso8601String().substring(...)`). */
object BakuTime {
    private val SEC = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    private val MIN = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.US)
    private fun at(ms: Long) = java.time.LocalDateTime.ofEpochSecond(Math.floorDiv(ms, 1000L), 0, java.time.ZoneOffset.ofHours(4))
    fun stamp(ms: Long, seconds: Boolean = true): String = at(ms).format(if (seconds) SEC else MIN)
}

fun median(values: Iterable<Double>): Double {
    val a = values.filter { it.isFinite() }.sorted()
    if (a.isEmpty()) return 0.0
    val n = a.size / 2
    return if (a.size % 2 == 1) a[n] else (a[n - 1] + a[n]) / 2
}

internal fun JSONObject.d(key: String, fallback: Double = 0.0): Double = (opt(key) as? Number)?.toDouble() ?: fallback
internal fun JSONObject.n(key: String): Long = (opt(key) as? Number)?.toLong() ?: 0L
internal fun JSONObject.dOrNull(key: String): Double? = (opt(key) as? Number)?.toDouble()
internal fun JSONObject.list(key: String): List<Double> = (opt(key) as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { (a.opt(it) as? Number)?.toDouble() } } ?: emptyList()

/** Sabit kurulum kimliği: scooter + telefon + tutucu. Yeni kurulum = yeni kimlik. Açık düz/pürüzlü etiketler referans oluşturur; sıradan sürüşler onları ezmez. */
class SurfaceProfile(
    val id: String = "g3-default", val name: String = "Ninebot G3 · varsayılan", val vehicleId: String = "g3",
    val version: Int = 1, val segmentM: Double = 50.0, val bufferMs: Int = 8000,
    val smoothRms: Double = 0.18, val roughRms: Double = 0.90, val impactThreshold: Double = 6.0,
    val highPassHz: Double = 0.7, val lowPassHz: Double = 35.0,
    val bands: Map<Int, CalibrationBand> = emptyMap(), val mount: List<Double> = emptyList(),
) {
    companion object {
        fun speedBand(speed: Double) = if (speed < 2.2) 0 else if (speed < 4.2) 1 else if (speed < 6) 2 else 3

        fun fromJson(j: JSONObject): SurfaceProfile {
            fun d(k: String, fallback: Double) = j.dOrNull(k)?.takeIf { it.isFinite() } ?: fallback
            val smooth = d("smoothRms", 0.18).coerceIn(0.0, 10.0)
            val bandsJson = j.optJSONObject("bands")
            val bands = LinkedHashMap<Int, CalibrationBand>()
            if (bandsJson != null) for (key in bandsJson.keys()) key.toIntOrNull()?.let { bands[it] = CalibrationBand.fromJson(bandsJson.getJSONObject(key)) }
            return SurfaceProfile(
                id = j.optString("id", "g3-default"), name = j.optString("name", "Profil"), vehicleId = j.optString("vehicleId", "g3"),
                version = j.n("version").toInt().let { if (j.has("version")) it else 1 }, segmentM = d("segmentM", 50.0).coerceIn(25.0, 50.0),
                bufferMs = (if (j.has("bufferMs")) j.n("bufferMs").toInt() else 8000).coerceIn(5000, 10000),
                smoothRms = smooth, roughRms = max(smooth + .1, d("roughRms", .9).coerceIn(.1, 30.0)),
                impactThreshold = d("impactThreshold", 6.0).coerceIn(1.0, 30.0), highPassHz = d("highPassHz", 0.7).coerceIn(.1, 3.0),
                lowPassHz = d("lowPassHz", 35.0).coerceIn(10.0, 40.0), mount = j.list("mount"), bands = bands,
            )
        }
    }

    fun calibration(speed: Double): CalibrationBand? = bands[speedBand(speed)]
    fun roughThreshold(speed: Double): Double = calibration(speed)?.rough ?: roughRms
    fun shockScore(hardest: Double): Double = 100 - ((hardest - impactThreshold) / impactThreshold).coerceIn(0.0, 1.0) * 65

    fun score(rms: Double, roughFraction: Double, hardest: Double, speed: Double): Double {
        val b = calibration(speed)
        val clean = b?.smooth ?: smoothRms
        val rough = b?.rough ?: roughRms
        val normalized = ((rms - clean) / max(rough - clean, 0.1)).coerceIn(0.0, 1.0)
        // Correlated RMS/rough distance use a maximum rather than additive penalties.
        val vibrationPenalty = max(normalized.pow(0.8) * 85, roughFraction * 75)
        val shockPenalty = 100 - shockScore(hardest)
        return (100 - max(vibrationPenalty, shockPenalty)).coerceIn(0.0, 100.0)
    }

    fun calibrated(band: Int, value: CalibrationBand) = SurfaceProfile(id, name, vehicleId, version + 1, segmentM, bufferMs, smoothRms, roughRms,
        impactThreshold, highPassHz, lowPassHz, bands + (band to value), mount)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("vehicleId", vehicleId).put("version", version)
        .put("segmentM", segmentM).put("bufferMs", bufferMs).put("smoothRms", smoothRms).put("roughRms", roughRms)
        .put("impactThreshold", impactThreshold).put("highPassHz", highPassHz).put("lowPassHz", lowPassHz)
        .put("mount", JSONArray(mount)).put("bands", JSONObject().also { o -> for ((k, v) in bands) o.put("$k", v.toJson()) })
}

class CalibrationBand(val smooth: Double, val rough: Double, val smoothPasses: Int, val roughPasses: Int, val at: Long) {
    val validated get() = smoothPasses >= 3 && roughPasses >= 3 && rough > smooth + 0.1
    fun toJson(): JSONObject = JSONObject().put("smooth", smooth).put("rough", rough).put("smoothPasses", smoothPasses).put("roughPasses", roughPasses).put("at", at)
    companion object {
        fun fromJson(j: JSONObject) = CalibrationBand(j.getDouble("smooth"), j.getDouble("rough"), j.getInt("smoothPasses"), j.getInt("roughPasses"), j.getLong("at"))
    }
}

/** 50 m'lik (profil kesim boyu) bir kesimden gelen bir ölçüm penceresi. */
data class SurfaceObservation(
    val id: String, val day: String, val leg: String, val pass: String, val segment: String, val profile: String, val at: Long,
    val startMs: Long, val endMs: Long, val way: Long, val direction: Int, val segmentIndex: Int, val profileVersion: Int, val meters: Double,
    val rms: Double, val roughM: Double, val speed: Double, val accuracy: Double, val lat: Double, val lon: Double, val sampleHz: Double,
    val confidence: Double, val impacts: List<JSONObject> = emptyList(), val jerk: Double? = null, val gyro: Double? = null,
    val bands: List<Double> = emptyList(), val calibrationLabel: String? = null, val segmentLength: Double = 50.0, val segmentGridM: Double = 50.0,
) {
    fun toJson(): JSONObject = JSONObject().put("v", SURFACE_ALGORITHM_VERSION).put("id", id).put("day", day).put("leg", leg).put("pass", pass)
        .put("segment", segment).put("profile", profile).put("at", at).put("startMs", startMs).put("endMs", endMs).put("way", way)
        .put("direction", direction).put("segmentIndex", segmentIndex).put("profileVersion", profileVersion).put("meters", meters)
        .put("rms", rms).put("roughM", roughM).put("speed", speed).put("accuracy", accuracy).put("lat", lat).put("lon", lon)
        .put("sampleHz", sampleHz).put("confidence", confidence).put("impacts", JSONArray(impacts.map { JSONObject(it.toString()) }))
        .put("jerk", jerk ?: JSONObject.NULL).put("gyro", gyro ?: JSONObject.NULL).put("bands", JSONArray(bands))
        .put("calibrationLabel", calibrationLabel ?: JSONObject.NULL).put("segmentLength", segmentLength).put("segmentGridM", segmentGridM)

    companion object {
        fun fromJson(j: JSONObject) = SurfaceObservation(
            id = j.opt("id").toString(), day = j.opt("day").toString(), leg = j.opt("leg").toString(), pass = j.opt("pass").toString(),
            segment = j.opt("segment").toString(), profile = j.opt("profile").toString(), at = j.n("at"), startMs = j.n("startMs"), endMs = j.n("endMs"),
            way = j.n("way"), direction = j.n("direction").toInt(), segmentIndex = j.n("segmentIndex").toInt(), profileVersion = j.n("profileVersion").toInt(),
            meters = j.d("meters"), rms = j.d("rms"), roughM = j.d("roughM"), speed = j.d("speed"), accuracy = j.d("accuracy"), lat = j.d("lat"),
            lon = j.d("lon"), sampleHz = j.d("sampleHz"), confidence = j.d("confidence"), jerk = j.dOrNull("jerk"), gyro = j.dOrNull("gyro"),
            bands = j.list("bands"),
            impacts = (j.opt("impacts") as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList(),
            calibrationLabel = j.opt("calibrationLabel") as? String, segmentLength = j.d("segmentLength", 50.0), segmentGridM = j.d("segmentGridM", 50.0),
        )
    }
}

class RoadBandEvidence(val band: Int, val rms: Double, val passes: Int, val speed: Double) {
    fun toJson(): JSONObject = JSONObject().put("band", band).put("rms", rms).put("passes", passes).put("speed", speed)
    companion object {
        fun fromJson(j: JSONObject) = RoadBandEvidence(j.getInt("band"), j.getDouble("rms"), j.getInt("passes"), j.getDouble("speed"))
    }
}

class SurfaceSummary(
    val key: String, val way: Long, val direction: Int, val segmentIndex: Int, val profile: String, val score: Double, val confidence: Double,
    val meters: Double, val roughM: Double, val passes: Int, val lastAt: Long, val rms: Double, val accuracy: Double, val impacts: Int, val hardest: Double,
    val lat: Double, val lon: Double, val p95: Double? = null, val jerk: Double? = null, val gyro: Double? = null, val bands: List<Double> = emptyList(),
    val calibrated: Boolean = false, val segmentLength: Double = 50.0, val segmentGridM: Double = 50.0,
    /** Geçişlerin medyan hızı (m/s); eski türetilmiş özetlerde yok. */
    val speed: Double? = null, val roadBands: Map<Int, RoadBandEvidence> = emptyMap(), val confirmedShock: Boolean = false,
) {
    /** En çok geçişi olan hız bandı (eşitte düşük bant). */
    val dominantRoadBand: RoadBandEvidence? get() = roadBands.values.sortedWith(compareByDescending<RoadBandEvidence> { it.passes }.thenBy { it.band }).firstOrNull()
    val roughFraction get() = if (meters <= 0) 0.0 else roughM / meters
    val impactsPer100M: Double? get() = if (meters < 20) null else impacts / meters * 100
    val coverage get() = (meters / max(1, passes) / segmentLength).coerceIn(0.0, 1.0)

    fun toJson(): JSONObject = JSONObject().put("key", key).put("way", way).put("direction", direction).put("segmentIndex", segmentIndex)
        .put("profile", profile).put("score", score).put("confidence", confidence).put("meters", meters).put("roughM", roughM).put("passes", passes)
        .put("lastAt", lastAt).put("rms", rms).put("accuracy", accuracy).put("impacts", impacts).put("hardest", hardest).put("lat", lat).put("lon", lon)
        .put("p95", p95 ?: JSONObject.NULL).put("jerk", jerk ?: JSONObject.NULL).put("gyro", gyro ?: JSONObject.NULL).put("bands", JSONArray(bands))
        .put("calibrated", calibrated).put("segmentLength", segmentLength).put("segmentGridM", segmentGridM).put("speed", speed ?: JSONObject.NULL)
        .put("confirmedShock", confirmedShock).put("roadBands", JSONObject().also { o -> for ((k, v) in roadBands) o.put("$k", v.toJson()) })

    companion object {
        fun fromJson(j: JSONObject): SurfaceSummary {
            val rb = LinkedHashMap<Int, RoadBandEvidence>()
            j.optJSONObject("roadBands")?.let { o -> for (k in o.keys()) rb[k.toInt()] = RoadBandEvidence.fromJson(o.getJSONObject(k)) }
            return SurfaceSummary(
                key = j.opt("key").toString(), way = j.n("way"), direction = j.n("direction").toInt(), segmentIndex = j.n("segmentIndex").toInt(),
                profile = j.opt("profile").toString(), score = j.d("score"), confidence = j.d("confidence"), meters = j.d("meters"), roughM = j.d("roughM"),
                passes = j.n("passes").toInt(), lastAt = j.n("lastAt"), rms = j.d("rms"), accuracy = j.d("accuracy"), impacts = j.n("impacts").toInt(),
                hardest = j.d("hardest"), lat = j.d("lat"), lon = j.d("lon"), p95 = j.dOrNull("p95"), jerk = j.dOrNull("jerk"), gyro = j.dOrNull("gyro"),
                bands = j.list("bands"), calibrated = j.optBoolean("calibrated"), segmentLength = j.d("segmentLength", 50.0), segmentGridM = j.d("segmentGridM", 50.0),
                speed = j.dOrNull("speed"), confirmedShock = j.optBoolean("confirmedShock"), roadBands = rb,
            )
        }
    }
}

/**
 * 1.7.2 (kullanıcı, 10 Eki 2026): her geçiş kaydedilir. Ham pencereler bugün ve dün tutulur; daha eski geçişler gün sonunda bu küçük özete
 * indirilir ve ham satırlar silinir (kayıt hiç durmaz, yer büyümez). Puan özetten her seferinde yeniden hesaplanır, böylece sonradan yapılan
 * kalibrasyon eski geçişlere de uygulanır. [dayId]: gün kimliği (gün silinince geçiş de silinir); [date]: Bakü tarihi.
 */
class PassEvidence(
    val id: String, val dayId: String, val date: String, val profile: String, val at: Long, val meters: Double, val roughM: Double,
    val rms: Double, val peak: Double, val speed: Double, val accuracyM: Double, val qualityM: Double, val impacts: Int = 0,
    val peaks: List<Double> = emptyList(), val jerk: Double? = null, val gyro: Double? = null, val bands: List<Double> = emptyList(),
) {
    fun score(p: SurfaceProfile): Double = p.score(rms, if (meters <= 0) 0.0 else roughM / meters, peak, speed)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("dayId", dayId).put("date", date).put("profile", profile).put("at", at)
        .put("meters", meters).put("roughM", roughM).put("rms", rms).put("peak", peak).put("speed", speed).put("accuracyM", accuracyM)
        .put("qualityM", qualityM).put("impacts", impacts).put("peaks", JSONArray(peaks)).put("jerk", jerk ?: JSONObject.NULL)
        .put("gyro", gyro ?: JSONObject.NULL).put("bands", JSONArray(bands))

    companion object {
        fun fromJson(j: JSONObject) = PassEvidence(
            id = j.opt("id").toString(), dayId = j.opt("dayId").toString(), date = j.opt("date").toString(), profile = j.opt("profile").toString(),
            at = j.n("at"), meters = j.d("meters"), roughM = j.d("roughM"), rms = j.d("rms"), peak = j.d("peak"), speed = j.d("speed"),
            accuracyM = j.d("accuracyM"), qualityM = j.d("qualityM"), impacts = j.n("impacts").toInt(), peaks = j.list("peaks"),
            jerk = j.dOrNull("jerk"), gyro = j.dOrNull("gyro"), bands = j.list("bands"),
        )
    }
}

/** Bir 50 m kesimin (yönüyle) sabit bilgisi; ham satırlar silinse de özet için kalır. */
class SegmentMeta(
    val key: String, val way: Long, val direction: Int, val segmentIndex: Int, val lat: Double, val lon: Double,
    val segmentLength: Double = 50.0, val segmentGridM: Double = 50.0,
) {
    fun toJson(): JSONObject = JSONObject().put("key", key).put("way", way).put("direction", direction).put("segmentIndex", segmentIndex)
        .put("lat", lat).put("lon", lon).put("segmentLength", segmentLength).put("segmentGridM", segmentGridM)

    companion object {
        fun of(r: SurfaceObservation) = SegmentMeta(r.segment, r.way, r.direction, r.segmentIndex, r.lat, r.lon, r.segmentLength, r.segmentGridM)
        fun fromJson(j: JSONObject) = SegmentMeta(j.opt("key").toString(), j.getLong("way"), j.getInt("direction"), j.getInt("segmentIndex"),
            j.getDouble("lat"), j.getDouble("lon"), j.d("segmentLength", 50.0), j.d("segmentGridM", 50.0))
    }
}

/** Bir kesimin günlere yayılmış geçiş özetleri. */
class SegmentHistory(val meta: SegmentMeta, val passes: MutableList<PassEvidence> = ArrayList()) {
    /** En çok bu kadar gün ve geçiş tutulur; daha eskilerin ağırlığı zaten çok küçük. */
    fun prune(now: Long) {
        passes.removeAll { now - it.at > KEEP_DAYS * 86_400_000L }
        if (passes.size > KEEP_PASSES) { passes.sortBy { it.at }; repeat(passes.size - KEEP_PASSES) { passes.removeAt(0) } }
    }

    fun toJson(): JSONObject = JSONObject().put("meta", meta.toJson()).put("passes", JSONArray(passes.map { it.toJson() }))

    companion object {
        const val KEEP_DAYS = 120
        const val KEEP_PASSES = 60
        fun fromJson(j: JSONObject) = SegmentHistory(SegmentMeta.fromJson(j.getJSONObject("meta")),
            (j.optJSONArray("passes")?.let { a -> (0 until a.length()).map { PassEvidence.fromJson(a.getJSONObject(it)) } } ?: emptyList()).toMutableList())
    }
}

object SurfaceScoring {
    /** Bir geçişin ham pencerelerinden özet (pencere uzunluğuyla ağırlıklı). */
    fun passEvidence(pass: List<SurfaceObservation>, profile: SurfaceProfile): PassEvidence? {
        var m = 0.0; var r2 = 0.0; var rough = 0.0; var speed = 0.0; var acc = 0.0; var quality = 0.0; var peak = 0.0
        var last = 0L
        val hits = LinkedHashMap<String, Double>()
        for (r in pass) {
            m += r.meters; r2 += r.rms * r.rms * r.meters; rough += if (r.rms >= profile.roughThreshold(r.speed)) r.meters else 0.0
            speed += r.speed * r.meters; acc += r.accuracy * r.meters; quality += r.confidence * r.meters
            last = max(last, r.at)
            for (hit in r.impacts) {
                val p = hit.d("peak")
                hits[hit.opt("id").toString()] = p; peak = max(peak, p)
            }
        }
        if (m <= 0 || pass.isEmpty()) return null
        fun aux(field: (SurfaceObservation) -> Double?): Double? = pass.mapNotNull(field).let { if (it.isEmpty()) null else median(it) }
        val peaks = hits.values.sortedDescending()
        val first = pass.first()
        return PassEvidence(
            id = first.pass, dayId = first.day, date = BakuDay.of(last), profile = first.profile, at = last, meters = m, roughM = rough,
            rms = sqrt(r2 / m), peak = peak, speed = speed / m, accuracyM = acc, qualityM = quality, impacts = hits.size, peaks = peaks.take(10),
            jerk = aux { it.jerk }, gyro = aux { it.gyro },
            bands = if (pass.none { it.bands.size >= 3 }) emptyList() else (0 until 3).map { i -> median(pass.filter { it.bands.size > i }.map { it.bands[i] }) },
        )
    }

    /**
     * Kesim puanı (1.7.2): önce her günün puanı = o günün geçiş puanlarının medyanı ("gün sonu ortalaması"); sonra günlerin ağırlıklı ortalaması.
     * Gün ağırlığı = o günün geçiş sayısı (en çok 5; tek bir yoğun gün baskın olmasın) × yenilik (21 günde yarıya iner). Güven ~12 geçişte ve
     * 3 ayrı günde dolar. Tek geçişteki tek darbe kesimi en çok "orta"ya (60) indirir; iki ayrı geçişte doğrulanan darbe sert sınırı korur.
     */
    fun summarizeSegment(meta: SegmentMeta, passes: List<PassEvidence>, profile: SurfaceProfile, now: Long): SurfaceSummary? {
        val ps = passes.filter { it.profile == profile.id && it.meters > 0 }
        if (ps.isEmpty()) return null
        val byDate = LinkedHashMap<String, MutableList<PassEvidence>>()
        for (p in ps) byDate.getOrPut(p.date) { ArrayList() }.add(p)
        var weighted = 0.0; var weights = 0.0
        for (day in byDate.values) {
            val lastAt = day.maxOf { it.at }
            val decay = 0.5.pow(max(0L, now - lastAt) / 86400000.0 / 21)
            val w = min(day.size, 5) * decay
            weighted += w * median(day.map { it.score(profile) }); weights += w
        }
        val scores = ps.map { it.score(profile) }
        val center = if (weights > 0) weighted / weights else median(scores)
        val dispersion = median(scores.map { Math.abs(it - center) })
        val consistency = (1 - dispersion / 60).coerceIn(.25, 1.0)
        val meters = ps.sumOf { it.meters }
        val lastAt = ps.maxOf { it.at }
        val ageDays = max(0L, now - lastAt) / 86400000.0
        val coverage = (meters / ps.size / meta.segmentLength).coerceIn(0.0, 1.0)
        val calibrated = ps.all { profile.calibration(it.speed)?.validated ?: false }
        val quality = ps.sumOf { it.qualityM }
        val dayFactor = min(1.0, .25 + .25 * byDate.size)
        val confidence = (consistency * coverage * min(1.0, weights / 12) * dayFactor * min(1.0, meters / 150) *
            (quality / meters) * (if (calibrated) 1.0 else 0.6) * 0.9.pow(ageDays / 30)).coerceIn(0.0, 1.0)
        val peaks = ps.flatMap { it.peaks }.sorted()
        val hardest = max(if (peaks.isEmpty()) 0.0 else peaks.last(), ps.maxOf { it.peak })
        val confirmedShock = ps.count { it.peak >= profile.impactThreshold } >= 2
        val shock = profile.shockScore(hardest)
        val score = min(center, if (confirmedShock) shock else max(shock, 60.0))
        fun aux(field: (PassEvidence) -> Double?): Double? = ps.mapNotNull(field).let { if (it.isEmpty()) null else median(it) }
        val perBand = LinkedHashMap<Int, MutableList<PassEvidence>>()
        for (p in ps) {
            val band = SpeedBand.road(p.speed * 3.6)
            if (band != null && p.rms.isFinite() && p.rms >= 0) perBand.getOrPut(band) { ArrayList() }.add(p)
        }
        return SurfaceSummary(
            key = meta.key, way = meta.way, direction = meta.direction, segmentIndex = meta.segmentIndex, profile = profile.id, score = score,
            confidence = confidence, meters = meters, roughM = ps.sumOf { it.roughM }, passes = ps.size, lastAt = lastAt, rms = median(ps.map { it.rms }),
            accuracy = ps.sumOf { it.accuracyM } / meters, impacts = ps.sumOf { it.impacts }, hardest = hardest,
            p95 = if (peaks.size >= 20) peaks[Math.round((peaks.size - 1) * 0.95).toInt()] else null,
            lat = meta.lat, lon = meta.lon, jerk = aux { it.jerk }, gyro = aux { it.gyro },
            bands = if (ps.none { it.bands.size >= 3 }) emptyList() else (0 until 3).map { i -> median(ps.filter { it.bands.size > i }.map { it.bands[i] }) },
            calibrated = calibrated, segmentLength = meta.segmentLength, segmentGridM = meta.segmentGridM, confirmedShock = confirmedShock,
            roadBands = perBand.mapValues { (b, v) -> RoadBandEvidence(b, median(v.map { it.rms }), v.size, median(v.map { it.speed })) },
            speed = median(ps.map { it.speed }),
        )
    }

    /** Ham pencereler (+ isteğe bağlı gün sonu özetleri) → kesim puanları. Sensör kareleri değil bağımsız geçişler istatistik birimidir. */
    fun summarizeSurface(rows: Iterable<SurfaceObservation>, profile: SurfaceProfile, now: Long, history: Map<String, SegmentHistory> = emptyMap()): List<SurfaceSummary> {
        val groups = LinkedHashMap<String, LinkedHashMap<String, MutableList<SurfaceObservation>>>()
        for (r in rows) {
            if (r.profile != profile.id || r.meters <= 0) continue
            groups.getOrPut(r.segment) { LinkedHashMap() }.getOrPut(r.pass) { ArrayList() }.add(r)
        }
        val out = ArrayList<SurfaceSummary>()
        for (key in LinkedHashSet<String>().apply { addAll(groups.keys); addAll(history.keys) }) {
            val hist = history[key]
            val passes = LinkedHashMap<String, PassEvidence>()
            hist?.passes?.forEach { passes[it.id] = it }
            var meta: SegmentMeta? = hist?.meta
            for (pass in groups[key]?.values.orEmpty()) {
                passEvidence(pass, profile)?.let { passes[it.id] = it }
                if (meta == null) meta = SegmentMeta.of(pass.first())
            }
            if (meta == null) continue
            summarizeSegment(meta, passes.values.toList(), profile, now)?.let { out += it }
        }
        return out
    }
}
