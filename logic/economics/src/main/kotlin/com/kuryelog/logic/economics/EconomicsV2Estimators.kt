package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsV2Estimators.kt @ 6388c04, satır satır; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Estimators behind [EconomicsV2]'s inputs (ref.py 2.1.0 sections 2, 7, 8 and 12): the Gamma-Poisson offer rate, the
 * opportunity rate g and the energy price mu (joint Lagrangian), realized g with a portable day bootstrap, the resolved
 * g and its label, Wolt's decline-threshold posterior and the small-sample calibration fits. Pure Kotlin, same parity
 * rules as [EconomicsV2]; results that have a vector are returned as org.json shaped like the vector's `expected`.
 */
object EconomicsV2Estimators {
    private const val INF = Double.POSITIVE_INFINITY

    /** Portable PRNG (bit for bit like ref.Lcg32): state' = (1664525 * state + 1013904223) mod 2^32; index = (state' * k) >> 32. */
    class Lcg32(seed: Long) {
        var state: Long = seed and 0xFFFFFFFFL
            private set
        fun index(k: Int): Int {
            state = (1664525L * state + 1013904223L) and 0xFFFFFFFFL
            return ((state * k) ushr 32).toInt()
        }
    }

    /** Neumaier sum of f(0 until n), like Python 3.12+ `sum()` over floats, without building a list. */
    private inline fun nsum(n: Int, f: (Int) -> Double): Double {
        var s = 0.0; var c = 0.0
        for (i in 0 until n) { val x = f(i); val t = s + x; c += if (Math.abs(s) >= Math.abs(x)) (s - t) + x else (x - t) + s; s = t }
        return if (c != 0.0 && c.isFinite()) s + c else s
    }
    private fun JSONObject?.num(k: String): Double? = (this?.opt(k) as? Number)?.toDouble()

    // ------------------------------------------------------------------------------------------------------------
    // 2. Offer-rate estimator (Gamma-Poisson, zone x hour block). rateCounts: {"Z2|B2": [offersSeenWhileFree, freeHours]}
    // ------------------------------------------------------------------------------------------------------------
    internal class Lam(val mean: Double, val a: Double, val b: Double, val source: String)
    private fun mult(o: JSONObject, k: String, what: String) = o.num(k) ?: throw IllegalArgumentException("unknown $what $k")
    internal fun lambdaPrior(zone: String, hb: String, m: EconomicsV2.Pri) =
        m.lambdaBase * mult(m.zoneMult, zone, "zone") * mult(m.hourMult, hb, "hour block")
    internal fun lambdaPost(zone: String, hb: String, counts: JSONObject?, m: EconomicsV2.Pri): Lam {
        val lam0 = lambdaPrior(zone, hb, m)
        val a0 = m.gammaShape; val b0 = a0 / lam0
        val c = counts?.opt("$zone|$hb") as? JSONArray
        val n = (c?.opt(0) as? Number)?.toDouble() ?: 0.0
        val h = (c?.opt(1) as? Number)?.toDouble() ?: 0.0
        val a = a0 + n; val b = b0 + h
        return Lam(a / b, a, b, if (n >= 20 && h >= 5.0) "personal" else if (n > 0) "blend" else "prior")
    }
    /** Prior offer rate per free hour in [zone] during hour block [hb]. */
    @JvmStatic @JvmOverloads fun lambdaPrior(zone: String, hb: String, priors: JSONObject = EconomicsV2.PRIORS) =
        lambdaPrior(zone, hb, EconomicsV2.pri(priors))
    /** Posterior mean (a0 + N) / (b0 + H) with source personal (N >= 20, H >= 5) / blend / prior: {meanPerH, a, b, source}. */
    @JvmStatic @JvmOverloads fun lambdaPosterior(zone: String, hb: String, counts: JSONObject? = null, priors: JSONObject = EconomicsV2.PRIORS): JSONObject =
        lambdaPost(zone, hb, counts, EconomicsV2.pri(priors)).let {
            EconomicsV2.obj("meanPerH" to it.mean, "a" to it.a, "b" to it.b, "source" to it.source)
        }

