package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats ShadowEvaluatorTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * Shadow mode (SPEC 2.1.0 §19, WP8). The recorded offers of the research corpora (`economics/shadow_corpus.json`: the
 * offer scenarios and offer parser rows of harness/golden_corpus.json and the field corpus sonuc-2026-10-06/saha, copied
 * verbatim by `shadow_parity.py --make-corpus`) run through the real parser, core, recorder and [ShadowMode] the way
 * OrderTracker wires them. With `KL_SHADOW_OUT=<dir>` every engine input, result and verdictShadow is dumped for
 * `python3 -I docs/ekonomi-v2/araclar/shadow_parity.py <dir>` (ref.evaluate on the same inputs, 1e-6); with
 * `KL_ROWS_OUT=<dir>` the offer rows are dumped for `validate_rows.py`. Rows marked "synthetic" vary a recorded card.
 */
class ShadowEvaluatorTest {
    companion object {
        val corpus: JSONObject by lazy {
            val s = ShadowEvaluatorTest::class.java.classLoader!!.getResourceAsStream("economics/shadow_corpus.json")!!
            JSONObject(s.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
        fun objects(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
        fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
        private val shadowDump = ArrayList<String>()
    }
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    /** 2026-10-06 18:00 Baku, like the corpora. */
    private val t0 = 1791295200000L

    /** Mirrors OrderTracker.onScreen / onClick / stop / init-after-death around the pure core, recorder and shadow mode. */
    private inner class Rig(val name: String, val commission: Double? = 20.0, val zone: String = OfferGeo.UNKNOWN, val appMode: String = "shadow") {
        var core = DeliveryCore()
        var sessions = OnlineSessions()
        var rec = EconomicsRecorder()
        var shadow = ShadowMode()
        val rows = ArrayList<EconomicsRecorder.Row>()
        val evaluations = ArrayList<Pair<OfferState, ShadowMode.Entry>>()
        /** Offers restored after a process death (their verdict was in the dead process), and when it died. */
        val restored = HashSet<String>()
        var diedAt = Long.MIN_VALUE
        val basis: (String) -> String = { if (commission != null) "gross_before_park" else "unknown" }
        init { wire() }
        private fun wire() { rec.verdictShadow = { shadow.verdictFor(it) } }
        fun context(now: Long) = ShadowEvaluator.Context.of(rec.courierState(now, core, sessions.openSessions().map { it.pkg }, zone, null),
            commission, "g3", appMode)
        fun frame(o: CourierObservation): List<CoreEvent> {
            sessions.observe(o)
            val before = core.pending.values.associateBy { it.id }
            val ev = core.observe(o)
            ev.forEach { sessions.event(it, 1) }
            val offer = core.pending[o.pkg]
            shadow.frame(o, offer) { context(o.at) }?.let { evaluations.add(offer!! to it) }
            rows += rec.frame(o, ev, before, core, sessions.openSessions(), zone, basis)
            return ev
        }
        fun click(pkg: String, text: String, at: Long) {
            val before = core.pending.values.associateBy { it.id }
            val ev = core.click(pkg, text, at)
            ev.forEach { sessions.event(it) }
            rows += rec.events(at, ev, before, core, sessions.openSessions(), basis)
        }
        fun stop(at: Long) {
            sessions.stop()
            val before = core.pending.values.associateBy { it.id }
            rows += rec.stop(at, core.stop(at), before, core, basis)
        }
        fun processDeath(at: Long) {
            val old = core; core = DeliveryCore()
            old.trips.forEach { (k, v) -> core.trips[k] = v }
            val dropped = ArrayList<OfferState>()
            old.pending.forEach { (k, v) -> if (WaitObservation.bakuDay(v.firstAt) == WaitObservation.bakuDay(at)) core.pending[k] = v else dropped.add(v) }
            restored += old.pending.values.map { it.id }; diedAt = at
            val raw = sessions.encode(); sessions = OnlineSessions().also { it.restore(raw) }
            rec = EconomicsRecorder(); shadow = ShadowMode(); wire()
            rows += rec.restart(at, core.pending.values, dropped, basis)
            core.start()
        }
        fun replay(sc: JSONObject) {
            for (st in objects(sc.getJSONArray("steps"))) {
                val at = st.getLong("at")
                when (st.getString("kind")) {
                    "shift_start" -> core.start()
                    "shift_stop" -> stop(at)
                    "frame" -> frame(CourierParser.parse(st.getString("pkg"), strings(st.getJSONArray("texts")), strings(st.optJSONArray("actions")), at,
                        st.optString("source", "accessibility"), st.optBoolean("complete", true)))
                    "click" -> click(st.getString("pkg"), st.getString("text"), at)
                    "process_death" -> processDeath(at)
                    "distance" -> { core.addDistance(st.getDouble("m")); rec.distance(at, st.getDouble("m")) }
                }
            }
        }
        fun offers() = rows.filter { it.file == EconomicsLogs.OFFERS }.map { it.json }
    }

    private fun obs(pkg: String, at: Long, texts: List<String>, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, texts, actions, at, source)
    private fun parserRow(id: String) = objects(corpus.getJSONArray("parser")).single { it.getString("id") == id }
    private fun card(id: String, at: Long): CourierObservation {
        val p = parserRow(id)
        return obs(p.getString("pkg"), at, strings(p.getJSONArray("texts")), strings(p.optJSONArray("actions")), if (id == "FP02" || id == "FP08") "ocr" else "accessibility")
    }
    private fun offer(o: CourierObservation) = OfferState("offer-${o.at}", o.pkg, o.at, o)
    private fun eval(o: OfferState, now: Long, c: ShadowEvaluator.Context): Pair<JSONObject, JSONObject> {
        val inp = ShadowEvaluator.input(o, now, c)!!
        return inp to EconomicsV2.evaluate(inp)
    }
    private fun dump(id: String, inp: JSONObject, res: JSONObject, verdict: JSONObject?) {
        val line = JSONObject().put("id", id).put("input", inp).put("output", EconomicsV2.r6(res)).put("verdictShadow", verdict ?: JSONObject.NULL).toString()
        shadowDump.add(line)
        val dir = System.getenv("KL_SHADOW_OUT") ?: return
        java.io.File(dir).apply { mkdirs() }.resolve("ShadowEvaluatorTest.jsonl").appendText(line + "\n")
    }
    private fun dumpRows(rows: List<JSONObject>) {
        val dir = System.getenv("KL_ROWS_OUT") ?: return
        val f = java.io.File(dir).apply { mkdirs() }.resolve("ShadowEvaluatorTest.jsonl")
        rows.forEach { f.appendText(JSONObject().put("def", "offerRecord").put("row", it).toString() + "\n") }
    }
    private fun list(a: JSONArray?) = strings(a)
    private fun calc(res: JSONObject) = res.getJSONObject("calculation")

    // ------------------------------------------------------------------ the recorded offers

    private val rigs = listOf<() -> Rig>({ Rig("c20") }, { Rig("nocommission", commission = null, zone = "Z1") }, { Rig("c12.5-Z3", commission = 12.5, zone = "Z3") })

    @Test fun everyCorpusOfferGetsItsVerdictAtTheFirstCompleteFrame() {
        val scenarios = objects(corpus.getJSONArray("scenarios"))
        assertEquals(28, scenarios.size)
        var withVerdict = 0; var rows = 0; val verdicts = sortedMapOf<String, Int>()
        for (make in rigs) for (sc in scenarios) {
            val r = make(); r.replay(sc)
            val id = sc.getString("id")
            val first = HashMap<String, ShadowMode.Entry>()
            for ((o, e) in r.evaluations) {
                assertNull("$id ${r.name}: ${e.error}", e.error)
                first.putIfAbsent(o.id, e)
                val res = e.result!!
                assertEquals("never shown", "suppressed_shadow", res.getJSONObject("display").getString("mode"))
                // local vals: the Gradle test module cannot smart-cast another module's public properties
                val verdict = e.verdict!!
                val input = e.input!!
                assertFalse(verdict.getBoolean("displayed"))
                assertEquals(e.computedAtMs, verdict.getLong("computedAtMs"))
                // the in-memory input and its JSON text give the same result (the parity script reads the text)
                assertEquals(EconomicsV2.r6(res).toString(), EconomicsV2.r6(EconomicsV2.evaluate(JSONObject(input.toString()))).toString())
                dump("$id:${r.name}:${o.id}:${e.computedAtMs}", input, res, verdict)
            }
            for ((oid, e) in first) {
                val o = r.evaluations.first { it.first.id == oid }.first
                if (oid !in r.restored) assertEquals("$id: computed at the offer's first frame", o.firstAt, e.computedAtMs)
            }
            for (row in r.offers()) {
                rows++
                val v = row.opt("verdictShadow") as? JSONObject
                if (v == null) {
                    val key = row.getString("offerKey")
                    assertTrue("$id ${r.name}: $key has no verdict but was evaluated in this process",
                        key in r.restored && r.evaluations.none { it.first.id == key && it.second.computedAtMs >= r.diedAt }); continue
                }
                withVerdict++
                verdicts[v.getString("verdict")] = (verdicts[v.getString("verdict")] ?: 0) + 1
                val reasons = list(v.getJSONArray("noVerdictReasons"))
                assertFalse("$id: $reasons", "OBSERVATION_STALE_OR_NOT_VISIBLE" in reasons || "TOO_LATE_FOR_SAFE_DECISION" in reasons)
                assertEquals("2.1.0", v.getString("specVersion")); assertFalse(v.getBoolean("displayed"))
                assertTrue("no block from missing data: $v", list(v.getJSONArray("blocks")).isEmpty())
                if (v.getString("verdict") != "NO_VERDICT") assertTrue("BATTERY_UNKNOWN is an advisory: $v", "BATTERY_UNKNOWN" in list(v.getJSONArray("flags")))
                assertEquals("prior", v.getString("gSource")); assertEquals(4.0, v.getDouble("g"), 0.0)
                val s = row.toString()
                for (secret in listOf("Customer", "Test 2", "Naxcivani", "Izmir", "Merkez", "9RQW", "Test küç", "Test Restoran")) assertFalse("$secret in $s", s.contains(secret))
            }
            dumpRows(r.offers())
        }
        assertTrue("rows: $rows, with verdict: $withVerdict", withVerdict >= rows - 6 && withVerdict > 60)
        // G/M/P verdicts dominate: computing at resolution instead would make them NO_VERDICT (see the next test)
        assertTrue("$verdicts", (verdicts["GOOD"] ?: 0) + (verdicts["MARGINAL"] ?: 0) + (verdicts["POOR"] ?: 0) > withVerdict * 3 / 4)
        println("shadow corpus verdicts: $verdicts of $rows offer rows")
    }

    @Test fun atResolutionTheSameOffersWouldBeNoVerdict() {
        // Why WP8 computes at the first frame: at the decision instant the card is gone or about to expire.
        var late = 0; var total = 0
        for (sc in objects(corpus.getJSONArray("scenarios"))) {
            val r = Rig("late"); r.replay(sc)
            for (row in r.offers()) {
                val o = r.evaluations.firstOrNull { it.first.id == row.getString("offerKey") }?.first ?: continue
                if (row.getString("decisionKind") == "accepted_observed") continue
                total++
                val (_, res) = eval(o, row.getLong("decisionAtMs"), r.context(row.getLong("decisionAtMs")))
                if (res.getString("verdict") == "NO_VERDICT") late++
            }
        }
        assertTrue("$late of $total", total >= 10 && late * 10 >= total * 8)
    }

    // ------------------------------------------------------------------ input mapping

    @Test fun u1LikeWoltOfferAtTwentyPercentCommissionIsNetOfThePark() {
        val e5 = card("FP01", t0)
        assertEquals(3.94, e5.gross!!, 1e-9)
        val o = offer(e5)
        val (inp, res) = eval(o, t0 + 1_500, ShadowEvaluator.Context(commissionPct = 20.0))
        val payout = inp.getJSONObject("payout")
        assertEquals("gross_before_park", payout.getString("basis")); assertEquals("marginal", payout.getString("tierMode"))
        assertEquals(0.8, payout.getJSONArray("keepSchedule").getJSONArray(0).getDouble(1), 1e-12)
        val rev = calc(res).getJSONObject("revenue")
        assertEquals(3.94, rev.getDouble("A"), 1e-9)
        assertEquals("afterPark is 3.152, not 3.94", 3.152, EconomicsV2.r6(rev.getDouble("afterPark")) as Double, 1e-9)
        assertFalse("FEE_BASIS_UNKNOWN" in list(res.getJSONArray("flags")))
        dump("FP01:c20", inp, res, ShadowEvaluator.verdictShadow(res, inp, t0 + 1_500))
        // No commission key: the basis is unknown and the park cut is only an unpriced flag (SPEC §14.2).
        val (inp2, res2) = eval(o, t0 + 1_500, ShadowEvaluator.Context())
        assertEquals("unknown", inp2.getJSONObject("payout").getString("basis")); assertEquals(1, inp2.getJSONObject("payout").length())
        assertTrue("FEE_BASIS_UNKNOWN" in list(res2.getJSONArray("flags")))
        assertEquals(0.8, calc(res2).getJSONObject("surplus").getJSONObject("altIfParkCut").getDouble("keep"), 1e-12)
        dump("FP01:nocommission", inp2, res2, ShadowEvaluator.verdictShadow(res2, inp2, t0 + 1_500))
    }

    @Test fun payoutVehicleAndModeDefaults() {
        assertEquals("{\"basis\":\"unknown\"}", ShadowEvaluator.payout(null).toString())
        assertEquals("{\"basis\":\"unknown\"}", ShadowEvaluator.payout(Double.NaN).toString())
        assertEquals(0.875, ShadowEvaluator.payout(12.5).getJSONArray("keepSchedule").getJSONArray(0).getDouble(1), 1e-12)
        assertEquals(0, ShadowEvaluator.payout(20.0).getJSONArray("keepSchedule").getJSONArray(0).getInt(0))
        assertEquals(0.0, ShadowEvaluator.payout(150.0).getJSONArray("keepSchedule").getJSONArray(0).getDouble(1), 0.0)
        for (label in listOf(null, "", "unknown", "G3", " g3 ", "skuter")) assertEquals(label.toString(), "g3", ShadowEvaluator.vehicleProfile(label))
        assertEquals("economics.appMode", ShadowEvaluator.APP_MODE_KEY); assertEquals("shadow", ShadowEvaluator.Context().appMode)
        val (inp, res) = eval(offer(card("P01", t0)), t0 + 1_000, ShadowEvaluator.Context(commissionPct = 20.0, vehicle = "unknown", appMode = "live"))
        assertEquals("shadow", inp.getString("appMode")); assertEquals("suppressed_shadow", res.getJSONObject("display").getString("mode"))
        assertEquals("g3", inp.getJSONObject("vehicle").getString("profile")); assertFalse(inp.getJSONObject("vehicle").has("whPerKm"))
        val g = inp.getJSONObject("g")
        assertEquals(4.0, g.getDouble("g"), 0.0); assertEquals("prior", g.getString("source")); assertTrue(g.getBoolean("calibrating"))
        assertTrue(res.getJSONObject("display").getBoolean("calibrating"))
        assertFalse("KuryePlus reads no scooter battery", inp.getJSONObject("state").has("scooter"))
        assertTrue("BATTERY_UNKNOWN" in list(res.getJSONArray("flags"))); assertTrue(list(res.getJSONArray("blocks")).isEmpty())
        assertEquals("unknown", inp.getJSONObject("offer").getString("routeAccess"))
        assertTrue("ROUTE_ACCESS_UNVERIFIED" in list(res.getJSONArray("flags")))
        dump("P01:live-label", inp, res, ShadowEvaluator.verdictShadow(res, inp, t0 + 1_000))
    }

    @Test fun unknownZonesAreLeftOutOfTheEngineInput() {
        // Bolt card: street names only, and no fresh GPS zone.
        val b = offer(card("P02", t0))
        val (inp, res) = eval(b, t0 + 1_000, ShadowEvaluator.Context(commissionPct = 20.0))
        assertFalse(inp.getJSONObject("offer").has("pickupZone")); assertFalse(inp.getJSONObject("offer").has("destZone"))
        assertFalse(inp.getJSONObject("state").has("hereZone"))
        assertEquals("prior_from_pickup_zone", calc(res).getString("destSource"))
        assertTrue("DROPOFF_UNKNOWN_EXPECTED_OVER_ZONES" in list(res.getJSONArray("flags")))
        dump("P02:zones-unknown", inp, res, ShadowEvaluator.verdictShadow(res, inp, t0 + 1_000))
        // Field card E5: the drop-off is a DMS coordinate → a coarse zone (never the coordinate itself).
        val w = offer(card("FP01", t0))
        val (inp2, res2) = eval(w, t0 + 1_000, ShadowEvaluator.Context(commissionPct = 20.0, hereZone = "Z2"))
        val z = inp2.getJSONObject("offer").getString("destZone")
        assertTrue(z, z in setOf("Z1", "Z2", "Z3", "Z4")); assertEquals("Z2", inp2.getJSONObject("state").getString("hereZone"))
        assertEquals("known", calc(res2).getString("destSource"))
        val text = inp2.toString()
        for (secret in listOf("40°", "49°", "Test", "Baku", "\"lat\"", "\"lon\"")) assertFalse("$secret in $text", text.contains(secret))
        dump("FP01:zones-known", inp2, res2, ShadowEvaluator.verdictShadow(res2, inp2, t0 + 1_000))
    }

    @Test fun courierStateFeedsTheDeclineBudgetAndTheOtherApp() {
        val state = EconomicsLogs.courierState(listOf(wolt, bolt), emptyList(), 3, 60.0, null, null, listOf(5), 0.82, "Z1", 12.0, true)
        val c = ShadowEvaluator.Context.of(state, 20.0, "g3", null)
        assertEquals(3, c.woltDeclinesInWindow); assertEquals(listOf(5), c.woltTriggerCounts); assertEquals(0.82, c.boltAcceptanceRate!!, 1e-12)
        assertEquals("Z1", c.hereZone); assertEquals(12.0, c.speedKmh!!, 1e-12); assertTrue(c.repositioning)
        val (inp, res) = eval(offer(card("P01", t0)), t0 + 1_000, c)
        val st = inp.getJSONObject("state")
        assertTrue(st.getBoolean("otherAppOnline")); assertEquals(3, st.getInt("woltDeclinesInWindow"))
        assertEquals("[5]", st.getJSONObject("woltPenaltyHistory").getJSONArray("triggerCounts").toString())
        assertTrue("OFFER_WHILE_REPOSITIONING" in list(res.getJSONArray("flags")))
        assertTrue("hazard at N = 3 once a trigger at 5 bounds K", calc(res).getDouble("kappa") > 0)
        dump("P01:declines3", inp, res, ShadowEvaluator.verdictShadow(res, inp, t0 + 1_000))
        val (bi, br) = eval(offer(card("P02", t0)), t0 + 1_000, c)
        assertEquals(0.82, bi.getJSONObject("state").getDouble("boltAR"), 1e-12)
        assertEquals("near_floor", calc(br).getJSONObject("kappaDetail").getString("regime"))
        dump("P02:ar082", bi, br, ShadowEvaluator.verdictShadow(br, bi, t0 + 1_000))
        // Alone on Wolt, nothing known: no other app, empty history, unknown acceptance rate stays out.
        val alone = ShadowEvaluator.Context.of(EconomicsLogs.unknownCourierState(), null, null, null)
        val (ai, _) = eval(offer(card("P02", t0)), t0 + 1_000, alone)
        assertFalse(ai.getJSONObject("state").getBoolean("otherAppOnline")); assertFalse(ai.getJSONObject("state").has("boltAR"))
        assertFalse(ai.getJSONObject("state").has("woltPenaltyHistory")); assertFalse(ai.getJSONObject("state").has("speedKmh"))
    }

    @Test fun parserRowOffersCoverEveryVerdictKind() {
        val seen = sortedSetOf<String>()
        val rows = objects(corpus.getJSONArray("parser"))
        assertEquals(listOf("P01", "P02", "FP01", "FP02", "FP08"), rows.map { it.getString("id") })
        val contexts = listOf(
            "c20" to ShadowEvaluator.Context(commissionPct = 20.0),
            "none-both-online" to ShadowEvaluator.Context(appsOnline = listOf(wolt, bolt), hereZone = "Z3"),
            "c20-declines5-ar079" to ShadowEvaluator.Context(commissionPct = 20.0, appsOnline = listOf(bolt), woltDeclinesInWindow = 5,
                woltTriggerCounts = listOf(6), boltAcceptanceRate = 0.79, hereZone = "Z4"))
        for (p in rows) {
            val id = p.getString("id")
            val o = offer(card(id, t0))
            assertEquals(id, ScreenStage.OFFER, o.data.stage)
            for ((name, c) in contexts) {
                // fresh (1.5 s), stale (7 s unseen), too late (55 s after first seen) and, synthetic, other fees
                val cases = listOf("fresh" to (o to t0 + 1_500), "stale" to (o to t0 + 7_000), "late" to (o to t0 + 55_000),
                    "synthetic-fee0.9" to (OfferState(o.id, o.pkg, o.firstAt, o.data.copy(gross = 0.9)) to t0 + 1_500),
                    "synthetic-fee9" to (OfferState(o.id, o.pkg, o.firstAt, o.data.copy(gross = 9.0)) to t0 + 1_500),
                    "synthetic-nofee" to (OfferState(o.id, o.pkg, o.firstAt, o.data.copy(gross = null)) to t0 + 1_500),
                    "synthetic-optional" to (OfferState(o.id, o.pkg, o.firstAt, o.data).also { it.optional = true } to t0 + 1_500))
                for ((k, x) in cases) {
                    val (inp, res) = eval(x.first, x.second, c)
                    val v = ShadowEvaluator.verdictShadow(res, inp, x.second)
                    seen += v.getString("verdict")
                    if (v.getString("verdict") == "NO_VERDICT") {
                        assertTrue(v.isNull("surplusMid") && v.isNull("tauMid") && !v.has("economicVerdict") && !v.has("scoreLevels"))
                        assertEquals(4.0, v.getDouble("g"), 0.0); assertTrue(list(v.getJSONArray("noVerdictReasons")).isNotEmpty())
                    } else {
                        assertEquals(7, v.getJSONObject("scoreLevels").length()); assertTrue(v.has("economicVerdict"))
                    }
                    when (k) {
                        "stale" -> assertEquals(listOf("OBSERVATION_STALE_OR_NOT_VISIBLE"), list(v.getJSONArray("noVerdictReasons")))
                        "late" -> assertTrue("TOO_LATE_FOR_SAFE_DECISION" in list(v.getJSONArray("noVerdictReasons")))
                        "synthetic-nofee" -> assertEquals(listOf("FEE_UNREADABLE"), list(v.getJSONArray("noVerdictReasons")))
                    }
                    dump("$id:$name:$k", inp, res, v)
                }
            }
        }
        assertEquals(setOf("GOOD", "MARGINAL", "NO_VERDICT", "POOR"), seen)
    }

    // ------------------------------------------------------------------ when it is computed

    @Test fun recomputedOnlyWhenFeeOrKmChangeBeforeTheDecision() {
        val r = Rig("recompute")
        r.core.start()
        val card = strings(parserRow("P01").getJSONArray("texts")); val acts = listOf("Accept", "Decline")
        fun woltCard(at: Long, km: String = "2.8", fee: String = "₼2.31") = obs(wolt, at, card.map {
            it.replace("2.8 km", "$km km").replace("₼2.31", fee) }, acts)
        r.frame(woltCard(t0))
        val id = r.core.pending[wolt]!!.id
        val first = r.shadow.entry(id)!!
        assertEquals(t0, first.computedAtMs)
        r.frame(woltCard(t0 + 1_000))
        assertSame("same fee and km: not recomputed", first, r.shadow.entry(id))
        r.frame(woltCard(t0 + 2_000, km = "2.9"))
        assertEquals(id, r.core.pending[wolt]!!.id)
        val second = r.shadow.entry(id)!!
        assertEquals("km changed", t0 + 2_000, second.computedAtMs); assertEquals(2.9, second.km!!, 1e-9)
        r.click(wolt, "Decline", t0 + 3_000)
        r.frame(woltCard(t0 + 3_500, km = "2.9", fee = "₼2.32"))
        assertEquals(id, r.core.pending[wolt]!!.id); assertEquals(2.32, r.core.pending[wolt]!!.data.gross!!, 1e-9)
        assertSame("after the decline tap: frozen", second, r.shadow.entry(id))
        // An incomplete frame or a frame of another screen computes nothing.
        val other = OfferState("x", wolt, t0, woltCard(t0))
        assertNull(r.shadow.frame(woltCard(t0 + 4_000).copy(complete = false), other) { r.context(t0 + 4_000) })
        assertNull(r.shadow.frame(obs(wolt, t0 + 4_000, listOf("Expect a task soon", "Delivery demand", "Medium")), other) { r.context(t0 + 4_000) })
        r.frame(obs(wolt, t0 + 40_000, listOf("Expect a task soon", "Delivery demand", "Medium")))
        r.frame(obs(wolt, t0 + 41_000, listOf("Expect a task soon", "Delivery demand", "Medium")))
        val row = r.offers().single()
        assertEquals("declined_tap_observed", row.getString("decisionKind"))
        assertEquals(t0 + 2_000, row.getJSONObject("verdictShadow").getLong("computedAtMs"))
        dumpRows(r.offers())
    }

    @Test fun modeOffComputesNothingAndOtherAppsAreIgnored() {
        val r = Rig("off", appMode = "off"); r.core.start()
        r.frame(card("P01", t0))
        assertNotNull(r.core.pending[wolt]); assertTrue(r.evaluations.isEmpty())
        assertNull(r.shadow.verdictFor(r.core.pending[wolt]!!))
        val x = OfferState("y", "com.example.courier", t0, card("P01", t0).copy(pkg = "com.example.courier"))
        assertNull(ShadowEvaluator.input(x, t0, ShadowEvaluator.Context()))
        assertNull(ShadowMode().frame(x.data, x) { ShadowEvaluator.Context() })
    }

    @Test fun anEngineFailureNeverBreaksTheFrame() {
        val m = ShadowMode()
        val o = card("P01", t0); val st = OfferState("z", wolt, t0, o)
        val e = m.frame(o, st) { throw IllegalStateException("prefs") }!!
        assertNotNull(e.error); assertNull(e.verdict); assertNull(m.verdictFor(st))
        assertNull("not retried until fee or km change", m.frame(o.copy(at = t0 + 1_000), st.also { it.lastSeenAt = t0 + 1_000 }) { ShadowEvaluator.Context() })
    }

    @Test fun latencyP95IsBelow50msOnTheJvm() {
        val inputs = ArrayList<Pair<OfferState, Long>>()
        for (sc in objects(corpus.getJSONArray("scenarios"))) {
            val r = Rig("latency"); r.replay(sc)
            r.evaluations.forEach { (o, e) -> inputs.add(o to e.computedAtMs) }
        }
        for (p in objects(corpus.getJSONArray("parser"))) inputs.add(offer(card(p.getString("id"), t0)) to t0 + 1_500)
        val c = ShadowEvaluator.Context(commissionPct = 20.0, appsOnline = listOf(wolt, bolt))
        val ns = ArrayList<Long>()
        repeat(8) { for ((o, at) in inputs) {
            val s = System.nanoTime()
            val inp = ShadowEvaluator.input(o, at, c)!!; val res = EconomicsV2.evaluate(inp); ShadowEvaluator.verdictShadow(res, inp, at)
            ns.add(System.nanoTime() - s)
        } }
        assertTrue("${ns.size} samples", ns.size >= 200)
        val sorted = ns.sorted()
        val p95 = sorted[(sorted.size * 95 + 99) / 100 - 1] / 1e6
        println("shadow latency JVM (input + engine + row), n=${ns.size}: p50 %.2f ms, p95 %.2f ms, max %.2f ms".format(sorted[sorted.size / 2] / 1e6, p95, sorted.last() / 1e6))
        assertTrue("p95 $p95 ms", p95 < 50.0)
    }
}
