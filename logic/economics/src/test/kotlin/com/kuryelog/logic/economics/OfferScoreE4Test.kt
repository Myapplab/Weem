package com.kuryelog.logic.economics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats OfferScoreE4Test.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * E4 (Faz 4): "Teklif puanı". Every offer gets a 1–10 score from EconomicsV2 (SPEC 2.1.0): fee ≤ feePoorBelow → 1–3, between
 * the thresholds → 4–7 linearly, ≥ feeGoodFrom → 8–10 by the surplus. Computed from the card at offer time, stored once with
 * the engine version, never recomputed; no evaluation → the reason. The courier's own 1–10 is stored apart; the day screen
 * shows both, the average difference and the biggest differences. Real engine runs on a made-up Wolt card.
 */
class OfferScoreE4Test {
    private val wolt = "com.wolt.courierapp"
    /** 2026-10-06 18:00 Baku, like the shadow corpora. */
    private val t0 = 1791295200000L

    private fun card(fee: Double?) = CourierObservation(wolt, t0, ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(), gross = fee, km = 2.0,
        minLo = 9, minHi = 17, restaurant = "Test Döner")
    private fun evaluate(fee: Double?, commission: Double? = 20.0): Pair<JSONObject, JSONObject> {
        val inp = ShadowEvaluator.input(OfferState("o", wolt, t0, card(fee)), t0, ShadowEvaluator.Context(commissionPct = commission))!!
        return inp to EconomicsV2.evaluate(inp)
    }
    private fun thresholds(commission: Double? = 20.0): Pair<Double, Double> {
        val c = evaluate(3.0, commission).second.getJSONObject("calculation")
        return c.getDouble("feePoorBelow") to c.getDouble("feeGoodFrom")
    }
    private fun score(fee: Double, commission: Double? = 20.0) = OfferScore.of(evaluate(fee, commission).second, fee)

    @Test fun bandsFollowTheEngineThresholds() {
        val (poor, good) = thresholds()
        assertTrue("$poor < $good", poor > 0 && poor < good)
        assertEquals("poor", score(poor).band); assertEquals(3, score(poor).value)
        assertEquals("good", score(good).band); assertEquals(8, score(good).value)
        // Linear 4–7 in between: four equal parts.
        val w = good - poor
        assertEquals(listOf(4, 5, 6, 7), listOf(0.1, 0.3, 0.6, 0.9).map { score(poor + it * w).value })
        assertTrue((1..10).contains(score(poor + 0.5 * w).value!!))
        // The bands match the engine's verdict: POOR below, GOOD above.
        assertEquals("POOR", evaluate(poor - 0.01).second.getJSONObject("calculation").getString("economicVerdict"))
        assertEquals("GOOD", evaluate(good + 0.01).second.getJSONObject("calculation").getString("economicVerdict"))
    }

    @Test fun outerBandsStepWithTheSurplus() {
        val (poor, good) = thresholds()
        // Each 0.5 AZN of surplus above feeGoodFrom (S_lo) is one point, 10 at most; the same below feePoorBelow (S_hi + κ).
        val perAzn = { fee: Double -> evaluate(fee).second.getJSONObject("calculation").getJSONObject("surplus").getDouble("lo") }
        val slope = perAzn(good + 1.0) - perAzn(good)   // AZN of S_lo per AZN of fee
        assertTrue(slope > 0.5 && slope < 1.0)
        assertEquals(8, score(good + 0.4 / slope).value); assertEquals(9, score(good + 0.6 / slope).value); assertEquals(10, score(good + 1.2 / slope).value)
        assertEquals(10, score(good + 10.0).value)
        assertEquals(3, score(poor - 0.4 / slope).value); assertEquals(2, score(poor - 0.6 / slope).value)
        val deep = poor - 1.2 / slope
        if (deep > 0) assertEquals(1, score(deep).value)
        // Any fee at or below feePoorBelow stays in 1–3.
        for (f in listOf(0.05, poor / 2, poor)) assertTrue("$f", score(f).value!! in 1..3)
    }

