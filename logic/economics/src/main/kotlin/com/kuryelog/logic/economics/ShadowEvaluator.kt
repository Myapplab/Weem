package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats ShadowEvaluator.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Shadow mode (SPEC 2.1.0 §19, WP8): every presented offer gets the v2 verdict of [EconomicsV2.evaluate], logged in its
 * `economics-offers.jsonl` row as `verdictShadow` with `displayed = false`. Nothing is shown: there is no UI for it, the
 * feeds do not carry it and the engine always runs with `appMode = shadow` (display `suppressed_shadow`). Advice only;
 * KuryePlus never taps, accepts or declines.
 *
 * Pure Kotlin (no Android imports, only org.json methods Android has). The engine input holds coarse zones and card
 * numbers only, never a name, address, note or coordinate. `docs/ekonomi-v2/araclar/shadow_parity.py` replays the
 * inputs dumped by `ShadowEvaluatorTest` through `ref.evaluate`.
 */
object ShadowEvaluator {
    /** New SharedPreferences key: "shadow" (default) or "off" (nothing computed). "live" is not built yet and logs like shadow. */
    const val APP_MODE_KEY = "economics.appMode"
    const val SHADOW = "shadow"
    const val OFF = "off"
    /** SPEC §20 `economics.vehicle` → vehicle profile. Only the G3 exists (597 Wh, the engine's battery prior); a new model gets a new id. */
    const val DEFAULT_VEHICLE = "g3"
    val VEHICLES = setOf(DEFAULT_VEHICLE)
    /** `calculation.scores` indicators whose level goes into `scoreLevels` (SPEC §15.1). */
    val SCORES = listOf("netEarnings", "overallImpact", "nextJob", "declineRisk", "energy", "venueWait", "shiftFit")
    private val ZONES = setOf("Z1", "Z2", "Z3", "Z4")

    /**
     * What KuryePlus knows when an offer is first shown. [commissionPct] is `commission:<pkg>`, else the global `commission`,
     * null when neither key exists; [appsOnline] are packages with an open online session.
     */
    data class Context(
        val commissionPct: Double? = null,
        val vehicle: String? = null,
        val appsOnline: Collection<String> = emptyList(),
        val woltDeclinesInWindow: Int = 0,
        val woltTriggerCounts: List<Int> = emptyList(),
        val boltAcceptanceRate: Double? = null,
        val hereZone: String = OfferGeo.UNKNOWN,
        val speedKmh: Double? = null,
        val repositioning: Boolean = false,
        val appMode: String? = SHADOW,
        /** WEEM N5 (MAP.md 10.6): batarya ve eve rota girdileri `:map` `RangePort`'tan; null iken Rawstats'taki gibi `BATTERY_UNKNOWN`. */
        val range: RangeInputs? = null
    ) {
        companion object {
            /** From a feed v2 `courierState` ([EconomicsRecorder.courierState]) and the prefs values. */
            fun of(courierState: JSONObject, commissionPct: Double?, vehicle: String?, appMode: String?): Context {
                val apps = ArrayList<String>()
                courierState.optJSONArray("appsOnline")?.let { a -> for (i in 0 until a.length()) apps.add(a.optString(i)) }
                val wolt = courierState.optJSONObject("wolt")
                val trig = ArrayList<Int>()
                wolt?.optJSONObject("penaltyHistory")?.optJSONArray("triggerCounts")?.let { a -> for (i in 0 until a.length()) trig.add(a.optInt(i)) }
                val bolt = courierState.optJSONObject("bolt")
                return Context(commissionPct, vehicle, apps, wolt?.optInt("declinesInWindow", 0) ?: 0, trig,
                    bolt?.takeIf { !it.isNull("acceptanceRate") }?.optDouble("acceptanceRate"), courierState.optString("hereZone", OfferGeo.UNKNOWN),
                    courierState.takeIf { !it.isNull("speedKmh") }?.optDouble("speedKmh"), courierState.optBoolean("repositioning", false), appMode)
            }
        }
    }

    fun vehicleProfile(label: String?): String = label?.trim()?.lowercase()?.takeIf { it in VEHICLES } ?: DEFAULT_VEHICLE

    /**
     * SPEC §17.2 / §20 payout: the card shows the gross fee, so with a commission c it is `gross_before_park` with
     * `keepSchedule [[0, 1 − c/100]]` (marginal tiers); without a commission key the basis is `unknown` (FEE_BASIS_UNKNOWN).
     */
    fun payout(commissionPct: Double?): JSONObject {
        val c = commissionPct?.takeIf { it.isFinite() } ?: return JSONObject().put("basis", "unknown")
        val keep = 1 - c.coerceIn(0.0, 100.0) / 100.0
        return JSONObject().put("basis", "gross_before_park").put("keepSchedule", JSONArray().put(JSONArray().put(0).put(keep)))
            .put("tierMode", "marginal")
    }

