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
import androidx.compose.ui.test.performTextInput
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.reminders.ReminderItem
import com.kuryelog.logic.map.reminders.ReminderKind
import com.kuryelog.logic.map.reminders.ReminderSettings
import com.kuryelog.logic.map.tasks.DailyTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/** Günlük işler, görev uyarı kartı ve hatırlatıcı ayarları (MAP.md 13.14, 13.16, 13.18 "Yeni" `AppAlertsTest`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TasksRemindersUiTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneOffset.ofHours(4)
    private val now = 1_790_000_000_000L
    private val here = GeoPoint(40.4, 49.8)
    private fun text(s: String, substring: Boolean = false) = compose.onAllNodes(hasText(s, substring = substring))

    @Test
    fun gorevListesiBosMetinKartlarVeSilmeOnayi() {
        var tasks by mutableStateOf(emptyList<DailyTask>())
        val deleted = ArrayList<String>()
        compose.setContent { WeemTheme(false) { TasksScreen(tasks, Lang.TR, zone, now, TaskActions(onDelete = { deleted += it })) } }
        text("Henüz iş eklenmedi").assertCountEquals(1)
        tasks = listOf(DailyTask("a", "Banka", "Filial", here, now), DailyTask("b", "Eczane", "Nizami", here, now + 60_000, completedAtMs = now))
        compose.waitForIdle()
        text("Banka").assertCountEquals(1)
        text("Bekliyor").assertCountEquals(1)
        text("Tamamlandı · rota açıldı").assertCountEquals(1)
        compose.onNodeWithTag("task-delete-a").performClick()
        compose.waitForIdle()
        text("Bu işi sil?").assertCountEquals(1)
        compose.onNodeWithTag("task-delete-confirm").performClick()
        assertEquals(listOf("a"), deleted)
    }

    @Test
    fun duzenleyiciAcilir() {
        compose.setContent { WeemTheme(false) { TasksScreen(emptyList(), Lang.TR, zone, now, TaskActions()) } }
        compose.onNodeWithTag("tasks-add").performClick()
        compose.waitForIdle()
        text("Günlük iş ekle").assertCountEquals(1)
    }

    @Test
    fun duzenleyiciKonumuBulmadanKaydetmezBulununcaKaydeder() {
        val added = ArrayList<DailyTask>()
        val actions = TaskActions(onAdd = { added += it }, resolve = { if (it.contains("ASAN")) FoundPlace("ASAN Xidmət", here) else null })
        compose.setContent { WeemTheme(false) { TasksScreen(emptyList(), Lang.TR, zone, now, actions) } }
        compose.onNodeWithTag("tasks-add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("task-save").performClick()
        compose.waitForIdle()
        text("İş adını yaz ve hedef konumu bul.").assertCountEquals(1)
        compose.onNodeWithTag("task-title").performTextInput("Belge")
        compose.onNodeWithTag("task-place").performTextInput("yok yer")
        compose.onNodeWithTag("task-find").performClick()
        compose.waitForIdle()
        text("Konum bulunamadı.", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("task-place").performTextInput(" ASAN")
        compose.onNodeWithTag("task-find").performClick()
        compose.waitForIdle()
        text("Kaydetmeden önce doğru şubeyi kontrol et.", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("task-save").performClick()
        compose.waitForIdle()
        assertEquals(1, added.size)
        assertEquals("Belge", added.single().title); assertEquals("ASAN Xidmət", added.single().place); assertEquals(here, added.single().point)
    }

    @Test
    fun gorevUyarisiYakinligiGosterirRotaVeKapat() {
        val t = DailyTask("a", "Banka", "Filial", GeoPoint(40.4 + 500 / 111195.0, 49.8), now)
        val routed = ArrayList<String>()
        val closed = ArrayList<String>()
        compose.setContent { WeemTheme(false) { TaskAlertCard(listOf(t), here, Lang.TR, { routed += it.id }, { closed += it.id }) } }
        text("Banka").assertCountEquals(1)
        text("m yakınında", substring = true).assertCountEquals(1)
        text("Kapatırsan iş beklemeye devam eder.").assertCountEquals(1)
        compose.onNodeWithTag("task-alert-route").performClick()
        compose.onNodeWithTag("task-alert-close").performClick()
        assertEquals(listOf("a"), routed); assertEquals(listOf("a"), closed)
    }
}
