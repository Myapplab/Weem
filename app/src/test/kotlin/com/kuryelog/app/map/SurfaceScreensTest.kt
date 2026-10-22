package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.calibration.RoadCalibrationRecord
import com.kuryelog.logic.map.calibration.RoadCalibrationReferences
import com.kuryelog.logic.map.calibration.RoadQuality
import com.kuryelog.logic.map.surface.RoadBandEvidence
import com.kuryelog.logic.map.surface.SurfaceSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A10.4b: Yol puanları paneli ve Yollarım'a bağlantısı (YolGo `road_score_ui_test` "quality tab is a plain street list…" ve "road quality shows a reference
 * score…" sınamaları; veri `SurfaceData` ile doğrudan). Cihazda denenmedi: sensör, servis ve yüzen panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SurfaceScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun piece(way: Long, index: Int, rms: Double) = SurfaceSummary("$way/40.000000,49.000000/0/$index", way, 0, index, "g3-default", 50.0, .9, 50.0, 0.0, 1, 0, rms, 4.0, 0, 0.0, 40.0, 49.0,
        roadBands = mapOf(20 to RoadBandEvidence(20, rms, 1, 20 / 3.6)))

    private fun record(level: Int, rms: Double) = RoadCalibrationRecord.fromJson(JSONObject().put("id", "r$level").put("at", 1000).put("profile", "g3-default").put("quality", RoadQuality.fromLevel(level).key)
        .put("mode", "fixed").put("requestedSeconds", 15).put("elapsedSeconds", 15).put("validSeconds", 12.0).put("valid", true).put("rms", rms).put("averageKmh", 20.0)
        .put("speedNotConstant", false).put("qualityLevel", level).put("roadRms", rms).put("roadRmsVersion", 1).put("roadValidSeconds", 12.0))

    // Referans (20 km/h): 6 → 4,0 · 7 → 2,0. Her sokakta 4 × 50 m parça; "Yol #4" tek parça.
    private val data = SurfaceData(
        listOf(1L to 4.0, 2L to 5.0, 3L to 1.0).flatMap { (way, rms) -> (0 until 4).map { piece(way, it, rms) } } + piece(123, 0, 3.4),
        listOf(record(6, 4.0), record(7, 2.0)), mapOf(1L to "Yol #1", 2L to "Yol #2", 3L to "Yol #3"),
    )

    @Test fun streetListIsWorstFirstTooFewLast() {
        val rows = streetRows(data, Lang.TR)
        assertEquals(listOf("Yol #2" to "6'dan kötü", "Yol #1" to "6,0", "Yol #3" to "7'den iyi", "isimsiz yol" to "ölçüm az"), rows.map { it.title to it.score })
        assertEquals("4 parça · 200 m", rows.first().subtitle)
    }

    @Test fun panelShowsIntroStateRowsAndForwardsTaps() {
        val rows = streetRows(data, Lang.TR)
        val taps = ArrayList<String>()
        var layer = false
        compose.setContent {
            WeemTheme(false) { SurfaceQualityPanel(Lang.TR, "Yol ölçülüyor", rows, layer, { layer = it; taps += "layer $it" }, { k, t -> taps += "street $k $t" }, { taps += "setup" }) }
        }
        compose.onNodeWithText("Yol puanları").assertExists()
        compose.onNodeWithTag("surface-state").assertExists()
        compose.onAllNodes(hasText("Belirsiz", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("street-name:Yol #2").performClick()
        compose.onNodeWithTag("surface-setup").performClick()
        compose.onNodeWithText("Haritada yüzey renkleri").performClick()
        assertEquals(listOf("street name:Yol #2 Yol #2", "setup", "layer true"), taps)
        compose.onAllNodesWithText("Henüz fiziksel yol ölçümü yok").assertCountEquals(0)
    }

    @Test fun emptyPanelSaysThereIsNoMeasurementYet() {
        compose.setContent { WeemTheme(false) { SurfaceQualityPanel(Lang.AZ, "Yol ölçülür", emptyList(), false, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Hələ fiziki yol ölçməsi yoxdur").assertExists()
        compose.onNodeWithText("Yol balları").assertExists()
    }

    @Test fun roadScoreLinesShowTheReferenceScoreAndTheMissingLevelPrompt() {
        val refs = RoadCalibrationReferences(data.records)
        var band: Int? = null
        compose.setContent { WeemTheme(false) { RoadScoreLines(refs, 3.4, 20.0, Lang.TR, onCalibrate = { band = it }) } }
        compose.onNodeWithText("Yol puanı: 6,3/10").assertExists()
        compose.onNodeWithText("Şu seviyeleri kalibre et: 1, 2, 3, 4, 5, 8, 9, 10 · 20 ±2,5 km/h").performClick()
        assertEquals(20, band)
    }

    @Test fun roadsScreenRendersTheSurfaceSlotOnlyInTheQualityTab() {
        compose.setContent {
            WeemTheme(false) { RoadsScreen(RoadsScreenState(), Lang.TR, RoadsActions(), onBack = {}, surface = { androidx.compose.material3.Text("YÜZEY-BÖLÜMÜ") }) }
        }
        compose.onNodeWithText("YÜZEY-BÖLÜMÜ").assertExists()
        compose.onNodeWithTag("roads-tab-1").performClick()
        compose.onAllNodesWithText("YÜZEY-BÖLÜMÜ").assertCountEquals(0)
    }
}
