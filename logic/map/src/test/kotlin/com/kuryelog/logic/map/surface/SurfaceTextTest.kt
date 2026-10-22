package com.kuryelog.logic.map.surface

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.calibration.RoadQuality
import com.kuryelog.logic.map.calibration.RoadReferenceConflict
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Yol puanı satırları (YolGo `test/road_score_ui_test.dart` @ 870ab6e: `RoadScoreDisplay` testleri; ekran testleri `:app`'te). */
class SurfaceTextTest {
    private fun record(level: Int, rms: Double, speed: Double = 20.0, id: String = "run") = RoadCalibrationRecord.fromJson(JSONObject().put("id", id).put("at", 1000)
        .put("profile", "mount").put("quality", RoadQuality.fromLevel(level).key).put("mode", "fixed").put("requestedSeconds", 15).put("elapsedSeconds", 15).put("validSeconds", 12.0)
        .put("valid", true).put("rms", rms).put("averageKmh", speed).put("speedNotConstant", false).put("qualityLevel", level)
        .put("roadRms", rms).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0))

    private fun refs(vararg r: RoadCalibrationRecord) = RoadCalibrationReferences(r.toList())

    @Test fun boundLabelsUseLevelSuffixForThreeSixAndTenInTrAndAz() {
        for (n in listOf(3, 6, 10)) {
            val tr = SurfaceText.roadScore(refs(record(n, 2.0)), 3.0, 20.0, Lang.TR).lines.single()
            assertTrue(tr, tr.contains("$n seviyesinden kötü"))
            val az = SurfaceText.roadScore(refs(record(n, 2.0)), 3.0, 20.0, Lang.AZ).lines.single()
            assertTrue(az, az.contains("$n səviyyəsindən pis"))
        }
        assertTrue(SurfaceText.roadScore(refs(record(7, 2.0)), 1.0, 20.0, Lang.AZ).lines.single().contains("7 səviyyəsindən yaxşı"))
    }

    @Test fun scoreLineShowsOneDecimalOverTenAndTheRefereceBandWaits() {
        val r = refs(record(6, 4.0), record(7, 2.0))
        assertEquals("Yol puanı: 6,3/10", SurfaceText.roadScore(r, 3.4, 20.0, Lang.TR, showMissingLevels = false).lines.single())
        assertEquals("Yol balı: 6,3/10", SurfaceText.roadScore(r, 3.4, 20.0, Lang.AZ, showMissingLevels = false).lines.single())
        assertEquals("Yol puanı: Referans bekliyor", SurfaceText.roadScore(RoadCalibrationReferences(emptyList()), 3.4, 20.0, Lang.TR, showMissingLevels = false).lines.single())
    }

    @Test fun confirmedShockWarningAccompaniesTheScore() {
        val v = SurfaceText.roadScore(refs(record(7, 2.0)), 2.0, 20.0, Lang.TR, confirmedShock = true)
        assertTrue(v.lines.single().endsWith("⚠ Doğrulanmış darbe"))
        assertEquals(1, v.lines.count { it.contains("Doğrulanmış darbe") })
    }

    @Test fun invertedAndEqualReferencesNameLevelsAndRms() {
        for (rms in listOf(2.0, 5.0)) {
            val v = SurfaceText.roadScore(refs(record(6, 2.0), record(7, rms)), 3.0, 20.0, Lang.TR, showMissingLevels = false)
            val line = v.lines.single { it.contains("6 ve 7") }
            assertTrue(line.contains("birini tekrar kalibre et")); assertTrue(line.contains("2.000")); assertTrue(line.contains(String.format(java.util.Locale.US, "%.3f", rms)))
            assertEquals(if (rms == 2.0) "eşit" else "ters sırada", if (line.contains("eşit")) "eşit" else "ters sırada")
            assertFalse("çelişkide puan satırı yok", v.lines.any { it.startsWith("Yol puanı") })
        }
    }

    @Test fun outsideBandPromptForwardsTheDisplayedFallbackBand() {
        val v = SurfaceText.roadScore(refs(record(7, 2.0, speed = 30.0)), 3.0, 36.0, Lang.TR)
        assertEquals(30, v.promptBand); assertTrue(v.prompt!!.startsWith("Şu seviyeleri kalibre et"))
        assertTrue(v.lines.any { it.contains("Hız referans bantları dışında") })
        assertTrue(v.lines.any { it.contains("Farklı hız bandı · en yakın 30 km/h") })
    }

    @Test fun missingLevelsPromptIsPerBandAndUnknownSpeedHasNoBand() {
        val r = refs(record(6, 4.0), record(7, 2.0), record(8, 1.0, speed = 30.0))
        assertEquals("Şu seviyeleri kalibre et: 1, 2, 3, 4, 5, 8, 9, 10 · 20 ±2,5 km/h", SurfaceText.roadScore(r, 3.4, 20.0, Lang.TR).prompt)
        assertEquals("Şu seviyeleri kalibre et: 1, 2, 3, 4, 5, 6, 7, 9, 10 · 30 ±2,5 km/h", SurfaceText.missingLevelsPrompt(r, 30, Lang.TR))
        assertEquals("Bu səviyyələri kalibrasiya et: 1, 2, 3, 4, 5, 8, 9, 10 · 20 ±2,5 km/h", SurfaceText.missingLevelsPrompt(r, 20, Lang.AZ))
        val unknown = SurfaceText.roadScore(r, 3.4, null, Lang.TR)
        assertNull(unknown.prompt); assertTrue(unknown.lines.last().startsWith("Ölçüm hızı bilinmiyor"))
        val complete = RoadCalibrationReferences((1..10).map { record(it, 11.0 - it) })
        assertNull(SurfaceText.roadScore(complete, 3.0, 20.0, Lang.TR).prompt)
    }

    @Test fun measurementAndSurfaceLabelsInBothLanguages() {
        assertEquals("Yol ölçülüyor", SurfaceText.measurementLabel(MeasurementState.RIDING, Lang.TR))
        assertEquals("Yol ölçülür", SurfaceText.measurementLabel(MeasurementState.RIDING, Lang.AZ))
        assertEquals(6, MeasurementState.entries.map { SurfaceText.measurementLabel(it, Lang.TR) }.distinct().size)
        assertEquals(10, SurfaceLabel.entries.map { SurfaceText.surfaceLabel(it, Lang.AZ) }.distinct().size)
        assertEquals("Kapalı · rota kullanmasın", SurfaceText.passability(Passability.CLOSED, Lang.TR))
    }

    @Test fun pieceTitlesDirectionAndSubtitles() {
        val s = SurfaceSummary("1/a/0/1", 1, 0, 1, "p", 50.0, .9, 50.0, 0.0, 3, 0, 1.0, 4.0, 2, 0.0, 40.0, 49.0)
        assertEquals("Nizami · → 2", SurfaceText.pieceTitle("Nizami", s))
        assertEquals("Nizami · ← 2", SurfaceText.pieceTitle("Nizami", SurfaceSummary("1/a/1/1", 1, 1, 1, "p", 50.0, .9, 50.0, 0.0, 3, 0, 1.0, 4.0, 2, 0.0, 40.0, 49.0)))
        assertEquals("güven 90% · 3 geçiş · 2 darbe", SurfaceText.pieceSubtitle(s, Lang.TR))
        assertEquals("4 parça · 1.2 km", SurfaceText.streetSubtitle(4, 1200.0, Lang.TR)); assertEquals("3 hissə · 150 m", SurfaceText.streetSubtitle(3, 150.0, Lang.AZ))
    }

    // A10.4b: `:app` ekranlarındaki birleşik metinler buraya taşındı (TextCoverageTest: `:app`'te yerel harfli düz metin olmaz).
    @Test fun detailLinesInBothLanguages() {
        val s = SurfaceSummary("1/a/0/1", 1, 0, 1, "p", 50.0, .9, 50.0, 0.0, 3, 0, 1.0, 4.0, 2, 0.0, 40.0, 49.0)
        assertEquals(listOf("Rota/harita puanı: 50/100", "güven 90%", "3 bağımsız geçiş · 50 m ölçüldü", "Bozuk ölçülen mesafe: 0%", "2 tarafsız darbe olayı · 4.0 /100 m",
            "Geçilebilirlik: yüzeyden çıkarılmaz; elle kapanış ayrı kaydedilir", "Son ölçüm: 1970-01-01 04:00"), SurfaceText.detailLines(s, Lang.TR))
        val short = SurfaceSummary("1/a/0/1", 1, 0, 1, "p", 50.0, .9, 10.0, 5.0, 3, 0, 1.0, 4.0, 2, 0.0, 40.0, 49.0)
        assertEquals(listOf("Marşrut/xəritə balı: 50/100", "inam 90%", "3 müstəqil keçid · 10 m ölçüldü", "Nahamar ölçülən məsafə: 50%", "2 neytral zərbə hadisəsi · — /100 m",
            "Keçid: səthdən çıxarılmır; bağlanma ayrı qeyd olunur", "Son ölçmə: 1970-01-01 04:00"), SurfaceText.detailLines(short, Lang.AZ))
    }

    @Test fun impactEventLines() {
        assertEquals("Darbe · 7.46 m/s²", SurfaceText.impactTitle(7.456, Lang.TR))
        assertEquals("Zərbə · 7.46 m/s²", SurfaceText.impactTitle(7.456, Lang.AZ))
        assertEquals("1970-01-01 04:00:00\nOtomatik · tür bilinmiyor · GPS 3.0 m", SurfaceText.impactSubtitle(0, 3.0, Lang.TR))
        assertEquals("1970-01-01 04:00:00\nAvtomatik · növ bilinmir · GPS 3.0 m", SurfaceText.impactSubtitle(0, 3.0, Lang.AZ))
    }

    @Test fun technicalAndDiagnosticsBlocks() {
        val s = SurfaceSummary("k", 1, 0, 0, "p", 50.0, .9, 50.0, 0.0, 1, 0, 0.5, 4.0, 2, 7.0, 40.0, 49.0, p95 = 3.0, jerk = 1.234, bands = listOf(.1, .25))
        assertEquals("RMS 0.500 m/s² · jerk 1.23 m/s³\nDarbe max 7.00 · P95 3.00 m/s²\nGyro — rad/s\nGPS 4.0 m · 50 m segment · 100% ölçüm kapsamı\nBantlar 0.100 / 0.250",
            SurfaceText.technical(s))
        assertEquals("Durum: closed\nNeden: day_closed\nBoot ms: 0\nGüven: 0.0\nBekleyen: 0 · kabul: 0\nAlgoritma v2 · 50 m · 8.0 s tampon\nHP 0.7 Hz · LP 35.0 Hz · darbe 6.0 m/s²",
            SurfaceText.diagnostics(SurfaceView(), SurfaceProfile()))
    }

    @Test fun bandStatusShowsPassesOnlyWhenValidated() {
        assertEquals("3 / 4 geçiş · kalibre", SurfaceText.bandStatus(CalibrationBand(.2, .9, 3, 4, 0), Lang.TR))
        assertEquals("3 / 4 keçid · kalibrə olunub", SurfaceText.bandStatus(CalibrationBand(.2, .9, 3, 4, 0), Lang.AZ))
        assertEquals("Kalibrasyon bekliyor", SurfaceText.bandStatus(null, Lang.TR))
        assertEquals("Kalibrasiya gözlənilir", SurfaceText.bandStatus(CalibrationBand(.2, .9, 2, 4, 0), Lang.AZ))
    }

    @Test fun conflictLineMatchesTheRoadScoreLine() {
        assertEquals("6 ve 7 ters sırada — birini tekrar kalibre et (RMS 6: 2.000, 7: 5.000 m/s²)", SurfaceText.conflictLine(RoadReferenceConflict(6, 7, 2.0, 5.0), Lang.TR))
        assertEquals("6 və 7 bərabərdir — birini yenidən kalibrasiya et (RMS 6: 2.000, 7: 2.000 m/s²)", SurfaceText.conflictLine(RoadReferenceConflict(6, 7, 2.0, 2.0), Lang.AZ))
        val r = refs(record(6, 2.0), record(7, 5.0))
        assertEquals(r.conflicts(20).map { SurfaceText.conflictLine(it, Lang.TR) }, SurfaceText.roadScore(r, 3.0, 20.0, Lang.TR, showMissingLevels = false).lines)
    }

    @Test fun uiTextHasBothLanguagesAndIsTranslated() {
        for (t in SurfaceUiText.entries) { assertTrue(t.name, t.tr.isNotBlank()); assertTrue(t.name, t.az.isNotBlank()) }
        assertEquals(setOf("NEW_PROFILE_HINT"), SurfaceUiText.entries.filter { it.tr == it.az }.map { it.name }.toSet())
    }
}
