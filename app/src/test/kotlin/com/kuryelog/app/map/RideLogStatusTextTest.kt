package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** YolGo `ride_log_status_test.dart` widget testi (1; döngü üç durumla aynen; A6.2b, MAP.md 12.7, 12.13): `RideLogStatusText` durum yazısı (`ride-log-status`) üç durumda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RideLogStatusTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun durumYazisiUcDurumda() {
        var logging by mutableStateOf(true)
        var active by mutableStateOf(true)
        compose.setContent { WeemTheme(false) { RideLogStatusText(logging, active, Lang.TR) } }
        for ((l, a, text) in listOf(Triple(true, true, "Kayıt sürüyor"), Triple(true, false, "Gün başlayınca kaydedilir"), Triple(false, true, "Kayıt kapalı"))) {
            logging = l; active = a
            compose.waitForIdle()
            compose.onNodeWithTag("ride-log-status").assertExists()
            compose.onAllNodes(hasText(text)).assertCountEquals(1)
        }
    }
}
