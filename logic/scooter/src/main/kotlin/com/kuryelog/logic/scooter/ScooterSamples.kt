package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.EnergyStep
import org.json.JSONObject

/**
 * The scooter's own values on the order timeline (SCOOTER.md 8.4; RS `ScooterSamples.kt` @ 6388c04, line by line): while a shift runs
 * and the link reads the scooter, every 1 Hz sample becomes one row of [FILE] in that day's folder, next to the order, energy and GPS
 * logs (schemaVersion 1, `$defs/scooterSample`). Read-only towards the scooter, no location (gps.csv has it), null = not fresh.
 * Signs follow `economics-energy.jsonl`: `vxa_w` is consumption-positive like `energy.vxa_w_avg`; `int_a`/`ext_a` keep the BMS sign
 * (+ charge, − discharge) like `energy.internal.a_avg`. `power_verified` stays false until checked in the field.
 */
object ScooterSamples {
    const val FILE = "economics-scooter.jsonl"
    const val SCHEMA = 1

    /** `source` of every row (`ScooterEnergy.SOURCE`, kept: analysis tools tell WEEM rows apart by `scooter_id_source`). */
    const val SOURCE = ScooterEnergy.SOURCE

    /** One sample as `ScooterEnergy.Sample` holds it; [chargeW] is V×A with the panel's sign (+ charge). */
    data class Values(
        val wallMs: Long, val monoMs: Long, val speedKmh: Double?, val chargeW: Double?, val intPct: Double?, val intV: Double?,
        val intA: Double?, val extPct: Double?, val extV: Double?, val extA: Double?, val tempC: Double?, val odometerKm: Double?,
    ) {
        companion object {
            fun of(s: ScooterEnergy.Sample) = Values(s.wallMs, s.monoMs, s.speedKmh, s.powerW, s.intPct, s.intV, s.intA, s.extPct, s.extV, s.extA, s.tempC, s.odometerKm)
        }
    }

    /** [x] rounded to [decimals], or JSON null when missing or not finite (org.json refuses NaN; a row must never fail). */
    private fun r(x: Double?, decimals: Int): Any {
        if (x == null || !x.isFinite()) return JSONObject.NULL
        val f = Math.pow(10.0, decimals.toDouble())
        val y = Math.round(x * f) / f
        return if (y == 0.0) 0.0 else y // no "-0.0"
    }

    /**
     * Trapezoid of the two V×A powers (panel sign, + into the battery), like the energy block: null over a gap longer than
     * [maxStepMs] (`ScooterEnergy.MAX_STEP_MS`), out of order, or without both powers. Never an invented value. D10: [complete]
     * false = an external pack is known but was not read in this step (only the internal pack's power; not the order's energy).
     */
    fun step(prevMonoMs: Long, prevChargeW: Double?, monoMs: Long, chargeW: Double?, maxStepMs: Long, complete: Boolean = true): EnergyStep? {
        val dt = monoMs - prevMonoMs
        if (dt <= 0 || dt > maxStepMs || prevChargeW == null || chargeW == null || !prevChargeW.isFinite() || !chargeW.isFinite()) return null
        val wh = (prevChargeW + chargeW) / 2 * dt / 3_600_000.0
        return EnergyStep(if (wh < 0) -wh else 0.0, if (wh > 0) wh else 0.0, dt, complete)
    }

    /** RS `ScooterLink.sample`'s step between the link's previous and current sample (null without a previous one). */
    fun stepOf(before: ScooterEnergy.Sample?, now: ScooterEnergy.Sample): EnergyStep? =
        before?.let { b -> step(b.monoMs, b.powerW, now.monoMs, now.powerW, ScooterEnergy.MAX_STEP_MS, b.bothPacks() && now.bothPacks()) }

    fun row(v: Values, scooterId: String, scooterIdSource: String): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA).put("kind", "scooter_sample")
        .put("wall_ms", v.wallMs).put("mono_ns", v.monoMs * 1_000_000L)
        .put("source", SOURCE).put("scooter_id", scooterId).put("scooter_id_source", scooterIdSource).put("power_verified", false)
        .put("speed_kmh", r(v.speedKmh, 1)).put("vxa_w", r(v.chargeW?.let { -it }, 1))
        .put("int_pct", r(v.intPct, 0)).put("int_v", r(v.intV, 2)).put("int_a", r(v.intA, 2))
        .put("ext_pct", r(v.extPct, 0)).put("ext_v", r(v.extV, 2)).put("ext_a", r(v.extA, 2))
        .put("temp_c", r(v.tempC, 1)).put("odometer_km", r(v.odometerKm, 1))
}
