package com.kuryelog.logic.map.calibration

import com.kuryelog.logic.map.surface.SpeedBand
import org.json.JSONObject

// Y14 yol kalibrasyonu — kayıtlar ve referans puanı (YolGo `lib/calibration/road_calibration.dart` @ 870ab6e; platform kanalı ve yoklama
// denetleyicisi yok: WEEM'de kayıt deposu `RoadCalibrationStore` doğrudan okunur).

enum class RoadQuality(val label: String) {
    VERY_GOOD("ÇOK İYİ"), GOOD("İYİ"), MIDDLE("ORTA"), BAD("KÖTÜ"), VERY_BAD("ÇOK KÖTÜ");

    /** JSON'daki ad (`veryGood`, …): kayıt dosyası biçimi YolGo ile aynı. */
    val key get() = when (this) { VERY_GOOD -> "veryGood"; GOOD -> "good"; MIDDLE -> "middle"; BAD -> "bad"; VERY_BAD -> "veryBad" }

    companion object {
        fun fromLevel(level: Int): RoadQuality {
            require(level in 1..10) { "level 1–10 olmalı: $level" }
            return entries[(10 - level) / 2]
        }

        fun byKey(key: String): RoadQuality = entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Bilinmeyen kalite: $key")
    }
}

enum class CalibrationSpeedMode { FIXED, VARIABLE;
    companion object { fun byKey(key: String) = entries.firstOrNull { it.name.lowercase() == key } ?: throw IllegalArgumentException("Bilinmeyen kip: $key") }
}

class RoadCalibrationRecord(
    val id: String, val at: Long, val profile: String, val quality: RoadQuality, val mode: CalibrationSpeedMode, val requestedSeconds: Int,
    val elapsedSeconds: Double, val validSeconds: Double, val valid: Boolean, val level: Int? = null, val rms: Double? = null, val averageKmh: Double? = null,
    val speedNotConstant: Boolean = false, val interrupted: Boolean = false, val rejectionReason: String? = null, val roadRms: Double? = null,
    val roadRmsVersion: Int? = null, val roadValidSeconds: Double? = null, val roadRmsRejectionReason: String? = null,
) {
    val isLegacy get() = level == null

    val usable get() = valid && !interrupted && validSeconds.isFinite() && validSeconds + 1e-9 >= 5 &&
        (isLegacy || validSeconds + 1e-9 >= requestedSeconds * .6) && rms != null && rms.isFinite() && rms >= 0

    /** E10: yalnız yol RMS sürüm 1 ve en az 5 sn (ve istenenin %60'ı) geçerli pencere. */
    val roadUsable get() = !isLegacy && usable && roadRmsVersion == 1 && roadRms != null && roadRms.isFinite() && roadRms >= 0 &&
        roadValidSeconds != null && roadValidSeconds.isFinite() && roadValidSeconds + 1e-9 >= 5 && roadValidSeconds + 1e-9 >= requestedSeconds * .6 &&
        roadRmsRejectionReason == null

    val consistentFixed get() = usable && mode == CalibrationSpeedMode.FIXED && !speedNotConstant

    companion object {
        /** Seviye 1–10 tam sayı değilse [IllegalArgumentException] (YolGo `FormatException`). */
        fun fromJson(j: JSONObject): RoadCalibrationRecord {
            val raw = j.opt("qualityLevel")?.takeIf { it != JSONObject.NULL }
            if (raw != null && (!(raw is Int || raw is Long) || (raw as Number).toLong() !in 1..10)) throw IllegalArgumentException("Kalibrasyon seviyesi 1–10 tam sayı olmalı")
            val level = (raw as Number?)?.toInt()
            fun num(k: String) = (j.opt(k) as? Number)?.toDouble()
            fun str(k: String) = (j.opt(k) as? String)
            val version = j.opt("roadRmsVersion").let { if (it is Int || it is Long) (it as Number).toInt() else null }
            return RoadCalibrationRecord(
                id = j.getString("id"), at = j.getLong("at"), profile = j.getString("profile"),
                quality = if (level == null) RoadQuality.byKey(j.getString("quality")) else RoadQuality.fromLevel(level), level = level,
                mode = CalibrationSpeedMode.byKey(j.getString("mode")), requestedSeconds = j.getInt("requestedSeconds"),
                elapsedSeconds = j.getDouble("elapsedSeconds"), validSeconds = j.getDouble("validSeconds"), rms = num("rms"), averageKmh = num("averageKmh"),
                valid = j.optBoolean("valid"), speedNotConstant = j.optBoolean("speedNotConstant"), interrupted = j.optBoolean("interrupted"),
                rejectionReason = str("rejectionReason"), roadRms = num("roadRms"), roadRmsVersion = version, roadValidSeconds = num("roadValidSeconds"),
                roadRmsRejectionReason = str("roadRmsRejectionReason"),
            )
        }
    }
}

