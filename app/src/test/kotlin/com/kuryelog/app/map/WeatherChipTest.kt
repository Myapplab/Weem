package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.app.map.MapTestFixtures.report4
import com.kuryelog.app.map.MapTestFixtures.report8
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.ui.HudLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `:app` Robolectric `WeatherChipTest` (2; A6.2b, MAP.md 10.9, 16.3): `v160_y1_y2_test.dart` "şerit 4 saat, açılınca 8 satır ve 8. saatin ayrıntısı" ve `v170_features_test.dart`
 * "hava: yuvarlak; 4 saat de yuvarlak; saatin ayrıntısı yalnız o saate dokununca".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h900dp-port-xxhdpi")
class WeatherChipTest {
    @get:Rule val compose = createComposeRule()

    private val now = MapTestFixtures.AT

    @Test
    fun serit4SaatAcilinca8SatirVe8SaatinAyrintisiAcilir() {
        compose.setContent { WeemTheme(true) { WeatherChip(WeatherView(report = report8()), Lang.TR, now) } }
        compose.onNodeWithTag("weather-compact").performClick()
        compose.waitForIdle()
        // Şerit: Şimdi + 11:00, 12:00, 13:00; 14:00 yok.
        compose.onAllNodes(hasText("13:00")).assertCountEquals(1)
        compose.onAllNodes(hasText("14:00")).assertCountEquals(0)
        compose.onNodeWithTag("weather-toggle").performClick()
        compose.waitForIdle()
        for (i in 0 until 8) compose.onNodeWithTag("weather-hour-$i").assertExists()
        compose.onAllNodes(hasTestTag("weather-hour-8")).assertCountEquals(0)
        compose.onNodeWithTag("weather-hour-7").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("weather-detail").assertExists()
        compose.onAllNodes(hasText("27°", substring = true) and hasAnyAncestor(hasTestTag("weather-detail"))).assertCountEquals(1)
    }

    @Test
    fun havaYuvarlakKapaliyken4SaatDeYuvarlakSaatinAyrintisiYalnizSaateDokununca() {
        compose.setContent { WeemTheme(false) { WeatherChip(WeatherView(report = report4()), Lang.TR, now) } }
        val b = compose.onNodeWithTag("live-weather").getBoundsInRoot()
        assertEquals(HudLayout.WEATHER_COMPACT_DP.dp, b.right - b.left)
        compose.onAllNodes(hasText("23°")).assertCountEquals(1)
        compose.onAllNodes(hasText("40%")).assertCountEquals(1)
        compose.onNodeWithTag("weather-compact").performClick()
        compose.waitForIdle()
        for (i in 0 until 4) compose.onNodeWithTag("weather-strip-$i").assertExists()
        // Şeritte ayrıntı yazısı yok; yalnız saate dokununca.
        compose.onAllNodes(hasText("Hissedilen", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("weather-detail")).assertCountEquals(0)
        compose.onNodeWithTag("weather-strip-0").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("weather-detail").assertExists()
    }
}