    @Test fun unknownFeeBasisUsesTheConservativeKeepLikeTheThresholds() {
        val (poor, good) = thresholds(commission = null)
        assertEquals(8, score(good, commission = null).value)
        assertEquals(3, score(poor, commission = null).value)
        assertEquals("middle", score((poor + good) / 2, commission = null).band)
    }

    @Test fun noVerdictGivesTheReason() {
        val (_, r) = evaluate(null)
        assertEquals(OfferScore.Score(null, null, "FEE_UNREADABLE"), OfferScore.of(r, null))
        assertEquals(OfferScore.Score(null, null, OfferScore.NOT_COMPUTED), OfferScore.of(null, 2.5))
        val tr = { a: String, _: String -> a }
        assertEquals("ücret okunamadı", OfferScore.reasonText("FEE_UNREADABLE", tr))
        assertTrue(OfferScore.reasonText(OfferScore.NOT_COMPUTED, tr).startsWith("hesaplanmadı"))
        // On the screens the reason stands in for the app's score; no row at all (orders before E4) says so.
        val unreadable = JSONObject().put("schemaVersion", 1).put("kind", "offer_score").put("offer_id", "o").put("score", JSONObject.NULL).put("reason", "FEE_UNREADABLE")
        assertEquals("Uygulama — (ücret okunamadı) · Sen —", OfferScore.pairText(unreadable, null, tr))
        assertEquals("Uygulama — (puan kaydı yok) · Sen 6", OfferScore.pairText(null, OfferScore.ratingRow("o", null, wolt, 6, t0, OfferScore.VIA_DAY), tr))
    }

    @Test fun storedRowCarriesTheEngineVersionAndNeverChanges() {
        val (inp, res) = evaluate(4.20)
        val entry = ShadowMode.Entry("offer-1", 4.20, 2.0, t0 + 500, inp, res, null, 0L, null)
        val offerRow = JSONObject().put("offerKey", "offer-1").put("pkg", wolt).put("platform", "wolt").put("firstSeenAtMs", t0)
            .put("decisionKind", "accepted_observed").put("decisionAtMs", t0 + 9_000).put("feeAzn", 4.20)
        val row = OfferScore.row(offerRow, entry, "trip-1", t0 + 9_500)
        assertEquals("offer_score", row.getString("kind")); assertEquals("offer-1", row.getString("offer_id")); assertEquals("trip-1", row.getString("trip_id"))
        assertEquals("EconomicsV2", row.getString("engine")); assertEquals("2.1.0", row.getString("spec_version")); assertEquals(1, row.getInt("mapping_version"))
        assertEquals(t0 + 500, row.getLong("computed_at"))
        assertEquals(OfferScore.of(res, 4.20).value, row.getInt("score"))
        for (k in listOf("fee_poor_below", "fee_good_from", "surplus_lo", "kappa", "g")) assertTrue(k, row.get(k) is Number)
        assertEquals("prior", row.getString("g_source"))
        // No customer data: ids and numbers only.
        assertFalse(row.toString().contains("Test Döner"))
        // The first row of an offer is its score: a later row (another engine, a restart) does not replace it.
        val later = JSONObject(row.toString()).put("score", 2).put("spec_version", "2.2.0")
        val kept = OfferScore.scores(sequenceOf(row.toString(), later.toString()))
        assertEquals(row.getInt("score"), kept.getValue("offer-1").getInt("score"))
        // The fee the engine evaluated is the one scored (a fee read after a decline tap does not mix with its surplus).
        val moved = OfferScore.row(JSONObject(offerRow.toString()).put("feeAzn", 9.0), entry, "trip-1", t0 + 9_500)
        assertEquals(row.getInt("score"), moved.getInt("score")); assertEquals(4.20, moved.getDouble("fee_azn"), 1e-9)
        // Without an evaluation the row says why.
        val none = OfferScore.row(offerRow, null, null, t0 + 9_500)
        assertTrue(none.isNull("score")); assertEquals(OfferScore.NOT_COMPUTED, none.getString("reason"))
    }

