package com.kuryelog.logic.orders.dataset

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Teklif veri seti satır sözleşmesi (`DatasetContract.kt`, MAP.md 13.3; yoklama sabitleri hariç): yalnız güvenli alanlar, kimlik korunur (müşteri adı, telefon, adres, not
 * yok; alış 3 ondalık, bırakış null), ölçüler `unavailable`. Gün sınırı telefon saat diliminde ([ZoneId], MAP.md 13.5; Bakü'de sonuç aynı).
 */
object DatasetContract {
    const val SCHEMA = 1

    /** Aynı tekliften iki satır arasında en az bu kadar süre (çekirdek iz değişmedikçe). */
    const val ROW_GAP_MS = 5_000L

    private val BAKU: ZoneId = ZoneId.of("Asia/Baku")
    private val OFFER_KEY = Regex("offer-[0-9]+-[0-9]+")
    private val DAYS = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    /** Ölçü alanları: teklifin içeriği değildir. */
    val MEASUREMENT = listOf("receivedAtMs", "rawstatsChangedAtMs", "feedDelayMs", "readTrigger")

    /** `YYYY-MM-DD` ve takvimde gerçek gün; değilse `IllegalArgumentException`. */
    fun date(day: String): String {
        require(DATE.matches(day))
        LocalDate.parse(day)
        return day
    }

    /** Takvim günü `YYYY-MM-DD`, [zone] saat diliminde. */
    fun day(wallMs: Long, zone: ZoneId): String = java.time.Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate().toString()

    /** `2026-10-10_Cumartesi` (gün adı her zaman Türkçe). */
    fun folder(day: String): String {
        date(day)
        return "${day}_${DAYS[LocalDate.parse(day).dayOfWeek.value - 1]}"
    }

    /** Ortak alanlar (`wall_ms`, `mono_ns`, `schema_version`, `source`, saat kaynakları, `scooter_id`, `power_verified` false). */
    fun common(wallMs: Long, monoNs: Long, source: String = "yolgo", scooter: String? = null): JSONObject = JSONObject()
        .put("wall_ms", wallMs).put("mono_ns", monoNs).put("schema_version", SCHEMA).put("source", source)
        .put("wall_ms_source", "native_clock").put("mono_ns_source", "elapsed_realtime")
        .put("scooter_id", scooter ?: JSONObject.NULL).put("scooter_id_source", if (scooter == null) "unavailable" else "record")
        .put("power_verified", false)

    fun number(j: JSONObject, key: String): Double? = (j.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    /** Geçerli koordinat 3 ondalığa yuvarlanır (≈110 m, `HALF_EVEN`); geçersizse null. */
    fun point(lat: Double?, lon: Double?): JSONObject? {
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        fun round(x: Double) = BigDecimal(x).setScale(3, RoundingMode.HALF_EVEN).toDouble()
        return JSONObject().put("lat", round(lat)).put("lon", round(lon))
    }

    /** Metindeki koordinat ya da Plus Code; Plus Code yalnız gözlenmiş [reference] ile çözülür, varsayılan yok. */
    fun coordinate(text: String?, reference: JSONObject?): JSONObject? {
        if (text == null) return null
        val pair = Regex("(?<![0-9])(-?[0-9]{1,2}\\.[0-9]{3,})\\s*[,;]\\s*(-?[0-9]{1,3}\\.[0-9]{3,})(?![0-9])").find(text)
        if (pair != null) return point(pair.groupValues[1].toDoubleOrNull(), pair.groupValues[2].toDoubleOrNull())
        // Yerel OLC çözümü; kısa kodlar gözlenmiş kurye noktası ister, varsayılan şehir yok.
        val alphabet = "23456789CFGHJMPQRVWX"
        val code = Regex("(?<![0-9A-Z])([23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3})(?![0-9A-Z])", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.uppercase() ?: return null
        val padding = 8 - code.indexOf('+')
        if (padding < 0 || padding % 2 != 0) return null
        val values = doubleArrayOf(20.0, 1.0, .05, .0025, .000125)
        var prefix = ""
        val refLat = reference?.let { number(it, "lat") }
        val refLon = reference?.let { number(it, "lon") }
        if (padding > 0) {
            if (refLat == null || refLon == null) return null
            var a = refLat + 90
            var b = refLon + 180
            for (i in 0 until padding / 2) {
                val la = (a / values[i]).toInt().coerceIn(0, 19)
                val lo = (b / values[i]).toInt().coerceIn(0, 19)
                prefix += "${alphabet[la]}${alphabet[lo]}"
                a -= la * values[i]
                b -= lo * values[i]
            }
        }
        val full = (prefix + code).replace("+", "")
        if (full.length < 10) return null
        var la = -90.0
        var lo = -180.0
        for (i in 0..4) {
            la += alphabet.indexOf(full[2 * i]) * values[i]
            lo += alphabet.indexOf(full[2 * i + 1]) * values[i]
        }
        var cellLat = values[4]
        var cellLon = values[4]
        for (i in 10 until full.length) {
            val x = alphabet.indexOf(full[i])
            cellLat /= 5
            cellLon /= 4
            la += (x / 4) * cellLat
            lo += (x % 4) * cellLon
        }
        la += cellLat / 2
        lo += cellLon / 2
        if (padding > 0) {
            val resolution = Math.pow(20.0, (2 - padding / 2).toDouble())
            if (refLat!! + resolution / 2 < la) la -= resolution else if (refLat - resolution / 2 > la) la += resolution
            if (refLon!! + resolution / 2 < lo) lo -= resolution else if (refLon - resolution / 2 > lo) lo += resolution
        }
        return point(la, lo)
    }

    /**
     * Rawstats teklif nesnesi → satır; güvensiz kimlik (`offer-[0-9]+-[0-9]+` değil), bilinmeyen platform, eksik/ters zaman → null. [courier] kurye noktası (gözlenmiş)
     * ya da null; [region] ("baku" …) satırdaki bölge etiketi; [zone] satırdaki `timezone` ve `utcOffsetMinutes` için (13.11).
     */
    fun offer(input: JSONObject, wallMs: Long, monoNs: Long, courier: JSONObject?, region: String?, zone: ZoneId = BAKU): JSONObject? {
        val v = input.optJSONObject("v2") ?: return null
        val key = v.optString("offerKey")
        if (!OFFER_KEY.matches(key) || v.optString("platform") !in setOf("wolt", "bolt")) return null
        val first = number(v, "firstSeenAtMs")?.toLong() ?: return null
        val last = number(v, "lastSeenAtMs")?.toLong() ?: return null
        if (first <= 0 || last < first) return null
        fun zoneOf(k: String) = v.optString(k).takeIf { it in setOf("Z1", "Z2", "Z3", "Z4") }
        val pickup = coordinate(input.optString("pickupAddress"), courier)
        val dropoff = coordinate(input.optString("dropoffAddress"), courier)
        val shown = v.optJSONObject("shown") ?: JSONObject()
        val safeShown = JSONObject()
        for (k in listOf("km", "minLo", "minHi", "stops", "readyInMin", "approachMin", "deliveryMin", "totalMin", "multiplier", "boostPct")) {
            safeShown.put(k, number(shown, k) ?: JSONObject.NULL)
        }
        val offsetMin = zone.rules.getOffset(java.time.Instant.ofEpochMilli(wallMs)).totalSeconds / 60
        val row = common(wallMs, monoNs).put("recordId", UUID.randomUUID().toString()).put("offerKey", key)
            .put("platform", v.getString("platform")).put("firstSeenAtMs", first).put("lastSeenAtMs", last)
            .put("feeAzn", number(input, "displayedFeeAzn") ?: JSONObject.NULL)
            .put("platformKm", number(input, "platformDistanceKm") ?: number(shown, "km") ?: JSONObject.NULL)
            .put("shown", safeShown).put("courier", courier ?: JSONObject.NULL).put("pickup", pickup ?: JSONObject.NULL)
            .put("dropoff", dropoff ?: JSONObject.NULL).put("pickupZone", zoneOf("pickupZone") ?: JSONObject.NULL)
            .put("dropoffZone", zoneOf("dropoffZone") ?: JSONObject.NULL).put("region", region ?: JSONObject.NULL)
            .put("timezone", zone.id).put("utcOffsetMinutes", offsetMin)
        val flags = JSONObject().put("feeAzn", if (number(input, "displayedFeeAzn") == null) "unavailable" else "feed_v2")
            .put("platformKm", if (row.isNull("platformKm")) "unavailable" else "feed_v2")
            .put("courier", if (courier == null) "unavailable" else "gps_coarse")
            .put("pickup", if (pickup == null) "unavailable_no_network" else "coordinate_or_plus_code_coarse")
            .put("dropoff", if (dropoff == null) "unavailable_no_network" else "coordinate_or_plus_code_coarse")
            .put("region", if (region == null) "unavailable" else "yolgo_setting")
            .put("pickupZone", if (zoneOf("pickupZone") == null) "unavailable" else "feed_v2")
            .put("dropoffZone", if (zoneOf("dropoffZone") == null) "unavailable" else "feed_v2")
        val parse = v.optJSONObject("parse")
        row.put("feeObservationSource", parse?.optString("feeSource")?.takeIf { it in setOf("accessibility", "ocr", "notification") } ?: JSONObject.NULL)
            .put("parseConfidence", parse?.optString("confidence")?.takeIf { it in setOf("low", "medium", "high") } ?: JSONObject.NULL)
            .put("feeBasis", input.optString("feeBasis").takeIf { it in setOf("gross", "net") } ?: JSONObject.NULL)
        for (k in listOf("pickupAirKm", "deliveryAirKm", "routeKm", "routeSeconds", "demGainM", "demLossM", "temperatureC", "windKmh", "rainMm")) {
            row.put(k, JSONObject.NULL)
            flags.put(k, "unavailable")
        }
        return row.put("field_sources", flags)
    }

    /** Rawstats'ın yükün güncel içeriğini ilk gördüğü an (feed v2 `changedAtMs`); eski Rawstats'ta null. */
    fun changedAt(feed: JSONObject): Long? = (feed.opt("changedAtMs") as? Number)?.toLong()?.takeIf { it > 0 }

    /**
     * Okuma ölçüsü: [receivedAtMs] okuma anı, [changedAtMs] Rawstats'ın içeriği ilk gördüğü an, `feedDelayMs` fark, [trigger] `observer` | `fallback`. Bilinmeyen
     * değer null + kaynak bayrağı; eski Rawstats'ta gecikme uydurulmaz.
     */
    fun measure(row: JSONObject, receivedAtMs: Long?, changedAtMs: Long?, trigger: String): JSONObject {
        val flags = row.optJSONObject("field_sources") ?: JSONObject().also { row.put("field_sources", it) }
        row.put("receivedAtMs", receivedAtMs ?: JSONObject.NULL).put("rawstatsChangedAtMs", changedAtMs ?: JSONObject.NULL)
            .put("feedDelayMs", if (receivedAtMs != null && changedAtMs != null) receivedAtMs - changedAtMs else JSONObject.NULL)
            .put("readTrigger", trigger)
        flags.put("receivedAtMs", if (receivedAtMs == null) "unavailable" else "native_clock")
            .put("rawstatsChangedAtMs", if (changedAtMs == null) "unavailable_old_rawstats" else "rawstats_feed_v2")
            .put("feedDelayMs", when { changedAtMs == null -> "unavailable_old_rawstats"; receivedAtMs == null -> "unavailable"; else -> "computed" })
        return row
    }

    /** Satır − `wall_ms`, `mono_ns`, `recordId`, ölçü alanları. */
    fun changedFingerprint(row: JSONObject): String {
        val copy = JSONObject(row.toString())
        for (key in listOf("wall_ms", "mono_ns", "recordId") + MEASUREMENT) copy.remove(key)
        copy.optJSONObject("field_sources")?.let { flags -> for (key in MEASUREMENT) flags.remove(key) }
        return copy.toString()
    }

    /** `changedFingerprint` − `lastSeenAtMs`, `courier`. */
    fun coreFingerprint(row: JSONObject): String {
        val copy = JSONObject(changedFingerprint(row))
        copy.remove("lastSeenAtMs")
        copy.remove("courier")
        copy.optJSONObject("field_sources")?.remove("courier")
        return copy.toString()
    }

    /** İç içe null alanlara `field_sources` bayrağı (`unavailable`); değer uydurulmaz. */
    fun flagUnknowns(row: JSONObject): JSONObject {
        val flags = row.optJSONObject("field_sources") ?: JSONObject()
        fun walk(value: Any?, path: String) {
            when (value) {
                JSONObject.NULL -> if (!flags.has(path)) flags.put(path, row.optString("${path}_source", "unavailable"))
                is JSONObject -> {
                    val keys = value.keys().asSequence().toList()
                    for (key in keys) if (key != "field_sources" && !key.endsWith("_source")) walk(value.opt(key), if (path.isEmpty()) key else "$path.$key")
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), "$path[$i]")
            }
        }
        walk(row, "")
        return row.put("field_sources", flags)
    }
}
