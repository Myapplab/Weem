package com.kuryelog.app.map

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.reminders.ActiveReminder
import com.kuryelog.logic.map.reminders.ReminderItem
import com.kuryelog.logic.map.reminders.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `reminder_card_test.dart` widget 2 test (A6.2b, MAP.md 13.16, 13.18; süre testi `ReminderTimingTest`'te): kullanıcı isteği — hatırlatıcı haritayı kapatmayan yarı boy kart: tek satır ≤ 44
 * dp, uzun metin taşmaz · ✓ tamamlar, erteleme düğmesi yok.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderCardTest {
    @get:Rule val compose = createComposeRule()

    private val item = ReminderItem(ReminderKind.CHECK, text = "Batarya, lastik, fren ve çantayı kontrol et; uzun metin tek satırda kısalmalı")

    private fun show(initial: ActiveReminder?, onDone: () -> Unit = {}) = compose.setContent {
        var active by mutableStateOf(initial)
        WeemTheme(false) { Box(Modifier.width(360.dp), contentAlignment = Alignment.TopCenter) { ReminderCard(active, Lang.TR, onDone = { onDone(); active = null }) } }
    }

    @Test
    fun kartKucukVeTekSatirUzunMetinTasmaz() {
        show(ActiveReminder(item, 1_792_000_000_000L))
        val b = compose.onNodeWithTag("reminder-card").getBoundsInRoot()
        assertTrue("kart ${b.bottom - b.top} ≤ 44 dp", b.bottom - b.top <= 44.dp)
        compose.onAllNodes(hasText("Kontrol et", substring = true)).assertCountEquals(1)
    }

    @Test
    fun tikTamamlarErtelemeDugmesiYok() {
        var done = 0
        show(ActiveReminder(item, 1_792_000_000_000L)) { done++ }
        compose.onAllNodes(hasTestTag("reminder-snooze")).assertCountEquals(0)
        compose.onNodeWithTag("reminder-done").performClick()
        compose.waitForIdle()
        assertEquals(1, done)
        compose.onAllNodes(hasTestTag("reminder-card")).assertCountEquals(0)
    }
}