    @Test fun myScoreIsSeparateAndTheNewestWins() {
        val a = OfferScore.ratingRow("offer-1", "trip-1", wolt, 5, t0 + 3_600_000, OfferScore.VIA_DAY)
        val b = OfferScore.ratingRow("offer-1", "trip-1", wolt, 7, t0 + 3_700_000, OfferScore.VIA_DAY)
        assertEquals("offer_rating", a.getString("kind"))
        val r = OfferScore.ratings(sequenceOf(a.toString(), b.toString(), """{"schemaVersion":1,"kind":"offer_rating","offer_id":"offer-1","rating":11,"at":${t0 + 9_000_000}}"""))
        assertEquals(7, OfferScore.ratingOf(r["offer-1"]))
        assertThrows(IllegalArgumentException::class.java) { OfferScore.ratingRow("offer-1", null, wolt, 0, t0, OfferScore.VIA_DAY) }
        // The app's score file is untouched by ratings and the other way round.
        assertTrue(OfferScore.scores(sequenceOf(a.toString())).isEmpty())
    }

    @Test fun daySummaryAverageAndBiggestDifferences() {
        fun s(id: String, v: Int?) = id to JSONObject().put("schemaVersion", 1).put("kind", "offer_score").put("offer_id", id).put("score", v ?: JSONObject.NULL)
        fun m(id: String, v: Int) = id to OfferScore.ratingRow(id, null, wolt, v, t0, OfferScore.VIA_DAY)
        val scores = mapOf(s("a", 8), s("b", 3), s("c", 6), s("d", null), s("e", 5))
        val mine = mapOf(m("a", 5), m("b", 4), m("c", 6), m("d", 7))
        val x = OfferScore.summary(listOf("a", "b", "c", "d", "e"), scores, mine)
        assertEquals(3, x.compared)                       // d has no app score, e has no rating of mine
        assertEquals((3 - 1 + 0) / 3.0, x.meanDiff!!, 1e-9)
        assertEquals((3 + 1 + 0) / 3.0, x.meanAbsDiff!!, 1e-9)
        assertEquals(listOf("a", "b"), x.biggest.map { it.offerId })
        // One order on the screen: "Uygulama N · Sen M (fark)"; the day's average with its sign.
        val tr = { a: String, _: String -> a }
        assertEquals("Uygulama 8 · Sen 5 (fark +3)", OfferScore.pairText(scores["a"], mine["a"], tr))
        assertEquals("Uygulama 3 · Sen 4 (fark -1)", OfferScore.pairText(scores["b"], mine["b"], tr))
        assertEquals("Uygulama 5 · Sen —", OfferScore.pairText(scores["e"], null, tr))
        assertEquals("+0.7", OfferScore.signed(x.meanDiff!!)); assertEquals("0.0", OfferScore.signed(-0.04)); assertEquals("-1.5", OfferScore.signed(-1.5))
        assertEquals(OfferScore.Summary(0, null, null, emptyList()), OfferScore.summary(listOf("e"), scores, mine))
    }

    // ------------------------------------------------------------------ wiring (source checks; the screen is telefonda denenmedi)
    // WEEM: `OrderTracker` → `EconomicsObserver` (logic/economics), ekran Compose (ECONOMICS.md 6).

    @Test fun recordedExportedAndShownOnTheOrdersPage() {
        // Written once per resolved offer from its offer row, both files in backups and exports (economics-* are kept whole).
        assertTrue(RepoSource.text("logic/economics/src/main/kotlin/com/kuryelog/logic/economics/EconomicsObserver.kt").contains("OfferScore.row("))
        for (f in listOf(OfferScore.FILE, OfferScore.RATINGS)) {
            assertTrue(f, f in BackupFiles.logNames)
            assertEquals(f, AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("2026-10-14/$f"))
        }
        val py = RepoSource.text("tools/analysis/anonymize.py")
        assertTrue(py.contains("name.startswith(\"economics-\")"))
    }
}
