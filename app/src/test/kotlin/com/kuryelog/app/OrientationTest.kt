package com.kuryelog.app

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kuryelog.logic.app.ScreenOrientation
import com.kuryelog.logic.app.Text as Str
import com.kuryelog.logic.app.ThemeMode
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A10.4g (SORU-N6): Daha fazla → Ekran yönü; seçim geri çağrıya gider, `ActivityInfo` eşlemesi sensörlü kilit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OrientationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun flagsUseSensorLocks() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, orientationFlag(ScreenOrientation.FREE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, orientationFlag(ScreenOrientation.PORTRAIT))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientationFlag(ScreenOrientation.LANDSCAPE))
    }

    @Test fun settingsRowsChangeOrientation() {
        val lang = Lang.TR
        val picked = mutableListOf<ScreenOrientation>()
        compose.setContent {
            var o by remember { mutableStateOf(ScreenOrientation.FREE) }
            WeemTheme(dark = false) {
                Box { WeemApp(lang, lang.key, ThemeMode.LIGHT, "1.0.0", {}, {}, orientation = o, onOrientation = { picked += it; o = it }) }
            }
        }
        compose.onNodeWithTag("tab_more").performClick()
        compose.onNodeWithText(Str.SETTINGS_ORIENTATION.get(lang)).performScrollTo()
        compose.onNodeWithText(Str.ORIENTATION_FREE.get(lang)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(Str.ORIENTATION_PORTRAIT.get(lang)).performScrollTo().performClick()
        compose.onNodeWithText(Str.ORIENTATION_PORTRAIT.get(lang)).assertIsSelected()
        compose.onNodeWithText(Str.ORIENTATION_LANDSCAPE.get(lang)).performScrollTo().performClick()
        assertEquals(listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE), picked)
    }
}