    /** A coarse zone, or null for "unknown": the engine then uses its documented default (ref.py fails on "unknown"). */
    private fun zone(z: String?): String? = z?.takeIf { it in ZONES }

    /**
     * SPEC §14.3 input of [o] at [nowMs] (the frame that showed it). Missing values are left out so the engine applies its
     * documented defaults: no fee → FEE_UNREADABLE, unknown zones → its zone defaults, no scooter battery (KuryePlus does
     * not read it) → BATTERY_UNKNOWN advisory, never a block. g is the prior until the analysis provides a personal value.
     * Route access is `unknown`: KuryePlus has no route (advisory ROUTE_ACCESS_UNVERIFIED). Null for other platforms.
     */
    fun input(o: OfferState, nowMs: Long, c: Context): JSONObject? {
        val platform = Platforms.of(o.pkg) ?: return null
        val d = o.data
        val all = EconomicsLogs.shown(d)
        val shown = JSONObject()
        val keys = all.keys()
        while (keys.hasNext()) { val k = keys.next(); if (!all.isNull(k)) shown.put(k, all.get(k)) }
        val offer = JSONObject().put("platform", platform).put("shown", shown).put("firstSeenAtMs", o.firstAt)
            .put("lastSeenAtMs", maxOf(o.lastSeenAt, o.firstAt)).put("optional", o.optional).put("routeAccess", "unknown")
        d.gross?.let { offer.put("feeAzn", it) }
        EconomicsLogs.expiresAt(o)?.let { offer.put("expiresAtMs", it).put("expirySource", "documented_upper_bound") }
        zone(EconomicsLogs.pickupZone(o))?.let { offer.put("pickupZone", it) }
        zone(EconomicsLogs.dropoffZone(o))?.let { offer.put("destZone", it) }
        val other = c.appsOnline.any { p -> Platforms.of(p).let { it != null && it != platform } }
        val st = JSONObject().put("minuteOfDay", EconomicsLogs.minuteOfDay(nowMs)).put("otherAppOnline", other)
            .put("woltDeclinesInWindow", maxOf(0, c.woltDeclinesInWindow)).put("repositioning", c.repositioning)
        zone(c.hereZone)?.let { st.put("hereZone", it) }
        c.speedKmh?.takeIf { it.isFinite() && it >= 0 }?.let { st.put("speedKmh", it) }
        c.boltAcceptanceRate?.takeIf { it.isFinite() && it >= 0 && it <= 1 }?.let { st.put("boltAR", it) }
        val trig = c.woltTriggerCounts.filter { it >= 1 }
        if (trig.isNotEmpty()) st.put("woltPenaltyHistory", JSONObject().put("triggerCounts", JSONArray(trig)))
        c.range?.let { r ->
            r.destToChargerKm?.takeIf { it.isFinite() && it >= 0 }?.let { offer.put("destToChargerKm", it) }
            r.destToHomeMin?.takeIf { it.isFinite() && it >= 0 }?.let { offer.put("destToHomeMin", it) }
            r.hereToHomeMin?.takeIf { it.isFinite() && it >= 0 }?.let { st.put("hereToHomeMin", it) }
            if (r.hasScooter) {
                val sc = JSONObject()
                r.blePct?.takeIf { it.isFinite() }?.let { sc.put("blePct", it) }
                r.bleAtMs?.let { sc.put("bleAtMs", it) }
                r.estimatorWh?.takeIf { it.isFinite() && it >= 0 }?.let { sc.put("estimatorWh", it) }
                st.put("scooter", sc)
            }
        }
        return JSONObject().put("nowMs", nowMs).put("appMode", SHADOW).put("offer", offer).put("state", st)
            .put("payout", payout(c.commissionPct)).put("g", EconomicsV2Estimators.resolveG())
            .put("vehicle", JSONObject().put("profile", vehicleProfile(c.vehicle)))
    }

