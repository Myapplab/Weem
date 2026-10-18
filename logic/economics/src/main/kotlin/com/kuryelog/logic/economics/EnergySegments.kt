package com.kuryelog.logic.economics

import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EnergySegments.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Energy schema from day one (main AI_HANDOFF "Ekonomi v2 için zorunlu veri kuralı"): one row per trip leg and per idle spell
 * in [FILE] (append-only, schemaVersion 1, `$defs/energySegment`). KuryePlus measures no scooter energy yet, so every energy
 * value is null with `source="not_measured"` and `power_verified=false`; the merged app (Sürüş Paneli) fills the same keys
 * later without a schema change ([ScooterBridge], [energyFrom]). Never an invented value. The v1 `tripLegs`/`idleSpell` rows
 * stay unchanged (their schema forbids extra keys); rows join by `tripId` / `spellId`.
 */
object EnergySegments {
    const val FILE = "economics-energy.jsonl"
    const val SCHEMA = 1
    const val SOURCE_APP = "kuryeplus"
    const val NOT_MEASURED = "not_measured"
    /** Settings key of the scooter in use; the courier has one scooter now (Ninebot Max G3), a second may come. */
    const val PREF_SCOOTER = "economics.scooterId"
    const val DEFAULT_SCOOTER = "scooter-1"

    /** Who wrote the row and when: wall clock, monotonic clock (`SystemClock.elapsedRealtimeNanos`), scooter in use. */
    data class Stamp(val wallMs: Long, val monoNs: Long, val scooterId: String, val scooterIdSource: String, val weather: OfferWeather?)

    private fun n(v: Any?): Any = v ?: JSONObject.NULL

    private fun battery() = JSONObject().put("pct_start", JSONObject.NULL).put("pct_end", JSONObject.NULL)
        .put("v_avg", JSONObject.NULL).put("a_avg", JSONObject.NULL)

    /** Energy block: all null until a scooter source exists. */
    fun energy(): JSONObject = JSONObject().put("source", NOT_MEASURED).put("power_verified", false)
        .put("internal", battery()).put("external", battery()).put("external_attached", JSONObject.NULL).put("external_feeding", JSONObject.NULL)
        .put("vxa_w_avg", JSONObject.NULL).put("wh", JSONObject.NULL).put("esc_w_avg", JSONObject.NULL).put("temp_c", JSONObject.NULL)
        .put("scooter_speed_kmh_avg", JSONObject.NULL).put("odometer_km_start", JSONObject.NULL).put("odometer_km_end", JSONObject.NULL)

    /** `energy.source` values of the schema (`$defs/energySegment`); Sürüş Paneli's `ScooterEnergy.SOURCE` is `surus_paneli`. */
    val ENERGY_SOURCES = setOf(NOT_MEASURED, "surus_paneli", "surus_paneli_ble", "manual")
    private val PACK = listOf("pct_start", "pct_end", "v_avg", "a_avg")
    private val NUMBERS = listOf("vxa_w_avg", "wh", "esc_w_avg", "temp_c", "scooter_speed_kmh_avg", "odometer_km_start", "odometer_km_end")

    /**
     * The scooter's energy block (Sürüş Paneli `SurusPaneli.energy()`: nested maps) in exactly the schema's shape: only the
     * schema's keys, a missing or non-finite number is null (org.json refuses NaN, and the row must never fail), a non-boolean
     * flag is null. An unknown `source` rejects the whole block (all null, `not_measured`): the row then still validates.
     */
    fun energyFrom(block: Map<String, Any?>?): JSONObject {
        val src = block?.get("source") as? String
        if (block == null || src !in ENERGY_SOURCES) return energy()
        fun num(v: Any?): Any = (v as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: JSONObject.NULL
        fun flag(v: Any?): Any = v as? Boolean ?: JSONObject.NULL
        fun pack(v: Any?): JSONObject { val m = v as? Map<*, *>; return JSONObject().also { j -> for (k in PACK) j.put(k, num(m?.get(k))) } }
        val out = JSONObject().put("source", src).put("power_verified", block["power_verified"] as? Boolean ?: false)
            .put("internal", pack(block["internal"])).put("external", pack(block["external"]))
            .put("external_attached", flag(block["external_attached"])).put("external_feeding", flag(block["external_feeding"]))
        for (k in NUMBERS) out.put(k, num(block[k]))
        return out
    }

    /** Context block: elevation is not measured here (YolGo DEM later); weather from YolGo's feed when fresh (K28). */
    fun context(start: Long, w: OfferWeather?): JSONObject = JSONObject()
        .put("elev_gain_m", JSONObject.NULL).put("elev_loss_m", JSONObject.NULL).put("elev_source", NOT_MEASURED)
        .put("ambient_c", n(w?.tempC)).put("wind_kmh", n(w?.windKmh)).put("gust_kmh", n(w?.gustKmh)).put("rain_mm", n(w?.rainMm))
        .put("weather_source", w?.source ?: OfferWeather.NOT_OBSERVED).put("hour_block", EconomicsLogs.hourBlock(start)).put("bag_load", JSONObject.NULL)

    /** Energy block of a [start, end] window; [ScooterBridge.energy] in the app, all null by default (tests, no scooter). */
    fun interface EnergyOf { fun block(start: Long, end: Long?): JSONObject }
    val NOT_MEASURED_ENERGY = EnergyOf { _, _ -> energy() }

    private fun row(s: Stamp, segment: String, pkg: String?, tripId: String?, spellId: String?, start: Long, end: Long?, km: Any?, e: EnergyOf) =
        JSONObject().put("schemaVersion", SCHEMA).put("kind", "energy_segment").put("segment", segment)
            .put("wall_ms", s.wallMs).put("mono_ns", s.monoNs).put("source", SOURCE_APP)
            .put("scooter_id", s.scooterId).put("scooter_id_source", s.scooterIdSource)
            .put("pkg", n(pkg)).put("tripId", n(tripId)).put("spellId", n(spellId)).put("startAtMs", start).put("endAtMs", n(end))
            .put("km", km ?: JSONObject.NULL).put("energy", e.block(start, end)).put("context", context(start, s.weather))

    /** One row per leg of a `tripLegs` row (approach, wait_at_venue, to_dropoff, handoff). */
    fun fromLegs(legs: JSONObject, s: Stamp, e: EnergyOf = NOT_MEASURED_ENERGY): List<JSONObject> {
        val a = legs.optJSONArray("legs") ?: return emptyList()
        val trip = legs.optString("tripId").ifEmpty { return emptyList() }
        return (0 until a.length()).mapNotNull { i ->
            val l = a.optJSONObject(i) ?: return@mapNotNull null
            row(s, l.optString("kind", "unknown"), legs.optString("pkg").ifEmpty { null }, trip, null, l.optLong("startAtMs"),
                l.longOrNull("endAtMs"), l.num("km"), e)
        }
    }

    /** One row for a closed `idleSpell` (the empty return and waiting between tasks). */
    fun fromIdle(spell: JSONObject, s: Stamp, e: EnergyOf = NOT_MEASURED_ENERGY): JSONObject? {
        val id = spell.optString("spellId").ifEmpty { return null }
        return row(s, "idle", null, null, id, spell.optLong("startAtMs"), spell.longOrNull("endAtMs"), spell.num("kmRidden"), e)
    }
}
