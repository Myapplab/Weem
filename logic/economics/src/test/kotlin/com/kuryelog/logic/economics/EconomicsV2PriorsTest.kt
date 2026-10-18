package com.kuryelog.logic.economics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsV2PriorsTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/** WP1: EconomicsV2.PRIORS and the constants equal ref.py 2.1.0 (golden_vectors.json `priors`, key by key). */
class EconomicsV2PriorsTest {
    private val golden get() = EconomicsV2GoldenTest.doc

    @Test fun priorsEqualGoldenKeyByKey() {
        val exp = golden.getJSONObject("priors")
        val got = EconomicsV2.r6(EconomicsV2.PRIORS) as JSONObject
        val out = mutableListOf<String>()
        val keys = exp.keys().asSequence().toList()
        assertEquals(keys.toSet(), got.keys().asSequence().toSet())
        for (k in keys) EconomicsV2GoldenTest.diff(exp.get(k), got.get(k), "priors.$k", out, tol = 0.0)
        assertTrue(out.joinToString("\n"), out.isEmpty())
        assertEquals(33, keys.size)
    }

    @Test fun buildPriorsIsAFreshEqualCopy() {
        val copy = EconomicsV2.buildPriors()
        assertNotSame(EconomicsV2.PRIORS, copy)
        assertEquals(EconomicsV2.PRIORS.toString(), copy.toString())
    }

    @Test fun constantsFromRefPy() {
        assertEquals("2.1.0", EconomicsV2.SPEC_VERSION); assertEquals(golden.getString("specVersion"), EconomicsV2.SPEC_VERSION)
        val rng = EconomicsV2Estimators.Lcg32(20261006)
        assertEquals(EconomicsV2.LCG_REFERENCE_FIRST5, List(5) { rng.index(1000) })
        assertEquals(17, EconomicsV2.ICON_ORDER.size)
        assertEquals("BATTERY_ESCAPE_NOT_MET", EconomicsV2.ICON_ORDER.first()); assertEquals("STOP_SUGGESTED_FATIGUE", EconomicsV2.ICON_ORDER.last())
        assertEquals(mapOf("POOR" to 0, "MARGINAL" to 1, "GOOD" to 2), EconomicsV2.VERDICT_RANK)
        assertEquals(listOf("Z1", "Z2", "Z3", "Z4"), EconomicsV2.ZONES.map { it.id })
        assertEquals(listOf(0.0, 2.5, 6.0, 12.0), EconomicsV2.ZONES.map { it.lo })
        assertEquals(listOf(0.0, 480.0, 705.0, 900.0, 1080.0, 1290.0), EconomicsV2.HOUR_BLOCKS.map { it.lo })
        assertEquals(40.4093, EconomicsV2.HUB_DEFAULT[0], 0.0); assertEquals(49.8671, EconomicsV2.HUB_DEFAULT[1], 0.0)
        assertEquals("Z1", EconomicsV2.zoneOf(40.4093, 49.8671))
        assertEquals("Z2", EconomicsV2.zoneOfRadius(2.5)); assertEquals("Z4", EconomicsV2.zoneOfRadius(30.0))
        // one degree of latitude on R = 6371.0088 km
        assertEquals(111.19508, EconomicsV2.haversineKm(40.0, 49.0, 41.0, 49.0), 1e-5)
    }

    @Test fun priorsArgumentIsUsed() {
        val v = EconomicsV2GoldenTest.vector("W1_wolt_real_231_prior")
        val inp = EconomicsV2GoldenTest.exactInput(v)
        val noTax = EconomicsV2.buildPriors()
        noTax.getJSONObject("tax").put("simplified_rate", 0.0)
        assertEquals(2.2638, EconomicsV2.evaluate(inp).getJSONObject("calculation").getJSONObject("revenue").getDouble("R"), 1e-12)
        assertEquals(2.31, EconomicsV2.evaluate(inp, noTax).getJSONObject("calculation").getJSONObject("revenue").getDouble("R"), 1e-12)
        assertEquals("PRIORS untouched", 0.02, EconomicsV2.PRIORS.getJSONObject("tax").getDouble("simplified_rate"), 0.0)
    }
}
