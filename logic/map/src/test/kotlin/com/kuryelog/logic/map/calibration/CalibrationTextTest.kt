package com.kuryelog.logic.map.calibration

import com.kuryelog.logic.core.Lang
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Kalibrasyon ekranının metinleri (YolGo `road_score_ui_test` kalibrasyon ekranı testleri, model düzeyinde; ekran `:app`'te çizilir). */
class CalibrationTextTest {
    private fun rec(id: String, level: Int?, rms: Double, speed: Double, profile: String = "g3-default", road: Boolean = true, validSeconds: Double = 12.0, legacy: Boolean = false) =
        RoadCalibrationRecord.fromJson(JSONObject().put("id", id).put("at", 1_790_000_000_000L).put("profile", profile)
            .put("quality", RoadQuality.fromLevel(level ?: 8).key).put("mode", "fixed").put("requestedSeconds", 15).put("elapsedSeconds", 15).put("validSeconds", validSeconds)
            .put("valid", true).put("rms", rms).put("averageKmh", speed).put("speedNotConstant", false)
            .also { if (!legacy) it.put("qualityLevel", level) }
            .also { if (road && !legacy) it.put("roadRms", rms).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0) })

    // Ekran fixture'ı (YolGo `_Calibration`): referans 6 → 4,0 ve 7 → 2,0 (20 km/h), ölçüm 6 → 3,4 (24 km/h), 8 → 1,0 (30 km/h), eski ve yanlış profil.
    private val sessions = listOf(
        rec("reference-6", 6, 4.0, 20.0), rec("reference-7", 7, 2.0, 20.0), rec("measurement", 6, 3.4, 24.0), rec("other-band", 8, 1.0, 30.0),
        rec("legacy", null, .01, 21.6, legacy = true), rec("wrong-profile", 10, .01, 21.6, profile = "other-mount"),
    )
    private val refs = RoadCalibrationReferences(sessions.filter { it.profile == "g3-default" })

    @Test fun missingLevelsPromptFollowsTheSelectedBand() {
        assertEquals("Şu seviyeleri kalibre et: 1, 2, 3, 4, 5, 8, 9, 10 · 20 ±2,5 km/h", com.kuryelog.logic.map.surface.SurfaceText.missingLevelsPrompt(refs, 20, Lang.TR))
        // Ölçüm 24 km/h 25 bandında: yol RMS'i olan 6. seviye (3,4) 25 bandında; 30 bandında yalnız 8.
        assertEquals("Şu seviyeleri kalibre et: 1, 2, 3, 4, 5, 6, 7, 9, 10 · 30 ±2,5 km/h", com.kuryelog.logic.map.surface.SurfaceText.missingLevelsPrompt(refs, 30, Lang.TR))
    }

    @Test fun savedRunScoreExcludesItselfAndWarnsAboutADifferentBand() {
        val r = sessions.first { it.id == "measurement" }
        val card = CalibrationText.recordCard(r, refs, "g3-default", Lang.TR)
        val lines = card.score!!.lines
        assertTrue(lines.any { it.startsWith("Yol puanı: 6,3/10") })
        assertTrue(lines.any { it.contains("Farklı hız bandı · en yakın 20 km/h referansı kullanıldı") })
        assertNull("kayıt kartında eksik seviye listesi tekrarlanmaz", card.score!!.prompt)
    }

    @Test fun cardsRequestRoadRmsRecalibrationOnlyWhenTheRoadValueIsMissing() {
        val legacy = CalibrationText.recordCard(sessions.first { it.id == "legacy" }, refs, "g3-default", Lang.TR)
        assertEquals("Eski kayıt · İYİ · SABİT HIZ", legacy.title)
        assertTrue(legacy.lines.contains("E10 için yeniden kalibre et")); assertNull(legacy.score)
        val noRoad = CalibrationText.recordCard(rec("x", 6, 4.0, 20.0, road = false), refs, "g3-default", Lang.AZ)
        assertTrue(noRoad.lines.contains("E10 üçün yenidən kalibrasiya et"))
        val ok = CalibrationText.recordCard(sessions.first { it.id == "reference-6" }, refs, "g3-default", Lang.TR)
        assertFalse(ok.lines.contains("E10 için yeniden kalibre et")); assertTrue(ok.lines.any { it.startsWith("Yol RMS 4.000 m/s² · 12.0 sn") })
        val other = CalibrationText.recordCard(sessions.first { it.id == "wrong-profile" }, refs, "g3-default", Lang.TR)
        assertTrue(other.lines.first().contains("Başka montaj profili")); assertNull(other.score)
    }

    @Test fun recordCardHeaderLines() {
        val c = CalibrationText.recordCard(sessions.first { it.id == "reference-6" }, refs, "g3-default", Lang.TR)
        assertEquals("6/10 · ORTA · SABİT HIZ", c.title)
        assertEquals("2026-09-21 18:13:20\n15.0 / 15 sn · geçerli veri 12.0 sn\n20.0 km/h · RMS 4.000 m/s² · GEÇERLİ", c.lines.first())
    }

    @Test fun levelRowsAndThresholds() {
        val full = RoadCalibrationReferences(listOf(10, 8, 6, 4, 2).flatMap { n -> (0 until 3).map { rec("$n-$it", n, 11.0 - n, 20.0) } })
        val row = CalibrationText.levelRow(RoadQuality.GOOD, full, 20)
        assertTrue(row.complete); assertEquals("İYİ · 3/3 kalibrasyon, sabit hız: var", row.title); assertEquals("20 km/h RMS medyanı: 3.000 m/s²", row.subtitle)
        assertEquals("20 km/h RMS eşikleri: 2.00 · 4.00 · 6.00 · 8.00 m/s²", CalibrationText.thresholds(full, 20))
        assertFalse(CalibrationText.levelRow(RoadQuality.GOOD, refs, 25).complete)
        assertTrue(CalibrationText.thresholds(refs, 25).startsWith("Aynı hız bandında"))
    }

    @Test fun phaseTitles() {
        fun st(p: CalibrationPhase, w: Boolean = false) = CalibrationState(p, true, remaining = 2, waitingOffset = w)
        assertEquals("Dur · GPS bekleniyor", CalibrationText.phaseTitle(st(CalibrationPhase.COUNTDOWN, true)))
        assertEquals("2 · Hazırlan", CalibrationText.phaseTitle(st(CalibrationPhase.COUNTDOWN)))
        assertEquals("Kalibrasyon başladı", CalibrationText.phaseTitle(st(CalibrationPhase.RECORDING)))
        assertEquals("Kalibrasyon bitti", CalibrationText.phaseTitle(st(CalibrationPhase.DONE)))
    }
}
