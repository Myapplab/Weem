package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadQuality
import com.kuryelog.logic.map.roads.StreetScores.buildStreets
import com.kuryelog.logic.map.roads.StreetScores.label
import com.kuryelog.logic.map.surface.RoadBandEvidence
import com.kuryelog.logic.map.surface.SurfaceSummary
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** E11 (C4) sade "Yol puanları" (YolGo `test/street_scores_test.dart` @ 870ab6e, aynı girdi → aynı çıktı); isolate sınaması WEEM'de tek iş parçacığı olarak. */
class StreetScoresTest {
    private fun piece(way: Long, index: Int, direction: Int = 0, length: Double = 50.0, rms: Double = 3.0, kmh: Double = 20.0, profile: String = "mount") = SurfaceSummary(
        key = "$way/40.000000,49.000000/$direction/$index", way = way, direction = direction, segmentIndex = index, profile = profile, score = 50.0, confidence = .9,
        meters = length, roughM = 0.0, passes = 1, lastAt = 0, rms = rms, accuracy = 4.0, impacts = 0, hardest = 0.0, lat = 40.0, lon = 49.0,
        segmentLength = length, segmentGridM = 50.0, roadBands = mapOf(20 to RoadBandEvidence(20, rms, 1, kmh / 3.6)))

    private fun streets(scores: List<Pair<SurfaceSummary, StreetScore?>>, names: Map<Long, String> = emptyMap()): List<Street> {
        val map = scores.toMap()
        return buildStreets(map.keys, { map[it] }, { names[it] })
    }

    private fun v(x: Double) = StreetScore.value(x)

    @Test fun scoreIsLengthWeightedMedianNotPlainMedian() {
        // 50 m → 3, 50 m → 5, 200 m → 8: toplam 300 m, yarısı 150 m 8'in içinde.
        val s = streets(listOf(piece(1, 0) to v(3.0), piece(1, 1) to v(5.0), piece(1, 2, length = 200.0) to v(8.0)), mapOf(1L to "Nizami")).single()
        assertEquals(8.0, s.score!!.value!!, 0.0); assertEquals("Nizami", s.name); assertEquals(3, s.scoredPieces); assertEquals(300.0, s.scoredMeters, 0.0)
    }

    @Test fun exactHalfSplitTakesTheMidpoint() {
        val s = streets(listOf(4.0, 5.0, 6.0, 7.0).mapIndexed { i, x -> piece(1, i) to v(x) }).single()
        assertEquals(5.5, s.score!!.value!!, 0.0)
    }

    @Test fun boundsSortAtTheirOwnLevel() {
        val worse = StreetScore.worseThan(5); val five = v(5.0); val better = StreetScore.betterThan(5)
        val sorted = listOf(better, five, worse, v(4.9), v(5.1)).sorted()
        assertEquals(listOf(v(4.9), worse, five, better, v(5.1)), sorted)
        // Ortanca bir sınır olabilir: 3 parçanın ikisi "7'den iyi".
        val s = streets(listOf(piece(2, 0) to v(6.0), piece(2, 1) to StreetScore.betterThan(7), piece(2, 2) to StreetScore.betterThan(7), piece(2, 3) to StreetScore.betterThan(7))).single()
        assertEquals(StreetScore.betterThan(7), s.score)
        // Yarıda bölünüp bir taraf sınırsa ortalama yok: kötü olan seçilir.
        val split = streets(listOf(piece(3, 0) to StreetScore.worseThan(4), piece(3, 1) to StreetScore.worseThan(4), piece(3, 2) to v(6.0), piece(3, 3) to v(6.0))).single()
        assertEquals(StreetScore.worseThan(4), split.score)
    }

