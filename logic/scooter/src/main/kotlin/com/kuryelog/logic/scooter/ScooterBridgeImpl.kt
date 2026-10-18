package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.EnergyBlock
import com.kuryelog.logic.core.EnergyStep
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ScooterLive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * The only place order logging talks to the scooter (SCOOTER.md 12.2; RS `ScooterBridge.kt` @ 6388c04): scooter code can never break
 * order logging. Every call into the [Source] is guarded and a failure or a malformed value counts as "not measured" (never an
 * invented value). [live] and [steps] come from the running link (`:scooter` Android); this class is Android-free.
 */
class ScooterBridgeImpl(
    private val source: Source,
    override val live: StateFlow<ScooterLive?>,
    override val steps: SharedFlow<EnergyStep>,
) : ScooterBridge {
    /** What the running link offers; read-only towards the scooter. */
    interface Source {
        /** `ScooterEnergy.block(from, to)`: the `energy` block as nested maps (keys of [EnergyShape]), or null. */
        fun energy(fromWallMs: Long, toWallMs: Long): Map<String, Any?>?

        /** Internal battery percent from fresh scooter values, or null without them. */
        fun batteryPct(): Double?

        /** A new order was accepted (`TripMeter.reset(tripId)`): the trip meter counts from here. */
        fun orderAccepted(tripId: String, atWallMs: Long)

        /** The order ended (`TripMeter.end(tripId)`): the trip meter stops adding the ride back to it. */
        fun orderDone(tripId: String, atWallMs: Long) {}
    }

    override fun orderAccepted(tripId: String, atWallMs: Long) { runCatching { source.orderAccepted(tripId, atWallMs) } }

    override fun orderDone(tripId: String, atWallMs: Long) { runCatching { source.orderDone(tripId, atWallMs) } }

    /** Energy block of one leg / idle spell. An open or reversed window is not measured: a partial figure would look like a whole one. */
    override suspend fun energy(fromWallMs: Long, toWallMs: Long): EnergyBlock {
        if (toWallMs <= fromWallMs) return EnergyShape.notMeasured()
        return EnergyShape.from(runCatching { source.energy(fromWallMs, toWallMs) }.getOrNull())
    }

    override fun batteryPct(): Double? = runCatching { source.batteryPct() }.getOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }

    companion object {
        /** Value of the offer record's `scooter_source` for values read from the scooter (RS `ScooterBridge.SOURCE`). */
        const val SOURCE = ScooterEnergy.SOURCE
    }
}

/**
 * The `energy` block of `economics-energy.jsonl` (RS `EnergySegments.energy()` / `energyFrom`; schemaVersion 1, `$defs/energySegment`):
 * all keys, null until a scooter measured them. [from] makes a source's nested maps the schema's exact shape.
 */
object EnergyShape {
    const val NOT_MEASURED = EnergyBlock.NOT_MEASURED

    /** `energy.source` values of the schema; the link's `ScooterEnergy.SOURCE` is `surus_paneli`. */
    val SOURCES = setOf(NOT_MEASURED, "surus_paneli", "surus_paneli_ble", "manual")
    private val PACK = listOf("pct_start", "pct_end", "v_avg", "a_avg")
    private val NUMBERS = listOf("vxa_w_avg", "wh", "esc_w_avg", "temp_c", "scooter_speed_kmh_avg", "odometer_km_start", "odometer_km_end")

    private fun battery() = JSONObject().put("pct_start", JSONObject.NULL).put("pct_end", JSONObject.NULL)
        .put("v_avg", JSONObject.NULL).put("a_avg", JSONObject.NULL)

    /** Energy block with every key null: nothing measured. */
    fun notMeasured(): EnergyBlock = EnergyBlock(
        JSONObject().put("source", NOT_MEASURED).put("power_verified", false)
            .put("internal", battery()).put("external", battery()).put("external_attached", JSONObject.NULL).put("external_feeding", JSONObject.NULL)
            .put("vxa_w_avg", JSONObject.NULL).put("wh", JSONObject.NULL).put("esc_w_avg", JSONObject.NULL).put("temp_c", JSONObject.NULL)
            .put("scooter_speed_kmh_avg", JSONObject.NULL).put("odometer_km_start", JSONObject.NULL).put("odometer_km_end", JSONObject.NULL),
    )

    /**
     * The scooter's energy block (`ScooterEnergy.block`: nested maps) in exactly the schema's shape: only the schema's keys, a missing or
     * non-finite number is null (org.json refuses NaN, and the row must never fail), a non-boolean flag is null. An unknown `source`
     * rejects the whole block (all null, `not_measured`): the row then still validates.
     */
    fun from(block: Map<String, Any?>?): EnergyBlock {
        val src = block?.get("source") as? String
        if (block == null || src !in SOURCES) return notMeasured()
        fun num(v: Any?): Any = (v as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: JSONObject.NULL
        fun flag(v: Any?): Any = v as? Boolean ?: JSONObject.NULL
        fun pack(v: Any?): JSONObject { val m = v as? Map<*, *>; return JSONObject().also { j -> for (k in PACK) j.put(k, num(m?.get(k))) } }
        val out = JSONObject().put("source", src).put("power_verified", block["power_verified"] as? Boolean ?: false)
            .put("internal", pack(block["internal"])).put("external", pack(block["external"]))
            .put("external_attached", flag(block["external_attached"])).put("external_feeding", flag(block["external_feeding"]))
        for (k in NUMBERS) out.put(k, num(block[k]))
        return EnergyBlock(out)
    }
}
