package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsV2.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Offer-economics decision model v2 (SPEC_V2 sections 3-15): a pure Kotlin port of `docs/ekonomi-v2/ref.py` 2.1.0
 * that reproduces `golden_vectors.json` within 1e-6. Read-only advice: nothing here taps, accepts, declines or talks
 * to a platform.
 *
 * [evaluate] and [evaluateStack] take and return org.json objects shaped like a vector's `input` and `expected`
 * (SPEC section 14). Results are unrounded; [r6] gives the vector format. Missing optional keys behave like ref.py's
 * `.get(key, default)` (an explicit JSON null counts as missing); a missing required key (offer, platform, shown, nowMs,
 * g, minuteOfDay, activeTrip) or an unknown zone or platform throws [IllegalArgumentException] where ref.py raises.
 * Units: AZN, offer-level minutes, rates per hour, Wh, km. Estimators for the inputs live in [EconomicsV2Estimators].
 */
object EconomicsV2 {
    const val SPEC_VERSION = "2.1.0"
    const val MEANING = "Advice only. Not an acceptance command; no platform action is taken."
    @JvmField val LCG_REFERENCE_FIRST5 = listOf(438, 808, 233, 479, 231)
    /** Display priority of blocks and flags (one icon at most; the BLOCKED chip names the first block in this order). */
    @JvmField val ICON_ORDER = listOf("BATTERY_ESCAPE_NOT_MET", "ROUTE_BLOCKED", "HEAT_STOP", "WIND_STOP",
        "SHIFT_HARD_DEADLINE", "STACK_PICKUP_DELAY_CAP", "STACK_FIRST_ORDER_LATE_CAP", "OUTSIDE_ALLOWED_ZONES",
        "BATTERY_UNKNOWN", "HEAT_CAUTION", "WIND_CAUTION", "DECLINE_COST_DOMINATES", "FEE_BASIS_UNKNOWN",
        "DROPOFF_UNKNOWN_EXPECTED_OVER_ZONES", "LAST_JOB_OF_SHIFT", "BREAK_SUGGESTED", "STOP_SUGGESTED_FATIGUE")
    @JvmField val VERDICT_RANK = mapOf("POOR" to 0, "MARGINAL" to 1, "GOOD" to 2)
    @JvmField val VERDICTS = listOf("GOOD", "MARGINAL", "POOR", "BLOCKED", "NO_VERDICT")
    /** YolGo Baku.center; replaced by the pickup centroid after >= 30 pickups. */
    @JvmField val HUB_DEFAULT = doubleArrayOf(40.4093, 49.8671)
    class Ring(val id: String, val lo: Double, val hi: Double)
    @JvmField val ZONES = listOf(Ring("Z1", 0.0, 2.5), Ring("Z2", 2.5, 6.0), Ring("Z3", 6.0, 12.0),
        Ring("Z4", 12.0, Double.POSITIVE_INFINITY))
    @JvmField val ZONE_MID_KM = mapOf("Z1" to 1.25, "Z2" to 4.25, "Z3" to 9.0, "Z4" to 15.0)
    @JvmField val HOUR_BLOCKS = listOf(Ring("B0", 0.0, 480.0), Ring("B1", 480.0, 705.0), Ring("B2", 705.0, 900.0),
        Ring("B3", 900.0, 1080.0), Ring("B4", 1080.0, 1290.0), Ring("B5", 1290.0, 1440.0))
    const val Z90 = 1.2815515655446004
    private const val INF = Double.POSITIVE_INFINITY

    // ------------------------------------------------------------------------------------------------------------
    // 0. Priors: every number is a default to be replaced by personal estimates (SPEC section 16, Appendix A).
    //    Never present a PRIOR value as the courier's own number. Treat PRIORS as read-only; tune a copy.
    // ------------------------------------------------------------------------------------------------------------
    @JvmField val PRIORS: JSONObject = buildPriors()

    /** A fresh copy of ref.PRIORS (same keys, values and labels). */
    fun buildPriors(): JSONObject = obj(
        "g_prior" to obj("value" to 4.0, "band" to arr(3.0, 5.5), "label" to "PRIOR",
            "basis" to "Wolt AZ statement 5-6 AZN/h (2022, web-wolt-az:W1) minus ~1.0-1.5 AZN/h variable cost; " +
                "Bolt marketing 'up to 7 AZN/h' (B11) as upper bound; synthetic sim fixed points 4.1-6.4 " +
                "(unlimited energy) and 3.0-4.3 (one battery). Not the courier's data."),
        "time" to obj(
            "wolt" to obj("mu" to Math.log(1.30), "sigma" to 0.30, "n" to 0, "M" to "midpoint of 'lo-hi min'",
                "label" to "PRIOR",
                "basis" to "2.8 km at ~17.5 km/h + ~5 min wait + ~2 min handoff ~= 17 min vs midpoint 12 (assumption)"),
            "bolt" to obj("mu" to Math.log(1.35), "sigma" to 0.30, "n" to 0, "M" to "approach ETA + delivery ETA",
                "label" to "PRIOR",
                "basis" to "Bolt '22 min' = 8 + 14 travel ETAs (B16) so service time is excluded; " +
                    "+5 min wait +3 min handoff ~= 30/22")),
        "approach_share" to obj("wolt" to 0.35, "bolt" to 0.40, "label" to "PRIOR"),
        "km_scope_factor" to obj("wolt" to 1.10, "label" to "PRIOR",
            "basis" to "ride km / shown km when the shown scope is unknown (W2/W10: approach is paid, scope of '2.8 km' unknown)"),
        "bolt_display_kmh" to obj("value" to 15.0, "label" to "PRIOR",
            "basis" to "speed implied by Bolt ETAs; sim assumption, unverified"),
        "detour" to obj("value" to 1.35, "label" to "PRIOR", "basis" to "road km / straight-line km, sim assumption"),
        "reposition_kmh" to obj("value" to 17.5, "label" to "PRIOR"),
        "wh_per_km" to obj("value" to 15.0, "label" to "PRIOR",
            "basis" to "heavy urban courier 15-17 Wh/km, crowd mean 13.5 (web-scooter:S6)"),
        "cost" to obj("maintenance_azn_per_km" to 0.081, "depreciation_azn_per_km" to 0.060,
            "battery_azn_per_wh" to 0.00196, "electricity_azn_per_wh" to 0.084 / 1000 / 0.87,
            "label" to "PRIOR (web-scooter:S19 typical; tyre/pad life, depreciation and Baku battery price unverified)"),
        "battery_season_factor" to obj("cold_below_c" to 10.0, "cold" to 1.5, "hot_from_c" to 28.0, "hot" to 1.3,
            "normal" to 1.0, "label" to "PRIOR",
            "basis" to "Sandia NMC 15C 2-3x worse cycling; Schmalstieg calendar Ea 58 kJ/mol (S11-S13)"),
        "battery_nominal_wh" to obj("value" to 597.0, "label" to "VERIFIED (S1)"),
        "lambda_base_per_h" to obj("value" to 6.0, "label" to "PRIOR (synthetic sim MED)"),
        "zone_rate_mult" to obj("Z1" to 1.30, "Z2" to 1.00, "Z3" to 0.45, "Z4" to 0.20, "label" to "PRIOR (synthetic sim)"),
        "hour_block_mult" to obj("B0" to 0.30, "B1" to 0.60, "B2" to 1.40, "B3" to 0.90, "B4" to 1.50, "B5" to 0.80,
            "label" to "PRIOR; peak times from Wolt AZ FAQ 11:45-15:00 and 18:00-21:30 (W11), magnitudes invented"),
        "d_prior_weight" to obj("value" to 0.5, "label" to "DESIGN hedge while zone rates are priors"),
        "p_acc" to obj("value" to 0.75, "label" to "PRIOR (share of free-time offers the policy accepts)"),
        "gamma_prior_shape" to obj("value" to 4.0, "label" to "PRIOR strength in pseudo-offers"),
        "wolt_K_support" to obj("support" to arr(3, 4, 5, 6, 8, 10), "probs" to arr(*Array(6) { 1.0 / 6 }),
            "window_min" to 60,
            "label" to "SCENARIO: only the existence of 'set offline ... for declining too many' is evidenced (W6)"),
        "wolt_offline_min" to obj("value" to 30.0, "label" to "SCENARIO (duration unknown)"),
        "substitution_other_app" to obj("value" to 0.65,
            "label" to "PRIOR: share of g kept while one app is offline and the other is online"),
        "bolt_ar_floor" to obj("value" to 0.80,
            "label" to "UNVERIFIED: Bolt help says the required rate varies by market (80% or 40%); " +
                "0.80 is the conservative default, user-settable (B7)"),
        "bolt_penalty" to obj("suspension_min" to 120.0, "p_penalty_at_cross" to 0.5, "near_band" to 0.05,
            "label" to "SCENARIO (Bolt AZ consequence unknown)"),
        "timeouts_count_as_declines" to obj("value" to true, "label" to "CONSERVATIVE ASSUMPTION (W7 unverified)"),
        "stack" to obj("max_pickup_delay_min" to 12.0, "max_first_order_late_min" to 10.0,
            "late_penalty_azn_per_min" to 0.05, "allow_interleave_default" to false,
            "label" to "PRIOR/SCENARIO (sim proxy; platform tolerance unknown). USER 2026-10-07: never carries two orders " +
                "at once, so only 'append' (finish A, then B) is evaluated unless allowInterleave is set"),
        "leisure_value_share_of_g" to obj("value" to 0.5, "label" to "USER PREFERENCE default"),
        "overrun_value_share_of_g" to obj("value" to 1.5, "label" to "USER PREFERENCE default"),
        "typical_task_min" to obj("value" to 20.0, "label" to "PRIOR until personal median exists"),
        "documented_window_ms" to obj("wolt" to 60000, "bolt" to 60000,
            "label" to "UPPER BOUND, not verified for Wolt AZ: the '60 s' text is in Wolt's UZB FAQ (W5 skeptic); " +
                "Wolt partner-app pages say 30-60 s; Bolt help 'one minute' (B8). Measure on the phone"),
        "min_remaining_ms_for_verdict" to obj("value" to 8000, "label" to "DESIGN"),
        "max_observation_age_ms" to obj("value" to 5000, "label" to "DESIGN"),
        "safety" to obj("heat_block_c" to 40.0, "heat_advise_c" to 35.0, "gust_block_ms" to 15.0, "gust_advise_ms" to 10.0,
            "reserve_min_wh" to 50.0, "reserve_share" to 0.25, "escape_condition_factor" to 1.15,
            "break_after_ride_min" to 240.0, "stop_after_online_h" to 10.0, "scooter_ble_max_age_ms" to 15000,
            "label" to "DESIGN defaults; user-adjustable; not legal limits"),
        "tax" to obj("simplified_rate" to 0.02, "income_rate" to 0.20, "exempt_share" to 0.75, "dsmf_rate" to 0.02,
            "dsmf_floor_monthly" to 60.0, "dsmf_cap_monthly" to 400.0, "its_monthly" to 16.0,
            "label" to "web-az-legal L1-L5, L19 (search-summary)"),
        "score_levels" to obj(
            "net_per_hour_vs_g" to arr(1.5, 1.0, 0.5),
            "next_job_D_azn" to arr(-0.25, 0.25, 0.75),
            "decline_risk_kappa_azn" to arr(0.0, 0.5, 1.5),
            "energy_share_of_available" to arr(0.10, 0.20, 0.35),
            "venue_wait_min" to arr(0.0, 3.0, 8.0),
            "label" to "DESIGN defaults for the multi-score card; not a verdict; user-tunable"))