/** Ölçülmüş bir puan ya da gerçek bir kalibrasyon referansına karşı açık sınır ("7 seviyesinden iyi"). */
class RoadCalibrationScore internal constructor(val band: Int, val differentBand: Boolean, val value: Double? = null, val betterThan: Int? = null, val worseThan: Int? = null) {
    val orderValue: Double get() = value ?: (betterThan ?: worseThan)!!.toDouble()
    val label: String get() = when {
        value != null -> String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')
        betterThan != null -> "$betterThan seviyesinden iyi"
        else -> "$worseThan seviyesinden kötü"
    }
}

class RoadReferenceConflict(val lower: Int, val upper: Int, val lowerRms: Double, val upperRms: Double)

/**
 * Eski (seviyesiz) sürüşler hiç seviye almaz ve referanslara girmez. Her puan tek hız bandı ve tam seviyelerin medyanlarını kullanır, kategori
 * ortalamalarını değil.
 */
class RoadCalibrationReferences(all: Iterable<RoadCalibrationRecord>) {
    val records: List<RoadCalibrationRecord> = all.filter { !it.isLegacy && it.usable && speedBand(it.averageKmh) != null }
    private val road = HashMap<Int, HashMap<Int, MutableList<RoadCalibrationRecord>>>()

    init {
        for (r in records.filter { it.roadUsable }) road.getOrPut(speedBand(r.averageKmh)!!) { HashMap() }.getOrPut(r.level!!) { ArrayList() }.add(r)
        for (band in road.values) for (rows in band.values) rows.sortBy { it.roadRms!! }
    }

    companion object {
        val bands = listOf(15, 20, 25, 30)
        fun speedBand(kmh: Double?): Int? = SpeedBand.calibration(kmh)
        private fun medianSorted(values: List<Double>): Double? = if (values.isEmpty()) null else values.size.let { n -> if (n % 2 == 1) values[n / 2] else (values[n / 2 - 1] + values[n / 2]) / 2 }
    }

    private fun rows(quality: RoadQuality, band: Int) = records.filter { it.quality == quality && speedBand(it.averageKmh) == band }
    fun count(quality: RoadQuality, band: Int) = rows(quality, band).size
    fun hasFixed(quality: RoadQuality, band: Int) = rows(quality, band).any { it.consistentFixed }
    fun complete(quality: RoadQuality, band: Int) = count(quality, band) >= 3 && hasFixed(quality, band)
    fun completeAll(band: Int) = RoadQuality.entries.all { complete(it, band) }
    fun median(quality: RoadQuality, band: Int): Double? = medianSorted(rows(quality, band).map { it.rms!! }.sorted())

    fun referenceBand(speedKmh: Double?): Int? {
        val wanted = speedBand(speedKmh)
        if (speedKmh == null || !speedKmh.isFinite() || speedKmh < 8) return null
        val available = bands.filter { b -> records.any { speedBand(it.averageKmh) == b } }
        if (available.isEmpty()) return null
        if (wanted in available) return wanted
        return available.sortedWith(compareBy<Int>({ Math.abs(it - speedKmh) }, { it })).first()
    }