    // ------------------------------------------------------------------------------------------------------------
    // 7. Opportunity rate g, energy shadow price mu (joint Lagrangian), realized g with bootstrap CI
    // ------------------------------------------------------------------------------------------------------------
    /** One offer of a pool: net revenue r, variable cost c (AZN), energy e (Wh), task time tau (min). */
    class PoolOffer(val r: Double, val c: Double, val e: Double, val tau: Double)
    @JvmStatic fun pool(a: JSONArray): List<PoolOffer> = (0 until a.length()).map {
        val o = a.getJSONObject(it); PoolOffer(o.getDouble("r"), o.getDouble("c"), o.getDouble("e"), o.getDouble("tau"))
    }

    /** g = lam * mean_i (r_i - c_i - mu*e_i - g*tau_i/60)^+ (renewal-reward fixed point), bisection 200 steps. */
    @JvmStatic @JvmOverloads fun solveG(pool: List<PoolOffer>, lamPerH: Double, mu: Double = 0.0, lo0: Double = 0.0, hi0: Double = 200.0): Double {
        fun f(g: Double) = lamPerH * nsum(pool.size) { val o = pool[it]; EconomicsV2.pos(o.r - o.c - mu * o.e - g * o.tau / 60.0) } / pool.size - g
        var lo = lo0; var hi = hi0
        if (f(lo) <= 0) return 0.0
        repeat(200) { val m = (lo + hi) / 2; if (f(m) > 0) lo = m else hi = m }
        return (lo + hi) / 2
    }

    class Rates(val acceptShare: Double, val moneyRate: Double, val energyRateWhPerH: Double) {
        fun json() = EconomicsV2.obj("acceptShare" to acceptShare, "moneyRate" to moneyRate, "energyRateWhPerH" to energyRateWhPerH)
    }
    @JvmStatic fun policyRates(pool: List<PoolOffer>, lamPerH: Double, g: Double, mu: Double): Rates {
        val acc = pool.filter { it.r - it.c - mu * it.e - g * it.tau / 60.0 >= 0 }
        val n = pool.size
        val den = 1.0 + lamPerH * nsum(acc.size) { acc[it].tau / 60.0 } / n
        val money = lamPerH * nsum(acc.size) { acc[it].r - acc[it].c } / n / den
        val energy = lamPerH * nsum(acc.size) { acc[it].e } / n / den
        return Rates(acc.size.toDouble() / n, money, energy)
    }
    /** {g, acceptShare, moneyRate, energyRateWhPerH} like the aux vector G4. */
    @JvmStatic fun fixedPoint(pool: List<PoolOffer>, lamPerH: Double): JSONObject {
        val g = solveG(pool, lamPerH)
        val r = policyRates(pool, lamPerH, g, 0.0)
        return EconomicsV2.obj("g" to g, "acceptShare" to r.acceptShare, "moneyRate" to r.moneyRate, "energyRateWhPerH" to r.energyRateWhPerH)
    }

    /**
     * Joint time (g) and energy (mu) shadow prices: max money s.t. energy used <= budget. mu = 0 unless the mu = 0 policy
     * exceeds the budget. g is the Lagrangian TIME price; with [chargePowerW] mu is capped at the fixed point mu = g(mu)/P.
     */
    @JvmStatic @JvmOverloads fun solveJoint(pool: List<PoolOffer>, lamPerH: Double, remainingH: Double, energyBudgetWh: Double,
                                            chargePowerW: Double? = null): JSONObject {
        fun out(g: Double, mu: Double, binding: Boolean, capped: Boolean, r: Rates) = EconomicsV2.obj("g" to g, "mu" to mu,
            "binding" to binding, "capByCharging" to capped, "acceptShare" to r.acceptShare, "moneyRate" to r.moneyRate,
            "energyRateWhPerH" to r.energyRateWhPerH)
        val g0 = solveG(pool, lamPerH, 0.0)
        val r0 = policyRates(pool, lamPerH, g0, 0.0)
        if (r0.energyRateWhPerH * remainingH <= energyBudgetWh) return out(g0, 0.0, false, false, r0)
        var lo = 0.0; var hi = 1.0
        repeat(100) {
            val m = (lo + hi) / 2
            if (policyRates(pool, lamPerH, solveG(pool, lamPerH, m), m).energyRateWhPerH * remainingH > energyBudgetWh) lo = m else hi = m
        }
        var mu = hi
        var gm = solveG(pool, lamPerH, mu)
        var rates = policyRates(pool, lamPerH, gm, mu)
        var capped = false
        if (chargePowerW != null && chargePowerW != 0.0) {
            // 2.1.0: buying 1 Wh back costs 1/P hours priced at the Lagrangian time price g(mu), not the money rate
            var lo2 = 0.0; var hi2 = mu
            repeat(200) { val m = (lo2 + hi2) / 2; if (solveG(pool, lamPerH, m) / chargePowerW > m) lo2 = m else hi2 = m }
            val muCap = (lo2 + hi2) / 2
            if (muCap < mu) { mu = muCap; capped = true; gm = solveG(pool, lamPerH, mu); rates = policyRates(pool, lamPerH, gm, mu) }
        }
        return out(gm, mu, true, capped, rates)
    }

