package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.reminders.ReminderKind
import com.kuryelog.logic.map.reminders.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hatırlatıcı ayarları (MAP.md 13.16): özet, anahtar, aralık düğmeleri, "Dene", "Nasıl çalışır"; erteleme düğmesi yok. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h2600dp-port-xxhdpi")
class ReminderSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun text(s: String, substring: Boolean = false) = compose.onAllNodes(hasText(s, substring = substring))

    @Test
    fun hatirlaticiAyarlariOzetAnahtarVeDetay() {
        var settings by mutableStateOf(ReminderSettings.defaults)
        val changes = ArrayList<ReminderKind>()
        var previewed: ReminderKind? = null
        compose.setContent {
            WeemTheme(false) {
                ReminderSettingsSection(
                    settings, Lang.TR,
                    onChange = { k, f -> changes += k; settings = ReminderSettings(settings.items.map { if (it.kind == k) f(it) else it }) },
                    onPreview = { previewed = it },
                )
            }
        }
        text("her 45 dk · gün içinde").assertCountEquals(1)
        text("Kapalı").assertCountEquals(2) // kontrol + yemek
        compose.onNodeWithTag("reminder-switch-food").performClick()
        compose.waitForIdle()
        text("saat 15:15").assertCountEquals(1)
        compose.onNodeWithTag("reminder-tile-water").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reminder-plus-water").performClick()
        compose.waitForIdle()
        assertEquals(50, settings.of(ReminderKind.WATER).intervalMin)
        compose.onNodeWithTag("reminder-try-water").performClick()
        assertEquals(ReminderKind.WATER, previewed)
        compose.onNodeWithTag("reminder-how").performClick()
        compose.waitForIdle()
        text("Yalnız belirlenen zamanda gelir;", substring = true).assertCountEquals(1)
        assertTrue(changes.isNotEmpty())
        compose.onAllNodes(hasTestTag("reminder-snooze")).assertCountEquals(0)
    }
}