    @Test fun fewerThanThreePiecesOr200mIsTooFewMeasurements() {
        fun scoreOf(pieces: List<SurfaceSummary>) = streets(pieces.map { it to v(6.0) }).single().score
        assertNull(scoreOf(listOf(piece(1, 0), piece(1, 1), piece(1, 2)))) // 150 m
        assertNull(scoreOf(listOf(piece(1, 0, length = 100.0), piece(1, 2, length = 100.0)))) // 2 parça
        assertEquals(6.0, scoreOf((0 until 4).map { piece(1, it) })!!.value!!, 0.0) // 4 × 50 = 200 m
        // İki yönde ölçülen aynı parça bir kez sayılır: 2 parça × 2 yön = 100 m.
        assertNull(scoreOf(listOf(0, 1).flatMap { d -> listOf(0, 1).map { i -> piece(1, i, direction = d) } }))
        // Kısa son parça gerçek uzunluğuyla sayılır.
        assertNull(scoreOf(listOf(piece(1, 0), piece(1, 1), piece(1, 2), piece(1, 3, length = 40.0))))
    }

    @Test fun unscoredPiecesDoNotCountAndNoScoreMeansTooFew() {
        val s = streets((0 until 6).map { piece(1, it) to (if (it < 2) v(7.0) else null) }).single()
        assertNull(s.score); assertEquals(6, s.pieces.size); assertEquals(2, s.scoredPieces)
        assertEquals("ölçüm az", label(s.score, Lang.TR)); assertEquals("ölçmə az", label(s.score, Lang.AZ))
    }

    @Test fun sameNamedWaysAreOneStreetUnnamedAreSeparate() {
        val all = streets(
            listOf(1L, 2L).flatMap { w -> (0 until 2).map { i -> piece(w, i) to v(4.0) } } + listOf(8L, 9L).flatMap { w -> (0 until 4).map { i -> piece(w, i) to v(9.0) } },
            mapOf(1L to "Azadlıq", 2L to "Azadlıq", 8L to ""))
        assertEquals(3, all.size)
        val named = all.first { it.name == "Azadlıq" }
        assertEquals(setOf(1L, 2L), named.ways); assertEquals(4.0, named.score!!.value!!, 0.0) // 2 + 2 parça = 200 m
        assertEquals(listOf(setOf(8L), setOf(9L)), all.filter { it.name == null }.map { it.ways })
    }

    @Test fun unnamedWayNameIsInTheSelectedLanguage() {
        val k = RoadKnowledge().also { it.names[1] = "Nizami"; it.names[2] = "" }
        assertEquals(listOf("Nizami", "isimsiz yol", "isimsiz yol"), listOf(k.nameOf(1), k.nameOf(2), k.nameOf(3)))
        assertEquals(listOf("Nizami", "adsız yol", "adsız yol"), listOf(k.nameOf(1, Lang.AZ), k.nameOf(2, Lang.AZ), k.nameOf(3, Lang.AZ)))
    }

    @Test fun orderIsWorstToBestTooFewLast() {
        val all = streets(
            (0 until 4).map { piece(1, it) to v(6.0) } + (0 until 4).map { piece(2, it) to StreetScore.worseThan(6) } + (0 until 4).map { piece(3, it) to StreetScore.betterThan(7) } +
                listOf(piece(4, 0) to v(1.0)) + (0 until 4).map { piece(5, it) to v(2.5) },
            mapOf(1L to "A", 2L to "B", 3L to "C", 4L to "D", 5L to "E"))
        assertEquals(listOf("E", "B", "A", "C", "D"), all.map { it.name })
    }

    @Test fun labelsOneDecimalAndTrAzSuffixesOneToTen() {
        assertEquals("6,3", label(v(6.25), Lang.TR)); assertEquals("7,0", label(v(7.0), Lang.TR))
        val tr = mapOf(1 to "1'den", 2 to "2'den", 3 to "3'ten", 4 to "4'ten", 5 to "5'ten", 6 to "6'dan", 7 to "7'den", 8 to "8'den", 9 to "9'dan", 10 to "10'dan")
        val az = mapOf(1 to "1-dən", 2 to "2-dən", 3 to "3-dən", 4 to "4-dən", 5 to "5-dən", 6 to "6-dan", 7 to "7-dən", 8 to "8-dən", 9 to "9-dan", 10 to "10-dan")
        for (n in 1..10) {
            assertEquals("${tr[n]} iyi", label(StreetScore.betterThan(n), Lang.TR)); assertEquals("${tr[n]} kötü", label(StreetScore.worseThan(n), Lang.TR))
            assertEquals("${az[n]} yaxşı", label(StreetScore.betterThan(n), Lang.AZ)); assertEquals("${az[n]} pis", label(StreetScore.worseThan(n), Lang.AZ))
        }
    }

