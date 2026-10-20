package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.map.ui.CameraMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** YolGo `locate_button_test.dart` 3. test (1; saf 2 test `LocateButtonTest`; A6.2b, MAP.md 6.14): tek düğme kipe göre simge gösterir ve dokununca çağırır; 64×64. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmartLocateButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tekDugmeKipeGoreSimgeGosterirVeDokununcaCagirir() {
        var mode by mutableStateOf(CameraMode.FOLLOW_HEADING)
        var taps = 0
        compose.setContent { WeemTheme(false) { SmartLocateButton(mode, onTap = { taps++ }) } }
        compose.onNodeWithTag("locate-followHeading").assertExists()
        compose.onNodeWithTag("smart-locate").assertExists()

        mode = CameraMode.FREE
        compose.waitForIdle()
        compose.onNodeWithTag("locate-free").assertExists()

        mode = CameraMode.FOLLOW_NORTH
        compose.waitForIdle()
        compose.onNodeWithTag("locate-followNorth").assertExists()

        compose.onNodeWithTag("smart-locate").performClick()
        assertEquals(1, taps)
        val b = compose.onNodeWithTag("smart-locate").getBoundsInRoot()
        assertEquals(64.dp, b.right - b.left)
        assertEquals(64.dp, b.bottom - b.top)
    }
}
