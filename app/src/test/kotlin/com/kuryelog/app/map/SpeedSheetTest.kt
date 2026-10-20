package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** YolGo `v150_features_test.dart` "hız penceresi: − / + hızı değiştirir ve kaydeder" (1; A6.2b, MAP.md 10.2, 16.3). Kayıt (`settings.v1`) yerine [onChange] sonucu denetlenir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SpeedSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hizPenceresiArtiEksiHiziDegistirirVeKaydeder() {
        var saved: List<Double>? = null
        compose.setContent {
            var choices by remember { mutableStateOf(listOf(20.0, 25.0, 29.0)) }
            WeemTheme(false) { SpeedSheet(choices, emptyList(), 598.0, Lang.TR, onChange = { choices = it; saved = it }) }
        }
        compose.onAllNodes(hasText("20 km/s")).assertCountEquals(1)
        compose.onNodeWithTag("speed-plus-0").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("21 km/s")).assertCountEquals(1)
        assertEquals(listOf(21.0, 25.0, 29.0), saved)
    }
}
