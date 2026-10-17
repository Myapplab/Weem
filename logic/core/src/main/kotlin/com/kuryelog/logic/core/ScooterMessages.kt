package com.kuryelog.logic.core

import com.kuryelog.logic.core.Msg.putN
import org.json.JSONObject

/**
 * Canlı scooter (bugünkü `scooter/v1/live`, `X_protocol` P4, RS `ScooterFeed.kt`): `:scooter` → `:map`, `:orders`, `:economics`.
 * 2 telefonda Receiver'dan **çıkmaz** (yalnız [BatterySummary]). Ölçülmeyen değer null; yalnız scooter değerleri ve sipariş sayacı.
 */
data class ScooterLive(
    val generatedAtMs: Long,
    val linkActive: Boolean,
    /** Son karenin yaşı; bağlantı yoksa null. */
    val ageMs: Long?,
    val scooter: Values = Values(),
    val trip: Trip? = null,
    val source: String = SOURCE,
) {
    data class Values(
        val internalPct: Double? = null, val externalPct: Double? = null, val speedKmh: Double? = null, val powerW: Double? = null,
        val voltageV: Double? = null, val currentA: Double? = null, val rangeKm: Double? = null, val tempC: Double? = null, val odometerKm: Double? = null,
    )
    /** Sipariş sayacı (bağlantı aktifken). */
    data class Trip(val wh: Double? = null, val pctUsed: Double? = null, val avgMovingKmh: Double? = null, val km: Double? = null,
        val movingMs: Long? = null, val startedAtMs: Long? = null)

    /** Kare ≤15 sn taze (RS `SCOOTER_STALE_MS`, YolGo `ScooterLive.maxFrameAgeMs`). */
    val fresh: Boolean get() = linkActive && ageMs != null && ageMs in 0..STALE_MS

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("generatedAtMs", generatedAtMs).put("source", source)
        .put("linkActive", linkActive).putN("ageMs", ageMs)
        .put("scooter", JSONObject().putN("internalPct", scooter.internalPct).putN("externalPct", scooter.externalPct).putN("speedKmh", scooter.speedKmh)
            .putN("powerW", scooter.powerW).putN("voltageV", scooter.voltageV).putN("currentA", scooter.currentA).putN("rangeKm", scooter.rangeKm)
            .putN("tempC", scooter.tempC).putN("odometerKm", scooter.odometerKm))
        .put("trip", trip?.let { JSONObject().putN("wh", it.wh).putN("pctUsed", it.pctUsed).putN("avgMovingKmh", it.avgMovingKmh).putN("km", it.km)
            .putN("movingMs", it.movingMs).putN("startedAtMs", it.startedAtMs) } ?: JSONObject.NULL)

    companion object {
        const val SCHEMA = 1
        const val STALE_MS = 15_000L
        /** Bugünkü üretici `rawstats_surus_paneli`; WEEM `:scooter` kendi adını yazar. */
        const val SOURCE = "weem_surus_paneli"

        /** Bugünkü feed ve WEEM aynı alan adlarını kullanır. */
        fun parse(raw: String?): ScooterLive? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): ScooterLive? {
            if (Msg.version(j) != SCHEMA) return null
            val s = j.optJSONObject("scooter") ?: JSONObject()
            val t = j.optJSONObject("trip")
            return ScooterLive(
                generatedAtMs = Msg.long(j, "generatedAtMs") ?: return null,
                linkActive = Msg.bool(j, "linkActive") ?: false,
                ageMs = Msg.long(j, "ageMs"),
                scooter = Values(Msg.num(s, "internalPct"), Msg.num(s, "externalPct"), Msg.num(s, "speedKmh"), Msg.num(s, "powerW"),
                    Msg.num(s, "voltageV"), Msg.num(s, "currentA"), Msg.num(s, "rangeKm"), Msg.num(s, "tempC"), Msg.num(s, "odometerKm")),
                trip = t?.let { Trip(Msg.num(it, "wh"), Msg.num(it, "pctUsed"), Msg.num(it, "avgMovingKmh"), Msg.num(it, "km"), Msg.long(it, "movingMs"), Msg.long(it, "startedAtMs")) },
                source = Msg.str(j, "source") ?: SOURCE,
            )
        }
    }
}