    @Test fun routeQualityIsValueMinusOneOverNineBoundsHalfLevelBeyond() {
        val all = streets(
            (0 until 4).map { piece(1, it) to v(1.0) } + (0 until 4).map { piece(2, it) to v(10.0) } + (0 until 4).map { piece(3, it) to v(5.5) } +
                (0 until 4).map { piece(4, it) to StreetScore.betterThan(7) } + (0 until 4).map { piece(5, it) to StreetScore.worseThan(5) } +
                (0 until 2).map { piece(6, it) to v(9.0) } +
                // Puanlı sokağın puansız parçası da sokağın puanını alır.
                (0 until 4).map { piece(7, it) to v(8.0) } + listOf(piece(7, 9) to null))
        val q = StreetScores.routeQuality(all)
        assertEquals(0.0, q[piece(1, 0).key]!!, 0.0); assertEquals(1.0, q[piece(2, 3).key]!!, 0.0); assertEquals(.5, q[piece(3, 1).key]!!, 1e-9)
        assertEquals(6.5 / 9, q[piece(4, 0).key]!!, 1e-9); assertEquals(3.5 / 9, q[piece(5, 0).key]!!, 1e-9)
        assertTrue(q.keys.none { it.startsWith("6/") }); assertEquals(7.0 / 9, q[piece(7, 9).key]!!, 1e-9)
        assertEquals(1.0, StreetScore.betterThan(10).routeQuality, 0.0); assertEquals(0.0, StreetScore.worseThan(1).routeQuality, 0.0)
    }

    private fun record(level: Int, rms: Double, speed: Double = 20.0) = RoadCalibrationRecord.fromJson(JSONObject().put("id", "run").put("at", 1000).put("profile", "mount")
        .put("quality", RoadQuality.fromLevel(level).key).put("mode", "fixed").put("requestedSeconds", 15).put("elapsedSeconds", 15).put("validSeconds", 12.0).put("valid", true)
        .put("rms", rms).put("averageKmh", speed).put("speedNotConstant", false).put("qualityLevel", level)
        .put("roadRms", rms).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0))

    @Test fun routeQualityFromSessionsGivesTheSameResultAsStepByStep() {
        val sessions = listOf(record(6, 4.0), record(7, 2.0))
        val pieces = (0 until 4).map { piece(1, it, rms = 3.4) } + (0 until 2).map { piece(2, it, rms = 1.0) } + (0 until 4).map { piece(3, it, rms = 1.0) }
        val name = { w: Long -> if (w == 3L) null else "Füzuli" }
        val sync = StreetScores.routeQuality(buildStreets(pieces, StreetScores.calibrationScorer(sessions), name))
        assertEquals(10, sync.size)
        assertEquals(sync, StreetScores.routeQuality(pieces, sessions, name))
        assertTrue(StreetScores.routeQuality(emptyList(), sessions, name).isEmpty())
    }

    @Test fun pieceScoreFromCalibrationReferencesPerProfile() {
        val scorer = StreetScores.calibrationScorer(listOf(record(6, 4.0), record(7, 2.0)))
        assertEquals(6.3, scorer(piece(1, 0, rms = 3.4))!!.value!!, 1e-9)
        assertEquals(StreetScore.betterThan(7), scorer(piece(1, 0, rms = 1.0)))
        assertEquals(StreetScore.worseThan(6), scorer(piece(1, 0, rms = 5.0)))
        assertNull(scorer(piece(1, 0, rms = 3.4, profile = "other")))
        val noBand = SurfaceSummary.fromJson(piece(1, 0).toJson().put("roadBands", JSONObject()))
        assertNull(scorer(noBand))
    }
}