    /** One worked day: AZN net of variable cost and the union online hours of both apps. */
    class Day(val net: Double, val onlineHours: Double)
    @JvmStatic fun days(a: JSONArray): List<Day> = (0 until a.length()).map { val o = a.getJSONObject(it); Day(o.getDouble("net"), o.getDouble("onlineHours")) }

    /**
     * Ratio estimator with a day-clustered percentile bootstrap: Lcg32(seed), k days resampled per replicate in order,
     * CI = sorted stats at floor(0.05 B) and floor(0.95 B) - 1. {value, ci90, nDays, hours}.
     */
    @JvmStatic @JvmOverloads fun gRealized(days: List<Day>, b: Int = 2000, seed: Long = 20261006): JSONObject {
        val totH = nsum(days.size) { days[it].onlineHours }
        val point = nsum(days.size) { days[it].net } / totH
        val rng = Lcg32(seed); val k = days.size
        val stats = ArrayList<Double>(b)
        repeat(b) {
            val s = List(k) { days[rng.index(k)] }
            val h = nsum(k) { s[it].onlineHours }
            stats.add(if (h > 0) nsum(k) { s[it].net } / h else point)
        }
        val sorted = stats.sortedWith(EconomicsV2.pyOrder)
        return EconomicsV2.obj("value" to point, "ci90" to listOf(sorted[(0.05 * b).toInt()], sorted[(0.95 * b).toInt() - 1]),
            "nDays" to k, "hours" to totH)
    }

    /**
     * Which g the verdict uses, and its label. The legacy YolGo 'hourly' setting is a TARGET, never g; its untouched
     * default "8" is ignored. [realized] is a [gRealized] result.
     */
    @JvmStatic @JvmOverloads fun resolveG(realized: JSONObject? = null, fixedPoint: Double? = null, userG: Double? = null,
                                          legacyHourlyText: String? = null, priors: JSONObject = EconomicsV2.PRIORS): JSONObject {
        val p = EconomicsV2.pri(priors)
        val legacy = legacyHourlyText?.trim()?.takeIf { it != "" && it != "8" }
        if (userG != null) return EconomicsV2.obj("g" to userG, "source" to "user", "calibrating" to false, "legacyTarget" to legacy)
        val nDays = realized.num("nDays"); val hours = realized.num("hours")
        if (realized == null || nDays == null || hours == null || nDays < 5 || hours < 20)
            return EconomicsV2.obj("g" to p.gPrior, "band" to p.gBand, "source" to "prior", "calibrating" to true, "legacyTarget" to legacy)
        val w = nDays / (nDays + 5.0)
        val base = fixedPoint ?: realized.num("value")!!
        var g = w * base + (1 - w) * p.gPrior
        val ciHi = realized.getJSONArray("ci90").getDouble(1)
        val capped = g > ciHi
        g = EconomicsV2.pyMin(g, ciHi)
        return EconomicsV2.obj("g" to g, "source" to "personal", "calibrating" to false, "weightPersonal" to w,
            "cappedAtRealizedCI" to capped, "legacyTarget" to legacy)
    }