    fun usesDifferentBand(speedKmh: Double?): Boolean = referenceBand(speedKmh).let { it != null && it != speedBand(speedKmh) }

    fun roadReferenceBand(speedKmh: Double?, excludingId: String? = null): Int? {
        if (speedKmh == null || !speedKmh.isFinite() || speedKmh < 8) return null
        val available = bands.filter { b -> road[b]?.values.orEmpty().any { rows -> rows.any { it.id != excludingId } } }
        val wanted = speedBand(speedKmh)
        if (wanted in available) return wanted
        return available.sortedWith(compareBy<Int>({ Math.abs(it - speedKmh) }, { it })).firstOrNull()
    }

    fun levelMedian(level: Int, band: Int, excludingId: String? = null): Double? {
        require(level in 1..10)
        return medianSorted((road[band]?.get(level).orEmpty()).filter { it.id != excludingId }.map { it.roadRms!! })
    }

    fun conflicts(band: Int, excludingId: String? = null): List<RoadReferenceConflict> {
        val points = (1..10).mapNotNull { l -> levelMedian(l, band, excludingId)?.let { l to it } }
        return (1 until points.size).filter { points[it].second >= points[it - 1].second }
            .map { RoadReferenceConflict(points[it - 1].first, points[it].first, points[it - 1].second, points[it].second) }
    }

    fun missingLevels(band: Int): List<Int> = (1..10).filter { levelMedian(it, band) == null }

    fun score(rms: Double, speedKmh: Double?, excludingId: String? = null): RoadCalibrationScore? {
        val band = roadReferenceBand(speedKmh, excludingId)
        if (band == null || !rms.isFinite() || rms < 0) return null
        val points = (1..10).mapNotNull { level -> levelMedian(level, band, excludingId)?.let { level to it } }
        if (points.isEmpty()) return null
        // Larger levels mean better roads, so RMS must strictly decrease. Equal/inverted references cannot establish a unique interpolation.
        for (i in 1 until points.size) if (points[i].second >= points[i - 1].second) return null
        val different = band != speedBand(speedKmh)
        if (rms < points.last().second) return RoadCalibrationScore(band, different, betterThan = points.last().first)
        if (rms > points.first().second) return RoadCalibrationScore(band, different, worseThan = points.first().first)
        for (i in points.indices) {
            val (level, reference) = points[i]
            if (rms == reference) return RoadCalibrationScore(band, different, value = level.toDouble())
            if (i + 1 < points.size && rms >= points[i + 1].second) {
                val next = points[i + 1]
                val fraction = (reference - rms) / (reference - next.second)
                return RoadCalibrationScore(band, different, value = level + fraction * (next.first - level))
            }
        }
        return null
    }

    fun ratio(rms: Double, speedKmh: Double?): Double? {
        val band = referenceBand(speedKmh)
        if (band == null || !rms.isFinite() || rms < 0) return null
        val good = median(RoadQuality.GOOD, band)
        return if (good == null || good <= 0) null else rms / good
    }

    fun thresholds(band: Int): List<Double>? {
        val medians = RoadQuality.entries.map { median(it, band) }
        if (medians.any { it == null }) return null
        val values = medians.map { it!! }
        if (values.first() <= 0) return null
        for (i in 1 until values.size) if (values[i] <= values[i - 1]) return null
        return (1 until values.size).map { (values[it - 1] + values[it]) / 2 }
    }

    fun classify(rms: Double, speedKmh: Double?): RoadQuality? {
        val band = referenceBand(speedKmh)
        if (band == null || !rms.isFinite() || rms < 0) return null
        val limits = thresholds(band) ?: return null
        for (i in limits.indices) if (rms < limits[i]) return RoadQuality.entries[i]
        return RoadQuality.VERY_BAD
    }
}