    /** Typed view of a priors object, read once per call (cached for [PRIORS]). */
    internal class Pri(private val p: JSONObject) {
        private fun o(x: JSONObject, k: String) = x.optJSONObject(k) ?: throw IllegalArgumentException("priors: no $k")
        private fun d(x: JSONObject, k: String) = (x.opt(k) as? Number)?.toDouble() ?: throw IllegalArgumentException("priors: no $k")
        private fun v(k: String) = d(o(p, k), "value")
        private fun l(x: JSONObject, k: String) = dlist(x.optJSONArray(k) ?: throw IllegalArgumentException("priors: no $k"))
        val gPrior = v("g_prior"); val gBand = l(o(p, "g_prior"), "band")
        val time = o(p, "time"); val approachShare = o(p, "approach_share"); val kmScopeWolt = d(o(p, "km_scope_factor"), "wolt")
        val boltDisplayKmh = v("bolt_display_kmh"); val detour = v("detour"); val repositionKmh = v("reposition_kmh")
        val whPerKm = v("wh_per_km"); val cost = o(p, "cost")
        val maint = d(cost, "maintenance_azn_per_km"); val depr = d(cost, "depreciation_azn_per_km")
        val battWh = d(cost, "battery_azn_per_wh"); val elecWh = d(cost, "electricity_azn_per_wh")
        val season = o(p, "battery_season_factor"); val batteryNominalWh = v("battery_nominal_wh")
        val lambdaBase = v("lambda_base_per_h"); val zoneMult = o(p, "zone_rate_mult"); val hourMult = o(p, "hour_block_mult")
        val dPriorWeight = v("d_prior_weight"); val pAcc = v("p_acc"); val gammaShape = v("gamma_prior_shape")
        val kSupport = l(o(p, "wolt_K_support"), "support"); val kProbs = l(o(p, "wolt_K_support"), "probs")
        val woltOfflineMin = v("wolt_offline_min"); val substitution = v("substitution_other_app"); val boltArFloor = v("bolt_ar_floor")
        private val bp = o(p, "bolt_penalty")
        val suspensionMin = d(bp, "suspension_min"); val pPenaltyAtCross = d(bp, "p_penalty_at_cross"); val nearBand = d(bp, "near_band")
        private val st = o(p, "stack")
        val maxPickupDelay = d(st, "max_pickup_delay_min"); val maxFirstLate = d(st, "max_first_order_late_min")
        val latePenalty = d(st, "late_penalty_azn_per_min"); val allowInterleaveDefault = st.opt("allow_interleave_default")
        val leisureShare = v("leisure_value_share_of_g"); val overrunShare = v("overrun_value_share_of_g")
        val typicalTaskMin = v("typical_task_min"); val window = o(p, "documented_window_ms")
        val minRemainingMs = v("min_remaining_ms_for_verdict"); val maxObservationAgeMs = v("max_observation_age_ms")
        private val sf = o(p, "safety")
        val heatBlock = d(sf, "heat_block_c"); val heatAdvise = d(sf, "heat_advise_c"); val gustBlock = d(sf, "gust_block_ms")
        val gustAdvise = d(sf, "gust_advise_ms"); val reserveMinWh = d(sf, "reserve_min_wh"); val reserveShare = d(sf, "reserve_share")
        val escapeFactor = d(sf, "escape_condition_factor"); val breakAfterRide = d(sf, "break_after_ride_min")
        val stopAfterOnline = d(sf, "stop_after_online_h"); val bleMaxAgeMs = d(sf, "scooter_ble_max_age_ms")
        private val tx = o(p, "tax")
        val simplifiedRate = d(tx, "simplified_rate"); val incomeRate = d(tx, "income_rate"); val exemptShare = d(tx, "exempt_share")
        val dsmfRate = d(tx, "dsmf_rate"); val dsmfFloor = d(tx, "dsmf_floor_monthly")
        private val sl = o(p, "score_levels")
        val lvNetPerHour = l(sl, "net_per_hour_vs_g"); val lvNextJob = l(sl, "next_job_D_azn")
        val lvKappa = l(sl, "decline_risk_kappa_azn"); val lvEnergy = l(sl, "energy_share_of_available"); val lvVenue = l(sl, "venue_wait_min")
        fun timeOf(platform: String) = time.optJSONObject(platform) ?: throw IllegalArgumentException("unknown platform $platform")
        fun approach(platform: String) = d(approachShare, platform)
    }

    @Volatile private var cachedPri: Pri? = null
    internal fun pri(p: JSONObject): Pri = if (p !== PRIORS) Pri(p) else cachedPri ?: Pri(p).also { cachedPri = it }

    // ------------------------------------------------------------------------------------------------------------
    // Python-compatible numbers and JSON helpers
    // ------------------------------------------------------------------------------------------------------------
    /** ref.r6: every float of a JSON structure rounded half-even to 6 decimals, -0.0 to 0.0, non-finite to null. */
    @JvmStatic fun r6(x: Any?): Any? = when (x) {
        null -> null
        is Double -> round6(x)
        is Float -> round6(x.toDouble())
        is BigDecimal -> round6(x.toDouble())
        is JSONObject -> JSONObject().also { o -> val it = x.keys(); while (it.hasNext()) { val k = it.next(); o.put(k, r6(x.opt(k)) ?: JSONObject.NULL) } }
        is JSONArray -> JSONArray().also { a -> for (i in 0 until x.length()) a.put(r6(x.opt(i)) ?: JSONObject.NULL) }
        else -> x
    }

    private fun round6(x: Double): Any {
        if (!x.isFinite()) return JSONObject.NULL
        val v = java.lang.Double.parseDouble(BigDecimal(x).setScale(6, RoundingMode.HALF_EVEN).toString())
        return if (v == 0.0) 0.0 else v
    }

    /** Python `f"{x:.{digits}f}"` (or `:+.{digits}f` with [plus]): half-even on the exact binary value, sign kept on -0. */
    @JvmStatic fun pyFixed(x: Double, digits: Int, plus: Boolean = false): String {
        if (x.isNaN()) return (if (plus) "+" else "") + "nan"
        val neg = x < 0 || (x == 0.0 && 1.0 / x < 0)
        val sign = if (neg) "-" else if (plus) "+" else ""
        if (x.isInfinite()) return sign + "inf"
        return sign + BigDecimal(Math.abs(x)).setScale(digits, RoundingMode.HALF_EVEN).toPlainString()
    }

