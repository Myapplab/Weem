package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsV2GoldenTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * WP1 parity: [EconomicsV2] / [EconomicsV2Estimators] reproduce `economics/golden_vectors.json` (ref.py 2.1.0):
 * numbers within 1e-6 after r6, strings, booleans, nulls and list order exact, same object key sets.
 */
class EconomicsV2GoldenTest {
    companion object {
        val doc: JSONObject by lazy {
            val s = EconomicsV2GoldenTest::class.java.classLoader!!.getResourceAsStream("economics/golden_vectors.json")!!
            JSONObject(s.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
        /** One unit in the 6th decimal; the 1e-9 absorbs the binary error of subtracting two 6-decimal values. */
        const val TOL = 1e-6 + 1e-9

        fun list(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
        fun nums(a: JSONArray) = (0 until a.length()).map { (a.get(it) as Number).toDouble() }
        private fun isNull(v: Any?) = v == null || v === JSONObject.NULL
        private fun keys(o: JSONObject): Set<String> = o.keys().asSequence().toSet()

        /** Deep comparison of an expected vector value with an r6-rounded result; appends one line per difference. */
        fun diff(e: Any?, a: Any?, path: String, out: MutableList<String>, tol: Double = TOL) {
            when {
                isNull(e) || isNull(a) -> if (!(isNull(e) && isNull(a))) out.add("$path: expected $e, got $a")
                e is JSONObject && a is JSONObject -> {
                    val ek = keys(e); val ak = keys(a)
                    if (ek != ak) out.add("$path: keys missing ${ek - ak}, extra ${ak - ek}")
                    for (k in ek intersect ak) diff(e.opt(k), a.opt(k), "$path.$k", out, tol)
                }
                e is JSONArray && a is JSONArray ->
                    if (e.length() != a.length()) out.add("$path: length ${e.length()} vs ${a.length()}: $e / $a")
                    else for (i in 0 until e.length()) diff(e.opt(i), a.opt(i), "$path[$i]", out, tol)
                e is Boolean || a is Boolean -> if (e != a) out.add("$path: expected $e, got $a")
                e is Number && a is Number -> {
                    val d = Math.abs(e.toDouble() - a.toDouble())
                    if (!(d <= tol)) out.add("$path: expected $e, got $a (diff $d)")
                }
                e is String && a is String -> if (e != a) out.add("$path: expected '$e', got '$a'")
                else -> out.add("$path: type ${e!!::class.java.simpleName} vs ${a!!::class.java.simpleName}")
            }
        }

        /**
         * The vector's input, except W2: ref.py computed it from PERSONAL_CAL = ln(1.42) / ln(1.30) but stored the mu values
         * rounded to 6 decimals, which alone moves tau by 2e-6 (ref.py itself misses `expected` by 2e-6 on the stored input).
         * The exact generator values are restored here; [storedW2InputIsOnlyInputRoundingAway] covers the stored input.
         */
        fun exactInput(v: JSONObject): JSONObject {
            val inp = JSONObject(v.getJSONObject("input").toString())
            if (v.getString("id") == "W2_wolt_real_231_personal") {
                val cal = inp.getJSONObject("calibration")
                for ((plat, k) in listOf("wolt" to 1.42, "bolt" to 1.30)) {
                    val c = cal.getJSONObject(plat)
                    assertEquals("W2 stored $plat mu", EconomicsV2.r6(Math.log(k)) as Double, c.getDouble("mu"), 0.0)
                    c.put("mu", Math.log(k))
                }
            }
            return inp
        }

        fun run(v: JSONObject, inp: JSONObject = exactInput(v)): JSONObject =
            if (v.getString("kind") == "stack") EconomicsV2.evaluateStack(inp) else EconomicsV2.evaluate(inp)

        fun vector(id: String) = list(doc.getJSONArray("offerVectors")).single { it.getString("id") == id }
    }

    @Test fun offerVectorsMatchRefPy() {
        val vecs = list(doc.getJSONArray("offerVectors"))
        assertEquals(34, vecs.size)
        val bad = mutableListOf<String>()
        for (v in vecs) {
            val out = mutableListOf<String>()
            diff(v.get("expected"), EconomicsV2.r6(run(v)), v.getString("id"), out)
            if (out.isNotEmpty()) bad.add(out.take(12).joinToString("\n  ", prefix = "${v.getString("id")} (${out.size}):\n  "))
        }
        assertTrue("${bad.size}/34 offer vectors differ:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun storedW2InputIsOnlyInputRoundingAway() {
        val v = vector("W2_wolt_real_231_personal")
        val out = mutableListOf<String>()
        diff(v.get("expected"), EconomicsV2.r6(run(v, v.getJSONObject("input"))), "W2(stored)", out, tol = 5e-6)
        assertTrue(out.joinToString("\n"), out.isEmpty())
    }

    @Test fun auxVectorsMatchRefPy() {
        val aux = list(doc.getJSONArray("auxVectors"))
        assertEquals(8, aux.size)
        val bad = mutableListOf<String>()
        for (v in aux) {
            val i = v.getJSONObject("input")
            val got: Any = when (val kind = v.getString("kind")) {
                "resolve_g" -> EconomicsV2Estimators.resolveG(i.optJSONObject("realized"), i.opt("fixedPoint").let { if (it is Number) it.toDouble() else null },
                    null, i.opt("legacyHourly") as? String)
                "g_realized" -> EconomicsV2Estimators.gRealized(EconomicsV2Estimators.days(i.getJSONArray("days")), i.getInt("B"), i.getLong("seed"))
                "solve_g" -> EconomicsV2Estimators.fixedPoint(EconomicsV2Estimators.pool(i.getJSONArray("pool")), i.getDouble("lambdaPerH"))
                "wolt_k_posterior" -> EconomicsV2Estimators.woltKPosterior(i)
                "time_calibration" -> EconomicsV2Estimators.timeCalibration(nums(i.getJSONArray("actualTotalMin")), nums(i.getJSONArray("shownM")),
                    nums(i.getJSONArray("excessReady")))
                "theil_sen" -> EconomicsV2Estimators.theilSen(nums(i.getJSONArray("km")), nums(i.getJSONArray("fee")))
                "lambda_posterior" -> EconomicsV2Estimators.lambdaPosterior(i.getString("zone"), i.getString("hb"), i.getJSONObject("counts"))
                else -> throw AssertionError("unknown aux kind $kind")
            }
            val out = mutableListOf<String>()
            diff(v.get("expected"), EconomicsV2.r6(got), v.getString("id"), out)
            bad.addAll(out)
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** E1/E4 carry the joint (g, mu) solution they were built from: SYNTH_POOL (= G4's pool), 6 offers/h, 4 h left. */
    @Test fun energyVectorsMatchSolveJoint() {
        val pool = EconomicsV2Estimators.pool(list(doc.getJSONArray("auxVectors")).single { it.getString("kind") == "solve_g" }
            .getJSONObject("input").getJSONArray("pool"))
        val out = mutableListOf<String>()
        val e1 = vector("E1_energy_binding_mu").getJSONObject("extra")
        diff(e1.get("solveJoint"), EconomicsV2.r6(EconomicsV2Estimators.solveJoint(pool, 6.0, 4.0, e1.getDouble("energyBudgetWh"))), "E1", out)
        diff(e1.get("solveJointNonBinding"), EconomicsV2.r6(EconomicsV2Estimators.solveJoint(pool, 6.0, 4.0, 1e9)), "E1.nonBinding", out)
        val e4 = vector("E4_energy_binding_capped_by_charging").getJSONObject("extra")
        diff(e4.get("solveJoint"), EconomicsV2.r6(EconomicsV2Estimators.solveJoint(pool, 6.0, 4.0, e4.getDouble("energyBudgetWh"), 170.0)), "E4", out)
        assertTrue(out.joinToString("\n"), out.isEmpty())
        // the vectors' g and mu inputs are the solution rounded to 6 decimals
        assertEquals(e1.getJSONObject("solveJoint").getDouble("mu"), vector("E1_energy_binding_mu").getJSONObject("input").getJSONObject("state").getDouble("mu"), 0.0)
    }

    @Test fun feeMutationChangesSurplus() {
        val v = vector("W1_wolt_real_231_prior")
        val base = run(v).getJSONObject("calculation").getJSONObject("surplus").getDouble("mid")
        val inp = exactInput(v); val offer = inp.getJSONObject("offer")
        offer.put("feeAzn", offer.getDouble("feeAzn") + 0.50)
        val moved = EconomicsV2.evaluate(inp).getJSONObject("calculation").getJSONObject("surplus").getDouble("mid")
        assertEquals("simplified 2% tax keeps 98% of +0.50", 0.49, moved - base, 1e-9)
        val exp = v.getJSONObject("expected").getJSONObject("calculation").getJSONObject("surplus").getDouble("mid")
        assertTrue("mutated surplus must leave the vector", Math.abs(EconomicsV2.r6(moved) as Double - exp) > 1e-3)
    }

    @Test fun rankOffersBySurplusWithoutBlocked() {
        val res = list(doc.getJSONArray("offerVectors")).map { it.getString("id") to run(it) }
        val ranked = EconomicsV2.rankOffers(res)
        val usable = res.filter { (_, r) -> !r.isNull("calculation") && r.getString("verdict") != "BLOCKED" }
        assertEquals(usable.size, ranked.size)
        val mids = ranked.map { id -> res.first { it.first == id }.second.getJSONObject("calculation").getJSONObject("surplus").getDouble("mid") }
        assertTrue(mids.zipWithNext().all { (a, b) -> a >= b })
        assertTrue("X4_fee_unreadable" !in ranked && "E3_battery_escape_block" !in ranked)
    }

    @Test fun evaluateUnderFiveMsOnTheJvm() {
        val vecs = list(doc.getJSONArray("offerVectors")).map { it to exactInput(it) }
        repeat(30) { for ((v, i) in vecs) run(v, i) }  // JIT warm-up
        val reps = 50
        for ((v, i) in vecs) {
            val t0 = System.nanoTime()
            repeat(reps) { run(v, i) }
            val ms = (System.nanoTime() - t0) / 1e6 / reps
            assertTrue("${v.getString("id")}: $ms ms per evaluate", ms < 5.0)
        }
    }

    @Test fun pythonRoundingAndFormatting() {
        // r6 = Python round(x, 6): half-even on the exact binary value, -0.0 -> 0.0, non-finite -> null
        val r = listOf(0.0000005 to 0.0, 2.5e-6 to 3e-6, 1.0000005 to 1.000001, 0.1234565 to 0.123456, 7.0000015 to 7.000001, -1e-9 to 0.0)
        for ((x, want) in r) assertEquals("r6($x)", want, EconomicsV2.r6(x) as Double, 0.0)
        assertEquals(1.0 / 0.0, 1.0 / (EconomicsV2.r6(-1e-9) as Double), 0.0)
        assertTrue(EconomicsV2.r6(Double.NaN) === JSONObject.NULL)
        assertEquals(3, EconomicsV2.r6(3))
        // f"{x:.2f}" / f"{x:+.1f}"
        assertEquals("0.12", EconomicsV2.pyFixed(0.125, 2)); assertEquals("2.67", EconomicsV2.pyFixed(2.675, 2))
        assertEquals("-0.0", EconomicsV2.pyFixed(-0.04, 1, plus = true)); assertEquals("+0.1", EconomicsV2.pyFixed(0.05, 1, plus = true))
        assertEquals("+0.2", EconomicsV2.pyFixed(0.25, 1, plus = true)); assertEquals("-0.00", EconomicsV2.pyFixed(-0.001, 2))
        assertEquals("1.00", EconomicsV2.pyFixed(1.0049999999999999, 2))
        // Python float % (divisor's sign) and fractional hour blocks
        assertEquals(1410.0, EconomicsV2.pyMod(-30.0, 1440.0), 0.0); assertEquals(1439.5, EconomicsV2.pyMod(-0.5, 1440.0), 0.0)
        assertEquals("B5", EconomicsV2.hourBlock(-30.0)); assertEquals("B1", EconomicsV2.hourBlock(2140.0))
        assertEquals("B1", EconomicsV2.hourBlock(704.999)); assertEquals("B2", EconomicsV2.hourBlock(705.0))
    }
}