/** `BatterySummary` v1 (plan 4.4): R → P, 60 sn'de bir ya da %1 değişince; bağlantı kopunca. [ScooterLive]'ın alt kümesi. */
data class BatterySummary(
    val atMs: Long, val linkActive: Boolean, val ageMs: Long? = null, val internalPct: Double? = null, val externalPct: Double? = null,
    val rangeKm: Double? = null, val tempC: Double? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("atMs", atMs).put("linkActive", linkActive).putN("ageMs", ageMs)
        .putN("internalPct", internalPct).putN("externalPct", externalPct).putN("rangeKm", rangeKm).putN("tempC", tempC)

    /** Göndermeye değer mi: bağlantı değişti, ≥%1 fark ya da ≥60 sn geçti. */
    fun due(previous: BatterySummary?): Boolean = previous == null || previous.linkActive != linkActive || atMs - previous.atMs >= INTERVAL_MS ||
        changed(previous.internalPct, internalPct) || changed(previous.externalPct, externalPct)

    companion object {
        const val SCHEMA = 1
        const val INTERVAL_MS = 60_000L
        private fun changed(a: Double?, b: Double?) = (a == null) != (b == null) || (a != null && b != null && kotlin.math.abs(a - b) >= 1.0)

        fun of(live: ScooterLive, atMs: Long) = BatterySummary(atMs, live.linkActive, live.ageMs, live.scooter.internalPct, live.scooter.externalPct,
            live.scooter.rangeKm, live.scooter.tempC)

        fun parse(raw: String?): BatterySummary? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): BatterySummary? {
            if (Msg.version(j) != SCHEMA) return null
            return BatterySummary(Msg.long(j, "atMs") ?: return null, Msg.bool(j, "linkActive") ?: return null, Msg.long(j, "ageMs"),
                Msg.num(j, "internalPct"), Msg.num(j, "externalPct"), Msg.num(j, "rangeKm"), Msg.num(j, "tempC"))
        }
    }
}

/**
 * Bir sipariş bacağı ya da bekleme parçasının scooter enerjisi: `economics-energy.jsonl` `energy` bloğu (RS `EnergySegments.energy`;
 * anahtarları `:economics` A4 sabitler). Burada yalnız taşınır; [source] `not_measured` ise ölçülmedi.
 */
class EnergyBlock(val json: JSONObject) {
    val source: String get() = json.optString("source", NOT_MEASURED)
    val measured: Boolean get() = source != NOT_MEASURED
    override fun equals(other: Any?) = other is EnergyBlock && JsonEq.same(json, other.json)
    override fun hashCode() = source.hashCode()
    override fun toString() = "EnergyBlock($json)"

    companion object {
        const val NOT_MEASURED = "not_measured"
        fun notMeasured() = EnergyBlock(JSONObject().put("source", NOT_MEASURED).put("power_verified", false))
    }
}

/** `EnergyRequest` v1 (P → R): sipariş ya da bekleme parçası kapanınca; yanıt gelene kadar kuyrukta (teslim zorunlu). */
data class EnergyRequest(val requestId: String, val segmentId: String, val kind: Kind, val fromWallMs: Long, val toWallMs: Long) {
    enum class Kind(val key: String) { LEG("leg"), IDLE("idle"); companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } } }

    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("requestId", requestId).put("segmentId", segmentId).put("kind", kind.key)
        .put("fromWallMs", fromWallMs).put("toWallMs", toWallMs)

    companion object {
        const val SCHEMA = 1
        fun parse(raw: String?): EnergyRequest? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): EnergyRequest? {
            if (Msg.version(j) != SCHEMA) return null
            val from = Msg.long(j, "fromWallMs") ?: return null
            val to = Msg.long(j, "toWallMs")?.takeIf { it > from } ?: return null
            return EnergyRequest(Msg.str(j, "requestId") ?: return null, Msg.str(j, "segmentId") ?: return null,
                Kind.of(Msg.str(j, "kind")) ?: return null, from, to)
        }
    }
}

/** `EnergyResult` v1 (R → P): ölçülemezse `not_measured` blok. */
data class EnergyResult(val requestId: String, val energy: EnergyBlock) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("requestId", requestId).put("energy", energy.json)

    companion object {
        const val SCHEMA = 1
        fun parse(raw: String?): EnergyResult? = Msg.obj(raw)?.let(::parse)
        fun parse(j: JSONObject): EnergyResult? {
            if (Msg.version(j) != SCHEMA) return null
            return EnergyResult(Msg.str(j, "requestId") ?: return null, EnergyBlock(j.optJSONObject("energy") ?: return null))
        }
    }
}