    /**
     * `offerRecord.verdictShadow` from an engine [result] (rounded like the vectors). NO_VERDICT has no calculation: its
     * numbers are null and g is the input's. `blocks` and `noVerdictReasons` are kept too (codes only), so a BLOCKED or
     * NO_VERDICT row says why.
     */
    fun verdictShadow(result: JSONObject, input: JSONObject, computedAtMs: Long): JSONObject {
        val r = EconomicsV2.r6(result) as JSONObject
        val calc = r.optJSONObject("calculation")
        val s = calc?.optJSONObject("surplus")
        val g = input.optJSONObject("g")
        fun n(o: JSONObject?, k: String): Any = o?.opt(k)?.takeIf { it is Number } ?: JSONObject.NULL
        val v = JSONObject().put("specVersion", r.getString("specVersion")).put("verdict", r.getString("verdict"))
            .put("surplusMid", n(s, "mid")).put("surplusLo", n(s, "lo")).put("surplusHi", n(s, "hi")).put("kappa", n(calc, "kappa"))
            .put("g", if (calc != null) n(calc, "g") else n(EconomicsV2.r6(g) as? JSONObject, "g"))
            .put("gSource", (calc?.opt("gSource") as? String) ?: (g?.opt("source") as? String) ?: "unknown")
            .put("tauMid", n(calc?.optJSONObject("tau"), "mid")).put("rideKm", n(calc, "rideKm"))
            .put("minFeeBreakEven", n(calc, "minFeeBreakEven")).put("feeGoodFrom", n(calc, "feeGoodFrom")).put("feePoorBelow", n(calc, "feePoorBelow"))
            .put("flags", r.optJSONArray("flags") ?: JSONArray()).put("blocks", r.optJSONArray("blocks") ?: JSONArray())
            .put("noVerdictReasons", r.optJSONArray("noVerdictReasons") ?: JSONArray())
            .put("computedAtMs", computedAtMs).put("displayed", false)
        (calc?.opt("economicVerdict") as? String)?.let { v.put("economicVerdict", it) }
        calc?.optJSONObject("scores")?.let { sc ->
            val levels = JSONObject()
            for (k in SCORES) levels.put(k, (sc.optJSONObject(k)?.opt("level") as? Number)?.toInt() ?: JSONObject.NULL)
            v.put("scoreLevels", levels)
        }
        return v
    }
}

/**
 * Shadow verdicts of the pending offers (SPEC §19, WP8). [frame] computes an offer's verdict at its first complete offer
 * frame and again only when its fee or km change before the decision (a decline tap freezes it). Computing at resolution
 * would make almost every verdict NO_VERDICT (stale > 5 s, < 8 s left). [verdictFor] hands it to the offer row
 * ([EconomicsRecorder.verdictShadow]). Kept in memory, keyed by offer id: an offer restored after a process death gets one
 * when it is seen again, else its row has none. Not thread-safe (OrderTracker is synchronized).
 */
class ShadowMode {
    /** One evaluation; [nanos] is the wall time of input + engine (for the latency test, not the row). */
    class Entry(val offerId: String, val gross: Double?, val km: Double?, val computedAtMs: Long, val input: JSONObject?,
                val result: JSONObject?, val verdict: JSONObject?, val nanos: Long, val error: Exception?)

    private val entries = LinkedHashMap<String, Entry>()

    /** After `DeliveryCore.observe(o)`: [offer] is the pending offer of `o.pkg`. Returns the new evaluation, if any. */
    fun frame(o: CourierObservation, offer: OfferState?, context: () -> ShadowEvaluator.Context): Entry? {
        if (o.stage != ScreenStage.OFFER || !o.complete || offer == null || offer.pkg != o.pkg || offer.lastSeenAt != o.at) return null
        if (Platforms.of(offer.pkg) == null) return null
        val d = offer.data
        val old = entries[offer.id]
        if (old != null && (offer.declineClickAt != null || old.gross == d.gross && old.km == d.km)) return null
        val t0 = System.nanoTime()
        val e = try {
            val c = context()
            if (c.appMode == ShadowEvaluator.OFF) return null
            val inp = ShadowEvaluator.input(offer, o.at, c) ?: return null
            val res = EconomicsV2.evaluate(inp)
            val v = ShadowEvaluator.verdictShadow(res, inp, o.at)
            Entry(offer.id, d.gross, d.km, o.at, inp, res, v, System.nanoTime() - t0, null)
        } catch (ex: Exception) {
            Entry(offer.id, d.gross, d.km, o.at, null, null, null, System.nanoTime() - t0, ex)
        }
        entries.remove(offer.id); entries[offer.id] = e
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        return e
    }

    fun verdictFor(offer: OfferState): JSONObject? = entries[offer.id]?.verdict
    fun entry(offerId: String): Entry? = entries[offerId]

    companion object {
        /** One offer is pending per app and it resolves when the next one replaces it; older entries are dropped. */
        const val MAX_ENTRIES = 16
    }
}