    /** Python float `%`: the result takes the divisor's sign. */
    @JvmStatic fun pyMod(x: Double, w: Double): Double {
        var m = x % w
        if (m != 0.0) { if ((w < 0) != (m < 0)) m += w } else m = Math.copySign(0.0, w)
        return m
    }

    /** Python 3.12+ `sum()` of floats (Neumaier compensated). */
    internal fun pySum(xs: List<Double>): Double {
        var s = 0.0; var c = 0.0
        for (x in xs) { val t = s + x; c += if (Math.abs(s) >= Math.abs(x)) (s - t) + x else (x - t) + s; s = t }
        return if (c != 0.0 && c.isFinite()) s + c else s
    }
    internal fun pyMax(a: Double, b: Double) = if (b > a) b else a
    internal fun pyMin(a: Double, b: Double) = if (b < a) b else a
    internal fun pos(x: Double) = if (x > 0) x else 0.0
    /** Python's `sorted()` order for floats (`<` only; equal values keep their order). */
    internal val pyOrder = Comparator<Double> { a, b -> if (a < b) -1 else if (b < a) 1 else 0 }

    internal fun obj(vararg kv: Pair<String, Any?>): JSONObject { val o = JSONObject(); for ((k, v) in kv) o.put(k, jv(v)); return o }
    internal fun arr(vararg xs: Any?): JSONArray { val a = JSONArray(); for (x in xs) a.put(jv(x)); return a }
    internal fun jv(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Double -> if (v.isFinite()) v else JSONObject.NULL
        is Float -> jv(v.toDouble())
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(jv(it)) } }
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), jv(x)) } }
        else -> v
    }
    /** Integral doubles (ms timestamps, counts) as Long, like the int values in ref.py's output. */
    internal fun numOut(x: Double?): Any? = if (x != null && x == Math.floor(x) && Math.abs(x) < 9.0e15) x.toLong() else x
    internal fun dlist(a: JSONArray): List<Double> = (0 until a.length()).map { (a.opt(it) as? Number)?.toDouble() ?: Double.NaN }

    internal fun JSONObject?.raw(k: String): Any? { if (this == null) return null; val v = opt(k); return if (v == null || v === JSONObject.NULL) null else v }
    internal fun JSONObject?.num(k: String): Double? = (raw(k) as? Number)?.toDouble()
    internal fun JSONObject?.str(k: String): String? = raw(k) as? String
    internal fun JSONObject?.objOf(k: String): JSONObject? = raw(k) as? JSONObject
    internal fun JSONObject?.truthy(k: String): Boolean = truthy(raw(k))
    internal fun truthy(v: Any?): Boolean = when (v) {
        null -> false; is Boolean -> v; is Number -> v.toDouble() != 0.0; is String -> v.isNotEmpty()
        is JSONObject -> v.length() > 0; is JSONArray -> v.length() > 0; else -> true
    }
    private fun JSONObject.reqObj(k: String) = objOf(k) ?: throw IllegalArgumentException("missing $k")
    private fun JSONObject.reqNum(k: String) = num(k) ?: throw IllegalArgumentException("missing $k")
    private fun JSONObject.reqStr(k: String) = str(k) ?: throw IllegalArgumentException("missing $k")
    private fun priority(code: String): Int { val i = ICON_ORDER.indexOf(code); return if (i >= 0) i else ICON_ORDER.size }

    // ------------------------------------------------------------------------------------------------------------
    // 1. Geography: coarse zones and hour blocks
    // ------------------------------------------------------------------------------------------------------------
    private fun rad(x: Double) = x * (Math.PI / 180.0)
    @JvmStatic fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = rad(lat1); val p2 = rad(lat2)
        val dp = p2 - p1; val dl = rad(lon2 - lon1)
        val s1 = Math.sin(dp / 2); val s2 = Math.sin(dl / 2)
        val a = s1 * s1 + Math.cos(p1) * Math.cos(p2) * (s2 * s2)
        return 2 * 6371.0088 * Math.asin(Math.sqrt(a))
    }
    @JvmStatic fun zoneOfRadius(rKm: Double): String = ZONES.firstOrNull { it.lo <= rKm && rKm < it.hi }?.id ?: "Z4"
    @JvmStatic fun zoneOf(lat: Double, lon: Double, hubLat: Double = HUB_DEFAULT[0], hubLon: Double = HUB_DEFAULT[1]) =
        zoneOfRadius(haversineKm(hubLat, hubLon, lat, lon))
    /** Fractional minutes are allowed (the arrival block uses minuteOfDay + tau_mid without rounding). */
    @JvmStatic fun hourBlock(minuteOfDay: Double): String {
        val m = pyMod(minuteOfDay, 1440.0)
        return HOUR_BLOCKS.firstOrNull { it.lo <= m && m < it.hi }?.id ?: "B0"
    }

    // ------------------------------------------------------------------------------------------------------------
    // 3. Value of being free in a zone (AZN), with online repositioning toward Z1
    // ------------------------------------------------------------------------------------------------------------
    internal class VFree(val wStayMin: Double, val costStay: Double, var choice: String, var cost: Double,
                         var eWaitRepositionMin: Double? = null, var eRideMin: Double? = null, var costReposition: Double? = null)

    /** Expected AZN cost (time at g + riding cost) until the next acceptable offer when free in [zone] during [hb]. */
    internal fun vFree(zone: String, hb: String, g: Double, cKm: Double?, pAcc: Double?, counts: JSONObject?, m: Pri): VFree {
        val p = pAcc ?: m.pAcc
        fun aPerMin(z: String) = pyMax(1e-9, p * EconomicsV2Estimators.lambdaPost(z, hb, counts, m).mean / 60.0)
        val wHere = 1.0 / aPerMin(zone)
        val stay = g * wHere / 60.0
        val out = VFree(wHere, stay, "stay", stay)
        if (zone == "Z1") return out
        val v = m.repositionKmh; val det = m.detour
        val rFrom = ZONE_MID_KM[zone] ?: throw IllegalArgumentException("unknown zone $zone")
        val rTo = ZONE_MID_KM.getValue("Z1")
        val set = sortedSetOf(rFrom, rTo)
        for (z in ZONES) for (b in doubleArrayOf(z.lo, z.hi)) if (rTo < b && b < rFrom) set.add(b)
        val bounds = set.toList().reversed()
        var surv = 1.0; var eWait = 0.0; var eRide = 0.0
        for (i in 0 until bounds.size - 1) {
            val hiR = bounds[i]; val loR = bounds[i + 1]
            val a = aPerMin(zoneOfRadius((hiR + loR) / 2))
            val t = (hiR - loR) * det / v * 60.0
            val part = (1 - Math.exp(-a * t)) / a
            eWait += surv * part; eRide += surv * part
            surv *= Math.exp(-a * t)
        }
        eWait += surv * (1.0 / aPerMin("Z1"))
        val ck = cKm ?: throw IllegalArgumentException("cost per km undefined (ride km 0)")
        val rep = g * eWait / 60.0 + ck * v * eRide / 60.0
        out.eWaitRepositionMin = eWait; out.eRideMin = eRide; out.costReposition = rep
        if (rep < stay) { out.choice = "reposition_online"; out.cost = rep }
        return out
    }

    /** D = E_dest[V(dest, hb)] - V(here, hb), both at the arrival hour block. Returns (D, vDest, vHere). */
    internal fun dTerm(destProbs: Map<String, Double>, here: String, hb: String, g: Double, cKm: Double?, counts: JSONObject?, m: Pri): Triple<Double, Double, VFree> {
        val vd = pySum(destProbs.map { (z, pz) -> pz * vFree(z, hb, g, cKm, null, counts, m).cost })
        val vh = vFree(here, hb, g, cKm, null, counts, m)
        return Triple(vd - vh.cost, vd, vh)
    }

    /** Shrink D while the zone x hour rates are priors: all personal 1.0, any blend 0.75, all prior d_prior_weight. */
    internal fun dWeight(zones: Collection<String>, hb: String, counts: JSONObject?, m: Pri): Pair<Double, String> {
        val srcs = (zones.toSet() + "Z1").map { EconomicsV2Estimators.lambdaPost(it, hb, counts, m).source }.toSet()
        if (srcs == setOf("personal")) return 1.0 to "personal"
        if ("personal" in srcs || "blend" in srcs) return 0.75 to "blend"
        return m.dPriorWeight to "prior"
    }

    // ------------------------------------------------------------------------------------------------------------
    // 4. Payout chain
    // ------------------------------------------------------------------------------------------------------------
    /** K(C): kept amount of period earnings C. schedule [[fromC, keepShare], ...]; 'marginal' slices or 'whole_period' cliff. */
    @JvmStatic fun keepIntegral(c: Double, schedule: List<DoubleArray>, mode: String?): Double {
        if (c <= 0) return 0.0
        if (mode == "whole_period") {
            var share = schedule[0][1]
            for (s in schedule) if (c >= s[0]) share = s[1]
            return share * c
        }
        var total = 0.0
        for (i in schedule.indices) {
            val f = schedule[i][0]; val nxt = if (i + 1 < schedule.size) schedule[i + 1][0] else INF
            if (c > f) total += schedule[i][1] * (Math.min(c, nxt) - f)
        }
        return total
    }

    internal data class Payout(val basis: Any?, val cum: Double, val schedule: List<DoubleArray>, val tierMode: String?,
                               val keepIfUnknown: Double, val tipsSubject: Boolean, val tipKeep: Double, val keepIfPark: Double) {
        companion object {
            fun of(p: JSONObject?): Payout {
                val sch = (p.raw("keepSchedule") as? JSONArray)?.takeIf { it.length() > 0 }?.let { a ->
                    (0 until a.length()).map { i -> a.optJSONArray(i)?.let { doubleArrayOf(it.optDouble(0), it.optDouble(1)) } ?: doubleArrayOf(Double.NaN, Double.NaN) }
                } ?: listOf(doubleArrayOf(0.0, 1.0))
                val basis: Any? = if (p != null && p.has("basis")) p.raw("basis") else "unknown"
                return Payout(basis, p.num("cumPeriodEarnings") ?: 0.0, sch, if (p != null && p.has("tierMode")) p.str("tierMode") else "marginal",
                    p.num("assumedKeepIfUnknown") ?: 1.0, p.truthy("tipsSubjectToPark"), p.num("tipKeep") ?: 1.0, p.num("assumedKeepIfPark") ?: 0.80)
            }
        }
    }

    internal class Rev(val a: Double, val afterPark: Double, val tip: Double, val taxRate: Double, val dsmf: Double,
                       val r: Double, val credit: Double, val basis: Any?) {
        fun json() = obj("A" to a, "afterPark" to afterPark, "tip" to tip, "taxRateOnRevenue" to taxRate, "dsmfMarginal" to dsmf,
            "R" to r, "costCredit" to credit, "basis" to basis)
    }

    /** Marginal AZN the courier keeps from fee [a] through the payout chain and marginal tax (ref.net_revenue). */
    internal fun netRevenue(a: Double, payout: Payout, tax: JSONObject?, tipConfirmed: Double, m: Pri): Rev {
        val afterPark = when (payout.basis) {
            "net_to_courier" -> a
            "gross_before_park" -> keepIntegral(payout.cum + a, payout.schedule, payout.tierMode) - keepIntegral(payout.cum, payout.schedule, payout.tierMode)
            else -> a * payout.keepIfUnknown
        }
        val tip = tipConfirmed * (if (payout.tipsSubject) payout.tipKeep else 1.0)
        val mode: Any? = if (tax != null && tax.has("mode")) tax.raw("mode") else "simplified_2pct"
        var rate = 0.0; var credit = 0.0
        when (mode) {
            "simplified_2pct" -> rate = m.simplifiedRate
            "income75_A" -> { rate = m.incomeRate * (1 - m.exemptShare); credit = m.incomeRate }
            "income75_B" -> { rate = m.incomeRate * (1 - m.exemptShare); credit = rate }
        }
        val dsmf = if ((tax.num("monthlyIncomeEstimate") ?: 0.0) > m.dsmfFloor / m.dsmfRate) m.dsmfRate else 0.0
        val gross = afterPark + tip
        return Rev(a, afterPark, tip, rate, dsmf, gross * (1 - rate - dsmf), credit, payout.basis)
    }

    // ------------------------------------------------------------------------------------------------------------
    // 5. Active time (calibrated on total task time: accept -> last delivery)
    // ------------------------------------------------------------------------------------------------------------
    internal class Shown(val m: Double?, val mApproach: Double?, val source: String)
    internal fun shownMinutes(platform: String, shown: JSONObject, p: Pri): Shown {
        if (platform == "wolt") {
            val lo = shown.num("minLo"); val hi = shown.num("minHi")
            val mm = if (lo != null && hi != null) (lo + hi) / 2.0 else lo ?: return Shown(null, null, "missing")
            return Shown(mm, p.approach("wolt") * mm, "wolt_range_midpoint")
        }
        val app = shown.num("approachMin"); val dl = shown.num("deliveryMin"); val tot = shown.num("totalMin")
        if (app != null && dl != null) return Shown(app + dl, app, "bolt_eta_sum")
        if (tot != null) return Shown(tot, shown.num("approachMinEstimated") ?: (p.approach("bolt") * tot), "bolt_button_total")
        return Shown(null, null, "missing")
    }

    internal class Tau(val mid: Double, val lo: Double, val hi: Double, val m: Double, val mApproach: Double, val k: Double,
                       val sigmaPred: Double, val excess: Double, val mSource: String, val calibration: String) {
        fun json() = obj("mid" to mid, "lo" to lo, "hi" to hi, "M" to m, "M_approach" to mApproach, "k" to k, "sigmaPred" to sigmaPred,
            "excessReadyWait" to excess, "Msource" to mSource, "calibration" to calibration)
    }

    internal fun tauHat(platform: String, shown: JSONObject, calib: JSONObject?, p: Pri): Tau? {
        val prior = p.timeOf(platform)
        val own = if (calib != null && calib.length() > 0 && calib.has(platform)) calib.objOf(platform) else null
        fun c(k: String) = own.num(k) ?: prior.num(k)
        val sh = shownMinutes(platform, shown, p)
        val mm = sh.m ?: return null
        val mApp = sh.mApproach!!
        val k = Math.exp(c("mu") ?: throw IllegalArgumentException("calibration: no mu"))
        val n = c("n") ?: 0.0
        val s = c("sigma") ?: throw IllegalArgumentException("calibration: no sigma")
        val sPred = Math.sqrt(s * s + (if (n > 0) s * s / n else 0.0))
        val ready = shown.num("readyInMin")
        val excess = if (ready != null) pyMax(0.0, ready - k * mApp) else 0.0
        return Tau(k * mm + excess, k * mm * Math.exp(-Z90 * sPred) + excess, k * mm * Math.exp(Z90 * sPred) + excess,
            mm, mApp, k, sPred, excess, sh.source, if (n >= 10) "personal" else "prior")
    }

    // ------------------------------------------------------------------------------------------------------------
    // 6. Ride distance, energy, variable cost
    // ------------------------------------------------------------------------------------------------------------
    internal fun rideKm(platform: String, shown: JSONObject, approachKm: Double?, p: Pri, kmScopeFactor: Double? = null): Pair<Double?, String> {
        if (platform == "wolt") {
            val km = shown.num("km") ?: return null to "missing"
            val scope: Any? = if (shown.has("kmScope")) shown.raw("kmScope") else "unknown"
            if (scope == "full_route") return km to "wolt_full_route"
            if (scope == "pickup_to_dropoff" && approachKm != null) return approachKm + km to "wolt_delivery_plus_own_approach"
            val f = if (kmScopeFactor != null && kmScopeFactor != 0.0) kmScopeFactor else p.kmScopeWolt
            return f * km to "wolt_scope_unknown_factor"
        }
        val dkm = shown.num("deliveryKmInferred")
        if (dkm != null && approachKm != null) return approachKm + dkm to "bolt_approach_route_plus_inferred_delivery"
        val mm = shownMinutes("bolt", shown, p).m ?: return null to "missing"
        return p.boltDisplayKmh * mm / 60.0 to "bolt_minutes_times_display_speed"
    }

    internal fun seasonFactor(ambientC: Double?, p: Pri): Double {
        val s = p.season
        if (ambientC == null) return s.num("normal")!!
        if (ambientC < s.num("cold_below_c")!!) return s.num("cold")!!
        if (ambientC >= s.num("hot_from_c")!!) return s.num("hot")!!
        return s.num("normal")!!
    }

    internal class VarCost(val km: Double, val wh: Double, val seasonFactor: Double, val maintenance: Double, val depreciation: Double,
                           val batteryWear: Double, val electricity: Double) {
        val total = pySum(listOf(maintenance, depreciation, batteryWear, electricity))
        val perKm: Double? = if (km > 0) total / km else null
        fun partsJson() = obj("maintenance" to maintenance, "depreciation" to depreciation, "batteryWear" to batteryWear, "electricity" to electricity)
    }

    internal fun variableCost(km: Double, cost: JSONObject?, whPerKm: Double?, ambientC: Double?, cond: Double, p: Pri): VarCost {
        val ov = if (cost != null && cost.length() > 0) cost else null
        val whk = if (whPerKm != null && whPerKm != 0.0) whPerKm else p.whPerKm
        val wh = km * whk * cond
        val f = seasonFactor(ambientC, p)
        return VarCost(km, wh, f, km * (ov.num("maintenance_azn_per_km") ?: p.maint), km * (ov.num("depreciation_azn_per_km") ?: p.depr),
            wh * (ov.num("battery_azn_per_wh") ?: p.battWh) * f, wh * (ov.num("electricity_azn_per_wh") ?: p.elecWh))
    }

    /** Variable AZN cost of one km (ref.cost_per_km). */
    @JvmStatic fun costPerKm(cost: JSONObject? = null, whPerKm: Double? = null, ambientC: Double? = null, priors: JSONObject = PRIORS) =
        variableCost(1.0, cost, whPerKm, ambientC, 1.0, pri(priors)).total

    // ------------------------------------------------------------------------------------------------------------
    // 8. Decline penalties: Wolt offline-for-declines (K posterior in EconomicsV2Estimators), Bolt acceptance rate
    // ------------------------------------------------------------------------------------------------------------
    internal class Kappa(val kappa: Double, val json: JSONObject)
    internal fun kappaWolt(nWindow: Double, history: JSONObject?, g: Double, otherAppOnline: Boolean, offlineMin: Double?, p: Pri): Kappa {
        val post = EconomicsV2Estimators.kPost(history, p)
        val pz = post.support.indices
        val pGtN = pySum(pz.filter { post.support[it] > nWindow }.map { post.probs[it] })
        val pEq = pySum(pz.filter { post.support[it] == nWindow + 1 }.map { post.probs[it] })
        val hazard = if (pGtN <= 0) 1.0 else pEq / pGtN
        val x = offlineMin ?: p.woltOfflineMin
        val dg = if (otherAppOnline) g * (1 - p.substitution) else g
        val kappa = hazard * x / 60.0 * dg
        return Kappa(kappa, obj("kappa" to kappa, "hazard" to hazard, "offlineMin" to x, "deltaG" to dg, "posterior" to post.json()))
    }

    internal fun kappaBolt(ar: Double, optional: Boolean, g: Double, otherAppOnline: Boolean, recentSurplus: List<Double>?, arFloor: Double?, p: Pri): Kappa {
        if (optional) return Kappa(0.0, obj("kappa" to 0.0, "regime" to "optional_order_exempt"))
        val floor = arFloor ?: p.boltArFloor
        val dg = if (otherAppOnline) g * (1 - p.substitution) else g
        val loss = p.pPenaltyAtCross * p.suspensionMin / 60.0 * dg
        val margin = ar - floor
        if (margin - 0.01 < 0) return Kappa(loss, obj("kappa" to loss, "regime" to "decline_crosses_floor", "margin" to margin, "loss" to loss))
        if (margin < p.nearBand) {
            val k = loss * (p.nearBand - margin) / p.nearBand
            return Kappa(k, obj("kappa" to k, "regime" to "near_floor", "margin" to margin, "loss" to loss))
        }
        if (!recentSurplus.isNullOrEmpty()) {
            val target = floor + p.nearBand
            val s = recentSurplus.sortedWith(pyOrder)
            val q = 1 - target
            val idx = Math.min(s.size - 1, Math.max(0, Math.floor(q * s.size).toInt()))
            val k = pos(-s[idx])
            return Kappa(k, obj("kappa" to k, "regime" to "steady_budget_quantile", "margin" to margin, "quantile" to q))
        }
        return Kappa(0.0, obj("kappa" to 0.0, "regime" to "slack", "margin" to margin))
    }

    private fun kappaFor(inp: JSONObject, o: JSONObject, st: JSONObject?, g: Double, p: Pri): Kappa {
        val other = if (st != null && st.has("otherAppOnline")) st.truthy("otherAppOnline") else true
        return if (o.reqStr("platform") == "wolt")
            kappaWolt(st.num("woltDeclinesInWindow") ?: 0.0, st.objOf("woltPenaltyHistory"), g, other, st.num("woltOfflineMin"), p)
        else kappaBolt(st.num("boltAR") ?: 1.0, o.truthy("optional"), g, other,
            (st.raw("recentSurplus") as? JSONArray)?.let { dlist(it) }, st.num("boltARFloor"), p)
    }

    // ------------------------------------------------------------------------------------------------------------
    // 9. Expiry (advisory), observation freshness; 10. hard blocks and advisories
    // ------------------------------------------------------------------------------------------------------------
    internal class Expiry(val expiresAtMs: Double?, val source: String, val remainingMs: Double?) {
        fun json() = obj("expiresAtMs" to numOut(expiresAtMs), "source" to source, "remainingMs" to numOut(remainingMs))
    }
    internal fun resolveExpiry(platform: String, offer: JSONObject, nowMs: Double, p: Pri): Expiry {
        val exp = offer.num("expiresAtMs"); val src = offer.raw("expirySource")
        val first = offer.num("firstSeenAtMs"); val win = p.window.num(platform)
        val (e, s) = when {
            exp != null && (src == "screen_timer" || src == "screen_progress" || src == "empirical_p10") -> exp to src as String
            first != null && win != null -> (first + win) to "documented_upper_bound"
            else -> null to "unknown"
        }
        return Expiry(e, s, if (e != null) e - nowMs else null)
    }

    internal fun availableWh(state: JSONObject?, nowMs: Double, p: Pri): Pair<Double?, String> {
        val sc = state.objOf("scooter")
        val pct = sc.num("blePct"); val at = sc.num("bleAtMs")
        if (pct != null && at != null && 0 <= nowMs - at && nowMs - at <= p.bleMaxAgeMs)
            return pct / 100.0 * p.batteryNominalWh * (sc.num("soh") ?: 1.0) to "ble_fresh"
        sc.num("estimatorWh")?.let { return it to "estimator" }
        return null to "unknown"
    }

    internal class Blocks(val blocks: MutableList<String>, val adv: List<String>, val availableWh: Double?, val availableSource: String) {
        fun battery() = obj("availableWh" to availableWh, "availableSource" to availableSource)
    }
    internal fun hardBlocks(inp: JSONObject, whWork: Double, tauHi: Double, p: Pri): Blocks {
        val st = inp.objOf("state"); val o = inp.reqObj("offer")
        val blocks = mutableListOf<String>(); val adv = mutableListOf<String>()
        val (av, avSrc) = availableWh(st, inp.reqNum("nowMs"), p)
        val whk = inp.objOf("vehicle").num("whPerKm")?.takeIf { it != 0.0 } ?: p.whPerKm
        val toCharger = o.num("destToChargerKm")
        if (av == null) adv.add("BATTERY_UNKNOWN")
        else if (toCharger != null) {
            var need = (whWork + toCharger * whk) * p.escapeFactor
            need += pyMax(p.reserveMinWh, p.reserveShare * (whWork + toCharger * whk))
            if (av < need) blocks.add("BATTERY_ESCAPE_NOT_MET")
        } else adv.add("CHARGER_DISTANCE_UNKNOWN")
        // 2.1.0: user-marked closures only; no legal small-EV road rule is applied by default.
        when (o.raw("routeAccess")) { "blocked" -> blocks.add("ROUTE_BLOCKED"); "unknown" -> adv.add("ROUTE_ACCESS_UNVERIFIED") }
        val w = st.objOf("weather"); val rules = st.objOf("safetyRules")
        fun rule(k: String) = if (rules != null && rules.has(k)) rules.truthy(k) else true
        val amb = w.num("ambientC")
        if (rule("heat") && amb != null) { if (amb >= p.heatBlock) blocks.add("HEAT_STOP") else if (amb >= p.heatAdvise) adv.add("HEAT_CAUTION") }
        val gust = w.num("gustMs")
        if (rule("wind") && gust != null) { if (gust >= p.gustBlock) blocks.add("WIND_STOP") else if (gust >= p.gustAdvise) adv.add("WIND_CAUTION") }
        val sh = st.objOf("shift"); val rem = sh.num("remainingMin")
        if (sh.truthy("hardDeadline") && rem != null && tauHi + (o.num("destToHomeMin") ?: 0.0) > rem) blocks.add("SHIFT_HARD_DEADLINE")
        val allowed = st.raw("allowedZones") as? JSONArray
        if (allowed != null && allowed.length() > 0) {
            val ok = (0 until allowed.length()).map { allowed.opt(it) }.toSet()
            val dz = o.objOf("destZoneProbs")
            val outside = dz != null && dz.keys().asSequence().any { z -> (dz.num(z) ?: 0.0) > 0 && z !in ok }
            if (outside) blocks.add("OUTSIDE_ALLOWED_ZONES")
        }
        val online = sh.num("onlineHours"); val ride = sh.num("continuousRideMin")
        if (online != null && online >= p.stopAfterOnline) adv.add("STOP_SUGGESTED_FATIGUE")
        if (ride != null && ride >= p.breakAfterRide) adv.add("BREAK_SUGGESTED")
        return Blocks(blocks, adv, av, avSrc)
    }

    // ------------------------------------------------------------------------------------------------------------
    // 11. The evaluator
    // ------------------------------------------------------------------------------------------------------------
    private val PICKUP_PRIOR = mapOf(
        "Z1" to linkedMapOf("Z1" to 0.45, "Z2" to 0.45, "Z3" to 0.10),
        "Z2" to linkedMapOf("Z1" to 0.25, "Z2" to 0.50, "Z3" to 0.25),
        "Z3" to linkedMapOf("Z2" to 0.35, "Z3" to 0.55, "Z4" to 0.10),
        "Z4" to linkedMapOf("Z3" to 0.40, "Z4" to 0.60))

    internal fun destProbs(o: JSONObject): Pair<LinkedHashMap<String, Double>, String> {
        val given = o.objOf("destZoneProbs")
        if (given != null && given.length() > 0) {
            val m = LinkedHashMap<String, Double>(); val it = given.keys()
            while (it.hasNext()) { val k = it.next(); m[k] = given.num(k) ?: throw IllegalArgumentException("destZoneProbs.$k") }
            return m to "given"
        }
        o.str("destZone")?.takeIf { it.isNotEmpty() }?.let { return linkedMapOf(it to 1.0) to "known" }
        // Bolt drop-off not shown: prior transition from the pickup zone (stays or moves one ring out)
        val pz = o.str("pickupZone") ?: "Z2"
        return LinkedHashMap(PICKUP_PRIOR[pz] ?: throw IllegalArgumentException("unknown zone $pz")) to "prior_from_pickup_zone"
    }

    private class Parts(val rev: Rev, val tau: Tau, val km: Double, val kmSrc: String, val cost: VarCost, val costLo: VarCost, val costHi: VarCost)
    private fun surplusParts(inp: JSONObject, a: Double, p: Pri): Parts {
        val o = inp.reqObj("offer"); val st = inp.objOf("state"); val plat = o.reqStr("platform"); val shown = o.reqObj("shown")
        val rev = netRevenue(a, Payout.of(inp.objOf("payout")), inp.objOf("tax"), o.num("tipConfirmedAzn") ?: 0.0, p)
        val tau = tauHat(plat, shown, inp.objOf("calibration"), p)!!
        val (km, kmSrc) = rideKm(plat, shown, o.num("approachKm"), p)
        val whk = inp.objOf("vehicle").num("whPerKm"); val amb = st.objOf("weather").num("ambientC"); val cost = inp.objOf("cost")
        return Parts(rev, tau, km!!, kmSrc, variableCost(km, cost, whk, amb, 1.0, p), variableCost(km * 0.90, cost, whk, amb, 1.0, p),
            variableCost(km * 1.10, cost, whk, amb, 1.0, p))
    }

    private class Pre(val flags: MutableList<String>, val novd: List<String>, val expiry: Expiry)
    /** NO_VERDICT gates and expiry flags shared by [evaluate] and [evaluateStack]. */
    private fun prechecks(inp: JSONObject, p: Pri): Pre {
        val o = inp.reqObj("offer"); val plat = o.reqStr("platform"); val now = inp.reqNum("nowMs"); val shown = o.reqObj("shown")
        val flags = mutableListOf<String>(); val novd = mutableListOf<String>()
        if (o.num("feeAzn") == null) novd.add("FEE_UNREADABLE")
        val lastSeen = o.num("lastSeenAtMs")
        if (lastSeen != null && now - lastSeen > p.maxObservationAgeMs) novd.add("OBSERVATION_STALE_OR_NOT_VISIBLE")
        if (o.raw("screenReadable") == false) novd.add("SCREEN_NOT_READABLE")
        if (tauHat(plat, shown, inp.objOf("calibration"), p) == null) novd.add("PLATFORM_MINUTES_UNREADABLE")
        if (rideKm(plat, shown, o.num("approachKm"), p).first == null) novd.add("DISTANCE_UNREADABLE")
        val expiry = resolveExpiry(plat, o, now, p)
        if (expiry.source == "unknown") flags.add("EXPIRY_UNKNOWN")
        else if (expiry.source == "documented_upper_bound") flags.add("EXPIRY_ESTIMATED_UPPER_BOUND")
        if (expiry.remainingMs != null && expiry.remainingMs < p.minRemainingMs) novd.add("TOO_LATE_FOR_SAFE_DECISION")
        return Pre(flags, novd, expiry)
    }

    private fun bandVerdict(lo: Double, hi: Double, kappa: Double) = if (lo >= 0) "GOOD" else if (hi < -kappa) "POOR" else "MARGINAL"

    /** Single offer while free (or while repositioning), SPEC section 14. Unrounded; [r6] gives the vector format. */
    @JvmStatic @JvmOverloads fun evaluate(inp: JSONObject, priors: JSONObject = PRIORS): JSONObject {
        val p = pri(priors)
        val o = inp.reqObj("offer"); val st = inp.objOf("state"); val plat = o.reqStr("platform")
        val pre = prechecks(inp, p); val flags = pre.flags
        if (pre.novd.isNotEmpty()) return output(inp, "NO_VERDICT", null, 0.0, 0.0, flags, emptyList(), pre.novd, pre.expiry, false)
        val gres = inp.reqObj("g"); val g = gres.reqNum("g")
        val mu = st.num("mu") ?: 0.0
        val a = o.reqNum("feeAzn")
        val pt = surplusParts(inp, a, p); val rev = pt.rev; val tau = pt.tau; val cost = pt.cost
        val ckm = cost.perKm
        val hb = hourBlock((st ?: JSONObject()).reqNum("minuteOfDay") + tau.mid)  // 2.1.0: no rounding
        val (probs, dsrc) = destProbs(o)
        if (dsrc == "prior_from_pickup_zone") flags.add("DROPOFF_UNKNOWN_EXPECTED_OVER_ZONES")
        val here = st.str("hereZone") ?: "Z2"
        val counts = inp.objOf("rateCounts")
        val (dRaw, vd, vh) = dTerm(probs, here, hb, g, ckm, counts, p)
        val (dw, dwSrc) = dWeight(probs.keys + here, hb, counts, p)
        var d = dw * dRaw
        if (dwSrc != "personal") flags.add("D_FROM_PRIOR_ZONE_RATES")
        if (tau.mSource == "bolt_button_total") flags.add("BOLT_ETA_SPLIT_MISSING")
        if (st.truthy("repositioning")) flags.add("OFFER_WHILE_REPOSITIONING")

        // end of shift
        val sh = st.objOf("shift"); val rem = sh.num("remainingMin")
        var lastJob = false; var gTime = g
        if (rem != null) {
            // expected wait for the next acceptable offer here, under the cheaper of stay / reposition online
            val wHere = if (vh.choice == "stay") vh.wStayMin else vh.eWaitRepositionMin!!
            if (rem < tau.mid + wHere + p.typicalTaskMin) lastJob = true
        }
        var eos: JSONObject? = null; var overrun = 0.0
        if (lastJob) {
            val vL = p.leisureShare * g; val vO = p.overrunShare * g
            val hkD = o.num("destToHomeKm") ?: 0.0; val hkX = st.num("hereToHomeKm") ?: 0.0
            val hmD = o.num("destToHomeMin") ?: 0.0; val hmX = st.num("hereToHomeMin") ?: 0.0
            overrun = pos(tau.mid + hmD - rem!!)
            // 2.1.0: an overrun minute is worth vO in total; task minutes are already priced at vL through gTime.
            val ck = ckm ?: throw IllegalArgumentException("cost per km undefined (ride km 0)")
            d = ck * (hkD - hkX) + vL * (hmD - hmX) / 60.0 + (vO - vL) * overrun / 60.0
            gTime = vL
            eos = obj("leisureValuePerH" to vL, "overrunValuePerH" to vO, "overrunMin" to overrun,
                "homeDeltaKm" to hkD - hkX, "homeDeltaMin" to hmD - hmX)
            flags.add("LAST_JOB_OF_SHIFT")
        }
        val credit = rev.credit
        val dFinal = d
        fun sOf(r: Double, cTotal: Double, wh: Double, t: Double) = r - cTotal * (1 - credit) - mu * wh - gTime * t / 60.0 - dFinal
        // mid (tau_mid, km), lo = pessimistic (tau_P90, km x1.10), hi = optimistic (tau_P10, km x0.90)
        val variants = linkedMapOf("mid" to (cost to tau.mid), "lo" to (pt.costHi to tau.hi), "hi" to (pt.costLo to tau.lo))
        fun band(r: Double) = variants.mapValues { (_, v) -> sOf(r, v.first.total, v.first.wh, v.second) }
        val s = band(rev.r); val sMid = s.getValue("mid"); val sLo = s.getValue("lo"); val sHi = s.getValue("hi")

        // fee basis unknown is unpriced: evaluate the full band also at an assumed park keep share
        val payout = Payout.of(inp.objOf("payout"))
        val tax = inp.objOf("tax"); val tip = o.num("tipConfirmedAzn") ?: 0.0
        var alt: Map<String, Double>? = null; var keepAlt = 0.0
        if (payout.basis == "unknown") {
            flags.add("FEE_BASIS_UNKNOWN")
            keepAlt = payout.keepIfPark
            alt = band(netRevenue(a, payout.copy(keepIfUnknown = keepAlt), tax, tip, p).r)
        }
        val kap = kappaFor(inp, o, st, g, p)
        val kappa = if (lastJob) 0.0 else kap.kappa
        var verdict = bandVerdict(sLo, sHi, kappa)
        var hiUsed = sHi
        if (alt != null) {
            // 2.1.0: keep the lower of the two band verdicts; an unknown basis never moves a verdict up
            val vAlt = bandVerdict(alt.getValue("lo"), alt.getValue("hi"), kappa)
            if (vAlt != verdict) flags.add("UNPRICED_FLAG_COULD_FLIP_VERDICT")
            if (VERDICT_RANK.getValue(vAlt) < VERDICT_RANK.getValue(verdict)) verdict = vAlt
            hiUsed = pyMin(sHi, alt.getValue("hi"))
        }
        if (verdict == "MARGINAL" && kappa > 0 && hiUsed < 0) flags.add("DECLINE_COST_DOMINATES")
        val econVerdict = verdict
        val hb2 = hardBlocks(inp, cost.wh, tau.hi, p)
        flags.addAll(hb2.adv)
        val blocks = hb2.blocks.sortedBy { priority(it) }
        if (blocks.isNotEmpty()) verdict = "BLOCKED"

        // Fee thresholds (bisection on A; S is increasing in A); under an unknown basis at the conservative keep share.
        val thrPayout = if (alt != null) payout.copy(keepIfUnknown = keepAlt) else payout
        fun sAt(ax: Double, which: String): Double {
            val v = variants.getValue(which)
            return sOf(netRevenue(ax, thrPayout, tax, tip, p).r, v.first.total, v.first.wh, v.second)
        }
        fun feeFor(target: Double, which: String): Double {
            var lo = 0.0; var hi = 200.0
            if (sAt(lo, which) >= target) return 0.0
            repeat(100) { val m = (lo + hi) / 2; if (sAt(m, which) >= target) hi = m else lo = m }
            return hi
        }
        val netTask = rev.r - cost.total * (1 - credit)
        val minFee = feeFor(0.0, "mid")
        val nph = netTask * 60.0 / tau.mid
        val terms = obj("revenueNet" to rev.r, "variableCost" to cost.total * (1 - credit), "energyShadow" to mu * cost.wh,
            "timeCost" to gTime * tau.mid / 60.0, "D" to dFinal)
        val calc = obj(
            "surplus" to obj("mid" to sMid, "lo" to sLo, "hi" to sHi,
                "altIfParkCut" to alt?.let { obj("mid" to it["mid"], "lo" to it["lo"], "hi" to it["hi"], "keep" to keepAlt) }),
            "kappa" to kappa, "kappaDetail" to kap.json, "economicVerdict" to econVerdict,
            "minFeeBreakEven" to minFee, "feeGoodFrom" to feeFor(0.0, "lo"), "feePoorBelow" to feeFor(-kappa, "hi"),
            "terms" to terms, "tau" to tau.json(), "rideKm" to pt.km, "rideKmSource" to pt.kmSrc, "energyWh" to cost.wh,
            "costParts" to cost.partsJson(), "costPerKm" to ckm, "revenue" to rev.json(), "g" to g, "gTime" to gTime,
            "gSource" to gres.raw("source"), "mu" to mu, "hourBlockAtArrival" to hb, "destZoneProbs" to probs,
            "destSource" to dsrc, "Draw" to dRaw, "Dweight" to dw,
            "vFreeHere" to obj("cost" to vh.cost, "choice" to vh.choice), "vFreeDestExpected" to vd,
            "endOfShift" to eos, "battery" to hb2.battery(), "netPerTaskHourMid" to nph)
        calc.put("scores", scores(Card(nph, rev.r - cost.total * (1 - credit), econVerdict, sMid, sLo, sHi, dFinal, kappa,
            hb2.availableWh, cost.wh, tau.excess, overrun), g, lastJob, rem != null, p))
        val calibrating = gres.truthy("calibrating") || tau.calibration == "prior"
        return output(inp, verdict, calc, sMid, minFee, flags, blocks, emptyList(), pre.expiry, calibrating)
    }

    private fun levelHigh(v: Double, th: List<Double>) = if (v >= th[0]) 3 else if (v >= th[1]) 2 else if (v >= th[2]) 1 else 0

    private class Card(val nph: Double, val aznTask: Double, val econVerdict: String, val sMid: Double, val sLo: Double, val sHi: Double,
                       val d: Double, val kappa: Double, val availableWh: Double?, val energyWh: Double, val venueWait: Double, val overrunMin: Double)

    /** Multi-score card (H8): level 0..3 per indicator, DESIGN thresholds from score_levels; advice, not a decision. */
    private fun scores(c: Card, g: Double, lastJob: Boolean, hasRemaining: Boolean, p: Pri): JSONObject {
        val out = JSONObject(); val pros = mutableListOf<String>(); val cons = mutableListOf<String>()
        val levels = LinkedHashMap<String, Int?>()
        val th = if (g > 0) p.lvNetPerHour.map { it * g } else listOf(INF, INF, INF)
        val lNet = levelHigh(c.nph, th); levels["netEarnings"] = lNet
        out.put("netEarnings", obj("aznTask" to c.aznTask, "perHour" to c.nph, "level" to lNet, "direction" to "good_high"))
        val lvl = when (c.econVerdict) { "GOOD" -> 3; "POOR" -> 0; else -> if (c.sMid >= 0) 2 else 1 }
        out.put("overallImpact", obj("azn" to c.sMid, "lo" to c.sLo, "hi" to c.sHi, "level" to lvl, "direction" to "good_high"))
        if (lastJob) { out.put("nextJob", JSONObject.NULL); levels["nextJob"] = null } else {
            val t = p.lvNextJob
            val l = if (c.d <= t[0]) 3 else if (c.d < t[1]) 2 else if (c.d <= t[2]) 1 else 0
            levels["nextJob"] = l
            out.put("nextJob", obj("D" to c.d, "level" to l, "direction" to "good_high"))
        }
        val tk = p.lvKappa
        val lk = if (c.kappa <= tk[0]) 0 else if (c.kappa < tk[1]) 1 else if (c.kappa < tk[2]) 2 else 3
        out.put("declineRisk", obj("kappa" to c.kappa, "level" to lk, "direction" to "risk_high"))
        val av = c.availableWh
        if (av != null && av != 0.0) {
            val share = c.energyWh / av; val t = p.lvEnergy
            val l = if (share < t[0]) 3 else if (share < t[1]) 2 else if (share < t[2]) 1 else 0
            levels["energy"] = l
            out.put("energy", obj("wh" to c.energyWh, "shareOfAvailable" to share, "level" to l, "direction" to "good_high"))
        } else {
            levels["energy"] = null
            out.put("energy", obj("wh" to c.energyWh, "shareOfAvailable" to null, "level" to null, "direction" to "good_high"))
        }
        val w = c.venueWait; val tv = p.lvVenue
        val lw = if (w <= tv[0]) 3 else if (w < tv[1]) 2 else if (w < tv[2]) 1 else 0
        levels["venueWait"] = lw
        out.put("venueWait", obj("minutes" to w, "level" to lw, "direction" to "good_high"))
        when {
            !hasRemaining -> { out.put("shiftFit", JSONObject.NULL); levels["shiftFit"] = null }
            !lastJob -> { out.put("shiftFit", obj("lastJob" to false, "overrunMin" to 0.0, "level" to 3, "direction" to "good_high")); levels["shiftFit"] = 3 }
            else -> {
                val l = if (c.overrunMin <= 0) 2 else 0; levels["shiftFit"] = l
                out.put("shiftFit", obj("lastJob" to true, "overrunMin" to c.overrunMin, "level" to l, "direction" to "good_high"))
            }
        }
        val names = linkedMapOf("netEarnings" to "NET_PER_HOUR", "nextJob" to "NEXT_JOB_ZONE", "energy" to "ENERGY_SHARE",
            "venueWait" to "FOOD_WAIT", "shiftFit" to "SHIFT_END")
        for ((key, code) in names) when (levels[key]) { 3 -> pros.add("GOOD_$code"); 0 -> cons.add("BAD_$code") }
        if (lk >= 2) pros.add("DECLINE_IS_COSTLY")
        out.put("pros", jv(pros)); out.put("cons", jv(cons))
        return out
    }

    private val COLOUR = mapOf("GOOD" to "green", "MARGINAL" to "amber", "POOR" to "red", "BLOCKED" to "red", "NO_VERDICT" to "grey")
    private val SHAPE = mapOf("GOOD" to "circle", "MARGINAL" to "diamond", "POOR" to "square", "BLOCKED" to "octagon", "NO_VERDICT" to "dash")

    private fun output(inp: JSONObject, verdict: String, calc: JSONObject?, chipS: Double, chipMinFee: Double, flags: List<String>,
                       blocks: List<String>, novd: List<String>, expiry: Expiry, calibrating: Boolean): JSONObject {
        val st = inp.objOf("state")
        val mode: Any? = if (inp.has("appMode")) inp.raw("appMode") else "shadow"
        var colour = COLOUR.getValue(verdict); var shape = SHAPE.getValue(verdict)
        if (calibrating && verdict in listOf("GOOD", "MARGINAL", "POOR")) { colour = "grey"; shape = "ring" }
        var chip = "—"
        if (calc != null && verdict != "BLOCKED")
            chip = (if (calibrating) "~" else "") + pyFixed(chipS, 1, plus = true) + "₼ · min " + pyFixed(chipMinFee, 2) + "₼"
        else if (verdict == "BLOCKED") chip = "⛔ " + blocks.sortedBy { priority(it) }[0]
        val allf = blocks + flags
        val icon = ICON_ORDER.firstOrNull { it in allf }
        val moving = (st.num("speedKmh") ?: 0.0) > 5.0
        val (dmode, audio) = when {
            mode == "shadow" -> "suppressed_shadow" to "none"
            mode == "off" -> "none" to "none"
            moving -> "defer_until_stationary" to
                (if (st.truthy("earcons")) when (verdict) { "GOOD" -> "rise2"; "POOR", "BLOCKED" -> "low1"; else -> "none" } else "none")
            else -> "show" to "none"
        }
        return obj("specVersion" to SPEC_VERSION, "verdict" to verdict, "blocks" to blocks, "noVerdictReasons" to novd,
            "flags" to flags.toSortedSet().toList(), "expiry" to expiry.json(),
            "display" to obj("mode" to dmode, "chip" to chip, "colour" to colour, "shape" to shape, "icon" to icon,
                "audio" to audio, "calibrating" to calibrating),
            "calculation" to calc, "meaning" to MEANING)
    }

    private class Seq(val name: String, val deltaMakespanMin: Double, val pickupDelayMin: Double, val lateA: Double, val extraKm: Double) {
        var cost = 0.0; var wh = 0.0; var deltaS = 0.0; var feasible = false; var why: List<String> = emptyList()
        fun json() = obj("name" to name, "deltaMakespanMin" to deltaMakespanMin, "pickupDelayMin" to pickupDelayMin, "lateA" to lateA,
            "extraKm" to extraKm, "cost" to cost, "wh" to wh, "deltaS" to deltaS, "feasible" to feasible, "why" to why)
    }

    /**
     * Second offer B while trip A is active (any platform): append (A then B) and, only when allowed, interleave.
     * dS = R_B - c_B - mu*e_B - g*dMakespan - L*lateA - dD.
     */
    @JvmStatic @JvmOverloads fun evaluateStack(inp: JSONObject, priors: JSONObject = PRIORS): JSONObject {
        val p = pri(priors)
        val o = inp.reqObj("offer"); val st = inp.objOf("state"); val trip = inp.reqObj("activeTrip")
        val pre = prechecks(inp, p)
        if (pre.novd.isNotEmpty())
            return output(inp, "NO_VERDICT", null, 0.0, 0.0, listOf("STACKED_OFFER_DURING_ACTIVE_TRIP") + pre.flags, emptyList(), pre.novd, pre.expiry, false)
        val gres = inp.reqObj("g"); val g = gres.reqNum("g")
        val mu = st.num("mu") ?: 0.0
        val pt = surplusParts(inp, o.reqNum("feeAzn"), p); val rev = pt.rev; val tau = pt.tau
        val ckm = pt.cost.perKm
        val afterPickup = tau.mid - tau.k * tau.mApproach - tau.excess  // wait + delivery + handoff (average)
        // 2.1.0: pickup delay is measured against the platform's raw approach minutes
        val platformApproach = tau.mApproach
        val seqs = mutableListOf<Seq>()
        val remaining = trip.reqNum("remainingMin"); val dropToB = trip.reqNum("dropToBPickupMin")
        val arrB = remaining + dropToB
        val ready = o.reqObj("shown").num("readyInMin")
        val extraWait = if (ready != null) pos(ready - arrB) else 0.0
        val dMk = dropToB + extraWait + afterPickup
        val kmExtra = trip.reqNum("dropToBPickupKm") + (pt.km - (o.num("approachKm") ?: 0.0))
        seqs.add(Seq("append", dMk, arrB - platformApproach, 0.0, kmExtra))
        val allowIl = if (trip.has("allowInterleave")) trip.raw("allowInterleave") else p.allowInterleaveDefault
        val il = trip.objOf("interleave")
        if (il != null && il.length() > 0 && truthy(allowIl)) {
            // deltaMakespanMin comes from the router and includes any wait at B's restaurant
            seqs.add(Seq("interleave", il.reqNum("deltaMakespanMin"), il.reqNum("arrivalAtBPickupMin") - platformApproach,
                pos(il.reqNum("extraMinToA") - trip.reqNum("slackMin")), il.reqNum("extraKm")))
        }
        val hb = hourBlock((st ?: JSONObject()).reqNum("minuteOfDay") + remaining)
        val (probs, dsrc) = destProbs(o)
        val counts = inp.objOf("rateCounts"); val dropZone = trip.reqStr("dropZone")
        val vA = vFree(dropZone, hb, g, ckm, null, counts, p).cost
        val vB = pySum(probs.map { (z, pz) -> pz * vFree(z, hb, g, ckm, null, counts, p).cost })
        val (dw, dwSrc) = dWeight(probs.keys + dropZone, hb, counts, p)
        val dD = dw * (vB - vA)
        val whk = inp.objOf("vehicle").num("whPerKm")?.takeIf { it != 0.0 } ?: p.whPerKm
        val amb = st.objOf("weather").num("ambientC")
        for (s in seqs) {
            val c = variableCost(s.extraKm, inp.objOf("cost"), whk, amb, 1.0, p)
            s.cost = c.total; s.wh = c.wh
            s.deltaS = rev.r - c.total * (1 - rev.credit) - mu * c.wh - g * s.deltaMakespanMin / 60.0 - p.latePenalty * s.lateA - dD
            s.feasible = s.pickupDelayMin <= p.maxPickupDelay && s.lateA <= p.maxFirstLate
            s.why = listOfNotNull(if (s.pickupDelayMin > p.maxPickupDelay) "STACK_PICKUP_DELAY_CAP" else null,
                if (s.lateA > p.maxFirstLate) "STACK_FIRST_ORDER_LATE_CAP" else null)
        }
        val feas = seqs.filter { it.feasible }
        val kap = kappaFor(inp, o, st, g, p); val kappa = kap.kappa
        val flags = mutableListOf("STACKED_OFFER_DURING_ACTIVE_TRIP")
        if (dwSrc != "personal") flags.add("D_FROM_PRIOR_ZONE_RATES")
        if (dsrc == "prior_from_pickup_zone") flags.add("DROPOFF_UNKNOWN_EXPECTED_OVER_ZONES")
        flags.addAll(pre.flags)
        var blocks: List<String> = emptyList()
        var verdict = "BLOCKED"
        val best: Seq
        if (feas.isEmpty()) {
            blocks = seqs.flatMap { it.why }.toSet().sortedBy { priority(it) }
            best = seqs.maxByOrNull { it.deltaS }!!
        } else best = feas.maxByOrNull { it.deltaS }!!
        // 2.1.0: the stack band includes the same +-10% km term as single offers
        val kmBand = 0.10 * best.cost
        val bandLo = best.deltaS - g * (tau.hi - tau.mid) / 60.0 - kmBand
        val bandHi = best.deltaS + g * (tau.mid - tau.lo) / 60.0 + kmBand
        if (feas.isNotEmpty()) {
            verdict = bandVerdict(bandLo, bandHi, kappa)
            if (verdict == "MARGINAL" && kappa > 0 && bandHi < 0) flags.add("DECLINE_COST_DOMINATES")
        }
        // hard safety blocks apply to the whole remaining plan (rest of A + best B sequence)
        val planWh = best.wh + (trip.num("remainingWh") ?: 0.0)
        val planTauHi = remaining + best.deltaMakespanMin + (tau.hi - tau.mid)
        val hb2 = hardBlocks(inp, planWh, planTauHi, p)
        flags.addAll(hb2.adv)
        if (hb2.blocks.isNotEmpty()) { blocks = (blocks.toSet() + hb2.blocks).sortedBy { priority(it) }; verdict = "BLOCKED" }
        // break-even fee for the best sequence: R(A) = rest, inverted by bisection
        val rest = best.deltaS - rev.r
        val payout = Payout.of(inp.objOf("payout")); val tax = inp.objOf("tax")
        var loA = 0.0; var hiA = 200.0
        repeat(100) { val m = (loA + hiA) / 2; if (netRevenue(m, payout, tax, 0.0, p).r + rest >= 0) hiA = m else loA = m }
        val calc = obj("surplus" to obj("mid" to best.deltaS, "lo" to bandLo, "hi" to bandHi, "altIfParkCut" to null),
            "kappa" to kappa, "kappaDetail" to kap.json, "sequences" to seqs.map { it.json() }, "best" to best.name, "deltaD" to dD,
            "Dweight" to dw, "minFeeBreakEven" to hiA, "revenue" to rev.json(), "tau" to tau.json(), "g" to g, "mu" to mu,
            "battery" to hb2.battery(), "planWh" to planWh, "planTauHiMin" to planTauHi)
        val calibrating = gres.truthy("calibrating") || tau.calibration == "prior"
        return output(inp, verdict, calc, best.deltaS, hiA, flags, blocks, emptyList(), pre.expiry, calibrating)
    }

    /** Several pending offers: names ranked by surplus (AZN), never by AZN/h (v1 finding K14); BLOCKED and NO_VERDICT left out. */
    @JvmStatic fun rankOffers(results: List<Pair<String, JSONObject>>): List<String> =
        results.filter { (_, r) -> r.objOf("calculation") != null && r.optString("verdict") != "BLOCKED" }
            .sortedBy { (_, r) -> -r.getJSONObject("calculation").getJSONObject("surplus").getDouble("mid") }.map { it.first }
}