    // ------------------------------------------------------------------------------------------------------------
    // 8. Wolt's unknown decline threshold K
    // ------------------------------------------------------------------------------------------------------------
    internal class KPost(val support: List<Double>, val probs: List<Double>, val consistent: Boolean) {
        fun json() = EconomicsV2.obj("support" to support.map { EconomicsV2.numOut(it) }, "probs" to probs, "consistent" to consistent)
    }
    internal fun kPost(history: JSONObject?, p: EconomicsV2.Pri): KPost {
        val lo = history.num("maxCountWithoutTrigger") ?: 0.0
        val trig = (history?.opt("triggerCounts") as? JSONArray)?.let { EconomicsV2.dlist(it) } ?: emptyList()
        val hi = if (trig.isNotEmpty()) trig.reduce { a, b -> EconomicsV2.pyMin(a, b) } else INF
        val idx = p.kSupport.indices.filter { lo < p.kSupport[it] && p.kSupport[it] <= hi }
        val s = nsum(idx.size) { p.kProbs[idx[it]] }
        if (s <= 0) return KPost(p.kSupport, p.kProbs, false)
        return KPost(idx.map { p.kSupport[it] }, idx.map { p.kProbs[it] / s }, true)
    }
    /** Truncation update: K > maxCountWithoutTrigger and K <= min(triggerCounts). {support, probs, consistent}. */
    @JvmStatic @JvmOverloads fun woltKPosterior(history: JSONObject?, priors: JSONObject = EconomicsV2.PRIORS): JSONObject =
        kPost(history, EconomicsV2.pri(priors)).json()

    // ------------------------------------------------------------------------------------------------------------
    // 12. Small-sample estimators (closed form) used to fill calibration inputs
    // ------------------------------------------------------------------------------------------------------------
    private fun median(s: List<Double>): Double { val n = s.size; return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2 }

    /**
     * Log-ratio model y = log((T - excess) / M): median location, 1.4826 * MAD scale (floor 0.10), normal-normal shrinkage
     * toward the prior. {mu, sigma, n, rawMedian}; n >= 10 makes the calibration 'personal'.
     */
    @JvmStatic @JvmOverloads fun timeCalibration(actualTotalMin: List<Double>, shownM: List<Double>, excessReady: List<Double>? = null,
                                                 priorMu: Double = Math.log(1.3), priorSigma0: Double = 0.2): JSONObject {
        val ex = if (excessReady.isNullOrEmpty()) List(actualTotalMin.size) { 0.0 } else excessReady
        val n0 = minOf(actualTotalMin.size, ex.size, shownM.size)
        val ys = (0 until n0).filter { shownM[it] > 0 && actualTotalMin[it] > 0 }
            .map { Math.log(EconomicsV2.pyMax(0.1, actualTotalMin[it] - ex[it]) / shownM[it]) }
        val n = ys.size
        if (n == 0) return EconomicsV2.obj("mu" to priorMu, "sigma" to 0.30, "n" to 0)
        val med = median(ys.sortedWith(EconomicsV2.pyOrder))
        val madv = median(ys.map { Math.abs(it - med) }.sortedWith(EconomicsV2.pyOrder))
        val sigma = EconomicsV2.pyMax(0.10, 1.4826 * madv)
        val precD = n / (sigma * sigma); val prec0 = 1 / (priorSigma0 * priorSigma0)
        val mu = (precD * med + prec0 * priorMu) / (precD + prec0)
        return EconomicsV2.obj("mu" to mu, "sigma" to sigma, "n" to n, "rawMedian" to med)
    }

    /** Robust fee fit fee = a + b*x (e.g. Wolt fee vs shown km). Gate: n >= 15 and x range >= 2. {a, b, n, gatePassed}. */
    @JvmStatic fun theilSen(xs: List<Double>, ys: List<Double>): JSONObject {
        val sl = ArrayList<Double>()
        for (i in xs.indices) for (j in i + 1 until xs.size) if (xs[j] != xs[i]) sl.add((ys[j] - ys[i]) / (xs[j] - xs[i]))
        require(sl.isNotEmpty()) { "theilSen needs two distinct x values" }
        val b = median(sl.sortedWith(EconomicsV2.pyOrder))
        val a = median(xs.indices.map { ys[it] - b * xs[it] }.sortedWith(EconomicsV2.pyOrder))
        val gate = xs.size >= 15 && (xs.reduce { p, q -> EconomicsV2.pyMax(p, q) } - xs.reduce { p, q -> EconomicsV2.pyMin(p, q) }) >= 2.0
        return EconomicsV2.obj("a" to a, "b" to b, "n" to xs.size, "gatePassed" to gate)
    }

    /** Venue wait shrunk toward [mu0] with k pseudo-observations. */
    @JvmStatic @JvmOverloads fun venueWaitShrink(venueWaits: List<Double>, mu0: Double, k: Double = 5.0): Double {
        val n = venueWaits.size
        val ybar = if (n > 0) nsum(n) { venueWaits[it] } / n else 0.0
        return (n * ybar + k * mu0) / (n + k)
    }
}
