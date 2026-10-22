package com.kuryelog.logic.map.surface

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.pow

/**
 * Y13 yüzey modelleri, puanlama ve depo (YolGo `surface_store_test`, `surface_store_exclusions_test`, `road_surface_score_test` @ 870ab6e).
 * Elle işaretlerle ilgili adımlar (`saveMark/removeMark`) WEEM'de `MarkStore`'a ait (A6.11) ve orada sınanır.
 */
class SurfaceStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val now = 1_790_000_000_000L

    private fun obs(id: String, day: String = "day-1", pass: String = "leg-1/boot-1", start: Long = 1000, end: Long = 2500, rms: Double = .3,
                    meters: Double = 25.0, label: String? = null, segment: String = "123/40.000000,49.000000/0/0", way: Long = 123, speed: Double = 5.0) =
        SurfaceObservation(id = id, day = day, leg = pass, pass = pass, segment = segment, profile = "g3-default", at = now, startMs = start, endMs = end, way = way,
            direction = 0, segmentIndex = 0, profileVersion = 1, meters = meters, rms = rms, roughM = if (rms >= .9) meters else 0.0, speed = speed, accuracy = 5.0,
            lat = 40.0, lon = 49.0, sampleHz = 100.0, confidence = .9, calibrationLabel = label)

    private fun with(o: SurfaceObservation, vararg edit: Pair<String, Any?>): SurfaceObservation =
        SurfaceObservation.fromJson(o.toJson().also { j -> edit.forEach { (k, v) -> j.put(k, v) } })

    private fun hit(id: String, at: Long, peak: Double) = JSONObject().put("id", id).put("at", at).put("peak", peak)
    private fun dir(name: String) = File(tmp.root, name).also { it.mkdirs() }
    private fun summarize(rows: List<SurfaceObservation>, profile: SurfaceProfile = SurfaceProfile()) = SurfaceScoring.summarizeSurface(rows, profile, now)

    // ---- puanlama ----

    @Test fun oneLargeShockInASinglePassLowersTheSegmentOnlyToFairNotPermanentlyBad() {
        val rows = (0 until 4).map { i -> obs("$i", pass = "pass-$i", rms = .2).let { if (i == 0) with(it, "impacts" to JSONArray().put(hit("one-large-hit", 1200, 15.0))) else it } }
        val s = summarize(rows).single()
        assertEquals(1, s.impacts); assertEquals(15.0, s.hardest, 0.0); assertNull(s.p95)
        assertEquals(.2, s.rms, .00001); assertEquals(60.0, s.score, .001)
    }

    @Test fun theSameHardShockConfirmedInTwoPassesKeepsTheStrictCap() {
        val rows = (0 until 4).map { i -> obs("$i", pass = "pass-$i", rms = .2).let { if (i < 2) with(it, "impacts" to JSONArray().put(hit("hit-$i", 1200, 15.0))) else it } }
        assertTrue(summarize(rows).single().score <= 35)
    }

    @Test fun passesRatherThanFramesDetermineStatisticalSupport() {
        val s = summarize(listOf(obs("1"), obs("2", start = 2500, end = 4000))).single()
        assertEquals(1, s.passes); assertEquals(50.0, s.meters, 0.0); assertFalse(s.calibrated)
        assertTrue(s.confidence < .25); assertNull(s.jerk); assertNull(s.gyro); assertTrue(s.bands.isEmpty()); assertNull(s.p95)
    }

    @Test fun measuredRoughDistanceHasNoStopWalkingDenominator() {
        val s = summarize(listOf(obs("a", rms = 1.2), obs("b", rms = .2))).single()
        assertEquals(.5, s.roughFraction, 0.0); assertEquals(0.0, s.impactsPer100M!!, 0.0)
        assertTrue(s.score in 0.0..100.0)
    }

    @Test fun sparseNewSpeedBinStaysUncalibratedExplicitRepeatsValidate() {
        val band = CalibrationBand(.2, 1.2, 3, 3, now)
        assertTrue(band.validated)
        val profile = SurfaceProfile().calibrated(2, band)
        assertTrue(profile.calibration(5.0)?.validated == true)
        assertNull(profile.calibration(3.0))
        assertEquals(2, SurfaceProfile.fromJson(profile.toJson()).version)
    }

    @Test fun bakuDateDoesNotEndADayAtUtcMidnight() {
        assertEquals("2026-10-03", BakuDay.of(java.time.Instant.parse("2026-10-02T21:00:00Z").toEpochMilli()))
        assertEquals("2026-10-02", BakuDay.of(java.time.Instant.parse("2026-10-02T19:59:59Z").toEpochMilli()))
    }

    @Test fun dayEndAveragingADenseSingleDayDoesNotDominateAndDaysCountEqually() {
        val meta = SegmentMeta("k", 1, 0, 0, 40.0, 49.0)
        val t = java.time.Instant.parse("2026-10-10T12:00:00Z").toEpochMilli()
        fun pass(id: String, date: String, rms: Double, daysAgo: Int) = PassEvidence(id, date, date, "g3-default", t - daysAgo * 86_400_000L, 50.0, 0.0, rms, 0.0, 5.0, 250.0, 45.0)
        // 20 geçişli kötü bir gün + iki tek geçişli iyi gün: gün ağırlığı en çok 5.
        val passes = (0 until 20).map { pass("bad-$it", "2026-10-10", .8, 0) } + pass("good-1", "2026-10-09", .2, 1) + pass("good-2", "2026-10-08", .2, 2)
        val s = SurfaceScoring.summarizeSegment(meta, passes, SurfaceProfile(), t)!!
        val p = SurfaceProfile(); val bad = p.score(.8, 0.0, 0.0, 5.0); val good = p.score(.2, 0.0, 0.0, 5.0)
        val expected = (5 * bad + 1 * good * .5.pow(1.0 / 21) + 1 * good * .5.pow(2.0 / 21)) / (5 + .5.pow(1.0 / 21) + .5.pow(2.0 / 21))
        assertEquals(expected, s.score, .01); assertEquals(22, s.passes)
    }

    @Test fun outOfReferenceRangePassesKeepTheirOwnBandForExplicitFallback() {
        val rows = listOf(Triple("a", 36.0, 3.0), Triple("b", 36.0, 5.0), Triple("c", 20.0, 40.0)).map { (id, kmh, rms) -> with(obs(id, pass = id), "speed" to kmh / 3.6, "rms" to rms) }
        val s = summarize(rows).single()
        assertEquals(35, s.dominantRoadBand!!.band); assertEquals(4.0, s.dominantRoadBand!!.rms, 1e-9); assertEquals(10.0, s.dominantRoadBand!!.speed, 1e-9)
    }

    @Test fun bandEvidenceKeepsPassMediansApartAndMostPassesSelectTheBand() {
        val rows = listOf(Triple("a", 20.0, 2.0), Triple("b", 20.0, 4.0), Triple("c", 30.0, 40.0)).map { (id, kmh, rms) -> with(obs(id, pass = id), "speed" to kmh / 3.6, "rms" to rms) }
        val s = summarize(rows).single()
        assertEquals(4.0, s.rms, 1e-9) // Existing global median remains unchanged.
        assertEquals(3.0, s.roadBands[20]!!.rms, 1e-9); assertEquals(2, s.roadBands[20]!!.passes)
        assertEquals(40.0, s.roadBands[30]!!.rms, 1e-9); assertEquals(20, s.dominantRoadBand!!.band)
        val restored = SurfaceSummary.fromJson(s.toJson()); assertEquals(3.0, restored.dominantRoadBand!!.rms, 1e-9)
        assertNull(SurfaceSummary.fromJson(s.toJson().also { it.remove("roadBands") }).dominantRoadBand)
    }

    @Test fun derivedRoadSummariesRetainMeasurementSpeedForReferenceBands() {
        val s = summarize((0 until 2).map { with(obs("$it", pass = "pass-$it"), "speed" to if (it == 0) 5.0 else 6.0) }).single()
        assertEquals(5.5, s.speed!!, 1e-9)
        assertEquals(5.5, SurfaceSummary.fromJson(s.toJson()).speed!!, 1e-9)
    }

    @Test fun legacySummaryWithoutSpeedStaysReadableWithoutInventingABand() {
        val original = summarize(listOf(obs("old"))).single().toJson().also { it.remove("speed") }
        val restored = SurfaceSummary.fromJson(original)
        assertNull(restored.speed); assertEquals(original.getDouble("rms"), restored.rms, 0.0); assertEquals(original.getDouble("score"), restored.score, 0.0)
    }

    @Test fun speedBandsFollowTheReferenceRanges() {
        assertNull(SpeedBand.calibration(12.4)); assertEquals(15, SpeedBand.calibration(12.5)); assertEquals(20, SpeedBand.calibration(17.5))
        assertEquals(30, SpeedBand.calibration(32.5)); assertNull(SpeedBand.calibration(32.6)); assertNull(SpeedBand.calibration(null))
        assertEquals(35, SpeedBand.road(36.0)); assertNull(SpeedBand.road(7.9)); assertEquals(10, SpeedBand.road(8.0))
    }

    // ---- depo ----

    @Test fun corruptMetadataFailsClosedAndRetainsOriginalBytes() {
        val d = dir("corrupt"); val meta = File(d, "metadata.json").apply { writeText("{broken") }
        val store = SurfaceStore(d)
        try { store.add(listOf(obs("new"))); fail() } catch (e: IllegalStateException) { /* beklenen */ }
        assertEquals("{broken", meta.readText()); assertFalse(File(d, "observations.jsonl").exists())
    }

    @Test fun unknownJournalLinesSurviveCompactionImpactIdsAndDayDecisionsDeduplicate() {
        val d = dir("journal"); File(d, "observations.jsonl").writeText("{unreadable\n")
        val store = SurfaceStore(d)
        val hitRow = with(obs("hit"), "impacts" to JSONArray().put(hit("boot:1200", 1200, 8.1)))
        store.add(listOf(hitRow, with(hitRow, "id" to "repeat")))
        val events = store.impactEvents(hitRow.segment)
        assertEquals(1, events.size); assertEquals("automatic-neutral", events.single().getString("source")); assertEquals(hitRow.at - 1300, events.single().getLong("utc"))
        store.recordDecisions(listOf(JSONObject().put("id", "decision").put("day", "day-1"), JSONObject().put("id", "decision").put("day", "day-1"), JSONObject().put("id", "other").put("day", "day-2")))
        assertEquals(2, store.decisions.size); store.deleteDay("day-1")
        assertEquals("day-2", store.decisions.single().getString("day")); assertTrue(store.impactEvents(hitRow.segment).isEmpty())
        assertTrue(File(d, "observations.unreadable.jsonl").readText().contains("{unreadable"))
    }

    @Test fun deletingACalibrationPassInvalidatesOnlyDerivedReferencesRetainingOtherDay() {
        val d = dir("cal"); val store = SurfaceStore(d)
        store.add((0 until 3).flatMap { i -> listOf(obs("smooth-$i", pass = "smooth-$i", label = "smooth", rms = .2, day = if (i == 0) "remove" else "keep"),
            obs("rough-$i", pass = "rough-$i", label = "rough", rms = 1.2, day = "keep")) })
        store.calibrate(); assertTrue(store.profile.calibration(5.0)?.validated == true)
        store.deleteDay("remove"); assertNull(store.profile.calibration(5.0))
        assertEquals(5, JSONObject(File(store.export()).readText()).getJSONArray("observations").length())
        store.saveProfile(SurfaceProfile(id = "other-vehicle", vehicleId = "other"))
        assertTrue(store.summaries.isEmpty()); assertTrue(store.profiles.any { it.id == "g3-default" })
    }

    @Test fun durableIdDedupLateRetractionResumeAndSelectiveDayDeletion() {
        val d = dir("durable"); val store = SurfaceStore(d)
        store.add(listOf(obs("one"), obs("one")))
        assertEquals(25.0, store.summaries.single().meters, 0.0)
        store.retract("day-1", "leg-1/boot-1", 2000, 5000)
        assertTrue(store.summaries.isEmpty())
        store.add(listOf(obs("later", start = 10000, end = 11500), obs("other-day", day = "day-2", pass = "leg-2/boot-1")))
        assertEquals(2, store.summaries.single().passes)
        store.deleteDay("day-1")
        assertEquals(1, store.summaries.single().passes); assertEquals(25.0, store.summaries.single().meters, 0.0)
        store.add(listOf(obs("resurrect", start = 15000, end = 16500)))
        assertEquals(25.0, store.summaries.single().meters, 0.0)
        assertTrue(File(store.export()).readText().contains("other-day"))
        val reloaded = SurfaceStore(d)
        assertEquals(25.0, reloaded.summaries.single().meters, 0.0)
    }

    @Test fun passesRollUpAtDayEndRawRowsAreDeletedScoreAndCalibrationKept() {
        val d = dir("rollup"); val t = System.currentTimeMillis(); val old = t - 3 * 86_400_000L
        fun at(id: String, ms: Long, pass: String = "p", day: String = "day-old", label: String? = null) = with(obs(id, pass = pass, day = day, label = label), "at" to ms)
        var store = SurfaceStore(d, nowMs = { t })
        store.add(listOf(at("a", old, "old-1"), at("b", old, "old-2"), at("c", t, "today", "day-new"), at("cal", old, "cal-1", label = "smooth")))
        assertEquals(4, store.summaries.single().passes) // kalibrasyon sürüşü de bir geçiş
        store = SurfaceStore(d, nowMs = { t })
        // Yeniden açılışta gün sonu: iki eski geçiş özete indi, puan aynı geçiş sayısıyla sürüyor.
        assertEquals(4, store.summaries.single().passes)
        assertTrue(File(d, "history.json").exists())
        val journal = File(d, "observations.jsonl").readText()
        assertFalse(journal.contains("\"old-1\"")); assertTrue(journal.contains("\"today\"")); assertTrue(journal.contains("\"cal-1\"")) // kalibrasyon sürüşü silinmez
        store.deleteDay("day-old"); assertEquals(1, store.summaries.single().passes)
    }

    // ---- geri alma / dışlamalar ----

    private fun exclusionDays(d: File): List<String> = JSONObject(File(d, "metadata.json").readText()).getJSONArray("exclusions").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("day") } }

    @Test fun retractionDeletesOnlyThatLegsOverlappingRowsAndRepeatDoesNoWork() {
        val d = dir("excl1"); val store = SurfaceStore(d)
        store.add(listOf(obs("a", start = 1000, end = 2500), obs("b", start = 10000, end = 11500), obs("c", pass = "leg-2/boot-1", start = 1000, end = 2500)))
        assertEquals(2, store.summaries.single().passes)
        store.retract("day-1", "leg-1/boot-1", 2000, 5000)
        assertEquals(2, store.summaries.single().passes) // b (aynı sefer, aralık dışı) ve c (başka sefer) kalır
        assertEquals(50.0, store.summaries.single().meters, 0.0)
        val metadata = File(d, "metadata.json").readText()
        val emissions = ArrayList<List<SurfaceSummary>>(); store.onChanged { emissions += it }
        store.retract("day-1", "leg-1/boot-1", 2500, 4000) // zaten kapsanıyor
        assertEquals("tekrar geri alma yazmaz", metadata, File(d, "metadata.json").readText())
        assertTrue("değişiklik yoksa dinleyiciler uyandırılmaz", emissions.isEmpty())
        store.add(listOf(obs("late", start = 3000, end = 4500)))
        assertEquals("dışlanan aralığa geç gelen satır kabul edilmez", 2, store.summaries.single().passes)
    }

    @Test fun deletedDaysExclusionsArePrunedAndOtherDaysKeepTheirs() {
        val d = dir("excl2"); val store = SurfaceStore(d)
        store.add(listOf(obs("a"), obs("d2", day = "day-2", pass = "leg-9/boot-1")))
        store.retract("day-1", "leg-1/boot-1", 0, 100); store.retract("day-2", "leg-9/boot-1", 0, 100)
        assertTrue(exclusionDays(d).containsAll(listOf("day-1", "day-2")))
        store.deleteDay("day-1")
        assertEquals(listOf("day-2"), exclusionDays(d))
    }

    @Test fun exclusionsOlderThanThreeDaysArePrunedOnLoadAndGhostRowsDoNotReturn() {
        val d = dir("excl3"); val t = System.currentTimeMillis()
        val old = "yolgo-${t - 5 * 86_400_000L}"
        var store = SurfaceStore(d, nowMs = { t })
        store.add(listOf(obs("keep", day = old, start = 10000, end = 11500)))
        store.retract(old, "leg-1/boot-1", 0, 5000)
        // Çökme benzetimi: dışlanmış satır sıkıştırma öncesi günlükte kalmış.
        File(d, "observations.jsonl").appendText(obs("ghost", day = old, start = 1000, end = 2500).toJson().toString() + "\n")
        store = SurfaceStore(d, nowMs = { t })
        assertTrue(exclusionDays(d).isEmpty()); assertEquals(1, store.summaries.single().passes)
        assertFalse(File(d, "observations.jsonl").readText().contains("ghost"))
    }

    @Test fun incrementalSummariesGiveTheSameResultAsAReload() {
        val d = dir("excl4"); val store = SurfaceStore(d)
        for (i in 0 until 6) store.add(listOf(obs("s$i", segment = "${124 + i % 3}/40.000000,49.000000/0/0", way = 124L + i % 3, pass = "leg-$i/boot-1")))
        store.retract("day-1", "leg-0/boot-1", 0, 9999)
        // confidence yaşa göre azalır (şimdiki zamana bağlı); ayrıca yaklaşık karşılaştırılır.
        fun stable(s: SurfaceSummary) = s.toJson().put("confidence", "%.6f".format(java.util.Locale.US, s.confidence)).put("score", "%.9f".format(java.util.Locale.US, s.score)).toString()
        val live = store.summaries.associate { it.key to stable(it) }
        assertEquals(3, live.size)
        assertEquals(live, SurfaceStore(d).summaries.associate { it.key to stable(it) })
    }
}
