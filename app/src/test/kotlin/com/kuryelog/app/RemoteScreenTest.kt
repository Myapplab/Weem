package com.kuryelog.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.remote.RemoteText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A9.1: Uzaktan sekmesi 2 telefon modu kapalıyken (varsayılan) yalnız yönlendirme metnini gösterir; düğme yok. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class RemoteScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun withoutTwoPhoneModeShowsWhereToEnableIt() {
        compose.setContent { WeemTheme(dark = false) { RemoteScreen(Lang.TR) } }
        compose.onNodeWithTag("remote_need_link").assertIsDisplayed()
        compose.onNodeWithText(RemoteText.NEED_LINK.get(Lang.TR)).assertIsDisplayed()
        compose.onNodeWithTag("remote_view").assertDoesNotExist()
        compose.onNodeWithTag("remote_share").assertDoesNotExist()
    }
}
