package com.kuryelog.logic.map.calibration

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Y14 referans puanı (YolGo `test/road_calibration_test.dart` + `road_calibration_score_test.dart` @ 870ab6e, aynı girdi → aynı çıktı). */
class RoadReferencesTest {
    private fun row(id: String = "run", level: Int? = null, quality: String = "good", rms: Double = 3.01, speed: Double = 21.6, validSeconds: Double = 12.0,
                    warning: Boolean = false, mode: String = "fixed"): JSONObject = JSONObject().put("id", id).put("at", 1000).put("profile", "mount").put("quality", quality)
        .put("mode", mode).put("requestedSeconds", 15).put("elapsedSeconds", 15).put("validSeconds", validSeconds).put("valid", true).put("rms", rms)
        .put("averageKmh", speed).put("speedNotConstant", warning).also { if (level != null) it.put("qualityLevel", level) }

    private fun record(level: Int, rms: Double, speed: Double = 20.0, id: String = "run", warning: Boolean = false, mode: String = "fixed") =
        RoadCalibrationRecord.fromJson(row(id = id, level = level, quality = RoadQuality.fromLevel(level).key, rms = rms, speed = speed, warning = warning, mode = mode))

    private fun roadRecord(level: Int, rms: Double, speed: Double = 20.0) = RoadCalibrationRecord.fromJson(row(level = level, rms = rms, speed = speed)
        .put("roadRms", rms).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0))

    private fun refs(vararg r: RoadCalibrationRecord) = RoadCalibrationReferences(r.toList())

    // ---- road_calibration_test ----

    @Test fun allTenIntegerLevelsRetainTheirFiveCategories() {
        val expected = listOf(RoadQuality.VERY_BAD, RoadQuality.VERY_BAD, RoadQuality.BAD, RoadQuality.BAD, RoadQuality.MIDDLE, RoadQuality.MIDDLE,
            RoadQuality.GOOD, RoadQuality.GOOD, RoadQuality.VERY_GOOD, RoadQuality.VERY_GOOD)
        for (n in 1..10) { val r = record(n, 3.0); assertEquals(n, r.level); assertEquals(expected[n - 1], r.quality); assertFalse(r.isLegacy) }
        for (n in listOf(0, 11)) {
            assertThrows(IllegalArgumentException::class.java) { RoadQuality.fromLevel(n) }
            assertThrows(IllegalArgumentException::class.java) { RoadCalibrationRecord.fromJson(row(level = n)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RoadCalibrationRecord.fromJson(row().put("qualityLevel", 7.5)) }
    }

    @Test fun oldGoodMiddleAndBadRecordsStayUnscaledAndOutsideNewReferences() {
        val rows = listOf("good", "middle", "bad").map { RoadCalibrationRecord.fromJson(row(id = it, quality = it)) }
        assertEquals(listOf("good", "middle", "bad"), rows.map { it.id })
        assertTrue(rows.all { it.isLegacy && it.level == null })
        val r = RoadCalibrationReferences(rows + record(8, 3.01))
        assertEquals(1, r.count(RoadQuality.GOOD, 20)); assertNull(r.median(RoadQuality.MIDDLE, 20))
    }

    @Test fun fiveCategoriesNeedThreeRecordsAndAnUntrimmedFixedRunPerBand() {
        val rows = ArrayList<RoadCalibrationRecord>()
        for (n in listOf(10, 8, 6, 4, 2)) {
            rows += record(n, 11 - n.toDouble(), mode = "variable"); rows += record(n, 11 - n.toDouble(), mode = "variable"); rows += record(n, 11 - n.toDouble())
        }
        val r = RoadCalibrationReferences(rows)
        assertTrue(r.completeAll(20)); assertFalse(r.completeAll(25))
        assertFalse(RoadCalibrationReferences(rows.drop(1)).completeAll(20))
        val warned = refs(record(8, 3.0, warning = true))
        assertEquals(1, warned.count(RoadQuality.GOOD, 20)); assertFalse(warned.hasFixed(RoadQuality.GOOD, 20))
    }

    @Test fun sameBandMediansAreIsolatedAndNearestBandFallbackIsExplicit() {
        val r = refs(record(8, 3.01, 21.6), record(6, 5.96, 21.4), record(8, 8.0, 29.4), record(6, 12.0, 30.0))
        assertEquals(3.01, r.median(RoadQuality.GOOD, 20)!!, 0.0)
        assertEquals(5.96 / 3.01, r.ratio(5.96, 21.4)!!, 1e-9); assertEquals(1.0, r.ratio(8.0, 30.0)!!, 0.0)
        assertEquals(20, r.referenceBand(21.4)); assertEquals(20, r.referenceBand(24.0)); assertEquals(30, r.referenceBand(26.0))
        assertTrue(r.usesDifferentBand(24.0)); assertFalse(r.usesDifferentBand(21.4)); assertNull(r.referenceBand(Double.NaN))
        assertEquals(20, r.referenceBand(11.7)); assertTrue(r.usesDifferentBand(11.7)); assertEquals(3.0 / 3.01, r.ratio(3.0, 11.7)!!, 1e-9)
    }

    @Test fun bandBoundariesAreDeterministicAndUnsupportedSpeedsDoNotPolluteReferences() {
        assertEquals(15, RoadCalibrationReferences.speedBand(12.5)); assertEquals(20, RoadCalibrationReferences.speedBand(17.5))
        assertEquals(25, RoadCalibrationReferences.speedBand(22.5)); assertEquals(30, RoadCalibrationReferences.speedBand(27.5))
        assertEquals(30, RoadCalibrationReferences.speedBand(32.5)); assertNull(RoadCalibrationReferences.speedBand(11.7)); assertNull(RoadCalibrationReferences.speedBand(33.0))
        val r = refs(record(8, 1.0, 11.7), record(8, 3.0, 20.0))
        assertEquals(0, r.count(RoadQuality.GOOD, 15)); assertEquals(3.0, r.median(RoadQuality.GOOD, 20)!!, 0.0)
    }

    @Test fun orderedFiveCategoryReferencesClassifyWithinTheirSpeedBand() {
        val r = RoadCalibrationReferences(listOf(10, 8, 6, 4, 2).map { record(it, 11 - it.toDouble()) })
        for (n in listOf(10, 8, 6, 4, 2)) assertEquals(RoadQuality.fromLevel(n), r.classify(11 - n.toDouble(), 20.0))
        assertNull(r.classify(Double.NaN, 20.0))
        assertNull(refs(record(8, 3.0), record(6, 6.0)).classify(3.0, 20.0))
        assertNull(RoadCalibrationReferences(listOf(10, 8, 6, 4, 2).map { record(it, it.toDouble()) }).classify(3.0, 20.0))
    }

    @Test fun retainedDurationBelowSixtyPercentAndTheShort117KmhRunCannotBeReferences() {
        assertFalse(RoadCalibrationRecord.fromJson(row(level = 8, validSeconds = 8.99)).usable)
        assertTrue(RoadCalibrationRecord.fromJson(row(level = 8, validSeconds = 9.0)).usable)
        assertTrue(RoadCalibrationRecord.fromJson(row(level = 8, validSeconds = 9 - 1e-12)).usable)
        assertFalse(RoadCalibrationRecord.fromJson(row(level = 8, speed = 11.7, validSeconds = 4.7)).usable)
        val rejected = RoadCalibrationRecord.fromJson(row(level = 8).put("valid", false).put("rejectionReason", "Sabit hızda geçerli süre %60 altında"))
        assertTrue(rejected.rejectionReason!!.contains("%60")); assertFalse(rejected.usable)
    }

    // ---- road_calibration_score_test ----

    @Test fun e10UsesOnlyRoadRmsV1WhilePr23KeepsOriginalRms() {
        val rows = listOf(Triple(6, 40.0, 4.0), Triple(7, 20.0, 2.0)).map { (level, old, road) ->
            RoadCalibrationRecord.fromJson(row(level = level, rms = old).put("roadRms", road).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0)) }
        val r = RoadCalibrationReferences(rows)
        assertEquals(6.3, r.score(3.4, 20.0)!!.value!!, 1e-9)
        assertEquals(40.0, r.median(RoadQuality.MIDDLE, 20)!!, 0.0)
        val old = refs(record(7, 2.0))
        assertNull(old.score(2.0, 20.0)); assertEquals((1..10).toList(), old.missingLevels(20))
        for (extras in listOf(mapOf("roadRmsVersion" to 2), mapOf("roadRmsVersion" to 1.5), mapOf("roadRms" to JSONObject.NULL),
            mapOf("roadValidSeconds" to 4.0), mapOf("roadRmsRejectionReason" to "durağan ofset eksik"))) {
            val j = row(level = 7).put("roadRms", 2.0).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0)
            extras.forEach { (k, v) -> j.put(k, v) }
            assertNull(refs(RoadCalibrationRecord.fromJson(j)).score(2.0, 20.0))
        }
    }

    @Test fun oneReferenceGivesItsExactLevelOrABoundNeverExtrapolation() {
        val r = refs(roadRecord(7, 3.0))
        assertEquals(7.0, r.score(3.0, 20.0)!!.value!!, 0.0); assertEquals("7,0", r.score(3.0, 20.0)!!.label)
        assertNull(r.score(2.0, 20.0)!!.value); assertEquals("7 seviyesinden iyi", r.score(2.0, 20.0)!!.label); assertEquals("7 seviyesinden kötü", r.score(4.0, 20.0)!!.label)
    }

    @Test fun neighboringLevelMediansInterpolateWithOneDecimal() {
        val r = refs(roadRecord(6, 4.0), roadRecord(7, 2.0))
        assertEquals(6.3, r.score(3.4, 20.0)!!.value!!, 1e-9); assertEquals("6,3", r.score(3.4, 20.0)!!.label)
        assertEquals("6,0", r.score(4.0, 20.0)!!.label); assertEquals("7,0", r.score(2.0, 20.0)!!.label)
    }

    @Test fun sparseReferencesInterpolateBetweenAvailableLevels() {
        val r = refs(roadRecord(3, 10.0), roadRecord(7, 2.0))
        assertEquals(5.0, r.score(6.0, 20.0)!!.value!!, 0.0); assertEquals("7 seviyesinden iyi", r.score(1.0, 20.0)!!.label); assertEquals("3 seviyesinden kötü", r.score(11.0, 20.0)!!.label)
    }

    @Test fun eachExactLevelUsesItsOwnValidMedianInASingleBand() {
        val r = refs(roadRecord(6, 4.0), roadRecord(6, 40.0), roadRecord(6, 6.0), roadRecord(7, 2.0), roadRecord(8, 100.0), roadRecord(6, 1000.0, speed = 30.0),
            RoadCalibrationRecord.fromJson(row(level = 6, rms = 10000.0).put("valid", false)))
        // The inconsistent level 8 must not silently manufacture a scale.
        assertNull(r.score(4.0, 20.0))
        val ordered = RoadCalibrationReferences(r.records.filter { it.level != 8 })
        assertEquals(6.5, ordered.score(4.0, 20.0)!!.value!!, 1e-9)
    }

    @Test fun equalOrInvertedLevelMediansDoNotFabricateARoadScore() {
        for (rms in listOf(2.0, 5.0)) assertNull(refs(roadRecord(6, 2.0), roadRecord(7, rms)).score(2.0, 20.0))
    }

    @Test fun missingLevelsArePerSpeedBandAndRequireAUsableLevelRun() {
        val r = refs(roadRecord(6, 4.0), roadRecord(7, 2.0), roadRecord(8, 1.0, speed = 30.0), roadRecord(9, 1.0, speed = 11.7),
            RoadCalibrationRecord.fromJson(row(level = 10, validSeconds = 4.0)), RoadCalibrationRecord.fromJson(row()))
        assertEquals(listOf(1, 2, 3, 4, 5, 8, 9, 10), r.missingLevels(20)); assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 9, 10), r.missingLevels(30))
        assertEquals((1..10).toList(), r.missingLevels(25))
    }

    @Test fun nearestBandFallbackPreservesThePr23WarningAndTieRule() {
        val r = refs(roadRecord(6, 4.0, 20.0), roadRecord(7, 2.0, 20.0), roadRecord(6, 40.0, 30.0), roadRecord(7, 20.0, 30.0))
        assertFalse(r.score(3.4, 20.0)!!.differentBand)
        val fallback = r.score(3.4, 25.0)!!
        assertEquals(20, fallback.band); assertTrue(fallback.differentBand); assertEquals("6,3", fallback.label)
        assertEquals(30, r.score(30.0, 26.0)!!.band); assertTrue(r.score(3.4, 11.7)!!.differentBand)
    }

    @Test fun legacyRunsNeverAffectTheScoreOrCountAsCalibratedLevels() {
        val legacy = RoadCalibrationRecord.fromJson(row(rms = .01))
        val r = refs(legacy, roadRecord(6, 4.0), roadRecord(7, 2.0))
        assertEquals("6,3", r.score(3.4, 20.0)!!.label); assertEquals(listOf(1, 2, 3, 4, 5, 8, 9, 10), r.missingLevels(20))
        assertNull(refs(legacy).score(3.0, 20.0))
    }

    @Test fun invalidMeasurementsAndUnknownSpeedHaveNoScore() {
        val r = refs(roadRecord(7, 2.0))
        for (rms in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(r.score(rms, 20.0))
        for (speed in listOf(null, 0.0, 7.9, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(r.score(2.0, speed))
    }
}
