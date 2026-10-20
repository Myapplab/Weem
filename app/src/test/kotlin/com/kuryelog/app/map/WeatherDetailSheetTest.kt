package com.kuryelog.app.map

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.app.map.MapTestFixtures.report4
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `hud_layout_test.dart` 2. test (1; A6.2b, MAP.md 10.9, 6.14): saat ayrıntısı seçilen gerçek saati kullanır ve ekranın %40'ı olan, klavyeyi düşen yükseklikte (844 − 300 dp)
 * açılır; alt kenarı klavyenin üstünde; içerik kaydırılır. Klavye yerine `availableHeightDp` (Robolectric'te IME yüksekliği verilmez).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h844dp-port-xxhdpi")
class WeatherDetailSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun saatAyrintisiGercekSaatiKullanirVeYuzdeKirkKlavyeyiDuser() {
        var index by mutableIntStateOf(0)
        val report = report4()
        compose.setContent {
            WeemTheme(true) {
                Box(Modifier.size(320.dp, 844.dp)) { WeatherDetailSheet(report, index, availableHeightDp = 844.0 - 300.0, lang = Lang.TR) }
            }
        }
        for (i in 0..3) {
            index = i
            compose.waitForIdle()
            val b = compose.onNodeWithTag("weather-detail").getBoundsInRoot()
            assertEquals((844 - 300) * .4, (b.bottom - b.top).value.toDouble(), 1.0)
            assertTrue(b.bottom <= (844 - 300).dp + 1.dp)
            compose.onAllNodes(hasText("${(23.0 - i).toInt()}°", substring = true) and hasAnyAncestor(hasTestTag("weather-detail"))).assertCountEquals(1)
            compose.onNodeWithTag("weather-detail-scroll").performTouchInput { swipeUp() }
            compose.waitForIdle()
            val scroll = compose.onNodeWithTag("weather-detail-scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("saat $i kaydırılır", scroll.value() > 0f)
        }
    }
}
